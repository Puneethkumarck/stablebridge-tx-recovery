# Default values for local development
# Override by creating terraform.tfvars.local (gitignored)

postgres_db       = "str"
postgres_user     = "str"
postgres_password = "str"

postgres_temporal_db       = "temporal"
postgres_temporal_user     = "temporal"
postgres_temporal_password = "temporal"

redpanda_memory = "1G"

grafana_admin_user     = "admin"
grafana_admin_password = "admin"

prometheus_retention = "30d"
