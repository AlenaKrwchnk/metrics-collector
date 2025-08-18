package com.example.metricscollector;

import java.io.OutputStream;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

public class LoadClient {
    // Константы для валидации
    private static final int MAX_METRIC_NAME_LENGTH = 100;
    private static final int MIN_METRIC_NAME_LENGTH = 3;
    private static final double MIN_VALUE = 0.0;
    private static final double MAX_VALUE = 100.0;
    private static final String METRIC_PREFIX = "cpu";
    private static final int BUFFER_HEADER_SIZE = 9; // 8 (timestamp) + 1 (name length)

    public static void main(String[] args) throws Exception {
        // Парсинг аргументов
        String host = args.length > 0 ? args[0] : "127.0.0.1";
        int port = args.length > 1 ? Integer.parseInt(args[1]) : 9000;
        int threads = args.length > 2 ? Integer.parseInt(args[2]) : 4;
        int messagesPerThread = args.length > 3 ? Integer.parseInt(args[3]) : 1000;

        // Создание пула потоков
        var pool = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(threads);
        Random rnd = new Random();

        // Статистика
        final int[] validMetrics = {0};
        final int[] invalidMetrics = {0};

        for (int t = 0; t < threads; t++) {
            pool.submit(() -> {
                try (Socket sock = new Socket(host, port);
                     OutputStream os = sock.getOutputStream()) {

                    for (int i = 0; i < messagesPerThread; i++) {
                        // Генерация данных
                        Instant now = Instant.now();
                        String name = generateValidMetricName(Thread.currentThread().getId(), i);
                        double val = generateValidValue(rnd);

                        // Создание бинарного пакета
                        byte[] packet = createMetricPacket(now, name, val);
                        if (packet != null) {
                            os.write(packet);
                            validMetrics[0]++;
                        } else {
                            invalidMetrics[0]++;
                        }
                    }
                    os.flush();
                } catch (Exception e) {
                    System.err.println("Thread error: " + e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        pool.shutdown();

        System.out.printf("Sent %d valid and %d invalid metrics across %d threads to %s:%d%n",
                validMetrics[0], invalidMetrics[0], threads, host, port);
    }

    private static String generateValidMetricName(long threadId, int index) {
        String name = String.format("%s.%d.%d", METRIC_PREFIX, threadId, index);
        return name.length() > MAX_METRIC_NAME_LENGTH ?
                name.substring(0, MAX_METRIC_NAME_LENGTH) :
                name;
    }

    private static double generateValidValue(Random rnd) {
        return MIN_VALUE + (MAX_VALUE - MIN_VALUE) * rnd.nextDouble();
    }

    private static byte[] createMetricPacket(Instant timestamp, String name, double value) {
        try {
            // Проверка имени
            if (name == null || name.length() < MIN_METRIC_NAME_LENGTH) {
                return null;
            }

            byte[] nameBytes = name.getBytes(StandardCharsets.UTF_8);
            if (nameBytes.length > Byte.MAX_VALUE) {
                return null;
            }

            // Создание буфера
            ByteBuffer buf = ByteBuffer.allocate(BUFFER_HEADER_SIZE + nameBytes.length + 8);

            // Упаковка данных
            buf.putLong(timestamp.toEpochMilli());  // 8 байт timestamp
            buf.put((byte) nameBytes.length);       // 1 байт длина имени
            buf.put(nameBytes);                     // N байт имя
            buf.putDouble(value);                   // 8 байт значение

            return buf.array();
        } catch (Exception e) {
            System.err.println("Failed to create packet: " + e.getMessage());
            return null;
        }
    }
}