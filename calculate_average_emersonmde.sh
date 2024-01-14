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

get_system_info() {
  if [[ "$OSTYPE" == "linux-gnu"* ]]; then
    total_memory=$(free -m | awk '/^Mem:/ {print $2}')
    num_cores=$(nproc)
  elif [[ "$OSTYPE" == "darwin"* ]]; then
    total_memory=$(sysctl -n hw.memsize | awk '{print int($1/1024/1024)}')
    num_cores=$(sysctl -n hw.ncpu)
  else
    echo "Unsupported OS type: $OSTYPE"
    exit 1
  fi
}

determine_optimal_settings() {
  MAX_HEAP_SIZE=$(echo "scale=0; ($total_memory * .95) / 1" | bc)M
  MIN_HEAP_SIZE=$MAX_HEAP_SIZE

  JAVA_OPTS="--enable-preview -Xms$MIN_HEAP_SIZE -Xmx$MAX_HEAP_SIZE $GC_TYPE"
}

get_system_info
determine_optimal_settings

# Check if the native image exists
if [ -f ./image_calculateaverage_emersonmde ]; then
  ./image_calculateaverage_emersonmde $JAVA_OPTS
else
  java $JAVA_OPTS -agentlib:native-image-agent=config-output-dir=./profile --class-path target/average-1.0.0-SNAPSHOT.jar dev.morling.onebrc.CalculateAverage_emersonmde
fi
