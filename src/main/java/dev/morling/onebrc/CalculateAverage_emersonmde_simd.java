/*
 *  Copyright 2023 The original authors
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package dev.morling.onebrc;

import jdk.incubator.vector.DoubleVector;
import jdk.incubator.vector.VectorOperators;
import jdk.incubator.vector.VectorSpecies;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class CalculateAverage_emersonmde_simd {

    private static final VectorSpecies<Double> SPECIES = DoubleVector.SPECIES_PREFERRED;
    private static final String FILE = "./measurements.txt";
    private static final long CHUNK_SIZE = 100 * 1024 * 1024; // 100MB chunk size, adjust as needed

    private static final Logger LOGGER = Logger.getLogger(
            CalculateAverage_emersonmde_simd.class.getName());

    public static void main(String[] args) {
        try (RandomAccessFile file = new RandomAccessFile(FILE, "r");
             FileChannel fileChannel = file.getChannel()) {

            long fileSize = fileChannel.size();
            long position = 0;
            ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
            ArrayList<Future<Map<String, StationMetrics>>> futures = new ArrayList<>();

            while (position < fileSize) {
                final long chunkSize = Math.min(CHUNK_SIZE, fileSize - position);
                final long finalPosition = position;
                Future<Map<String, StationMetrics>> future = executor.submit(() -> {
                    try {
                        return processChunk(fileChannel, finalPosition, chunkSize);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });
                futures.add(future);

                position += chunkSize;
                position = adjustToLineEnd(file, position, fileSize);
            }

            // Merge results from each thread
            Map<String, StationMetrics> results = new HashMap<>();
            for (var future : futures) {
                mergeResults(results, future.get());
            }

            String resultString = results.values().parallelStream()
                    .map(StationMetrics::toString)
                    .collect(Collectors.joining(", ", "{", "}"));

            System.out.println(resultString);

            executor.shutdown();
            executor.close();
        } catch (FileNotFoundException e) {
            LOGGER.severe(STR."File not found: \{e.getMessage()}");
            throw new RuntimeException(e);
        } catch (IOException | InterruptedException |
                 ExecutionException e) {
            LOGGER.severe(STR."Error processing file: \{e.getMessage()}");
            throw new RuntimeException(e);
        }
    }

    // Use Convert values to Vector and use Vector API reduce lanes to calculate
    // min, max, sum, and count
    private static StationMetrics calculateResults(String station, double[] values, StationMetrics previousStationMetrics) {
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        double sum = 0;
        int count = values.length;

        int i = 0;
        for (; i < values.length - SPECIES.length(); i += SPECIES.length()) {
            DoubleVector vector = DoubleVector.fromArray(SPECIES, values, i);
            min = Math.min(min, vector.reduceLanes(VectorOperators.MIN));
            max = Math.max(max, vector.reduceLanes(VectorOperators.MAX));
            sum += vector.reduceLanes(VectorOperators.ADD);
        }

        // Handle remaining elements
        for (; i < values.length; i++) {
            min = Math.min(min, values[i]);
            max = Math.max(max, values[i]);
            sum += values[i];
        }

        if (previousStationMetrics != null) {
            min = Math.min(min, previousStationMetrics.min);
            max = Math.max(max, previousStationMetrics.max);
            sum += previousStationMetrics.sum;
            count += previousStationMetrics.count;
        }

        double mean = count > 0 ? sum / count : 0;
        return new StationMetrics(station, min, mean, max, sum, count);
    }

    private static void mergeResults(Map<String, StationMetrics> lhs, Map<String, StationMetrics> rhs) {
        for (Map.Entry<String, StationMetrics> rhsEntry : rhs.entrySet()) {
            String rhsStation = rhsEntry.getKey();
            StationMetrics rhsMetrics = rhsEntry.getValue();
            lhs.compute(
                    rhsStation,
                    (_, lhsMetrics) -> {
                        if (lhsMetrics == null) {
                            return rhsMetrics;
                        }
                        return new StationMetrics(
                                rhsStation,
                                Math.min(lhsMetrics.min, rhsMetrics.min),
                                (lhsMetrics.sum + rhsMetrics.sum) / (lhsMetrics.count + rhsMetrics.count),
                                Math.max(lhsMetrics.max, rhsMetrics.max),
                                lhsMetrics.sum + rhsMetrics.sum,
                                lhsMetrics.count + rhsMetrics.count);
                    });
        }
    }

    private static Map<String, StationMetrics> processChunk(FileChannel fileChannel, long position, long chunkSize)
            throws IOException {
        MappedByteBuffer buffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, position, chunkSize);

        double[] values = new double[SPECIES.length()];
        int valuesIndex = 0;
        Map<String, StationMetrics> results = new HashMap<>();
        // StationMetrics previousStationMetrics = null;

        StringBuilder line = new StringBuilder();
        while (buffer.hasRemaining()) {
            char c = (char) buffer.get();
            if (c == '\n') {
                int separatorIndex = line.indexOf(";");

                if (separatorIndex == -1) {
                    continue;
                }

                String station = line.substring(0, separatorIndex);
                String valueString = line.substring(separatorIndex + 1);
                if (valueString.isEmpty() || valueString.equals("-")) {
                    continue;
                }

                try {
                    values[valuesIndex++] = Double.parseDouble(valueString);
                }
                catch (NumberFormatException e) {
                    continue;
                }

                if (valuesIndex > values.length - 1) {
                    results.put(station, calculateResults(station, values, results.get(station)));

                    values = new double[SPECIES.length()];
                    valuesIndex = 0;
                }
                line.setLength(0);
            }
            else {
                line.append(c);
            }
        }
        if (!line.isEmpty()) {
            int separatorIndex = line.indexOf(";");

            if (separatorIndex == -1) {
                return results;
            }

            String station = line.substring(0, separatorIndex);
            String valueString = line.substring(separatorIndex + 1);
            if (valueString.isEmpty() || valueString.equals("-")) {
                return results;
            }

            try {
                values[valuesIndex] = Double.parseDouble(valueString);
            }
            catch (NumberFormatException e) {
                return results;
            }

            results.put(station, calculateResults(station, values, results.get(station)));
        }

        return results;
    }

    private static TemperatureRecord processLine(String line) {
        int separatorIndex = line.indexOf(';');

        if (separatorIndex == -1) {
            return null;
        }

        String station = line.substring(0, separatorIndex);
        String valueString = line.substring(separatorIndex + 1);
        if (valueString.isEmpty() || valueString.equals("-")) {
            return null;
        }
        double value = Double.parseDouble(valueString);

        return new TemperatureRecord(station, value);
    }

    private static long adjustToLineEnd(RandomAccessFile file, long position, long ignoredFileSize)
            throws IOException {
        file.seek(position);
        while (file.read() != '\n' && position < file.length()) {
            position++;
        }
        return position;
    }

    private static class MeasurementAggregator {

        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        double sum;
        long count;

        void add(double value) {
            min = Math.min(min, value);
            max = Math.max(max, value);
            sum += value;
            count++;
        }
    }

    private static class Result {
        String station;
        double min;
        double mean;
        double max;
    }

    private static class List<Double> {
        private int size = 0;
        private int capacity = 4096;
        private double[] data = new double[capacity];

        public void add(double value) {
            if (size == capacity) {
                capacity *= 2;
                double[] newData = new double[capacity];
                System.arraycopy(data, 0, newData, 0, size);
                data = newData;
            }
            data[size++] = value;
        }

        public double get(int index) {
            return data[index];
        }

        public int size() {
            return size;
        }

        // Allocate a new array and copy the values from the old array into the new array
        public void addAll(List<Double> values) {
            int newSize = size + values.size;
            if (newSize > capacity) {
                capacity = newSize;
                double[] newData = new double[capacity];
                System.arraycopy(data, 0, newData, 0, size);
                data = newData;
            }
            System.arraycopy(values.data, 0, data, size, values.size);
            size = newSize;
        }

        public double[] getData() {
            return data;
        }
    }

    public record StationMetrics(String station, double min, double mean, double max, double sum, int count) {

        public String toString() {
            return String.format("%s=%.1f/%.1f/%.1f", station, min, mean, max);
        }
    }

    public record TemperatureRecord(String station, double temperature) {
    }
}