/*
 *  Copyright 2023 The original authors
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package dev.morling.onebrc;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.util.*;

import static java.nio.channels.FileChannel.MapMode.READ_ONLY;

public final class CalculateAverage_kogupta {
    // station name: non-null, UTF-8 string, <= 100 bytes, <= 10k unique names
    // temperature: non null, [-99.9, 99.9], only 1 decimal place
    public static void main(String[] args) throws IOException {
        // Stream<String> lines = Files.lines()
        // it internally creates chunked mappedByteBuffer
        // do it manually instead

        // use an un-synchronized map for each chunk/thread
        // merge these maps to a bigger, sorted map

        File f = new File(args[0]);
        long length = f.length();
        long segmentSize = 1 << 30;

        // !! byte buffer processing !!
        // [start: 13,786,487,663, end: 13,786,488,692, length: 1,029]

        // find segments
        try (RandomAccessFile raf = new RandomAccessFile(f, "r")) {
            // create segments
            // ensure that segments are aligned - ends on line separator, starts after line separator
            ArrayList<Segment> segments = createSegments(length, raf, segmentSize);

            Map<String, Stat> stats = segments.stream()
                                          .parallel()
                                          .map(segment -> processSegment(segment))
                                          .reduce((a, b) -> merge(a, b))
                                          .orElse(Collections.emptyMap());

            TreeMap<String, Stat> sortedMap = new TreeMap<>(stats);
            System.out.println(sortedMap);
        }
    }

    private static Map<String, Stat> merge(Map<String, Stat> a, Map<String, Stat> b) {
        for (var kv : a.entrySet()) {
            var key = kv.getKey();
            Stat other = b.get(key);
            if (other != null) {
                // key in b
                // merge current values into b
                kv.getValue().mergeInto(other);

            } else {
                // key not in b
                b.put(key, kv.getValue());
            }
        }

        return b;
    }

    private static Map<String, Stat> processSegment(Segment segment) {
        Map<String, Stat> result = new HashMap<>();
        try {
            MappedByteBuffer buffer = segment.raf.getChannel().map(READ_ONLY, segment.start, segment.size());
            // Toluca;19.1
            // Las Vegas;31.0
            // Yinchuan;-3.2

            // TODO
            byte[] cityBytes = new byte[100];
            byte[] tempBytes = new byte[5];
            while (buffer.hasRemaining()) {
                int start = buffer.position();
                int index = findIndex(buffer, (byte) ';');
                int length = index - start;

                buffer.get(start, cityBytes, 0, length);
                String city = new String(cityBytes, 0, length);

                int end = findIndex(buffer, (byte) '\n');
                int tempLength = end - index - 1;
                buffer.get(index + 1, tempBytes, 0, tempLength);
                String temp = new String(tempBytes, 0, tempLength);
                double v = Double.parseDouble(temp);

                result.merge(city, new Stat(v), Stat::merge);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        return result;
    }

    private static int findIndex(ByteBuffer buffer, byte b) {
        while (buffer.hasRemaining()) {
            if (buffer.get() == b)
                return buffer.position() - 1;
        }

        return buffer.position();
    }

    private static ArrayList<Segment> createSegments(long length, RandomAccessFile raf, long segmentSize) throws IOException {
        long end = -1;
        ArrayList<Segment> segments = new ArrayList<>();
        while (end < length) {
            long start = end + 1;
            end = nextLineSeparator(raf, Math.min(start + segmentSize, length), length);
            Segment segment = new Segment(raf, start, end);
            segments.add(segment);
        }

        // checkSegmentAlignment(segments, raf, length);
        return segments;
    }

    private static long nextLineSeparator(RandomAccessFile raf, long from, long length) throws IOException {
        raf.seek(from);
        while (from < length) {
            if (raf.read() == '\n')
                break;
            from++;
        }

        return from;
    }

    record Segment(RandomAccessFile raf, long start, long end) {
        long size() {
            return end - start;
        }

        @Override
        public String toString() {
            return "[start: %,3d, end: %,3d, length: %,3d]".formatted(start, end, end - start);
        }
    }

    public static final class Stat {
        int count;
        double sum;
        double max;
        double min;

        public Stat(double v) {
            count = 1;
            sum = max = min = v;
        }

        public void mergeInto(Stat other) {
            // modify other
            other.sum += sum;
            other.count++;
            other.max = Math.max(other.max, max);
            other.min = Math.min(other.min, min);
        }

        public Stat merge(Stat other) {
            // modify this
            sum += other.sum;
            count++;
            max = Math.max(other.max, max);
            min = Math.min(other.min, min);

            return this;
        }

        @Override
        public String toString() {
            return round(min) + "/" + round(sum / count) + "/" + round(max);
        }

        private static double round(double v) {
            return Math.round(v * 10.0) / 10.0;
        }
    }
}
