package com.example.metricscollector;

import com.zaxxer.hikari.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class TimescaleRepository {
    private static final Logger log = LoggerFactory.getLogger(TimescaleRepository.class);
    private static final int MAX_BATCH_SIZE = 1000;
    private static final int MAX_RETRIES = 3;
    private static final long RETRY_DELAY_MS = 1000;

    private final HikariDataSource ds;
    private final Config cfg;

    public TimescaleRepository(Config cfg) {
        this.cfg = cfg;
        HikariConfig hc = new HikariConfig();
        hc.setJdbcUrl(cfg.jdbcUrl());
        hc.setUsername(cfg.user());
        hc.setPassword(cfg.pass());
        hc.setMaximumPoolSize(10);
        hc.setMinimumIdle(2);
        hc.setConnectionTimeout(TimeUnit.SECONDS.toMillis(30));
        hc.addDataSourceProperty("rewriteBatchedStatements", "true");
        hc.addDataSourceProperty("useServerPrepStmts", "true");
        this.ds = new HikariDataSource(hc);
        init();
    }

    private void init() {
        String createTableSQL = String.format(
                "CREATE TABLE IF NOT EXISTS %s (" +
                        "metric_name TEXT NOT NULL, " +
                        "ts TIMESTAMPTZ NOT NULL, " +
                        "value DOUBLE PRECISION NOT NULL, " +
                        "PRIMARY KEY (metric_name, ts))",
                cfg.table()
        );

        try (Connection c = ds.getConnection();
             Statement st = c.createStatement()) {

            st.executeUpdate(createTableSQL);

            try {
                st.execute("SELECT create_hypertable('" + cfg.table() + "', 'ts', if_not_exists => true)");
                log.info("Hypertable created or already exists");
            } catch (SQLException e) {
                log.warn("Hypertable creation: {}", e.getMessage());
            }

        } catch (SQLException e) {
            throw new RuntimeException("Failed to initialize TimescaleDB table", e);
        }
    }

    public void flushBatch(Map<String, List<MetricPoint>> snap) throws SQLException {
        if (snap == null || snap.isEmpty()) {
            return;
        }

        String insertSQL = String.format(
                "INSERT INTO %s (ts, metric_name, value) VALUES (?, ?, ?) " +
                        "ON CONFLICT (metric_name, ts) DO UPDATE SET value = EXCLUDED.value",
                cfg.table()
        );

        int retryCount = 0;
        while (retryCount <= MAX_RETRIES) {
            try (Connection c = ds.getConnection();
                 PreparedStatement ps = c.prepareStatement(insertSQL)) {

                c.setAutoCommit(false);
                int batchSize = 0;

                for (List<MetricPoint> points : snap.values()) {
                    for (MetricPoint point : points) {
                        if (!isValidMetric(point)) {
                            log.warn("Invalid metric point: {}", point);
                            continue;
                        }

                        ps.setTimestamp(1, Timestamp.from(point.ts()));
                        ps.setString(2, point.name());
                        ps.setDouble(3, point.value());
                        ps.addBatch();

                        if (++batchSize % MAX_BATCH_SIZE == 0) {
                            ps.executeBatch();
                            c.commit();
                        }
                    }
                }

                if (batchSize % MAX_BATCH_SIZE != 0) {
                    ps.executeBatch();
                    c.commit();
                }

                log.debug("Successfully flushed {} metrics", batchSize);
                return;

            } catch (SQLException e) {
                retryCount++;
                if (retryCount > MAX_RETRIES) {
                    throw e;
                }

                log.warn("Batch insert failed (attempt {}/{}), retrying...", retryCount, MAX_RETRIES);
                try {
                    Thread.sleep(RETRY_DELAY_MS);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new SQLException("Interrupted during retry", ie);
                }
            }
        }
    }

    private boolean isValidMetric(MetricPoint point) {
        // Проверка корректности временной метки
        if (point.ts().isBefore(Instant.now().minus(1, ChronoUnit.DAYS)) ||
                point.ts().isAfter(Instant.now().plus(1, ChronoUnit.HOURS))) {
            return false;
        }

        // Проверка имени метрики
        if (point.name() == null || point.name().isEmpty() || point.name().length() > 255) {
            return false;
        }

        // Проверка значения
        if (Double.isNaN(point.value()) || Double.isInfinite(point.value())) {
            return false;
        }

        return true;
    }

    public HikariDataSource dataSource() {
        return ds;
    }

    public void close() {
        if (ds != null && !ds.isClosed()) {
            ds.close();
            log.info("Database connection pool closed");
        }
    }
}