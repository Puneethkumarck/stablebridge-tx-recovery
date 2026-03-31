# ---------------------------------------------------------------------------
# Docker
# ---------------------------------------------------------------------------
variable "docker_host" {
  description = "Docker daemon socket"
  type        = string
  default     = "unix:///var/run/docker.sock"
}

variable "network_name" {
  description = "Docker network for all containers"
  type        = string
  default     = "str-network"
}

# ---------------------------------------------------------------------------
# PostgreSQL (Application)
# ---------------------------------------------------------------------------
variable "postgres_image" {
  description = "PostgreSQL Docker image"
  type        = string
  default     = "postgres:16-alpine"
}

variable "postgres_db" {
  description = "Database name"
  type        = string
  default     = "str"
}

variable "postgres_user" {
  description = "Database user"
  type        = string
  default     = "str"
}

variable "postgres_password" {
  description = "Database password"
  type        = string
  default     = "str"
  sensitive   = true
}

variable "postgres_port" {
  description = "Host port for PostgreSQL"
  type        = number
  default     = 5432
}

# ---------------------------------------------------------------------------
# PostgreSQL (Temporal)
# ---------------------------------------------------------------------------
variable "postgres_temporal_db" {
  description = "Temporal database name"
  type        = string
  default     = "temporal"
}

variable "postgres_temporal_user" {
  description = "Temporal database user"
  type        = string
  default     = "temporal"
}

variable "postgres_temporal_password" {
  description = "Temporal database password"
  type        = string
  default     = "temporal"
  sensitive   = true
}

variable "postgres_temporal_port" {
  description = "Host port for Temporal PostgreSQL"
  type        = number
  default     = 5433
}

# ---------------------------------------------------------------------------
# Redis
# ---------------------------------------------------------------------------
variable "redis_image" {
  description = "Redis Stack image"
  type        = string
  default     = "redis/redis-stack:7.4.0-v8"
}

variable "redis_port" {
  description = "Host port for Redis"
  type        = number
  default     = 6379
}

variable "redis_insight_port" {
  description = "Host port for Redis Insight UI"
  type        = number
  default     = 8001
}

# ---------------------------------------------------------------------------
# Redpanda (Kafka-compatible)
# ---------------------------------------------------------------------------
variable "redpanda_image" {
  description = "Redpanda image"
  type        = string
  default     = "docker.redpanda.com/redpandadata/redpanda:v24.3.1"
}

variable "redpanda_kafka_port" {
  description = "Host port for Kafka protocol"
  type        = number
  default     = 19092
}

variable "redpanda_proxy_port" {
  description = "Host port for Pandaproxy (HTTP)"
  type        = number
  default     = 18082
}

variable "redpanda_schema_port" {
  description = "Host port for Schema Registry"
  type        = number
  default     = 18081
}

variable "redpanda_memory" {
  description = "Memory limit for Redpanda"
  type        = string
  default     = "1G"
}

# ---------------------------------------------------------------------------
# Temporal
# ---------------------------------------------------------------------------
variable "temporal_image" {
  description = "Temporal auto-setup image"
  type        = string
  default     = "temporalio/auto-setup:1.29.4.1"
}

variable "temporal_port" {
  description = "Host port for Temporal frontend"
  type        = number
  default     = 7233
}

variable "temporal_ui_image" {
  description = "Temporal UI image"
  type        = string
  default     = "temporalio/ui:2.48.1"
}

variable "temporal_ui_port" {
  description = "Host port for Temporal UI"
  type        = number
  default     = 8088
}

# ---------------------------------------------------------------------------
# Prometheus
# ---------------------------------------------------------------------------
variable "prometheus_image" {
  description = "Prometheus image"
  type        = string
  default     = "prom/prometheus:v3.4.0"
}

variable "prometheus_port" {
  description = "Host port for Prometheus"
  type        = number
  default     = 9091
}

variable "prometheus_retention" {
  description = "Prometheus data retention period"
  type        = string
  default     = "30d"
}

# ---------------------------------------------------------------------------
# Grafana
# ---------------------------------------------------------------------------
variable "grafana_image" {
  description = "Grafana image"
  type        = string
  default     = "grafana/grafana:11.6.0"
}

variable "grafana_port" {
  description = "Host port for Grafana"
  type        = number
  default     = 3000
}

variable "grafana_admin_user" {
  description = "Grafana admin username"
  type        = string
  default     = "admin"
}

variable "grafana_admin_password" {
  description = "Grafana admin password"
  type        = string
  default     = "admin"
  sensitive   = true
}

# ---------------------------------------------------------------------------
# Application
# ---------------------------------------------------------------------------
variable "app_image" {
  description = "TX Recovery application Docker image"
  type        = string
  default     = "stablebridge/tx-recovery:latest"
}

variable "app_port" {
  description = "Host port for the application API"
  type        = number
  default     = 8080
}

variable "app_mgmt_port" {
  description = "Host port for actuator/management"
  type        = number
  default     = 8081
}

variable "str_api_key" {
  description = "API key for X-API-Key header authentication"
  type        = string
  default     = "change-me"
  sensitive   = true
}

variable "spring_profiles_active" {
  description = "Spring profiles to activate (e.g., testnet)"
  type        = string
  default     = ""
}

# ---------------------------------------------------------------------------
# Signer
# ---------------------------------------------------------------------------
variable "signer_backend" {
  description = "Signer backend (local-keystore or callback)"
  type        = string
  default     = ""
}

variable "signer_keystore_path" {
  description = "Path to signer PKCS12 keystore"
  type        = string
  default     = ""
}

variable "signer_password" {
  description = "Signer keystore password"
  type        = string
  default     = ""
  sensitive   = true
}

# ---------------------------------------------------------------------------
# RPC URLs
# ---------------------------------------------------------------------------
variable "evm_ethereum_rpc_url" {
  description = "Ethereum mainnet/Sepolia RPC URL"
  type        = string
  default     = "http://localhost:8545"
}

variable "evm_base_rpc_url" {
  description = "Base mainnet/testnet RPC URL"
  type        = string
  default     = "http://localhost:8546"
}

variable "evm_polygon_rpc_url" {
  description = "Polygon mainnet/testnet RPC URL"
  type        = string
  default     = "http://localhost:8547"
}

variable "solana_rpc_url" {
  description = "Solana mainnet/devnet RPC URL"
  type        = string
  default     = "http://localhost:8899"
}
