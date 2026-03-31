output "postgres_url" {
  description = "JDBC connection URL for PostgreSQL"
  value       = "jdbc:postgresql://localhost:${var.postgres_port}/${var.postgres_db}"
}

output "redis_url" {
  description = "Redis connection URL"
  value       = "redis://localhost:${var.redis_port}"
}

output "kafka_bootstrap_servers" {
  description = "Kafka bootstrap servers for the application"
  value       = "localhost:${var.redpanda_kafka_port}"
}

output "temporal_frontend" {
  description = "Temporal frontend address"
  value       = "localhost:${var.temporal_port}"
}

output "services" {
  description = "Service endpoints"
  value = {
    app_api         = "http://localhost:${var.app_port}/api/v1/status"
    actuator_health = "http://localhost:${var.app_mgmt_port}/actuator/health"
    prometheus_metrics = "http://localhost:${var.app_mgmt_port}/actuator/prometheus"
    postgresql      = "localhost:${var.postgres_port}"
    postgresql_temporal = "localhost:${var.postgres_temporal_port}"
    redis           = "localhost:${var.redis_port}"
    redis_insight   = "http://localhost:${var.redis_insight_port}"
    kafka           = "localhost:${var.redpanda_kafka_port}"
    temporal        = "localhost:${var.temporal_port}"
    temporal_ui     = "http://localhost:${var.temporal_ui_port}"
    prometheus      = "http://localhost:${var.prometheus_port}"
    grafana         = "http://localhost:${var.grafana_port}"
  }
}
