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
#native-image --pgo-instrument --enable-preview --no-fallback -march=native -cp target/average-1.0.0-SNAPSHOT.jar -o image_calculateaverage_emersonmde dev.morling.onebrc.CalculateAverage_emersonmde
#./calculate_average_emersonmde.sh
native-image -O3 --strict-image-heap --enable-preview --no-fallback -march=native -cp target/average-1.0.0-SNAPSHOT.jar -o image_calculateaverage_emersonmde dev.morling.onebrc.CalculateAverage_emersonmde
