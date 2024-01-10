#!/bin/sh
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

# shellcheck disable=SC2039
source "$HOME/.sdkman/bin/sdkman-init.sh"
sdk use java 21.0.1-graal 1>&2

# Detect architecture and set GC and march options accordingly
ARCH=$(uname -m)
if [ "$ARCH" = "x86_64" ] || [ "$ARCH" = "aarch64" ]; then
    # G1 GC is supported on Linux AMD64 and AArch64
    GC_TYPE="--gc=G1"
    MARCH_TYPE="-march=native"
elif [ "$ARCH" = "arm64" ]; then
    # arm64 doesn't support G1 GC
    GC_TYPE="--gc=serial"
    MARCH_TYPE="-march=armv8-a"
else
    echo "Unsupported architecture: $ARCH"
    MARCH_TYPE="-march=native"
    exit 1
fi

NATIVE_IMAGE_OPTS="$MARCH_TYPE --no-fallback $GC_TYPE --enable-preview"
echo "Building native image with options: $NATIVE_IMAGE_OPTS"
native-image $NATIVE_IMAGE_OPTS -cp target/average-1.0.0-SNAPSHOT.jar -o image_calculateaverage_emersonmde dev.morling.onebrc.CalculateAverage_emersonmde
