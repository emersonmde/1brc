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



# Get total system memory in MB and number of CPU cores
system_memory=$(sysctl -n hw.memsize | awk '{print int($1/1024/1024)}')
num_cores=$(sysctl -n hw.ncpu)

# Determine heap size settings for native image
# Set to a fraction of system memory, e.g., 1/4 for Xms and 1/2 for Xmx
Xms=$(echo "$system_memory / 4" | bc)M
Xmx=$(echo "$system_memory / 2" | bc)M

# Determine the GC strategy
# For simplicity, we'll use the G1 GC for native images
GCStrategy="--gc=G1"

# Additional native-image options for performance
PerformanceOptions="-O3 -march=native --no-fallback --enable-preview --enable-native-access"

# Output the recommended native-image options
echo "$PerformanceOptions --vm.Xms$Xms --vm.Xmx$Xmx $GCStrategy"

