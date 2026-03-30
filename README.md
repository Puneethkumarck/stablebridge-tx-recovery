# StableBridge Transaction Recovery

Fintech microservice for stablecoin transaction recovery across EVM and Solana chains. Detects stuck or failed on-chain transactions and orchestrates automated recovery with configurable escalation, gas management, and human-approval workflows.

## Architecture

Hexagonal (Ports & Adapters) + DDD + CQRS + Event-Driven, built with Java 25 and Spring Boot 4.0.x.

```
                         ┌─────────────────────────────────┐
                         │       Application Layer          │
                         │  Controllers  Listeners  Jobs    │
                         └────────────┬────────────────────┘
                                      │
                         ┌────────────▼────────────────────┐
                         │         Domain Layer             │
                         │  Services  Aggregates  Ports     │
                         │  State Machine  Events  Models   │
                         └────────────┬────────────────────┘
                                      │
          ┌──────────────┬────────────▼──────┬──────────────┐
          │  PostgreSQL   │    Kafka          │   RPC Nodes  │
          │  (JPA/Flyway) │    (Events)       │   (EVM/Sol)  │
          └──────────────┴───────────────────┴──────────────┘

  External:  Temporal (Workflows)  ·  Redis (Cache/Nonce)  ·  Prometheus/Grafana
```

### Module Structure

```
stablebridge-tx-recovery/               <- Root (convention plugins in buildSrc/)
├── stablebridge-tx-recovery/           <- Main Spring Boot application
├── stablebridge-tx-recovery-api/       <- Shared API contracts (DTOs, events, validation)
└── buildSrc/                           <- Gradle convention plugins (service + library)
```

### Tech Stack

| Component       | Version / Library                      |
|-----------------|----------------------------------------|
| Java            | 25 (LTS)                               |
| Spring Boot     | 4.0.3                                  |
| Build           | Gradle (Kotlin DSL) + convention plugins |
| Database        | PostgreSQL 16                           |
| Migrations      | Flyway                                 |
| Messaging       | Kafka (Redpanda in dev)                |
| Workflows       | Temporal                               |
| Cache           | Redis                                  |
| Resilience      | Resilience4j (circuit breakers)        |
| Mapping         | MapStruct 1.6.3                        |
| Crypto          | Bouncy Castle                          |
| Observability   | Micrometer + Prometheus + Grafana      |
| Containers      | Jib (eclipse-temurin:25-jre-alpine)    |

## Quick Start

### Prerequisites

- Java 25
- Docker & Docker Compose

### Run

```bash
# 1. Start infrastructure (PostgreSQL, Redis, Kafka, Temporal, Prometheus, Grafana)
make infra-up

# 2. Run the application with dev profile
make run

# 3. Submit a test transaction
curl -s -X POST http://localhost:8080/api/v1/transactions \
  -H "Content-Type: application/json" \
  -H "X-API-Key: <your-api-key>" \
  -d '{
    "chain": "ethereum_mainnet",
    "fromAddress": "0x...",
    "toAddress": "0x...",
    "tokenContract": "0xA0b86991c6218b36c1d19D4a2e9Eb0cE3606eB48",
    "amount": "1000000"
  }' | jq
```

### Available Make Targets

| Target             | Description                                      |
|--------------------|--------------------------------------------------|
| `make build`       | Compile + test + Spotless check                  |
| `make test`        | Run unit tests                                   |
| `make integration-test` | Run integration tests with Testcontainers   |
| `make run`         | Start application with dev profile               |
| `make infra-up`    | Start all infrastructure via Docker Compose       |
| `make infra-down`  | Stop all infrastructure                          |
| `make format`      | Auto-format code with Spotless                   |
| `make check`       | Run Spotless check + full build                  |
| `make clean`       | Clean Gradle build output                        |

## API Reference

Base URL: `http://localhost:8080`

### Transactions

| Method | Path                            | Description                      |
|--------|---------------------------------|----------------------------------|
| POST   | `/api/v1/transactions`          | Submit a single transaction      |
| POST   | `/api/v1/transactions/batch`    | Submit a batch of transactions   |
| GET    | `/api/v1/transactions/{id}`     | Get transaction by ID            |
| GET    | `/api/v1/transactions`          | List transactions with filters   |

**List query parameters:** `chain`, `status`, `fromAddress`, `toAddress`, `token`, `fromDate`, `toDate`, `page` (default 0), `size` (default 20)

### Approvals

| Method | Path                                        | Description              |
|--------|---------------------------------------------|--------------------------|
| POST   | `/api/v1/transactions/{id}/approve`         | Approve a transaction    |
| POST   | `/api/v1/transactions/{id}/cancel`          | Cancel a transaction     |

### Chain Status

| Method | Path                      | Description                    |
|--------|---------------------------|--------------------------------|
| GET    | `/api/v1/status`          | Get all chain statuses         |
| GET    | `/api/v1/status/{chain}`  | Get detailed status for chain  |

### Gas Oracle

| Method | Path                          | Description                        |
|--------|-------------------------------|------------------------------------|
| GET    | `/api/v1/gas/{chain}`         | Get current gas estimates          |
| GET    | `/api/v1/gas/{chain}/history` | Get gas price history (query: `hours`, default 24, max 168) |

### Address Pool

| Method | Path                                    | Description                         |
|--------|-----------------------------------------|-------------------------------------|
| POST   | `/api/v1/addresses`                     | Register a new address              |
| GET    | `/api/v1/addresses`                     | List addresses (query: `chain`, `tier`, `status`) |
| DELETE | `/api/v1/addresses/{address}`           | Drain an address (query: `chain`)   |
| POST   | `/api/v1/addresses/{address}/nonces/sync` | Sync nonce from chain             |

### Health & Metrics

Management endpoints run on port **8081**:

| Path                        | Description             |
|-----------------------------|-------------------------|
| `/actuator/health`          | Health check (all indicators) |
| `/actuator/prometheus`      | Prometheus metrics       |
| `/actuator/info`            | Application info         |

### Error Response Format

All errors follow a consistent structure:

```json
{
  "errorCode": "STR-4041",
  "message": "Transaction not found",
  "timestamp": "2026-03-30T12:00:00Z",
  "path": "/api/v1/transactions/abc123"
}
```

Error code prefixes: `STR-400x` (bad request), `STR-404x` (not found), `STR-409x` (conflict), `STR-500x` (server error).

## Configuration Reference

All custom properties are under the `str.*` prefix. Configure via `application.yml` or environment variables.

### API Security (`str.api.*`)

| Property           | Type              | Default | Description                           |
|--------------------|-------------------|---------|---------------------------------------|
| `str.api.key`      | String            | `""`    | Global API key for authentication     |
| `str.api.operators` | Map<String,String> | `{}`   | Named operator-to-key mappings        |

### Signer (`str.signer.*`)

| Property                          | Type    | Default | Description                        |
|-----------------------------------|---------|---------|------------------------------------|
| `str.signer.backend`              | String  |         | Signer backend type                |
| `str.signer.keystore-path`        | String  |         | Path to keystore file              |
| `str.signer.password`             | String  |         | Keystore password                  |
| `str.signer.callback.hmac-secret` | String  |         | HMAC secret for callback verification |
| `str.signer.callback.timeout`     | Duration| `PT5S`  | Callback timeout                   |
| `str.signer.callback.tls.verify`  | boolean | `true`  | TLS certificate verification       |

### Kafka (`str.kafka.*`)

| Property                  | Type   | Default                           | Description                |
|---------------------------|--------|-----------------------------------|----------------------------|
| `str.kafka.enabled-chains`| String | `ethereum_mainnet,solana_mainnet`  | Comma-separated chain IDs  |
| `str.kafka.topic-replicas`| int    | `1`                               | Topic replication factor   |

### Temporal (`str.temporal.*`)

| Property                                   | Type     | Default                      | Description                         |
|--------------------------------------------|----------|------------------------------|-------------------------------------|
| `str.temporal.target`                      | String   | `127.0.0.1:7233`             | Temporal frontend address           |
| `str.temporal.namespace`                   | String   | `stablebridge-tx-recovery`   | Temporal namespace                  |
| `str.temporal.task-queue`                  | String   | `str-transaction-lifecycle`  | Worker task queue                   |
| `str.temporal.workflow-execution-timeout`  | Duration | `PT24H`                      | Max workflow execution time         |
| `str.temporal.workflow-run-timeout`        | Duration | `PT2H`                       | Max single workflow run time        |
| `str.temporal.non-retryable-exceptions`    | List     |                              | Exception classes that skip retry   |
| `str.temporal.activity-options.*`          | Object   |                              | Per-activity timeout/retry config   |

Activity option profiles: `default-options`, `signing`, `confirmation`, `recovery-execution`. Each has: `start-to-close-timeout`, `max-attempts`, `initial-interval`, `backoff-coefficient`.

### Escalation (`str.escalation.*`)

| Property                                  | Type       | Default  | Description                        |
|-------------------------------------------|------------|----------|------------------------------------|
| `str.escalation.high-value-threshold-usd` | BigDecimal | `50000`  | USD threshold for high-value rules |
| `str.escalation.gas-budget.percentage`    | double     | `0.01`   | Gas budget as % of tx value        |
| `str.escalation.gas-budget.absolute-min-usd` | BigDecimal | `5`  | Minimum gas budget in USD          |
| `str.escalation.gas-budget.absolute-max-usd` | BigDecimal | `500`| Maximum gas budget in USD          |
| `str.escalation.default-tiers`            | List       |          | Standard escalation tier config    |
| `str.escalation.high-value-tiers`         | List       |          | High-value escalation tier config  |

Each tier: `level`, `stuck-threshold` (Duration), `gas-multiplier`, `requires-human-approval` (boolean), `description`.

### Transaction Submission (`str.submission.*`)

| Property                              | Type       | Default   | Description                         |
|---------------------------------------|------------|-----------|-------------------------------------|
| `str.submission.sequential-threshold-usd` | BigDecimal | `100000` | USD threshold for sequential submission |
| `str.submission.max-pipeline-depth`   | int        | `20`      | Max concurrent pipeline depth       |

### Redis (`str.redis.*`)

| Property         | Type   | Default     | Description   |
|------------------|--------|-------------|---------------|
| `str.redis.host` | String | `localhost` | Redis host    |
| `str.redis.port` | int    | `6379`      | Redis port    |

### Chain Configuration (`str.chains.*`)

Per-chain configuration keyed by chain ID (e.g., `ethereum_mainnet`, `base_mainnet`, `polygon_mainnet`, `solana_mainnet`):

| Property                                    | Type     | Description                         |
|---------------------------------------------|----------|-------------------------------------|
| `str.chains.{id}.enabled`                   | boolean  | Enable/disable chain                |
| `str.chains.{id}.chain-family`              | String   | `EVM` or `SOLANA`                   |
| `str.chains.{id}.chain-id`                  | int      | Numeric chain ID                    |
| `str.chains.{id}.finality-blocks`           | int      | Blocks required for finality        |
| `str.chains.{id}.stuck-threshold-blocks`    | int      | Blocks before tx is considered stuck|
| `str.chains.{id}.poll-interval`             | Duration | Block polling interval              |
| `str.chains.{id}.max-fee-cap-gwei`          | int      | Max gas fee cap (EVM only)          |
| `str.chains.{id}.token-contracts`           | List     | Monitored token contract addresses (EVM) |
| `str.chains.{id}.token-mints`               | List     | Monitored token mint addresses (Solana) |
| `str.chains.{id}.rpc.urls`                  | List     | RPC endpoint URLs                   |
| `str.chains.{id}.rpc.timeout`               | Duration | RPC call timeout                    |
| `str.chains.{id}.rpc.max-retries`           | int      | Max RPC retry attempts              |
| `str.chains.{id}.rpc.rate-limit-rps`        | int      | Requests per second limit           |
| `str.chains.{id}.rpc.rate-limit-burst`      | int      | Burst request limit                 |
| `str.chains.{id}.rpc.circuit-breaker.*`     | Object   | Resilience4j circuit breaker config |

### EVM Legacy (`str.evm.chains.*`)

Legacy EVM chain config (array-based): `name`, `rpc-urls`, `max-fee-cap-gwei`, `block-time`, `rpc-timeout`, `rate-limit-per-second`, `rate-limit-burst`.

## Development

### Build & Test

```bash
./gradlew build                # Full build: compile + test + Spotless check
./gradlew test                 # Unit tests only
./gradlew integrationTest      # Integration tests (requires Docker for Testcontainers)
./gradlew businessTest         # Business/E2E tests
./gradlew spotlessApply        # Auto-format code
```

### Test Infrastructure

Tests use Testcontainers with custom annotations:

| Annotation   | Starts              |
|-------------|---------------------|
| `@PgTest`    | PostgreSQL container |
| `@KafkaTest` | Kafka container      |

Both include `@SpringBootTest` and `@ActiveProfiles("test")`.

### Project Package Structure

```
com.stablebridge.txrecovery/
├── application/          <- Inbound adapters: REST controllers, security, config
├── domain/               <- Core business logic: services, models, ports, state machine
└── infrastructure/       <- Outbound adapters: database, Kafka, RPC clients, Redis
```

Dependencies point inward: `application` -> `domain` <- `infrastructure`.

## Deployment

### Docker Image

Built with Jib (no Dockerfile required):

```bash
./gradlew jibDockerBuild       # Build to local Docker daemon
./gradlew jib                  # Build and push to registry
```

Image: `stablebridge/tx-recovery` | Base: `eclipse-temurin:25-jre-alpine` | Ports: `8080` (app), `8081` (management) | Runs as UID `1000`.

JVM flags: `-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0`

### Infrastructure Requirements

The full stack (via `docker compose up -d`) includes:

| Service          | Image                          | Port(s)       |
|------------------|--------------------------------|---------------|
| PostgreSQL       | `postgres:16-alpine`           | 5432          |
| PostgreSQL (Temporal) | `postgres:16-alpine`      | 5433          |
| Redis            | `redis/redis-stack:7.4.0-v8`   | 6379, 8001    |
| Redpanda (Kafka) | `redpanda:v24.3.1`            | 19092         |
| Temporal         | `temporalio/auto-setup:1.29.4` | 7233          |
| Temporal UI      | `temporalio/ui:2.48.1`         | 8088          |
| Prometheus       | `prom/prometheus:v3.4.0`       | 9091          |
| Grafana          | `grafana/grafana:11.6.0`       | 3000          |

### Environment Variables

Key environment variables for production deployment:

| Variable                          | Description                   |
|-----------------------------------|-------------------------------|
| `STR_SIGNER_BACKEND`             | Signer backend type           |
| `STR_SIGNER_KEYSTORE_PATH`       | Keystore file path            |
| `STR_SIGNER_PASSWORD`            | Keystore password             |
| `STR_SIGNER_CALLBACK_HMAC_SECRET`| Callback HMAC secret          |
| `TEMPORAL_FRONTEND_URL`          | Temporal server address       |
| `STR_REDIS_HOST`                 | Redis host                    |
| `STR_REDIS_PORT`                 | Redis port                    |
| `EVM_ETHEREUM_RPC_URL`           | Ethereum RPC endpoint         |
| `EVM_BASE_RPC_URL`               | Base RPC endpoint             |
| `EVM_POLYGON_RPC_URL`            | Polygon RPC endpoint          |
| `SOLANA_RPC_URL`                 | Solana RPC endpoint           |

### Health Checks

- **Liveness/Readiness:** `GET http://localhost:8081/actuator/health`
- **Metrics:** `GET http://localhost:8081/actuator/prometheus`

Health indicators report status for PostgreSQL, Redis, Kafka, Temporal, and per-chain RPC connectivity.

## License

Proprietary. All rights reserved.
