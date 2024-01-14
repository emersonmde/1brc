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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class CalculateAverage_emersonmde {

    private static final String FILE = "./measurements.txt";
    private static final long CHUNK_SIZE = 100 * 1024 * 1024; // 100MB chunk size, adjust as needed

    private static final Logger LOGGER = Logger.getLogger(
            CalculateAverage_emersonmde.class.getName());

    public static void main(String[] args) {
        try (RandomAccessFile file = new RandomAccessFile(FILE, "r");
                FileChannel fileChannel = file.getChannel()) {

            long fileSize = fileChannel.size();
            int processors = Runtime.getRuntime().availableProcessors();
            ExecutorService executor = Executors.newFixedThreadPool(processors);
            List<Future<Map<String, MeasurementAggregator>>> futures = new ArrayList<>();

            // Calculate segment size and adjust for line boundaries
            long segmentSize = fileSize / processors;
            for (int i = 0; i < processors; i++) {
                final long start = i * segmentSize;
                final long end = (i < processors - 1) ? adjustToLineEnd(file, (i + 1) * segmentSize, fileSize) : fileSize;

                // Submit a task to process each file segment
                Future<Map<String, MeasurementAggregator>> future = executor.submit(() -> {
                    Map<String, MeasurementAggregator> results = new HashMap<>();
                    try {
                        processChunk(fileChannel, start, end - start, results);
                    }
                    catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                    return results;
                });
                futures.add(future);
            }

            String resultString = futures.parallelStream()
                    .map(future -> {
                        try {
                            return future.get();
                        }
                        catch (InterruptedException | ExecutionException e) {
                            throw new RuntimeException(e);
                        }
                    })
                    .reduce(new ConcurrentHashMap<>(), (finalResults, individualResult) -> {
                        mergeHashMaps(finalResults, individualResult);
                        return finalResults;
                    })
                    .entrySet().parallelStream()
                    .map(entry -> {
                        String station = entry.getKey();
                        MeasurementAggregator aggregator = entry.getValue();
                        double mean = aggregator.sum / aggregator.count;
                        return String.format("%s=%.2f/%.2f/%.2f", station, aggregator.min, mean, aggregator.max);
                    })
                    .collect(Collectors.joining(", "));

            System.out.println(resultString);
            executor.shutdown();
        }
        catch (FileNotFoundException e) {
            LOGGER.severe("File not found: " + e.getMessage());
            throw new RuntimeException(e);
        }
        catch (IOException e) {
            LOGGER.severe("Error processing file: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }

    private static void mergeHashMaps(Map<String, MeasurementAggregator> lhs, Map<String, MeasurementAggregator> rhs) {
        for (Map.Entry<String, MeasurementAggregator> entry : rhs.entrySet()) {
            String key = entry.getKey();
            MeasurementAggregator value = entry.getValue();
            lhs.merge(key, value, (v1, v2) -> {
                v1.min = Math.min(v1.min, v2.min);
                v1.max = Math.max(v1.max, v2.max);
                v1.sum += v2.sum;
                v1.count += v2.count;
                return v1;
            });
        }
    }

    private static void processChunk(FileChannel fileChannel, long position, long chunkSize,
                                     Map<String, MeasurementAggregator> results)
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

    private static void processLine(
                                    String line, Map<String, MeasurementAggregator> results) {
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
