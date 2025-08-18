package com.example.metricscollector;

import java.util.*;

public class Flusher extends Thread {
    private final MetricStore store;
    private final TimescaleRepository repo;
    private final Config cfg;

    public Flusher(MetricStore store, TimescaleRepository repo, Config cfg) {
        this.store = store; this.repo = repo; this.cfg = cfg;
    }

    @Override public void run() {
        while (true) {
            try {
                Thread.sleep(cfg.flushIntervalSec() * 1000L);
                Map<String,List<MetricPoint>> snap = store.snapshotAndReset();
                if (!snap.isEmpty()) repo.flushBatch(snap);
            } catch (Exception e) { e.printStackTrace(); }
        }
    }
}