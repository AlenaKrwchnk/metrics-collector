package com.example.metricscollector;

public class Main {
    public static void main(String[] args) throws Exception {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 9000;
        Config cfg = new Config(
                port,
                10, // flush every 10 sec
                2,  // worker threads
                "jdbc:postgresql://localhost:5432/metrics",
                "testuser", "testpassword", "metrics"
        );
        MetricStore store = new MetricStore();
        TimescaleRepository repo = new TimescaleRepository(cfg);
        Flusher flusher = new Flusher(store, repo, cfg);
        MetricsServer server = new MetricsServer(cfg, store);
        flusher.start();
        server.start();
    }
}
