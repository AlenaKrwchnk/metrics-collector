package com.example.metricscollector;

import org.junit.jupiter.api.*;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.sql.*;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class TimescaleIntegrationTest {
    private PostgreSQLContainer<?> pg;
    private TimescaleRepository repo;
    private MetricStore store;

    @BeforeAll
    void startPg() throws Exception {
        DockerImageName timescaleImage = DockerImageName
                .parse("timescale/timescaledb:2.21.3-pg15")
                .asCompatibleSubstituteFor("postgres");

        pg = new PostgreSQLContainer<>(timescaleImage)
                .withDatabaseName("metrics")
                .withUsername("postgres")
                .withPassword("postgres");

        pg.start();
        Config cfg = new Config(
                0, 5, 2,
                pg.getJdbcUrl(), pg.getUsername(), pg.getPassword(), "metrics"
        );
        repo = new TimescaleRepository(cfg);
        store = new MetricStore();
    }

    @AfterAll
    void stopPg() { if (pg != null) pg.stop(); }

    @Test
    void flushesDataToTimescale() throws Exception {
        MetricPoint p = new MetricPoint("cpu", Instant.now(), 55.5);
        store.add(p);
        Map<String, List<MetricPoint>> snap = store.snapshotAndReset();
        repo.flushBatch(snap);

        try (Connection c = repo.dataSource().getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT metric_name,value FROM metrics WHERE metric_name=?")) {
            ps.setString(1, "cpu");
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertEquals("cpu", rs.getString(1));
                assertEquals(55.5, rs.getDouble(2), 0.001);
            }
        }
    }
}