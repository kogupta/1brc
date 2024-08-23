#!/usr/bin/env bash

source "$HOME/.sdkman/bin/sdkman-init.sh"
sdk use java 21.0.3-graal 1>&2

FILE=${1:-measurements.txt}
USE_NATIVE_IMAGE=${2:-true}

EXEC=CalculateAverage_kogupta
IMAGE="$EXEC"_image

if [ ! -f "target/$IMAGE" ] && [ "$USE_NATIVE_IMAGE" = true ]; then
    NATIVE_IMAGE_OPTS="--gc=epsilon -O3 -H:+UnlockExperimentalVMOptions -H:-GenLoopSafepoints -march=native
    --enable-preview -H:InlineAllBonus=10 -H:-ParseRuntimeOptions --initialize-at-build-time=dev.morling.onebrc.$EXEC"

    native-image "$NATIVE_IMAGE_OPTS"
      -cp target/average-1.0.0-SNAPSHOT.jar \
      -o target/$IMAGE dev.morling.onebrc.$EXEC
fi

if [ -f target/"$IMAGE" ]; then
    echo "Using native image $IMAGE" 1>&2
    hyperfine -w 2 -r 5 target/"$IMAGE" "$FILE"
else
    echo "Native image not found, using JVM mode." 1>&2
    JAVA_OPTS="--enable-preview"
    hyperfine -w 2 -r 5 java $JAVA_OPTS --class-path target/average-1.0.0-SNAPSHOT.jar dev.morling.onebrc."$EXEC"
    "$FILE"
fi
