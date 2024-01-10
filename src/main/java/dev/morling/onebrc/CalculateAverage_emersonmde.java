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
import java.util.logging.Handler;
import java.util.logging.Logger;
import java.util.logging.Formatter;
import java.util.logging.LogRecord;

class CustomLogFormatter extends Formatter {
    @Override
    public String format(LogRecord record) {
        return String.format("[%1$tF %1$tT] [%2$s] [%3$s] %4$s %n",
                new java.util.Date(record.getMillis()),
                record.getLevel().getLocalizedName(),
                record.getSourceClassName() + " " + record.getSourceMethodName(),
                record.getMessage());
    }
}

public class CalculateAverage_emersonmde {

    private static final String FILE = "./measurements.txt";
    private static final long CHUNK_SIZE = 100 * 1024 * 1024; // 100MB chunk size, adjust as needed

    private static final Logger LOGGER = Logger.getLogger(CalculateAverage_emersonmde.class.getName());

    public static void main(String[] args) {
        for (Handler handler : LOGGER.getParent().getHandlers()) {
            handler.setFormatter(new CustomLogFormatter());
        }
        try (RandomAccessFile file = new RandomAccessFile(FILE, "r");
                FileChannel fileChannel = file.getChannel()) {

            long fileSize = fileChannel.size();
            long position = 0;
            ConcurrentHashMap<String, MeasurementAggregator> results = new ConcurrentHashMap<>();
            int processors = Runtime.getRuntime().availableProcessors();
            ExecutorService executor = Executors.newFixedThreadPool(processors);
            List<Future<?>> futures = new ArrayList<>();

            LOGGER.info("Starting to process file of size " + fileSize + " bytes");
            while (position < fileSize) {
                long chunkSize = Math.min(CHUNK_SIZE, fileSize - position);
                MappedByteBuffer buffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, position, chunkSize);

                Future<?> future = executor.submit(() -> processChunk(buffer, results));
                futures.add(future);

                position += chunkSize;
                position = adjustToLineEnd(file, position, fileSize);
            }

            // Wait for all tasks to complete
            for (Future<?> future : futures) {
                future.get();
            }
            LOGGER.info("Finished processing chunks shutting down executor");
            executor.shutdown();

            results.forEach(
                    (station, aggregator) -> {
                        double mean = aggregator.sum / aggregator.count;
                        LOGGER.info(
                                station + ": Min=" + aggregator.min + ", Max=" + aggregator.max + ", Mean=" + mean);
                    });
        }
        catch (FileNotFoundException e) {
            LOGGER.severe("File not found: " + e.getMessage());
            throw new RuntimeException(e);
        }
        catch (IOException | InterruptedException | ExecutionException e) {
            LOGGER.severe("Error processing file: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }

    private static void processChunk(
                                     MappedByteBuffer buffer, ConcurrentHashMap<String, MeasurementAggregator> results) {
        LOGGER.info(() -> "Processing chunk of " + buffer.capacity() + " bytes with thread ID " + Thread.currentThread().getId());
        long startTime = System.currentTimeMillis();

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
        if (line.length() > 0) {
            processLine(line.toString(), results);
        }
        long endTime = System.currentTimeMillis();
        LOGGER.info("Processed chunk in " + (endTime - startTime) + " ms with thread ID " + Thread.currentThread().getId());
    }

    private static void processLine(
                                    String line, ConcurrentHashMap<String, MeasurementAggregator> results) {
        String[] parts = line.split(";");
        if (parts.length != 2)
            return;

        try {
            String station = parts[0];
            double value = Double.parseDouble(parts[1]);

            results.compute(
                    station,
                    (_, v) -> {
                        if (v == null)
                            v = new MeasurementAggregator();
                        v.add(value);
                        return v;
                    });
        }
        catch (NumberFormatException e) {
            LOGGER.severe("Invalid number format in line: " + line + ". Error: " + e.getMessage());
        }
    }

    private static long adjustToLineEnd(RandomAccessFile file, long position, long fileSize)
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
