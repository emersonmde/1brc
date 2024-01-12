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



# Get total system memory in MB
system_memory=$(sysctl -n hw.memsize | awk '{print int($1/1024/1024)}')

# Set minimum heap size (e.g., 1/4 of system memory)
Xms=$(echo "$system_memory / 4" | bc)M

# Set maximum heap size (e.g., 1/2 of system memory)
Xmx=$(echo "$system_memory / 2" | bc)M

# Get the number of CPU cores
num_cores=$(sysctl -n hw.ncpu)

# Set the number of garbage collector threads
# For systems with more than 8 cores, set to 5/8 of the number of cores, otherwise use the number of cores
if [ "$num_cores" -gt 8 ]; then
    gc_threads=$(echo "$num_cores * 5 / 8" | bc)
else
    gc_threads=$num_cores
fi

XXGcThreads="-XX:ParallelGCThreads=$gc_threads -XX:ConcGCThreads=$(echo "$gc_threads / 2" | bc)"

# Include the --enable-preview flag
EnablePreview="--enable-preview"

# Output the recommended JVM options
echo "$EnablePreview -Xms$Xms -Xmx$Xmx $XXGcThreads"

