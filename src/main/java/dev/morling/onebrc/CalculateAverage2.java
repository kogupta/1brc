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
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.StructuredTaskScope;

import static java.nio.channels.FileChannel.MapMode.READ_ONLY;

public final class CalculateAverage2 {
//    private static final String input = "/Users/kgupta/depot/personal/1brc/measurements.txt";
    private static final String FILE = "./measurements.txt";

    private static final long CHUNK_SIZE = 10 * 1024 * 1024;

    public static void main(String[] args) throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(new File(FILE), "r")) {
            List<MappedByteBuffer> buffers = mappedChunks(raf);

            List<StructuredTaskScope.Subtask<Map<String, Measurement>>> subtasks = new ArrayList<>(buffers.size());

            try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
                for (MappedByteBuffer buffer : buffers) {
                    var subtask = scope.fork(() -> process(buffer));
                    subtasks.add(subtask);
                }

                scope.join();
                scope.throwIfFailed();

                Optional<Map<String, Measurement>> cities = subtasks.stream()
                        .map(StructuredTaskScope.Subtask::get)
                        .reduce(CalculateAverage2::accumulator);
                var sortedCities = new TreeMap<>(cities.orElseThrow());
                System.out.println(sortedCities);
            } catch (ExecutionException | InterruptedException e) {
                throw new RuntimeException(e);
            }


        }

    }

    private static List<MappedByteBuffer> mappedChunks(RandomAccessFile raf) throws IOException {
        List<MappedByteBuffer> buffers = new ArrayList<>();

        long len = raf.length();
        FileChannel fileChannel = raf.getChannel();

        long start = 0;
        while (start < len) {
            MappedByteBuffer buffer = fileChannel.map(
                    READ_ONLY,
                    start,
                    Math.min(CHUNK_SIZE, len - start));
            buffer.order(ByteOrder.nativeOrder());

            // don't split the data in the middle of lines
            // find the closest previous newline
            int realEnd = buffer.limit() - 1;
            while (buffer.get(realEnd) != '\n')
                realEnd--;

            realEnd++;

            buffer.limit(realEnd);
            start += realEnd;

            buffers.add(buffer);
        }

        return buffers;
    }

    private static Map<String, Measurement> process(MappedByteBuffer bb) {
        Map<String, Measurement> cities = new HashMap<>();

        final int limit = bb.limit();
        final byte[] buffer = new byte[128];

        while (bb.position() < limit) {
            final int currentPosition = bb.position();

            // find the `;` separator
            int separator = currentPosition;
            while (separator != limit && bb.get(separator) != ';')
                separator++;

            // find the end of the line
            int end = separator + 1;
            while (end != limit && !Character.isWhitespace((char) bb.get(end)))
                end++;

            // get the name as a string
            int nameLength = separator - currentPosition;
            bb.get(buffer, 0, nameLength);
            String city = new String(buffer, 0, nameLength);

            // get rid of the separator
            bb.get();

            // get the double value
            int valueLength = end - separator - 1;
            bb.get(buffer, 0, valueLength);
            String valueStr = new String(buffer, 0, valueLength);
            double value = Double.parseDouble(valueStr);

            // and get rid of the new line (handle both kinds)
            byte newline = bb.get();
            if (newline == '\r')
                bb.get();

            // update the map with the new measurement
            Measurement agg = cities.get(city);
            if (agg == null) {
                agg = new Measurement();
                cities.put(city, agg);
            }

            agg.min = Math.min(agg.min, value);
            agg.max = Math.max(agg.max, value);
            agg.sum += value;
            agg.count++;
        }

        return cities;

    }

    private static Map<String, Measurement> accumulator(Map<String, Measurement> a,
                                                        Map<String, Measurement> b) {
        for (Map.Entry<String, Measurement> entry : b.entrySet()) {
            a.merge(entry.getKey(), entry.getValue(), Measurement::merge2);
        }

        return a;
    }

    private static final class Measurement {
        private double min = Double.POSITIVE_INFINITY;
        private double max = Double.NEGATIVE_INFINITY;
        private double sum;
        private long count;

        @SuppressWarnings("StringTemplateMigration")
        public String toString() {
            return round(min) + "/" + round(sum / count) + "/" + round(max);
        }

        private double round(double value) {
            return Math.round(value * 10.0) / 10.0;
        }

//        public static Measurement merge(Measurement m1, Measurement m2) {
//            var result = new Measurement();
//            result.min = Math.min(m1.min, m2.min);
//            result.max = Math.max(m1.max, m2.max);
//            result.sum = m1.sum + m2.sum;
//            result.count = m1.count + m2.count;
//            return result;
//        }

        public static Measurement merge2(Measurement m1, Measurement m2) {
            m1.min = Math.min(m1.min, m2.min);
            m1.max = Math.max(m1.max, m2.max);
            m1.sum = m1.sum + m2.sum;
            m1.count = m1.count + m2.count;
            return m1;
        }

    }
}
