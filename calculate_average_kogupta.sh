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
sdk use java 21.0.3-graal 1>&2

USE_NATIVE_IMAGE=${2:-false}

EXEC=CalculateAverage_kogupta
IMAGE="$EXEC"_image

if [ ! -f "target/$IMAGE" ] && [ "$USE_NATIVE_IMAGE" = true ]; then
    NATIVE_IMAGE_OPTS="--gc=epsilon -O3 -H:+UnlockExperimentalVMOptions -H:-GenLoopSafepoints -march=native --enable-preview -H:InlineAllBonus=10 -H:-ParseRuntimeOptions --initialize-at-build-time=dev.morling.onebrc.$EXEC"

    native-image "$NATIVE_IMAGE_OPTS" \
      -cp target/average-1.0.0-SNAPSHOT.jar \
      -o target/$IMAGE dev.morling.onebrc.$EXEC
fi

if [ -f target/"$IMAGE" ]; then
    echo "Using native image $IMAGE" 1>&2
    hyperfine -w 0 -r 5 target/"$IMAGE" "$FILE"
else
    echo "Native image not found, using JVM mode." 1>&2
    JAVA_OPTS="--enable-preview"
    java $JAVA_OPTS -cp target/average-1.0.0-SNAPSHOT.jar dev.morling.onebrc.$EXEC
fi
