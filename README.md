<p align="center">
  <img src="https://img.shields.io/badge/Java-25_LTS-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white" alt="Java 25"/>
  <img src="https://img.shields.io/badge/Spring_Boot-4.0.3-6DB33F?style=for-the-badge&logo=springboot&logoColor=white" alt="Spring Boot 4.0.3"/>
  <img src="https://img.shields.io/badge/PostgreSQL-16-4169E1?style=for-the-badge&logo=postgresql&logoColor=white" alt="PostgreSQL 16"/>
  <img src="https://img.shields.io/badge/Kafka-Redpanda-E0234E?style=for-the-badge&logo=apachekafka&logoColor=white" alt="Kafka"/>
  <img src="https://img.shields.io/badge/Temporal-1.29-000000?style=for-the-badge&logo=temporal&logoColor=white" alt="Temporal"/>
  <img src="https://img.shields.io/badge/Redis-7.4-DC382D?style=for-the-badge&logo=redis&logoColor=white" alt="Redis"/>
  <img src="https://img.shields.io/badge/License-MIT-yellow?style=for-the-badge" alt="License"/>
</p>

# StableBridge Transaction Recovery

> **Enterprise-grade microservice for stablecoin transaction recovery across EVM and Solana chains.** Detects stuck or failed on-chain transactions and orchestrates automated recovery with configurable escalation tiers, gas management, and human-approval workflows.

---

## Table of Contents

- [Problem Statement](#problem-statement)
- [Key Features](#key-features)
- [Architecture](#architecture)
  - [System Architecture](#system-architecture)
  - [Hexagonal Layer Design](#hexagonal-layer-design)
  - [Transaction Lifecycle State Machine](#transaction-lifecycle-state-machine)
  - [Temporal Workflow Orchestration](#temporal-workflow-orchestration)
  - [Event-Driven Architecture](#event-driven-architecture)
- [Supported Chains](#supported-chains)
- [Design Patterns & Principles](#design-patterns--principles)
- [Tech Stack](#tech-stack)
- [Module Structure](#module-structure)
- [Getting Started](#getting-started)
  - [Prerequisites](#prerequisites)
  - [Quick Start](#quick-start)
  - [Make Targets](#make-targets)
- [API Reference](#api-reference)
  - [Transactions](#transactions)
  - [Approvals](#approvals)
  - [Chain Status](#chain-status)
  - [Gas Oracle](#gas-oracle)
  - [Address Pool](#address-pool)
  - [Health & Metrics](#health--metrics)
- [Configuration Reference](#configuration-reference)
- [Resilience & Fault Tolerance](#resilience--fault-tolerance)
- [Security](#security)
- [Observability](#observability)
- [Testing Strategy](#testing-strategy)
- [CI/CD Pipeline](#cicd-pipeline)
- [Deployment](#deployment)
- [Infrastructure](#infrastructure)
- [License](#license)

---

## Problem Statement

Blockchain transactions fail silently. A submitted stablecoin transfer can get stuck due to gas spikes, nonce gaps, network congestion, or RPC node failures. Without automated detection and recovery:

- **Funds stay locked** in pending transactions for hours or days
- **Manual intervention** requires deep chain-specific knowledge
- **No visibility** into which transactions are stuck and why
- **Escalation** depends on ad-hoc processes with no audit trail

StableBridge TX Recovery solves this with a **fully automated, multi-chain transaction lifecycle manager** that detects stuck transactions, applies tiered recovery strategies, and escalates to human operators only when necessary.

---

## Key Features

| Category | Capability |
|----------|-----------|
| **Multi-Chain Support** | EVM chains (Ethereum, Base, Polygon) + Solana with chain-specific transaction managers |
| **Automated Recovery** | Tiered escalation: gas bumping, nonce replacement, transaction resubmission |
| **Human Approval Workflows** | High-value transactions require manual approval via Temporal signals |
| **Durable Orchestration** | Temporal workflows survive process restarts; 24-hour execution timeout per transaction |
| **Gas Oracle** | Real-time gas price estimation with EIP-1559 support and historical tracking |
| **Address Pool Management** | Register, drain, and rotate signer addresses with on-chain nonce synchronization |
| **Transactional Outbox** | Reliable event publishing via outbox pattern — at-least-once delivery, no dual-write problem |
| **Batch Submission** | Submit multiple transactions in a single API call with pipeline depth control |
| **Configurable Escalation** | Separate escalation tiers for standard vs. high-value transactions |
| **Full Observability** | Prometheus metrics, Grafana dashboards, structured logging, health indicators |
| **Pluggable Signing** | Local keystore or remote callback signer with HMAC-SHA256 verification |

---

## Architecture

### System Architecture

```
                                    StableBridge TX Recovery
 ┌──────────────────────────────────────────────────────────────────────────────────────┐
 │                                                                                      │
 │  ┌─────────────────────────────────────────────────────────────────────────────────┐  │
 │  │                          APPLICATION LAYER (Inbound)                            │  │
 │  │                                                                                 │  │
 │  │  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  ┌────────────────────┐  │  │
 │  │  │ Transaction  │  │   Approval   │  │  Gas Oracle  │  │  Address Pool      │  │  │
 │  │  │ Controller   │  │  Controller  │  │  Controller  │  │  Controller        │  │  │
 │  │  └──────┬───────┘  └──────┬───────┘  └──────┬───────┘  └────────┬───────────┘  │  │
 │  │         │                 │                  │                   │              │  │
 │  │  ┌──────┴─────────────────┴──────────────────┴───────────────────┘              │  │
 │  │  │                   Temporal Workflow Engine                                   │  │
 │  │  │            TransactionLifecycleWorkflow (Signals + Queries)                  │  │
 │  │  └──────┬──────────────────────────────────────────────────────────             │  │
 │  └─────────┼──────────────────────────────────────────────────────────────────────┘  │
 │            │                                                                         │
 │  ┌─────────┼──────────────────────────────────────────────────────────────────────┐  │
 │  │         ▼                    DOMAIN LAYER (Core)                                │  │
 │  │                                                                                 │  │
 │  │  ┌──────────────────┐  ┌───────────────────┐  ┌─────────────────────────────┐  │  │
 │  │  │  Transaction     │  │  Escalation       │  │  Address Pool               │  │  │
 │  │  │  Submission      │  │  Policy Engine    │  │  Service                    │  │  │
 │  │  │  Service         │  │                   │  │                             │  │  │
 │  │  └────────┬─────────┘  └────────┬──────────┘  └─────────────┬───────────────┘  │  │
 │  │           │                     │                           │                  │  │
 │  │  ┌────────┴─────────────────────┴───────────────────────────┴───────────────┐  │  │
 │  │  │              State Machine  ·  Domain Events  ·  Repository Ports        │  │  │
 │  │  └─────────────────────────────────┬────────────────────────────────────────┘  │  │
 │  └────────────────────────────────────┼───────────────────────────────────────────┘  │
 │                                       │                                              │
 │  ┌────────────────────────────────────┼───────────────────────────────────────────┐  │
 │  │                                    ▼                                            │  │
 │  │                     INFRASTRUCTURE LAYER (Outbound)                             │  │
 │  │                                                                                 │  │
 │  │  ┌──────────┐ ┌──────────┐ ┌───────────┐ ┌─────────┐ ┌────────┐ ┌───────────┐ │  │
 │  │  │PostgreSQL│ │  Kafka   │ │ EVM RPC   │ │ Solana  │ │ Redis  │ │  Signer   │ │  │
 │  │  │ JPA +    │ │ Outbox   │ │ Client    │ │ RPC     │ │ Nonce  │ │ Callback/ │ │  │
 │  │  │ Flyway   │ │ Relay    │ │ (Web3)    │ │ Client  │ │ + Fee  │ │ Keystore  │ │  │
 │  │  └──────────┘ └──────────┘ └───────────┘ └─────────┘ └────────┘ └───────────┘ │  │
 │  └─────────────────────────────────────────────────────────────────────────────────┘  │
 └──────────────────────────────────────────────────────────────────────────────────────┘

 External:  Temporal Server  ·  Prometheus / Grafana  ·  Blockchain RPC Nodes
```

### Hexagonal Layer Design

The codebase follows strict **Hexagonal Architecture (Ports & Adapters)** with DDD tactical patterns. Dependencies always point inward:

```
┌─────────────────────────────────────────────────────────────────────┐
│                                                                     │
│   application/  ──────────►  domain/  ◄──────────  infrastructure/ │
│   (Inbound Adapters)         (Core)                (Outbound Adapters) │
│                                                                     │
│   REST Controllers           Services              PostgreSQL (JPA) │
│   Temporal Workflows         Aggregates            Kafka (Outbox)   │
│   Scheduled Jobs             State Machine         EVM RPC Client   │
│                              Domain Events         Solana RPC Client│
│   Security Filters           Repository Ports      Redis Adapters   │
│                              Value Objects          Signer Adapters  │
│                              Exceptions             MapStruct Mappers│
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘

Rules:
  ✓ Domain layer has ZERO Spring imports (only Lombok)
  ✓ Domain defines port interfaces — infrastructure implements them
  ✓ No JPA annotations in domain models
  ✓ Application layer is thin — delegates to domain services
  ✓ All mapping via MapStruct at layer boundaries
```

### Transaction Lifecycle State Machine

Every transaction follows a deterministic, type-safe state machine:

```
                              ┌──────────┐
                              │ RECEIVED │  (API submission accepted)
                              └────┬─────┘
                                   │
                              ┌────▼─────┐
                              │ BUILDING │  (construct chain-specific tx)
                              └────┬─────┘
                                   │
                              ┌────▼─────┐
                              │ SIGNING  │  (sign via keystore or callback)
                              └────┬─────┘
                                   │
                              ┌────▼──────┐
                              │ SUBMITTED │  (broadcast to network)
                              └────┬──────┘
                                   │
                              ┌────▼─────┐
                         ┌────│ PENDING  │────┐
                         │    └──────────┘    │
                         │                    │
                    ┌────▼────┐          ┌────▼──────┐
                    │  STUCK  │          │ CONFIRMED │  (included in block)
                    └────┬────┘          └─────┬─────┘
                         │                     │
              ┌──────────┼──────────┐    ┌─────▼──────┐
              │          │          │    │ FINALIZED  │  (block finality reached)
        ┌─────▼─────┐ ┌──▼───────┐ │    └────────────┘
        │RECOVERING │ │AWAITING  │ │         ✓ Terminal
        │(auto)     │ │ HUMAN    │ │
        └─────┬─────┘ └────┬─────┘ │
              │             │       │
              └──────┬──────┘  ┌────▼──────┐
                     │         │CANCELLING │
                     ▼         └─────┬─────┘
              (back to BUILDING      │
               or SUBMITTED)    ┌────▼─────┐     ┌────────┐
                                │CANCELLED │     │ FAILED │
                                └──────────┘     └────────┘
                                  ✓ Terminal      ✓ Terminal

              ┌─────────┐
              │ DROPPED │  (tx disappeared from mempool)
              └─────────┘
```

**Escalation Tiers** — configurable per transaction value:

| Tier | Trigger | Action | Approval |
|------|---------|--------|----------|
| 1 | Stuck > 10 min | Gas bump (1.2x multiplier) | Automatic |
| 2 | Stuck > 30 min | Gas bump (1.5x multiplier) | Automatic |
| 3 | Stuck > 60 min | Nonce replacement (2.0x gas) | **Human required** |

High-value transactions (> $50,000 USD) use a separate, more conservative escalation schedule.

### Temporal Workflow Orchestration

Long-running transaction lifecycles are modeled as **durable Temporal workflows** that survive crashes, restarts, and deployments:

```
┌─────────────────────────── TransactionLifecycleWorkflow ──────────────────────────┐
│                                                                                    │
│  ┌──────────┐    ┌──────────┐    ┌──────────┐    ┌──────────┐    ┌─────────────┐  │
│  │ Acquire  │───►│  Build   │───►│  Sign    │───►│Broadcast │───►│   Poll &    │  │
│  │ Resource │    │    Tx    │    │    Tx    │    │    Tx    │    │   Confirm   │  │
│  └──────────┘    └──────────┘    └──────────┘    └──────────┘    └──────┬──────┘  │
│                                                                         │         │
│                   ┌─────────────────────────────────────────────────────┤         │
│                   │                                                     │         │
│                   ▼                                                     ▼         │
│           ┌──────────────┐    ┌──────────────────┐            ┌──────────────┐   │
│           │  Assess if   │    │   Escalate       │            │  Finalized   │   │
│           │   Stuck      │───►│   (gas bump /    │            │  (publish    │   │
│           │              │    │    replace)       │            │   event)     │   │
│           └──────────────┘    └────────┬─────────┘            └──────────────┘   │
│                                        │                                         │
│                   ┌────────────────────┤                                         │
│                   ▼                    ▼                                         │
│          ┌───────────────┐   ┌─────────────────┐                                │
│          │ Auto-Recovery │   │ Human Approval  │  ◄── Signal: approveRecovery() │
│          └───────┬───────┘   └────────┬────────┘                                │
│                  │                    │           ◄── Signal: cancelTransaction()│
│                  └────────┬───────────┘                                          │
│                           ▼                                                      │
│                    (loop back to Build)              Query: getStatus()          │
│                                                                                  │
└──────────────────────────────────────────────────────────────────────────────────┘

  Execution Timeout: 24 hours  ·  Run Timeout: 2 hours  ·  Continue-As-New on timeout
```

| Activity | Timeout | Max Retries | Backoff |
|----------|---------|-------------|---------|
| Default | 30s | 3 | 2.0x exponential |
| Signing | 10s | 2 | 2.0x exponential |
| Confirmation | 5 min | 1 (no retry) | - |
| Recovery Execution | 60s | 3 | 2.0x exponential |

### Event-Driven Architecture

All event publishing uses the **Transactional Outbox Pattern** to guarantee reliable at-least-once delivery with no dual-write problem:

```
┌──────────────────────────────────────────────────────────────────────┐
│                        TRANSACTION (atomic)                          │
│                                                                      │
│   ┌─────────────┐         ┌──────────────────┐                      │
│   │ Domain      │  save   │  Outbox Event    │  schedule             │
│   │ Aggregate   │────────►│  Table           │─────────┐            │
│   │ (PostgreSQL)│         │  (PostgreSQL)    │         │            │
│   └─────────────┘         └──────────────────┘         │            │
│                                                         │            │
└─────────────────────────────────────────────────────────┼────────────┘
                                                          │
                                              ┌───────────▼──────────┐
                                              │  Outbox Event Relay  │  (scheduled, ShedLock)
                                              │  ┌───────────────┐   │
                                              │  │  Poll outbox   │   │
                                              │  │  Publish Kafka │   │
                                              │  │  Mark sent     │   │
                                              │  └───────────────┘   │
                                              └───────────┬──────────┘
                                                          │
                                              ┌───────────▼──────────┐
                                              │   Kafka              │
                                              │                      │
                                              │  str.tx.events.{chain}│
                                              │  str.tx.dlq.{chain}  │
                                              │  (6 partitions,      │
                                              │   30-day retention)  │
                                              └──────────────────────┘
```

---

## Supported Chains

| Chain | Family | Chain ID | Finality | Stuck Threshold | Gas Model |
|-------|--------|----------|----------|-----------------|-----------|
| Ethereum Mainnet | EVM | 1 | 12 blocks | 250 blocks | EIP-1559 |
| Base Mainnet | EVM | 8453 | 1 block | 100 blocks | EIP-1559 |
| Polygon Mainnet | EVM | 137 | 256 blocks | 500 blocks | EIP-1559 |
| Solana Mainnet | Solana | 0 | 31 slots | 150 slots | Priority fees |

Each chain is independently configurable: RPC endpoints, rate limits, circuit breakers, token contracts/mints, and polling intervals.

---

## Design Patterns & Principles

| Pattern | Where | Purpose |
|---------|-------|---------|
| **Hexagonal Architecture** | Entire codebase | Isolate domain from infrastructure; swap adapters freely |
| **Domain-Driven Design** | `domain/` package | Rich domain models, aggregates, value objects, ubiquitous language |
| **CQRS** | Command handlers + Query services | Separate write (submission, approval) from read (status, listing) paths |
| **Event-Driven** | Kafka + Outbox | Reliable async event publishing with full audit trail of state changes |
| **Transactional Outbox** | `OutboxEventRelay` | Atomically persist domain changes + events in a single DB transaction |
| **State Machine** | `StateMachine<S, T>` | Type-safe, table-driven state transitions with action callbacks |
| **Durable Execution** | Temporal Workflows | Survive crashes; automatic retries; long-running process orchestration |
| **Saga / Compensating Tx** | Recovery workflow | Escalation tiers with rollback (cancellation) support |
| **Repository (Port/Adapter)** | Domain ports + JPA adapters | Domain defines interfaces; infrastructure provides implementations |
| **Strategy** | `EvmRecoveryStrategy` / `SolanaRecoveryStrategy` | Chain-specific recovery logic behind a common interface |
| **Circuit Breaker** | Resilience4j on RPC clients | Fail fast when chain RPC is degraded; auto-recover |
| **Rate Limiter** | Per-chain RPC configuration | Respect RPC provider rate limits |
| **Pessimistic Locking** | Nonce management | Prevent nonce collision under concurrent transaction submission |
| **Builder (Lombok)** | All records and DTOs | Immutable construction with `@Builder(toBuilder = true)` |
| **MapStruct Mapping** | Layer boundaries | Compile-time, type-safe object mapping between layers |
| **Distributed Locking** | ShedLock | Prevent duplicate outbox relay execution across replicas |

---

## Tech Stack

| Component | Technology | Version |
|-----------|-----------|---------|
| **Language** | Java (LTS) | 25 |
| **Framework** | Spring Boot | 4.0.3 |
| **Build** | Gradle (Kotlin DSL) + Convention Plugins | 8.x |
| **Database** | PostgreSQL | 16 |
| **Migrations** | Flyway | Latest |
| **Messaging** | Apache Kafka (Redpanda in dev) | - |
| **Workflow Engine** | Temporal | 1.29.4 |
| **Cache / Nonce Store** | Redis | 7.4 |
| **Resilience** | Resilience4j | - |
| **Object Mapping** | MapStruct | 1.6.3 |
| **Cryptography** | Bouncy Castle | - |
| **Metrics** | Micrometer + Prometheus + Grafana | - |
| **Structured Logging** | Logstash Logback Encoder | - |
| **Containerization** | Jib (eclipse-temurin:25-jre-alpine) | - |
| **Testing** | JUnit 5 + AssertJ + Testcontainers + WireMock + ArchUnit | - |
| **Code Quality** | Spotless + Lombok + ArchUnit | - |
| **IaC** | Terraform | - |

---

## Module Structure

```
stablebridge-tx-recovery/                      <- Root project
│
├── stablebridge-tx-recovery/                  <- Main Spring Boot application
│   └── src/
│       ├── main/java/com/stablebridge/txrecovery/
│       │   ├── application/                   <- Inbound adapters
│       │   │   ├── config/                    <- Spring configuration, properties
│       │   │   ├── controller/                <- REST API endpoints
│       │   │   │   ├── transaction/           <- Transaction CRUD
│       │   │   │   ├── approval/              <- Human approval endpoints
│       │   │   │   ├── address/               <- Address pool management
│       │   │   │   ├── gas/                   <- Gas oracle queries
│       │   │   │   └── status/                <- Chain health status
│       │   │   ├── security/                  <- API key authentication
│       │   │   └── workflow/                  <- Temporal workflow + activities
│       │   │
│       │   ├── domain/                        <- Core business logic (framework-free)
│       │   │   ├── transaction/               <- Transaction aggregate
│       │   │   │   ├── model/                 <- TransactionIntent, TransactionStatus, etc.
│       │   │   │   └── event/                 <- TransactionLifecycleEvent
│       │   │   ├── address/                   <- Address pool aggregate
│       │   │   ├── recovery/                  <- Escalation engine, gas oracle
│       │   │   ├── status/                    <- Chain status service
│       │   │   ├── common/model/              <- Shared value objects, StateMachine
│       │   │   └── exception/                 <- Domain exceptions
│       │   │
│       │   └── infrastructure/                <- Outbound adapters
│       │       ├── db/                        <- JPA entities, repositories, adapters
│       │       ├── client/evm/                <- EVM JSON-RPC client + recovery
│       │       ├── client/solana/             <- Solana JSON-RPC client + recovery
│       │       ├── redis/                     <- Nonce manager, fee cache
│       │       ├── signer/                    <- Callback + local keystore signers
│       │       └── stream/                    <- Kafka outbox publisher + relay
│       │
│       ├── main/resources/
│       │   ├── application.yml                <- Base configuration
│       │   └── db/migration/                  <- Flyway migrations (V1..V14)
│       │
│       ├── test/                              <- Unit tests
│       ├── testFixtures/                      <- Shared fixtures, stubs, base classes
│       └── integration-test/                  <- Integration tests (Testcontainers)
│
├── stablebridge-tx-recovery-api/              <- Shared API contracts (java-library)
│   └── src/main/java/.../api/model/           <- DTOs, requests, responses, events
│
├── buildSrc/                                  <- Gradle convention plugins
│   └── src/main/kotlin/
│       ├── stablebridge-tx-recovery.service.gradle.kts
│       └── stablebridge-tx-recovery.library.gradle.kts
│
├── infra/
│   ├── terraform/                             <- Infrastructure as Code
│   ├── prometheus/                            <- Prometheus scrape config
│   └── grafana/                               <- Grafana dashboard definitions
│
├── postman/                                   <- Postman API collection
├── docker-compose.yml                         <- Full local dev stack
├── Makefile                                   <- Developer workflow automation
└── .github/workflows/ci.yml                   <- CI/CD pipeline
```

---

## Getting Started

### Prerequisites

- **Java 25** (LTS)
- **Docker** & **Docker Compose**
- **Make** (optional, for convenience targets)

### Quick Start

```bash
# 1. Clone the repository
git clone https://github.com/stablebridge/tx-recovery.git
cd tx-recovery

# 2. Start infrastructure (PostgreSQL, Redis, Kafka, Temporal, Prometheus, Grafana)
make infra-up

# 3. Build and run the application
make run

# 4. Register a signer address
make register-address

# 5. Submit a test transaction
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

# 6. Check transaction status
curl -s http://localhost:8080/api/v1/transactions | jq
```

### Make Targets

| Target | Description |
|--------|-------------|
| `make build` | Compile + format + test |
| `make test` | Unit tests only |
| `make integration-test` | Integration tests (requires Docker) |
| `make format` | Auto-format with Spotless |
| `make check` | Spotless check + full build |
| `make run` | Run with mainnet profile |
| `make run-testnet` | Run with testnet (Sepolia, Solana Devnet) |
| `make up` | Start app + all infrastructure |
| `make up-testnet` | Start app + infra in testnet mode |
| `make down` | Stop everything |
| `make infra-up` | Start only infrastructure services |
| `make infra-down` | Stop infrastructure |
| `make infra-clean` | Stop + delete all volumes |
| `make docker-build` | Build Docker image via Jib |
| `make check-health` | Health check via actuator |
| `make check-status` | Get all chain statuses |
| `make check-kafka` | List Kafka topics |
| `make check-redis` | List Redis nonce keys |
| `make register-address` | Register a signer address |
| `make sync-nonce` | Sync on-chain nonce |
| `make submit-tx` | Submit a test transaction |

---

## API Reference

Base URL: `http://localhost:8080` | Authentication: `X-API-Key` header

### Transactions

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/v1/transactions` | Submit a single transaction |
| `POST` | `/api/v1/transactions/batch` | Submit a batch of transactions |
| `GET` | `/api/v1/transactions/{id}` | Get transaction by ID |
| `GET` | `/api/v1/transactions` | List transactions with filters |

**Query parameters for listing:** `chain`, `status`, `fromAddress`, `toAddress`, `token`, `fromDate`, `toDate`, `page` (default 0), `size` (default 20)

### Approvals

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/v1/transactions/{id}/approve` | Approve a transaction escalation |
| `POST` | `/api/v1/transactions/{id}/cancel` | Cancel a transaction |

### Chain Status

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/v1/status` | All chain statuses (RPC health, block height, workflow counts) |
| `GET` | `/api/v1/status/{chain}` | Detailed status for a specific chain |

### Gas Oracle

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/v1/gas/{chain}` | Current gas price estimates |
| `GET` | `/api/v1/gas/{chain}/history` | Historical gas prices (query: `hours`, default 24, max 168) |

### Address Pool

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/v1/addresses` | Register a new signer address |
| `GET` | `/api/v1/addresses` | List addresses (query: `chain`, `tier`, `status`) |
| `DELETE` | `/api/v1/addresses/{address}` | Drain an address (query: `chain`) |
| `POST` | `/api/v1/addresses/{address}/nonces/sync` | Sync nonce from chain |

### Health & Metrics

Management endpoints on port **8081**:

| Path | Description |
|------|-------------|
| `/actuator/health` | Liveness + readiness (PostgreSQL, Redis, Kafka, Temporal, per-chain RPC) |
| `/actuator/prometheus` | Prometheus metrics scrape endpoint |
| `/actuator/info` | Application info (version, build time) |

### Error Response Format

```json
{
  "errorCode": "STR-4041",
  "message": "Transaction not found",
  "timestamp": "2026-03-30T12:00:00Z",
  "path": "/api/v1/transactions/abc123"
}
```

| Prefix | Category |
|--------|----------|
| `STR-400x` | Bad request / validation |
| `STR-401x` | Authentication |
| `STR-404x` | Not found |
| `STR-409x` | Conflict / state violation |
| `STR-500x` | Internal server error |

---

## Configuration Reference

All custom properties use the `str.*` prefix. Full reference:

<details>
<summary><b>API Security</b> (<code>str.api.*</code>)</summary>

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `str.api.key` | String | `""` | Global API key |
| `str.api.operators` | Map | `{}` | Named operator-to-key mappings |

</details>

<details>
<summary><b>Signer</b> (<code>str.signer.*</code>)</summary>

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `str.signer.backend` | String | - | `callback` or `local` |
| `str.signer.keystore-path` | String | - | Path to JKS/PKCS12 keystore |
| `str.signer.password` | String | - | Keystore password |
| `str.signer.callback.hmac-secret` | String | - | HMAC-SHA256 verification secret |
| `str.signer.callback.timeout` | Duration | `PT5S` | HTTP callback timeout |
| `str.signer.callback.tls.verify` | boolean | `true` | TLS certificate verification |

</details>

<details>
<summary><b>Kafka</b> (<code>str.kafka.*</code>)</summary>

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `str.kafka.enabled-chains` | String | `ethereum_mainnet,solana_mainnet` | Comma-separated chain IDs |
| `str.kafka.topic-replicas` | int | `1` | Topic replication factor |

Note: Kafka properties are configured directly in `application.yml`, not via `StrProperties`.

</details>

<details>
<summary><b>Temporal</b> (<code>str.temporal.*</code>)</summary>

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `str.temporal.target` | String | `127.0.0.1:7233` | Frontend address |
| `str.temporal.namespace` | String | `stablebridge-tx-recovery` | Namespace |
| `str.temporal.task-queue` | String | `str-transaction-lifecycle` | Worker task queue |
| `str.temporal.workflow-execution-timeout` | Duration | `PT24H` | Max execution time |
| `str.temporal.workflow-run-timeout` | Duration | `PT2H` | Max single run time |

Activity profiles: `default-options`, `signing`, `confirmation`, `recovery-execution` — each with `start-to-close-timeout`, `max-attempts`, `initial-interval`, `backoff-coefficient`.

</details>

<details>
<summary><b>Escalation</b> (<code>str.escalation.*</code>)</summary>

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `str.escalation.high-value-threshold-usd` | BigDecimal | `50000` | High-value threshold |
| `str.escalation.gas-budget.percentage` | double | `0.01` | Gas budget as % of tx value |
| `str.escalation.gas-budget.absolute-min-usd` | BigDecimal | `5` | Min gas budget |
| `str.escalation.gas-budget.absolute-max-usd` | BigDecimal | `500` | Max gas budget |

</details>

<details>
<summary><b>Chain Configuration</b> (<code>str.chains.{id}.*</code>)</summary>

Per-chain configuration (e.g., `str.chains.ethereum_mainnet`):

| Property | Type | Description |
|----------|------|-------------|
| `.enabled` | boolean | Enable/disable chain |
| `.chain-family` | String | `EVM` or `SOLANA` |
| `.chain-id` | int | Numeric chain ID |
| `.finality-blocks` | int | Blocks for finality |
| `.stuck-threshold-blocks` | int | Blocks before stuck |
| `.poll-interval` | Duration | Block polling interval |
| `.max-fee-cap-gwei` | int | Max gas fee cap (EVM) |
| `.token-contracts` | List | Token addresses (EVM) |
| `.token-mints` | List | Token mints (Solana) |
| `.rpc.urls` | List | RPC endpoint URLs |
| `.rpc.timeout` | Duration | RPC call timeout |
| `.rpc.max-retries` | int | Max retry attempts |
| `.rpc.rate-limit-rps` | int | Requests/sec limit |
| `.rpc.rate-limit-burst` | int | Burst limit |
| `.rpc.circuit-breaker.*` | Object | Resilience4j config |

</details>

---

## Resilience & Fault Tolerance

```
┌──────────────────────────────────────────────────────────┐
│                   Resilience Stack                        │
│                                                          │
│  ┌────────────────┐  Per-chain RPC circuit breakers      │
│  │ Circuit Breaker│  50% failure threshold               │
│  │ (Resilience4j) │  30s open-state wait                 │
│  └────────────────┘  10-call sliding window              │
│                                                          │
│  ┌────────────────┐  25 req/sec sustained                │
│  │  Rate Limiter  │  50 req/sec burst                    │
│  │  (per chain)   │  Prevents RPC provider throttling    │
│  └────────────────┘                                      │
│                                                          │
│  ┌────────────────┐  Temporal automatic retry            │
│  │ Retry + Backoff│  Exponential backoff (2.0x)          │
│  │ (Temporal)     │  Non-retryable exception whitelist   │
│  └────────────────┘                                      │
│                                                          │
│  ┌────────────────┐  Kafka producer: acks=all, retries=3 │
│  │ Idempotent     │  Outbox relay: ShedLock (5 min)      │
│  │ Delivery       │  At-least-once with deduplication    │
│  └────────────────┘                                      │
│                                                          │
│  ┌────────────────┐  Redis CAS for nonce management      │
│  │ Pessimistic    │  Prevents nonce collision under      │
│  │ Locking        │  concurrent submission               │
│  └────────────────┘                                      │
│                                                          │
│  ┌────────────────┐  24h workflow execution timeout       │
│  │ Durable        │  Continue-As-New on run timeout      │
│  │ Execution      │  Survives crashes and restarts       │
│  └────────────────┘                                      │
└──────────────────────────────────────────────────────────┘
```

---

## Security

| Mechanism | Implementation |
|-----------|---------------|
| **API Authentication** | API key via `X-API-Key` header with constant-time comparison (`MessageDigest.isEqual`) |
| **Operator Identity** | Named operator-to-key mappings for audit trail on approvals |
| **Callback Signing** | HMAC-SHA256 verification for remote signer callbacks |
| **TLS** | Configurable TLS verification (TLSv1.3) for signer communication |
| **Secrets** | All sensitive values via environment variables (never in config files) |
| **Nonce Protection** | Redis CAS (compare-and-swap) prevents nonce reuse under concurrency |

---

## Observability

| Layer | Technology | Details |
|-------|-----------|---------|
| **Metrics** | Micrometer + Prometheus | Transaction counts, latencies, gas prices, RPC health — scraped at `:8081/actuator/prometheus` |
| **Dashboards** | Grafana | Pre-configured dashboards in `infra/grafana/` |
| **Logging** | SLF4J + Logstash Encoder | Structured JSON logs for log aggregation |
| **Health** | Spring Boot Actuator | Composite health: PostgreSQL, Redis, Kafka, Temporal, per-chain RPC |

Access Grafana at `http://localhost:3000` (admin/admin) and Prometheus at `http://localhost:9091`.

---

## Testing Strategy

Three-tier testing pyramid with strict conventions:

```
                    ┌───────────────┐
                    │  Integration  │  Infrastructure adapters with real deps
                    │  Tests        │  @PgTest, @KafkaTest (Testcontainers)
                    ├───────────────┤
                    │  Architecture │  ArchUnit rules: layer deps, no field
                    │  Tests        │  injection, no System.out, no generic exceptions
                ┌───┴───────────────┴───┐
                │      Unit Tests       │  Domain logic, mappers, services
                │  Mockito BDD + AssertJ│  No Spring context
                └───────────────────────┘
```

| Tier | Source Set | Framework | Docker Required |
|------|-----------|-----------|-----------------|
| Unit | `src/test/` | JUnit 5, Mockito (BDD), AssertJ | No |
| Architecture | `src/test/` | ArchUnit | No |
| Integration | `src/integration-test/` | Spring Boot Test, Testcontainers | Yes |

**Key conventions:**
- Single `assertThat().usingRecursiveComparison()` (no field-by-field asserts)
- BDD Mockito: `given()`/`then()`, never `when()`/`verify()`
- No generic matchers (`any()`, `anyString()`) — use actual values
- Test fixtures via `testFixtures` source set with builder pattern

```bash
./gradlew test                # Unit tests
./gradlew integrationTest     # Integration tests
./gradlew build               # All tests + Spotless check
```

---

## CI/CD Pipeline

GitHub Actions workflow (`.github/workflows/ci.yml`):

```
  ┌────────┐
  │  Lint  │  Spotless format check
  └───┬────┘
      │
      ├──────────────┬──────────────┐
      ▼              ▼              ▼
┌───────────┐ ┌────────────┐ ┌───────────┐
│Unit Tests │ │Integration │ │ Business  │    (parallel)
│           │ │   Tests    │ │   Tests   │
└─────┬─────┘ └─────┬──────┘ └─────┬─────┘
      │              │              │
      └──────────────┼──────────────┘
                     ▼
               ┌───────────┐
               │   Build   │  Assemble distribution
               └─────┬─────┘
                     │
                     ▼
               ┌───────────┐
               │  Docker   │  Jib build + push (main branch only)
               └───────────┘
```

- **Java:** 25 (Temurin)
- **Concurrency:** Cancels in-progress runs on same branch
- **Artifacts:** Test results retained for 14 days

---

## Deployment

### Docker Image

Built with Jib (no Dockerfile required):

```bash
./gradlew jibDockerBuild       # Build to local Docker daemon
./gradlew jib                  # Build and push to registry
```

| Property | Value |
|----------|-------|
| Image | `stablebridge/tx-recovery` |
| Base | `eclipse-temurin:25-jre-alpine` |
| Ports | `8080` (app), `8081` (management) |
| User | UID `1000` |
| JVM Flags | `-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0` |

### Environment Variables

| Variable | Description |
|----------|-------------|
| `STR_SIGNER_BACKEND` | Signer backend (`callback` or `local`) |
| `STR_SIGNER_KEYSTORE_PATH` | Keystore file path |
| `STR_SIGNER_PASSWORD` | Keystore password |
| `STR_SIGNER_CALLBACK_HMAC_SECRET` | Callback HMAC secret |
| `TEMPORAL_FRONTEND_URL` | Temporal server address |
| `STR_REDIS_HOST` / `STR_REDIS_PORT` | Redis connection |
| `EVM_ETHEREUM_RPC_URL` | Ethereum RPC endpoint |
| `EVM_BASE_RPC_URL` | Base RPC endpoint |
| `EVM_POLYGON_RPC_URL` | Polygon RPC endpoint |
| `SOLANA_RPC_URL` | Solana RPC endpoint |

---

## Infrastructure

Local development stack via Docker Compose:

| Service | Image | Port(s) | Purpose |
|---------|-------|---------|---------|
| PostgreSQL | `postgres:16-alpine` | 5432 | Application database |
| PostgreSQL (Temporal) | `postgres:16-alpine` | 5433 | Temporal server database |
| Redis | `redis/redis-stack:7.4.0-v8` | 6379, 8001 | Nonce management + caching |
| Redpanda | `redpanda:v24.3.1` | 19092 | Kafka-compatible event streaming |
| Temporal | `temporalio/auto-setup:1.29.4.1` | 7233 | Workflow orchestration |
| Temporal UI | `temporalio/ui:2.48.1` | 8088 | Workflow visualization |
| Prometheus | `prom/prometheus:v3.4.0` | 9091 | Metrics collection |
| Grafana | `grafana/grafana:11.6.0` | 3000 | Dashboards & alerting |

```bash
make infra-up       # Start all services
make infra-down     # Stop all services
make infra-clean    # Stop + delete volumes
```

Terraform configuration for cloud deployment is in `infra/terraform/`.

---

## License

This project is licensed under the [MIT License](LICENSE).
