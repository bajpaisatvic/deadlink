# DeadLink — Distributed Broken Link Detector

![CI/CD](https://github.com/bajpaisatvic/deadlink/actions/workflows/ci.yml/badge.svg)
![Java](https://img.shields.io/badge/Java-17-orange)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5-green)
![Kafka](https://img.shields.io/badge/Apache%20Kafka-7.5-black)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-15-blue)
![Docker](https://img.shields.io/badge/Docker-Compose-2496ED)

A self-hostable, distributed service that continuously monitors websites for broken links and alerts teams the moment link health changes — or recovers.

---

## What It Does

Register a URL. DeadLink crawls it, discovers every link on the page graph, and fires a webhook the moment a healthy link goes dead — or a dead link comes back. Everything runs in Docker. Zero manual checks.

---

## Architecture

```
┌──────────────────────────────────────────────────────────────────┐
│                        CLIENT / USER                             │
│              POST /api/sites · GET /api/sites/{id}               │
│              POST /api/sites/{id}/crawl                          │
└────────────────────────────┬─────────────────────────────────────┘
                             │ REST
                             ▼
┌──────────────────────────────────────────────────────────────────┐
│                    CRAWLER SERVICE  :8081                        │
│                                                                  │
│  SiteController ──► CrawlerService (BFS via Jsoup)               │
│                          │                                       │
│  SiteSchedulingService ──┘  (every 60s, re-crawl eligible sites) │
│                          │                                       │
│  KafkaProducerService ───┘  publishes one job per discovered URL │
└────────────────────────────┬─────────────────────────────────────┘
                             │ Kafka topic: link-check-jobs
                             ▼
┌──────────────────────────────────────────────────────────────────┐
│                    CHECKER SERVICE  :8082                        │
│                                                                  │
│  LinkCheckConsumer ──► LinkCheckerService (HTTP HEAD)            │
│                          │                                       │
│                    ┌─────┴──────┐                                │
│                    │            │                                │
│             link_check_results  link_status_snapshots            │
│             (audit history)     (current state)                  │
│                                 │                                │
│              status changed? ───┘                                │
│              KafkaProducerService ──► publishes event            │
└────────────────────────────┬─────────────────────────────────────┘
                             │ Kafka topic: link-status-changed
                             ▼
┌──────────────────────────────────────────────────────────────────┐
│                     ALERT SERVICE  :8083                         │
│                                                                  │
│  LinkStatusConsumer ──► AlertService (webhook + deduplication)   │
│                                                                  │
│  ReportController ──► ReportService                              │
│  GET /api/report/{siteId}                                        │
└──────────────────────────────────────────────────────────────────┘

Infrastructure (Docker Compose)
  ├── Apache Kafka + Zookeeper
  └── PostgreSQL 15  (schemas: crawler · checker · alert)
```

### Data Flow

1. **Register** — `POST /api/sites` saves the site and immediately triggers a BFS crawl.
2. **Crawl** — Crawler does a depth-limited BFS, extracts every `<a href>`, deduplicates against `discovered_links`, and publishes one `LinkCheckJob` per unique URL to Kafka.
3. **Check** — Checker consumes jobs, fires an HTTP HEAD request, classifies the result (`HEALTHY` / `BROKEN` / `REDIRECTED` / `TIMEOUT`), and writes to the audit log. If the status differs from the last snapshot it publishes a `LinkStatusChanged` event.
4. **Alert** — Alert Service consumes status-change events, fires the registered webhook, deduplicates alerts within a 1-hour window, and persists every alert to `alert_logs`.
5. **Re-crawl** — The scheduler queries for sites whose `last_crawled_at` is older than `check_interval` and re-triggers step 2 automatically.

---

## Services & Endpoints

### Crawler Service — `localhost:8081`

| Method | Path                        | Description                           |
| ------ | --------------------------- | ------------------------------------- |
| `POST` | `/api/sites`                | Register a new site and start crawl   |
| `GET`  | `/api/sites/{siteId}`       | Get site details and last crawl stats |
| `POST` | `/api/sites/{siteId}/crawl` | Trigger a manual re-crawl             |

**Register a site:**

```bash
curl -X POST http://localhost:8081/api/sites \
  -H "Content-Type: application/json" \
  -d '{
    "name": "My Docs",
    "rootUrl": "https://docs.example.com",
    "crawlDepth": 2,
    "checkIntervalHours": 24,
    "webhookUrl": "https://hooks.slack.com/...",
    "ownerEmail": "team@example.com"
  }'
```

```json
{ "siteId": "550e8400-...", "message": "Site registered. Crawl started." }
```

### Alert Service — `localhost:8083`

| Method | Path                   | Description                        |
| ------ | ---------------------- | ---------------------------------- |
| `GET`  | `/api/report/{siteId}` | Full broken-link report for a site |

```bash
curl http://localhost:8083/api/report/550e8400-...
```

```json
{
  "siteId": "550e8400-...",
  "siteName": "My Docs",
  "lastCrawledAt": "2024-01-15T10:30:00Z",
  "summary": { "total": 340, "healthy": 312, "broken": 20, "redirected": 8 },
  "brokenLinks": [
    {
      "url": "https://old-domain.com/api",
      "foundOn": "https://docs.example.com/guides/setup",
      "status": "BROKEN",
      "httpStatus": 404,
      "brokenSince": "2024-01-12T08:00:00Z"
    }
  ]
}
```

---

## Kafka Topics

| Topic                 | Producer        | Consumer        | When                                                     |
| --------------------- | --------------- | --------------- | -------------------------------------------------------- |
| `link-check-jobs`     | Crawler Service | Checker Service | After every crawl, one message per discovered URL        |
| `link-status-changed` | Checker Service | Alert Service   | Only when a link's status differs from its last snapshot |

**`link-check-jobs` payload:**

```json
{
  "linkId": "uuid",
  "siteId": "uuid",
  "url": "https://...",
  "crawledAt": "2024-01-15T10:30:00Z"
}
```

**`link-status-changed` payload:**

```json
{
  "linkId": "uuid",
  "siteId": "uuid",
  "url": "https://...",
  "previousStatus": "HEALTHY",
  "newStatus": "BROKEN",
  "httpStatus": 404,
  "changedAt": "2024-01-15T10:30:00Z"
}
```

---

## Database Schema

All three services share one PostgreSQL instance (`deadlink` database) but write to separate schemas.

```
crawler schema          checker schema              alert schema
──────────────          ──────────────              ────────────
sites                   link_check_results          alert_logs
discovered_links        link_status_snapshots
```

---

## Getting Started

### Prerequisites

- Docker + Docker Compose
- Java 17 (for local development)
- Maven (or use the included `./mvnw` wrapper)

### Run the Full Stack

```bash
git clone https://github.com/bajpaisatvic/deadlink
cd deadlink

docker-compose up --build
```

Services will be available at:

- Crawler Service: http://localhost:8081
- Checker Service: http://localhost:8082
- Alert Service: http://localhost:8083

### Run a Single Service Locally

```bash
# Start infrastructure only
docker-compose up zookeeper kafka postgres -d

# Run crawler-service
cd crawler-service
./mvnw spring-boot:run
```

### Run Tests

```bash
# All services
cd crawler-service && ./mvnw test
cd checker-service && ./mvnw test
cd alert-service && ./mvnw test
```

---

## Project Structure

```
deadlink/
├── docker-compose.yaml
├── .github/
│   └── workflows/ci.yml
├── crawler-service/
│   └── src/main/java/com/deadlink/crawler/
│       ├── controller/SiteController.java
│       ├── service/CrawlerService.java          ← BFS + Jsoup
│       ├── service/SiteSchedulingService.java   ← @Scheduled re-crawl
│       ├── service/KafkaProducerService.java
│       ├── model/  (Site, DiscoveredLink)
│       └── config/ (KafkaProducerConfig)
├── checker-service/
│   └── src/main/java/com/deadlink/checker/
│       ├── consumer/LinkCheckConsumer.java
│       ├── service/LinkCheckerService.java      ← HTTP HEAD checks
│       ├── service/KafkaProducerService.java
│       ├── model/  (LinkCheckResult, LinkStatusSnapshot)
│       └── config/ (KafkaConsumerConfig, KafkaProducerConfig)
└── alert-service/
    └── src/main/java/com/deadlink/alert/
        ├── consumer/LinkStatusConsumer.java
        ├── controller/ReportController.java
        ├── service/AlertService.java            ← Webhook + deduplication
        ├── service/ReportService.java
        ├── model/  (AlertLog)
        └── config/ (KafkaConsumerConfig)
```

---

## CI/CD

GitHub Actions runs on every push to `main` and every pull request:

1. **Test** — runs `./mvnw test` for all three services in parallel
2. **Build & Push** (main branch only) — builds Docker images and pushes to GitHub Container Registry (`ghcr.io`)

Images are tagged `:latest` and available at:

```
ghcr.io/bajpaisatvic/deadlink/crawler-service:latest
ghcr.io/bajpaisatvic/deadlink/checker-service:latest
ghcr.io/bajpaisatvic/deadlink/alert-service:latest
```

---

## Design Decisions

**Why Kafka instead of synchronous calls between services?**
Kafka decouples the crawl hot-path from the check workers. Checker instances can scale horizontally — add Kafka partitions and run more instances in the same consumer group. Zero code changes needed.

**Why HTTP HEAD instead of GET for link checking?**
HEAD requests return headers only — no response body is transferred. This is 10–50× faster and uses far less bandwidth when all we care about is liveness.

**How does deduplication work?**
`discovered_links` has a `UNIQUE(site_id, url)` constraint. Kafka jobs carry `linkId`, not raw URLs. Even if a URL appears on 50 pages, exactly one check job is enqueued.

**What happens if the Checker crashes mid-run?**
Kafka consumer group offsets ensure unprocessed messages stay in the topic. When the service restarts it resumes from its last committed offset — zero message loss.

**How does the alert deduplication work?**
Before firing a webhook, AlertService checks `alert_logs` for the same `link_id` within the last hour. Duplicate alerts for flapping links are suppressed.

---

## Tech Stack

| Layer            | Technology                   |
| ---------------- | ---------------------------- |
| Language         | Java 17                      |
| Framework        | Spring Boot 3.5              |
| Messaging        | Apache Kafka (Confluent 7.5) |
| Database         | PostgreSQL 15                |
| Migrations       | Flyway                       |
| HTML Parsing     | Jsoup 1.17.2                 |
| Containerization | Docker + Docker Compose      |
| CI/CD            | GitHub Actions → GHCR        |
| Testing          | JUnit 5 + Mockito            |
