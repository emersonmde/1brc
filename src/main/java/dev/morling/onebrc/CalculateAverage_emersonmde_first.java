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

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class CalculateAverage_emersonmde_first {

    private static final String FILE = "./measurements.txt";
    private static final long CHUNK_SIZE = 100 * 1024 * 1024; // 100MB chunk size, adjust as needed

    private static final Logger LOGGER = Logger.getLogger(
            CalculateAverage_emersonmde_first.class.getName());

    public static void main(String[] args) {
        try (RandomAccessFile file = new RandomAccessFile(FILE, "r");
             FileChannel fileChannel = file.getChannel()) {

            long fileSize = fileChannel.size();
            long position = 0;
            ConcurrentHashMap<String, MeasurementAggregator> results = new ConcurrentHashMap<>();
            int processors = Runtime.getRuntime().availableProcessors();
            ExecutorService executor = Executors.newFixedThreadPool(processors);
            List<Future<?>> futures = new ArrayList<>();

            while (position < fileSize) {
                final long chunkSize = Math.min(CHUNK_SIZE, fileSize - position);
                final long finalPosition = position;
                Future<?> future = executor.submit(() -> {
                    try {
                        processChunk(fileChannel, finalPosition, chunkSize, results);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });
                futures.add(future);

                position += chunkSize;
                position = adjustToLineEnd(file, position, fileSize);
            }

            // Wait for all tasks to complete
            for (Future<?> future : futures) {
                future.get();
            }
            executor.shutdown();

            String resultString = results.entrySet().parallelStream()
                    .map(entry -> {
                        String station = entry.getKey();
                        MeasurementAggregator aggregator = entry.getValue();
                        double mean = aggregator.sum / aggregator.count;
                        return String.format("%s=%.1f/%.1f/%.1f", station, aggregator.min, mean, aggregator.max);
                    })
                    .collect(Collectors.joining(", ", "{", "}"));

            System.out.println(resultString);
        } catch (

                FileNotFoundException e) {
            LOGGER.severe(STR."File not found: \{e.getMessage()}");
            throw new RuntimeException(e);
        } catch (IOException | InterruptedException |
                 ExecutionException e) {
            LOGGER.severe(STR."Error processing file: \{e.getMessage()}");
            throw new RuntimeException(e);
        }
    }

    private static void processChunk(FileChannel fileChannel, long position, long chunkSize,
                                     ConcurrentHashMap<String, MeasurementAggregator> results)
            throws IOException {
        MappedByteBuffer buffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, position, chunkSize);

        StringBuilder line = new StringBuilder();
        while (buffer.hasRemaining()) {
            char c = (char) buffer.get();
            if (c == '\n') {
                processLine(line.toString(), results);
                line.setLength(0);
            }
            else {
                line.append(c);
            }
        }
        if (!line.isEmpty()) {
            processLine(line.toString(), results);
        }
    }

    // Parse a double from a string in the most efficent way possible
    private static double parseDouble(String s) {
        if (s == null || s.isEmpty()) {
            throw new NumberFormatException("Empty or null string");
        }

        double result = 0;
        double fractionalDivisor = 1;
        boolean negative = false;
        boolean fractionalPart = false;

        // Handle negative numbers
        int startIndex = s.charAt(0) == '-' ? 1 : 0;
        if (startIndex == 1) {
            negative = true;
        }

        for (int i = startIndex; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '.') {
                if (fractionalPart) {
                    throw new NumberFormatException("More than one decimal point found in input string: " + s);
                }
                fractionalPart = true;
            }
            else {
                int digit = c - '0';
                if (digit < 0 || digit > 9) {
                    throw new NumberFormatException("Invalid character encountered: " + c);
                }
                if (fractionalPart) {
                    fractionalDivisor *= 10;
                    result += digit / fractionalDivisor;
                }
                else {
                    result = result * 10 + digit;
                }
            }
        }

        return negative ? -result : result;
    }

    // TODO:
    // - Look into vectorization
    // - Look into Compiler directives
    // - Look into Unsafe
    // - Look into AOT
    private static void processLine(
                                    String line, ConcurrentHashMap<String, MeasurementAggregator> results) {
        int separatorIndex = line.indexOf(';');
        if (separatorIndex == -1) {
            return;
        }

        String station = line.substring(0, separatorIndex);
        String valueString = line.substring(separatorIndex + 1);
        if (valueString.isEmpty() || valueString.equals("-")) {
            return;
        }
        double value = Double.parseDouble(valueString);

        results.compute(
                station,
                (_, v) -> {
                    if (v == null) {
                        v = new MeasurementAggregator();
                    }
                    v.add(value);
                    return v;
                });
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
}
