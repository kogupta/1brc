#!/usr/bin/env bash
#
#  Copyright 2023 The original authors
#
#  Licensed under the Apache License, Version 2.0 (the "License");
#  you may not use this file except in compliance with the License.
#  You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
#  Unless required by applicable law or agreed to in writing, software
#  distributed under the License is distributed on an "AS IS" BASIS,
#  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#  See the License for the specific language governing permissions and
#  limitations under the License.
#

source "$HOME/.sdkman/bin/sdkman-init.sh"

USE_GRAAL=${1:-false}

if [ "$USE_GRAAL" = true ]; then
    sdk use java 21.0.3-graal 1>&2
else
    sdk use java 21.0.4-oracle 1>&2
fi

EXEC=CalculateAverage_kogupta

#USE_NATIVE_IMAGE=${1:-false}
#IMAGE="$EXEC"_image
#
#if [ ! -f "target/$IMAGE" ] && [ "$USE_NATIVE_IMAGE" = true ]; then
#    NATIVE_IMAGE_OPTS="--gc=epsilon -O3 -H:+UnlockExperimentalVMOptions -H:-GenLoopSafepoints -march=native --enable-preview -H:InlineAllBonus=10 -H:-ParseRuntimeOptions --initialize-at-build-time=dev.morling.onebrc.$EXEC"
#
#    native-image "$NATIVE_IMAGE_OPTS" \
#      -cp target/average-1.0.0-SNAPSHOT.jar \
#      -o target/$IMAGE dev.morling.onebrc.$EXEC
#fi

#if [ -f target/"$IMAGE" ]; then
#    echo "Using native image $IMAGE" 1>&2
#    hyperfine -w 0 -r 5 target/"$IMAGE" "$FILE"
#else
#    echo "Native image not found, using JVM mode." 1>&2
#    JAVA_OPTS="--enable-preview -Xlog:gc*:gc.log -XX:StartFlightRecording=dumponexit=true,filename=recording%t.jfr"
#    /usr/bin/time -p java $JAVA_OPTS -cp target/average-1.0.0-SNAPSHOT.jar dev.morling.onebrc.$EXEC
#fi


hyperfine -w 2 -r 3 "java --enable-preview -cp target/average-1.0.0-SNAPSHOT.jar dev.morling.onebrc.$EXEC"

# results:
#  $ ./calculate_average_kogupta.sh true 
#  Using java version 21.0.3-graal in this shell.
#  Benchmark 1: java -Xms1G -Xmx1G -cp target/average-1.0.0-SNAPSHOT.jar dev.morling.onebrc.CalculateAverage_kogupta
#    Time (mean ± σ):     47.243 s ±  4.268 s    [User: 294.762 s, System: 9.814 s]
#    Range (min … max):   40.565 s … 50.933 s    5 runs
#  
#  $ ./calculate_average_kogupta.sh false
#  Using java version 21.0.4-oracle in this shell.
#  Benchmark 1: java -Xms1G -Xmx1G -cp target/average-1.0.0-SNAPSHOT.jar dev.morling.onebrc.CalculateAverage_kogupta
#    Time (mean ± σ):     71.180 s ±  2.527 s    [User: 452.746 s, System: 11.939 s]
#    Range (min … max):   68.141 s … 73.560 s    5 runs
