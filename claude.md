# DeadLink — Distributed Broken Link Detector

> CLAUDE.md — Project bible for AI-assisted development

---

## 📌 Project Overview

**DeadLink** is a self-hostable, distributed service that continuously monitors documentation sites and websites for broken links, and alerts teams when link health changes.

**Core value proposition:** Teams register a URL. DeadLink crawls it, checks every link, and fires a webhook/email the moment a healthy link dies — or a broken one recovers. Everything runs in Docker. Zero manual checks.

---

## 🏗️ Architecture

Three independent Spring Boot microservices, communicating via Kafka. All containerized with Docker Compose.

```
┌──────────────────────┐
│   Crawler Service    │  REST API to register sites + BFS crawler + cron scheduler
│   Port: 8081         │  Publishes jobs → Kafka topic: link-check-jobs
└──────────┬───────────┘
           │ Kafka: link-check-jobs
           ▼
┌──────────────────────┐
│  Checker Service     │  Kafka consumer → HTTP HEAD requests → writes results to DB
│   Port: 8082         │  If status changed → publishes → Kafka topic: link-status-changed
└──────────┬───────────┘
           │ Kafka: link-status-changed
           ▼
┌──────────────────────┐
│   Alert Service      │  Kafka consumer → fires webhooks/emails → exposes report REST API
│   Port: 8083         │  GET /api/report/{siteId} → full broken link report
└──────────────────────┘

Infrastructure (via Docker Compose):
- Kafka + Zookeeper
- PostgreSQL (shared DB, separate schemas per service)
- All 3 Spring Boot services
```

---

## 📁 Project Structure

```
deadlink/
├── CLAUDE.md                          ← You are here
├── docker-compose.yml                 ← Full stack orchestration
├── .github/
│   └── workflows/
│       └── ci.yml                     ← GitHub Actions: test → build → push to GHCR
│
├── crawler-service/
│   ├── Dockerfile
│   ├── pom.xml
│   └── src/main/java/com/deadlink/crawler/
│       ├── CrawlerApplication.java
│       ├── controller/
│       │   └── SiteController.java        ← POST /api/sites, GET /api/sites/{id}
│       ├── service/
│       │   ├── CrawlerService.java        ← BFS link extraction logic
│       │   ├── SiteSchedulerService.java  ← Cron-based re-crawl trigger
│       │   └── KafkaProducerService.java  ← Publishes LinkCheckJob to Kafka
│       ├── model/
│       │   ├── Site.java                  ← JPA entity
│       │   └── DiscoveredLink.java        ← JPA entity
│       ├── repository/
│       │   ├── SiteRepository.java
│       │   └── DiscoveredLinkRepository.java
│       ├── dto/
│       │   ├── SiteRegistrationRequest.java
│       │   └── LinkCheckJob.java          ← Kafka message payload
│       └── config/
│           ├── KafkaProducerConfig.java
│           └── SchedulerConfig.java
│
├── checker-service/
│   ├── Dockerfile
│   ├── pom.xml
│   └── src/main/java/com/deadlink/checker/
│       ├── CheckerApplication.java
│       ├── consumer/
│       │   └── LinkCheckConsumer.java     ← @KafkaListener on link-check-jobs
│       ├── service/
│       │   ├── LinkCheckerService.java    ← HTTP HEAD request logic + status detection
│       │   └── KafkaProducerService.java  ← Publishes LinkStatusChanged if status differs
│       ├── model/
│       │   ├── LinkCheckResult.java       ← JPA entity (audit history)
│       │   └── LinkStatusSnapshot.java   ← JPA entity (current state per link)
│       ├── repository/
│       │   ├── LinkCheckResultRepository.java
│       │   └── LinkStatusSnapshotRepository.java
│       ├── dto/
│       │   ├── LinkCheckJob.java          ← Kafka message consumed
│       │   └── LinkStatusChanged.java    ← Kafka message produced
│       └── config/
│           ├── KafkaConsumerConfig.java
│           └── KafkaProducerConfig.java
│
└── alert-service/
    ├── Dockerfile
    ├── pom.xml
    └── src/main/java/com/deadlink/alert/
        ├── AlertApplication.java
        ├── consumer/
        │   └── LinkStatusConsumer.java    ← @KafkaListener on link-status-changed
        ├── controller/
        │   └── ReportController.java      ← GET /api/report/{siteId}
        ├── service/
        │   ├── AlertService.java          ← Webhook delivery + deduplication logic
        │   └── ReportService.java         ← Aggregates report from DB
        ├── model/
        │   └── AlertLog.java              ← JPA entity
        ├── repository/
        │   └── AlertLogRepository.java
        ├── dto/
        │   └── LinkStatusChanged.java     ← Kafka message consumed
        └── config/
            └── KafkaConsumerConfig.java
```

---

## 🗄️ Database Schema

All services share one PostgreSQL instance but use separate tables. Use Flyway for all migrations.

### `sites` (crawler-service)

```sql
CREATE TABLE sites (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name            VARCHAR(255) NOT NULL,
    root_url        TEXT NOT NULL,
    crawl_depth     INTEGER NOT NULL DEFAULT 2,
    check_interval  INTEGER NOT NULL DEFAULT 24,  -- hours
    webhook_url     TEXT,
    owner_email     VARCHAR(255),
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    last_crawled_at TIMESTAMP
);
```

### `discovered_links` (crawler-service)

```sql
CREATE TABLE discovered_links (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    site_id    UUID NOT NULL REFERENCES sites(id),
    url        TEXT NOT NULL,
    found_on   TEXT NOT NULL,            -- page where this link was found
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE(site_id, url)                 -- deduplication constraint
);
```

### `link_check_results` (checker-service) — audit history

```sql
CREATE TABLE link_check_results (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    link_id        UUID NOT NULL,        -- references discovered_links.id
    checked_at     TIMESTAMP NOT NULL DEFAULT NOW(),
    http_status    INTEGER,              -- null for timeout/DNS failure
    response_time  INTEGER,             -- milliseconds
    status         VARCHAR(20) NOT NULL, -- HEALTHY | BROKEN | REDIRECTED | TIMEOUT
    redirect_url   TEXT                 -- populated when status = REDIRECTED
);
```

### `link_status_snapshots` (checker-service) — current state

```sql
CREATE TABLE link_status_snapshots (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    link_id               UUID NOT NULL UNIQUE,
    current_status        VARCHAR(20) NOT NULL,
    last_changed_at       TIMESTAMP NOT NULL,
    consecutive_failures  INTEGER NOT NULL DEFAULT 0
);
```

### `alert_logs` (alert-service)

```sql
CREATE TABLE alert_logs (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    site_id     UUID NOT NULL,
    link_id     UUID NOT NULL,
    alert_type  VARCHAR(20) NOT NULL,  -- LINK_BROKEN | LINK_RECOVERED
    fired_at    TIMESTAMP NOT NULL DEFAULT NOW(),
    delivered   BOOLEAN NOT NULL DEFAULT FALSE
);
```

---

## 📨 Kafka Topics & Message Contracts

### Topic: `link-check-jobs`

Produced by: Crawler Service
Consumed by: Checker Service

```json
{
  "linkId": "uuid",
  "siteId": "uuid",
  "url": "https://example.com/some-page",
  "crawledAt": "2024-01-15T10:30:00Z"
}
```

### Topic: `link-status-changed`

Produced by: Checker Service (ONLY when status differs from last snapshot)
Consumed by: Alert Service

```json
{
  "linkId": "uuid",
  "siteId": "uuid",
  "url": "https://example.com/some-page",
  "previousStatus": "HEALTHY",
  "newStatus": "BROKEN",
  "httpStatus": 404,
  "changedAt": "2024-01-15T10:30:00Z"
}
```

---

## 🔌 REST API Contracts

### Crawler Service (Port 8081)

```
POST /api/sites
Body: {
  "name": "Razorpay Docs",
  "rootUrl": "https://docs.razorpay.com",
  "crawlDepth": 2,
  "checkIntervalHours": 24,
  "webhookUrl": "https://hooks.slack.com/...",   // optional
  "ownerEmail": "team@company.com"               // optional
}
Response 201: { "siteId": "uuid", "message": "Site registered. Crawl started." }

GET /api/sites/{siteId}
Response 200: { site object with last crawl stats }

POST /api/sites/{siteId}/crawl
Response 202: { "message": "Manual crawl triggered." }
```

### Alert Service (Port 8083)

```
GET /api/report/{siteId}
Response 200: {
  "siteId": "uuid",
  "siteName": "Razorpay Docs",
  "lastCrawledAt": "2024-01-15T10:30:00Z",
  "summary": {
    "total": 340,
    "healthy": 312,
    "broken": 20,
    "redirected": 8
  },
  "brokenLinks": [
    {
      "url": "https://old-domain.com/api",
      "foundOn": "https://docs.razorpay.com/guides/setup",
      "status": "BROKEN",
      "httpStatus": 404,
      "brokenSince": "2024-01-12T08:00:00Z"
    }
  ]
}
```

---

## ⚙️ Key Implementation Details

### BFS Crawler (CrawlerService.java)

- Use `Jsoup` to fetch and parse HTML — extract all `<a href>` attributes
- Implement BFS with a `visited` Set to avoid re-crawling same URLs
- Respect `crawlDepth` — don't go deeper than configured
- **Rate limiting:** Use a `RateLimiter` (Guava) — max 5 requests/second per domain to avoid hammering sites
- Only crawl same-domain links (don't follow external links during crawl, but DO check them for liveness)
- Save each unique URL to `discovered_links` with `ON CONFLICT DO NOTHING` (upsert)
- After saving, publish one `LinkCheckJob` per discovered link to Kafka

### Link Checker (LinkCheckerService.java)

- Use `java.net.HttpURLConnection` with `HEAD` method — faster than GET, no body download
- Timeout: connect=5s, read=10s
- Classify response:
  - 200–299 → `HEALTHY`
  - 301, 302, 307, 308 → `REDIRECTED` (capture `Location` header)
  - 400–499 → `BROKEN`
  - 500–599 → `BROKEN`
  - IOException / SocketTimeoutException → `TIMEOUT`
  - UnknownHostException → `BROKEN` (DNS failure)
- Always write to `link_check_results` (audit log)
- Read current snapshot from `link_status_snapshots`
- **Only publish to `link-status-changed` if status differs from snapshot** — this is the critical state-change detection logic
- Upsert the snapshot after every check

### Alert Service (AlertService.java)

- Before firing webhook, check `alert_logs` — don't re-alert for the same link within 1 hour (deduplication)
- Webhook payload: POST to `site.webhookUrl` with the `LinkStatusChanged` DTO as JSON body
- Mark `alert_logs.delivered = true` after successful delivery
- Retry failed webhooks up to 3 times with exponential backoff

### Scheduler (SiteSchedulerService.java)

- Use `@Scheduled(fixedDelay = 60000)` — every 60 seconds, query for sites where `last_crawled_at < NOW() - interval`
- Trigger re-crawl for eligible sites
- Update `last_crawled_at` immediately to prevent duplicate triggers

---

## 🐳 Docker Compose

```yaml
# docker-compose.yml — reference for all services
services:
  zookeeper:
    image: confluentinc/cp-zookeeper:7.5.0
    environment:
      ZOOKEEPER_CLIENT_PORT: 2181

  kafka:
    image: confluentinc/cp-kafka:7.5.0
    depends_on: [zookeeper]
    ports: ["9092:9092"]
    environment:
      KAFKA_BROKER_ID: 1
      KAFKA_ZOOKEEPER_CONNECT: zookeeper:2181
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://kafka:9092
      KAFKA_AUTO_CREATE_TOPICS_ENABLE: "true"

  postgres:
    image: postgres:15
    environment:
      POSTGRES_DB: deadlink
      POSTGRES_USER: deadlink
      POSTGRES_PASSWORD: deadlink
    ports: ["5432:5432"]

  crawler-service:
    build: ./crawler-service
    ports: ["8081:8081"]
    depends_on: [kafka, postgres]
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/deadlink
      SPRING_KAFKA_BOOTSTRAP_SERVERS: kafka:9092

  checker-service:
    build: ./checker-service
    ports: ["8082:8082"]
    depends_on: [kafka, postgres]
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/deadlink
      SPRING_KAFKA_BOOTSTRAP_SERVERS: kafka:9092

  alert-service:
    build: ./alert-service
    ports: ["8083:8083"]
    depends_on: [kafka, postgres]
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/deadlink
      SPRING_KAFKA_BOOTSTRAP_SERVERS: kafka:9092
```

---

## 🔁 GitHub Actions CI/CD

```yaml
# .github/workflows/ci.yml
name: CI/CD Pipeline
on:
  push:
    branches: [main]
  pull_request:
    branches: [main]

jobs:
  test-and-build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          java-version: "17"
          distribution: "temurin"

      - name: Run tests — Crawler Service
        run: cd crawler-service && mvn test

      - name: Run tests — Checker Service
        run: cd checker-service && mvn test

      - name: Run tests — Alert Service
        run: cd alert-service && mvn test

      - name: Log in to GitHub Container Registry
        if: github.ref == 'refs/heads/main'
        uses: docker/login-action@v3
        with:
          registry: ghcr.io
          username: ${{ github.actor }}
          password: ${{ secrets.GITHUB_TOKEN }}

      - name: Build and push Docker images
        if: github.ref == 'refs/heads/main'
        run: |
          docker build -t ghcr.io/${{ github.repository }}/crawler-service:latest ./crawler-service
          docker build -t ghcr.io/${{ github.repository }}/checker-service:latest ./checker-service
          docker build -t ghcr.io/${{ github.repository }}/alert-service:latest ./alert-service
          docker push ghcr.io/${{ github.repository }}/crawler-service:latest
          docker push ghcr.io/${{ github.repository }}/checker-service:latest
          docker push ghcr.io/${{ github.repository }}/alert-service:latest
```

---

## 🧪 Testing Guidelines

- Maintain **>90% unit test coverage** across all services (mirrors Satvic's Zeta internship standard)
- Use **JUnit 5 + Mockito** — no integration tests needed for unit layer
- Key classes to test thoroughly:
  - `CrawlerService` — BFS logic, depth limiting, deduplication
  - `LinkCheckerService` — all status classifications, edge cases (timeout, DNS fail, redirect chain)
  - `AlertService` — deduplication logic, retry logic
  - `KafkaProducerService` in each service — verify correct topic and payload
- Use `@ExtendWith(MockitoExtension.class)` for all unit tests
- Mock `HttpURLConnection` for checker tests — don't make real HTTP calls in unit tests

---

## 📦 Maven Dependencies (add to each service's pom.xml)

```xml
<!-- Spring Boot starter parent: 3.2.x -->
<dependencies>
  <dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
  </dependency>
  <dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-jpa</artifactId>
  </dependency>
  <dependency>
    <groupId>org.springframework.kafka</groupId>
    <artifactId>spring-kafka</artifactId>
  </dependency>
  <dependency>
    <groupId>org.postgresql</groupId>
    <artifactId>postgresql</artifactId>
  </dependency>
  <dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-core</artifactId>
  </dependency>
  <!-- Crawler service only -->
  <dependency>
    <groupId>org.jsoup</groupId>
    <artifactId>jsoup</artifactId>
    <version>1.17.2</version>
  </dependency>
  <dependency>
    <groupId>com.google.guava</groupId>
    <artifactId>guava</artifactId>
    <version>32.1.3-jre</version>
  </dependency>
  <!-- Test -->
  <dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-test</artifactId>
    <scope>test</scope>
  </dependency>
  <dependency>
    <groupId>org.springframework.kafka</groupId>
    <artifactId>spring-kafka-test</artifactId>
    <scope>test</scope>
  </dependency>
</dependencies>
```

---

## 🗓️ 7-Day Build Plan

| Day | Goal            | Done when                                                                                             |
| --- | --------------- | ----------------------------------------------------------------------------------------------------- |
| 1–2 | Crawler Service | POST /api/sites works, BFS crawl runs, jobs published to Kafka, JUnit tests passing                   |
| 3–4 | Checker Service | Consumes jobs, HTTP checks work, DB writes correct, status-change detection publishes to second topic |
| 5   | Alert Service   | Consumes status changes, webhook fires, GET /api/report/{siteId} returns correct data                 |
| 6   | Docker + CI/CD  | `docker-compose up` starts full stack, GitHub Actions pipeline green                                  |
| 7   | GitHub polish   | README with architecture diagram, badges, demo GIF, repo pinned                                       |

---

## 💬 Key Interview Talking Points

**"Why Kafka instead of direct DB writes on redirect?"**
The redirect must be sub-10ms. Writing to DB or checking analytics synchronously adds latency on the hot path. Kafka decouples the hot path (redirect) from analytics (checker), letting each scale independently.

**"How do you avoid checking the same link 50 times if it appears on 50 pages?"**
`discovered_links` has a `UNIQUE(site_id, url)` constraint. The Kafka job payload carries `linkId`, not raw URLs. Even if the crawler encounters the same URL on 50 pages, only one check job is enqueued (idempotent upsert).

**"What if the Checker Worker crashes mid-run?"**
Kafka's consumer group offset means unprocessed messages stay in the topic. When the worker restarts, it resumes from its last committed offset. Zero data loss — this is Kafka's core durability guarantee.

**"How would you scale this to 10,000 monitored sites?"**
Increase Kafka partitions for `link-check-jobs` and run multiple Checker Service instances in the same consumer group. Each partition is consumed by one instance — linear horizontal scaling with zero code changes. The Crawler and Alert services are stateless too.

**"Why did you choose HEAD over GET for link checking?"**
HEAD requests ask the server for headers only — no response body is transferred. This is 10–50x faster and less bandwidth-intensive for checking liveness, which is all we care about. Some servers don't support HEAD; the checker falls back to GET in that case.

---

## 🚀 Getting Started (for Claude Code)

```bash
# 1. Clone and enter project
git clone https://github.com/bajpaisatvic/deadlink
cd deadlink

# 2. Start infrastructure
docker-compose up zookeeper kafka postgres -d

# 3. Run any service locally
cd crawler-service
mvn spring-boot:run

# 4. Run all tests
mvn test

# 5. Start full stack
docker-compose up --build
```

**When asking Claude Code for help, always specify which service you're working in.**
Example: "In the checker-service, implement the LinkCheckerService.java that consumes from Kafka and performs HTTP HEAD checks..."
