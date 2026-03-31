# ---------------------------------------------------------------------------
# StableBridge TX Recovery — Local Infrastructure (Docker)
#
# Manages the same services as docker-compose.yml via Terraform:
#   PostgreSQL 16 (app + Temporal), Redis Stack, Redpanda (Kafka),
#   Temporal Server + UI, Prometheus, Grafana
#
# Usage:
#   terraform init
#   terraform apply
#   terraform destroy
# ---------------------------------------------------------------------------

locals {
  project_root = abspath("${path.module}/../..")
}

# ---------------------------------------------------------------------------
# Docker Network
# ---------------------------------------------------------------------------
resource "docker_network" "str" {
  name = var.network_name
}

# ---------------------------------------------------------------------------
# Docker Images
# ---------------------------------------------------------------------------
resource "docker_image" "postgres" {
  name         = var.postgres_image
  keep_locally = true
}

resource "docker_image" "redis" {
  name         = var.redis_image
  keep_locally = true
}

resource "docker_image" "redpanda" {
  name         = var.redpanda_image
  keep_locally = true
}

resource "docker_image" "temporal" {
  name         = var.temporal_image
  keep_locally = true
}

resource "docker_image" "temporal_ui" {
  name         = var.temporal_ui_image
  keep_locally = true
}

resource "docker_image" "prometheus" {
  name         = var.prometheus_image
  keep_locally = true
}

resource "docker_image" "grafana" {
  name         = var.grafana_image
  keep_locally = true
}

# ---------------------------------------------------------------------------
# Docker Volumes
# ---------------------------------------------------------------------------
resource "docker_volume" "pg_data" {
  name = "str_pg_data"
}

resource "docker_volume" "pg_temporal_data" {
  name = "str_pg_temporal_data"
}

resource "docker_volume" "redis_data" {
  name = "str_redis_data"
}

resource "docker_volume" "redpanda_data" {
  name = "str_redpanda_data"
}

resource "docker_volume" "prometheus_data" {
  name = "str_prometheus_data"
}

resource "docker_volume" "grafana_data" {
  name = "str_grafana_data"
}

# ---------------------------------------------------------------------------
# PostgreSQL (Application Database)
# ---------------------------------------------------------------------------
resource "docker_container" "postgres" {
  name  = "str-postgres"
  image = docker_image.postgres.image_id

  env = [
    "POSTGRES_DB=${var.postgres_db}",
    "POSTGRES_USER=${var.postgres_user}",
    "POSTGRES_PASSWORD=${var.postgres_password}",
  ]

  ports {
    internal = 5432
    external = var.postgres_port
  }

  volumes {
    volume_name    = docker_volume.pg_data.name
    container_path = "/var/lib/postgresql/data"
  }

  networks_advanced {
    name = docker_network.str.name
  }

  restart = "unless-stopped"

  healthcheck {
    test         = ["CMD-SHELL", "pg_isready -U ${var.postgres_user} -d ${var.postgres_db}"]
    interval     = "5s"
    timeout      = "3s"
    retries      = 5
    start_period = "10s"
  }
}

# ---------------------------------------------------------------------------
# PostgreSQL (Temporal Persistence)
# ---------------------------------------------------------------------------
resource "docker_container" "postgres_temporal" {
  name  = "str-postgres-temporal"
  image = docker_image.postgres.image_id

  env = [
    "POSTGRES_DB=${var.postgres_temporal_db}",
    "POSTGRES_USER=${var.postgres_temporal_user}",
    "POSTGRES_PASSWORD=${var.postgres_temporal_password}",
  ]

  ports {
    internal = 5432
    external = var.postgres_temporal_port
  }

  volumes {
    volume_name    = docker_volume.pg_temporal_data.name
    container_path = "/var/lib/postgresql/data"
  }

  networks_advanced {
    name = docker_network.str.name
  }

  restart = "unless-stopped"

  healthcheck {
    test         = ["CMD-SHELL", "pg_isready -U ${var.postgres_temporal_user} -d ${var.postgres_temporal_db}"]
    interval     = "5s"
    timeout      = "3s"
    retries      = 5
    start_period = "10s"
  }
}

# ---------------------------------------------------------------------------
# Redis Stack (Nonce cache + state management)
# ---------------------------------------------------------------------------
resource "docker_container" "redis" {
  name  = "str-redis"
  image = docker_image.redis.image_id

  ports {
    internal = 6379
    external = var.redis_port
  }

  ports {
    internal = 8001
    external = var.redis_insight_port
  }

  volumes {
    volume_name    = docker_volume.redis_data.name
    container_path = "/data"
  }

  networks_advanced {
    name = docker_network.str.name
  }

  restart = "unless-stopped"

  healthcheck {
    test         = ["CMD", "redis-cli", "ping"]
    interval     = "5s"
    timeout      = "3s"
    retries      = 5
    start_period = "5s"
  }
}

# ---------------------------------------------------------------------------
# Redpanda (Kafka-compatible streaming)
# ---------------------------------------------------------------------------
resource "docker_container" "redpanda" {
  name  = "str-redpanda"
  image = docker_image.redpanda.image_id

  command = [
    "redpanda", "start",
    "--kafka-addr", "internal://0.0.0.0:9092,external://0.0.0.0:19092",
    "--advertise-kafka-addr", "internal://str-redpanda:9092,external://localhost:${var.redpanda_kafka_port}",
    "--pandaproxy-addr", "internal://0.0.0.0:8082,external://0.0.0.0:18082",
    "--advertise-pandaproxy-addr", "internal://str-redpanda:8082,external://localhost:${var.redpanda_proxy_port}",
    "--schema-registry-addr", "internal://0.0.0.0:8081,external://0.0.0.0:18081",
    "--rpc-addr", "str-redpanda:33145",
    "--advertise-rpc-addr", "str-redpanda:33145",
    "--smp", "1",
    "--memory", var.redpanda_memory,
    "--mode", "dev-container",
    "--default-log-level=warn",
  ]

  ports {
    internal = 19092
    external = var.redpanda_kafka_port
  }

  ports {
    internal = 18082
    external = var.redpanda_proxy_port
  }

  ports {
    internal = 18081
    external = var.redpanda_schema_port
  }

  volumes {
    volume_name    = docker_volume.redpanda_data.name
    container_path = "/var/lib/redpanda/data"
  }

  networks_advanced {
    name = docker_network.str.name
  }

  restart = "unless-stopped"

  healthcheck {
    test         = ["CMD-SHELL", "rpk cluster health | grep -q 'Healthy:.*true'"]
    interval     = "5s"
    timeout      = "3s"
    retries      = 5
    start_period = "10s"
  }
}

# ---------------------------------------------------------------------------
# Temporal Server (Workflow orchestration)
# ---------------------------------------------------------------------------
resource "docker_container" "temporal" {
  name  = "str-temporal"
  image = docker_image.temporal.image_id

  env = [
    "DB=postgres12",
    "DB_PORT=5432",
    "POSTGRES_USER=${var.postgres_temporal_user}",
    "POSTGRES_PWD=${var.postgres_temporal_password}",
    "POSTGRES_SEEDS=str-postgres-temporal",
    "BIND_ON_IP=0.0.0.0",
    "PROMETHEUS_ENDPOINT=0.0.0.0:8000",
  ]

  ports {
    internal = 7233
    external = var.temporal_port
  }

  networks_advanced {
    name = docker_network.str.name
  }

  restart    = "unless-stopped"
  depends_on = [docker_container.postgres_temporal]

  healthcheck {
    test         = ["CMD", "tctl", "cluster", "health"]
    interval     = "10s"
    timeout      = "5s"
    retries      = 10
    start_period = "40s"
  }
}

# ---------------------------------------------------------------------------
# Temporal UI (Workflow visualization)
# ---------------------------------------------------------------------------
resource "docker_container" "temporal_ui" {
  name  = "str-temporal-ui"
  image = docker_image.temporal_ui.image_id

  env = [
    "TEMPORAL_ADDRESS=str-temporal:7233",
    "TEMPORAL_CORS_ORIGINS=http://localhost:${var.temporal_ui_port}",
  ]

  ports {
    internal = 8080
    external = var.temporal_ui_port
  }

  networks_advanced {
    name = docker_network.str.name
  }

  restart    = "unless-stopped"
  depends_on = [docker_container.temporal]

  healthcheck {
    test         = ["CMD-SHELL", "wget -qO- http://localhost:8080/api/v1/settings || exit 1"]
    interval     = "10s"
    timeout      = "3s"
    retries      = 5
    start_period = "15s"
  }
}

# ---------------------------------------------------------------------------
# Prometheus
# ---------------------------------------------------------------------------
resource "docker_container" "prometheus" {
  name  = "str-prometheus"
  image = docker_image.prometheus.image_id

  command = [
    "--config.file=/etc/prometheus/prometheus.yml",
    "--storage.tsdb.retention.time=${var.prometheus_retention}",
  ]

  ports {
    internal = 9090
    external = var.prometheus_port
  }

  volumes {
    host_path      = "${local.project_root}/infra/prometheus/prometheus.yml"
    container_path = "/etc/prometheus/prometheus.yml"
    read_only      = true
  }

  volumes {
    host_path      = "${local.project_root}/infra/prometheus/alerts.yml"
    container_path = "/etc/prometheus/alerts.yml"
    read_only      = true
  }

  volumes {
    volume_name    = docker_volume.prometheus_data.name
    container_path = "/prometheus"
  }

  networks_advanced {
    name = docker_network.str.name
  }

  host {
    host = "host.docker.internal"
    ip   = "host-gateway"
  }

  restart = "unless-stopped"

  healthcheck {
    test         = ["CMD-SHELL", "wget -qO- http://localhost:9090/-/healthy || exit 1"]
    interval     = "10s"
    timeout      = "3s"
    retries      = 5
    start_period = "10s"
  }
}

# ---------------------------------------------------------------------------
# Grafana
# ---------------------------------------------------------------------------
resource "docker_container" "grafana" {
  name  = "str-grafana"
  image = docker_image.grafana.image_id

  env = [
    "GF_SECURITY_ADMIN_USER=${var.grafana_admin_user}",
    "GF_SECURITY_ADMIN_PASSWORD=${var.grafana_admin_password}",
    "GF_USERS_ALLOW_SIGN_UP=false",
  ]

  ports {
    internal = 3000
    external = var.grafana_port
  }

  volumes {
    host_path      = "${local.project_root}/infra/grafana/provisioning"
    container_path = "/etc/grafana/provisioning"
    read_only      = true
  }

  volumes {
    host_path      = "${local.project_root}/infra/grafana/dashboards"
    container_path = "/var/lib/grafana/dashboards"
    read_only      = true
  }

  volumes {
    volume_name    = docker_volume.grafana_data.name
    container_path = "/var/lib/grafana"
  }

  networks_advanced {
    name = docker_network.str.name
  }

  restart    = "unless-stopped"
  depends_on = [docker_container.prometheus]

  healthcheck {
    test         = ["CMD-SHELL", "wget -qO- http://localhost:3000/api/health || exit 1"]
    interval     = "10s"
    timeout      = "3s"
    retries      = 5
    start_period = "10s"
  }
}

# ---------------------------------------------------------------------------
# Application
# ---------------------------------------------------------------------------
resource "docker_container" "app" {
  name  = "str-app"
  image = var.app_image

  env = [
    "SPRING_DATASOURCE_URL=jdbc:postgresql://str-postgres:5432/${var.postgres_db}",
    "SPRING_DATASOURCE_USERNAME=${var.postgres_user}",
    "SPRING_DATASOURCE_PASSWORD=${var.postgres_password}",
    "STR_REDIS_HOST=str-redis",
    "STR_REDIS_PORT=6379",
    "KAFKA_BOOTSTRAP_SERVERS=str-redpanda:9092",
    "TEMPORAL_FRONTEND_URL=str-temporal:7233",
    "STR_API_KEY=${var.str_api_key}",
    "SPRING_PROFILES_ACTIVE=${var.spring_profiles_active}",
    "STR_SIGNER_BACKEND=${var.signer_backend}",
    "STR_SIGNER_KEYSTORE_PATH=${var.signer_keystore_path}",
    "STR_SIGNER_PASSWORD=${var.signer_password}",
    "EVM_ETHEREUM_RPC_URL=${var.evm_ethereum_rpc_url}",
    "EVM_BASE_RPC_URL=${var.evm_base_rpc_url}",
    "EVM_POLYGON_RPC_URL=${var.evm_polygon_rpc_url}",
    "SOLANA_RPC_URL=${var.solana_rpc_url}",
  ]

  ports {
    internal = 8080
    external = var.app_port
  }

  ports {
    internal = 8081
    external = var.app_mgmt_port
  }

  networks_advanced {
    name = docker_network.str.name
  }

  restart = "unless-stopped"
  depends_on = [
    docker_container.postgres,
    docker_container.redis,
    docker_container.redpanda,
    docker_container.temporal,
  ]

  healthcheck {
    test         = ["CMD-SHELL", "curl -sf http://localhost:8081/actuator/health || exit 1"]
    interval     = "15s"
    timeout      = "5s"
    retries      = 10
    start_period = "45s"
  }
}
