package com.example.metricscollector;

import java.time.Instant;

public record MetricPoint(String name, Instant ts, double value) {}
