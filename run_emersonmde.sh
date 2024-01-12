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

# Function to determine optimal heap size and GC type
determine_optimal_settings() {
  # Set heap size to 90% of system memory and convert to integer
  MAX_HEAP_SIZE=$(echo "scale=0; ($total_memory * .99) / 1" | bc)M
  # Set min heap size to same as max heap size
  MIN_HEAP_SIZE=$MAX_HEAP_SIZE
#  # Set young generation size to 25% of max heap size and convert to integer
#  YOUNG_GEN_SIZE=$(echo "scale=0; ($MAX_HEAP_SIZE * .25) / 1" | bc)M


  # Output the determined settings
  echo "Optimal Settings:"
  echo "Max Heap Size: $MAX_HEAP_SIZE"
  echo "Min Heap Size: $MIN_HEAP_SIZE"
  JAVA_OPTS="--enable-preview -Xms$MIN_HEAP_SIZE -Xmx$MAX_HEAP_SIZE $GC_TYPE"
}

# Check if the native image exists
if [ -f ./image_calculateaverage_emersonmde ]; then
  echo "Running native image with optimal hardware settings..."

  # Get system info
  get_system_info

  # Determine optimal settings for native image
  determine_optimal_settings

  # Run the native image with determined settings
  time ./image_calculateaverage_emersonmde $JAVA_OPTS
else
  # Get system info
  get_system_info

  determine_optimal_settings

  # Output hardware and JVM settings
  echo "System Hardware Details:"
  echo "Number of CPU Cores: $num_cores"
  echo "Total System Memory: $total_memory MB"
  echo "Calculating averages using emersonmde with JVM options: '$JAVA_OPTS'"

  # Run the application in JVM mode
  time java $JAVA_OPTS -agentlib:native-image-agent=config-output-dir=./profile --class-path target/average-1.0.0-SNAPSHOT.jar dev.morling.onebrc.CalculateAverage_emersonmde
fi
