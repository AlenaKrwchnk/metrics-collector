package com.example.metricscollector;

public record Config(int port, int flushIntervalSec, int workerThreads,
                     String jdbcUrl, String user, String pass, String table) {}