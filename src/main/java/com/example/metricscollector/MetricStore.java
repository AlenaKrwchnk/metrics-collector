package com.example.metricscollector;


import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

public class MetricStore {
    private final AtomicReference<Map<String, List<MetricPoint>>> ref =
            new AtomicReference<>(new ConcurrentHashMap<>());

    public void add(MetricPoint p) {
        ref.get().computeIfAbsent(p.name(), k -> Collections.synchronizedList(new ArrayList<>())).add(p);
    }

    public Map<String, List<MetricPoint>> snapshotAndReset() {
        Map<String, List<MetricPoint>> snap = ref.getAndSet(new ConcurrentHashMap<>());
        return snap;
    }
}