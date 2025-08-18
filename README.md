# Metrics Collector
High-performance time-series metrics collection system with Spring Boot 3.5 and TimescaleDB
## 🛠 Технологический стек
- **Ядро**: Spring Boot 3.5.4 (Java 21)
- **Сеть**: Netty 4.1.111 (TCP-сервер)
- **База данных**: TimescaleDB (PostgreSQL 16)
- **Пул соединений**: HikariCP 5.1.0
- **Тестирование**: JUnit 5.10 + Testcontainers 1.21

- ## 📌 Features

- Real-time metrics ingestion via TCP
- TimescaleDB hypertable for time-series data
- Configurable batching and flushing
- Multi-threaded load generator
- Docker-compose deployment
### Prerequisites
- Java 21+
- Docker 20+
- Maven 3.9+

- ### 1. Start TimescaleDB
```bash
docker-compose up -d
2. Build and Run
bash
mvn clean package
java -jar target/metrics-collector-*.jar
3. Generate Load
bash
java -cp target/metrics-collector-*.jar com.example.metricscollector.LoadClient \
  127.0.0.1 9000 8 50000
```
⚙️ Configuration
Environment variables for server:

Variable	Default	Description
METRICS_PORT	9000	TCP listening port
FLUSH_INTERVAL_SECONDS	5	Batch flush interval



🧪 Testing
Run unit and integration tests:

bash
mvn test


Просмотр таблицы с добавленными метриками(500 например): 
docker exec -it metrics psql -U testuser -d metrics -c "SELECT * FROM metrics LIMIT 500;"
