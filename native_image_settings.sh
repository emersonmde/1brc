#!/bin/zsh

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

