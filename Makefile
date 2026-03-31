.PHONY: help build test integration-test clean format check \
       run run-testnet \
       infra-up infra-down infra-clean infra-status infra-logs \
       up up-testnet down \
       terraform-init terraform-plan terraform-up terraform-up-testnet terraform-down \
       docker-build \
       check-health check-status check-redis check-kafka check-temporal \
       register-address sync-nonce submit-tx

# ---------------------------------------------------------------------------
# Help
# ---------------------------------------------------------------------------
help: ## Show this help
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | sort | \
		awk 'BEGIN {FS = ":.*?## "}; {printf "\033[36m%-25s\033[0m %s\n", $$1, $$2}'

# ---------------------------------------------------------------------------
# Build & Test
# ---------------------------------------------------------------------------
build: ## Build everything (compile + Spotless + tests)
	./gradlew build

test: ## Run unit tests only
	./gradlew test

integration-test: ## Run integration tests (requires Docker services)
	./gradlew integrationTest

clean: ## Clean build artifacts
	./gradlew clean

format: ## Auto-format code with Spotless
	./gradlew spotlessApply

check: ## Run Spotless check and full build
	./gradlew spotlessCheck build

# ---------------------------------------------------------------------------
# Run Application
# ---------------------------------------------------------------------------
run: ## Run with default profile (mainnet config)
	./gradlew :stablebridge-tx-recovery:bootRun

run-testnet: ## Run with testnet profile (Sepolia, Solana Devnet)
	set -a && . ./.env && set +a && ./gradlew :stablebridge-tx-recovery:bootRun --args='--spring.profiles.active=testnet'

# ---------------------------------------------------------------------------
# One-command up/down (infra + app in containers)
# ---------------------------------------------------------------------------
up: docker-build ## Build image, start infra + app (mainnet)
	docker compose --profile app up -d
	@echo "Waiting for app to be healthy..."
	@until curl -sf http://localhost:8081/actuator/health > /dev/null 2>&1; do sleep 2; done
	@$(MAKE) --no-print-directory _print-urls

up-testnet: docker-build ## Build image, start infra + app (testnet profile)
	SPRING_PROFILES_ACTIVE=testnet docker compose --profile app up -d
	@echo "Waiting for app to be healthy..."
	@until curl -sf http://localhost:8081/actuator/health > /dev/null 2>&1; do sleep 2; done
	@$(MAKE) --no-print-directory _print-urls PROFILE=testnet

_print-urls:
	@echo ""
	@echo "============================================"
	@echo " StableBridge TX Recovery is running $(if $(PROFILE),($(PROFILE)),)"
	@echo "============================================"
	@echo ""
	@echo " App API:           http://localhost:8080/api/v1/status"
	@echo " Actuator Health:   http://localhost:8081/actuator/health"
	@echo " Prometheus Metrics: http://localhost:8081/actuator/prometheus"
	@echo ""
	@echo " Temporal UI:       http://localhost:8088"
	@echo " Redis Insight:     http://localhost:8001"
	@echo " Prometheus:        http://localhost:9091"
	@echo " Grafana:           http://localhost:3000  (admin/admin)"
	@echo ""
	@echo " API Key Header:    X-API-Key: $${STR_API_KEY:-change-me}"
	@echo "============================================"
	@echo ""

down: ## Stop everything (app + infra)
	docker compose --profile app down

# ---------------------------------------------------------------------------
# Docker Compose Infrastructure
# ---------------------------------------------------------------------------
infra-up: ## Start local infrastructure (PostgreSQL, Redis, Redpanda, Temporal, Prometheus, Grafana)
	docker compose up -d
	@echo "Waiting for Temporal to be healthy..."
	@until docker exec str-temporal tctl cluster health > /dev/null 2>&1; do sleep 2; done
	@docker exec str-temporal temporal operator namespace create stablebridge-tx-recovery --address localhost:7233 -n stablebridge-tx-recovery > /dev/null 2>&1 || true
	@$(MAKE) --no-print-directory _print-infra-urls

_print-infra-urls:
	@echo ""
	@echo "============================================"
	@echo " StableBridge TX Recovery — Infrastructure"
	@echo "============================================"
	@echo ""
	@echo " PostgreSQL:        localhost:5432  (str/str/str)"
	@echo " PostgreSQL (Temp): localhost:5433  (temporal/temporal/temporal)"
	@echo " Redis:             localhost:6379"
	@echo " Redis Insight:     http://localhost:8001"
	@echo " Kafka (Redpanda):  localhost:19092"
	@echo " Temporal:          localhost:7233"
	@echo " Temporal UI:       http://localhost:8088"
	@echo " Prometheus:        http://localhost:9091"
	@echo " Grafana:           http://localhost:3000  (admin/admin)"
	@echo ""
	@echo " Run the app:  source .env && make run-testnet"
	@echo "============================================"
	@echo ""

infra-down: ## Stop local infrastructure
	docker compose down

infra-clean: ## Stop infrastructure and delete all volumes
	docker compose down -v

infra-status: ## Show infrastructure container status
	docker compose ps

infra-logs: ## Tail infrastructure logs
	docker compose logs -f

# ---------------------------------------------------------------------------
# Terraform Local Infrastructure
# ---------------------------------------------------------------------------
terraform-init: ## Initialize Terraform (local Docker provider)
	cd infra/terraform && terraform init

terraform-plan: ## Show Terraform execution plan
	cd infra/terraform && terraform plan

terraform-up: docker-build ## Build image + provision all infra + app via Terraform
	cd infra/terraform && terraform apply -auto-approve
	@echo "Waiting for app to be healthy..."
	@until curl -sf http://localhost:8081/actuator/health > /dev/null 2>&1; do sleep 2; done
	@$(MAKE) --no-print-directory _print-urls

terraform-up-testnet: docker-build ## Build image + provision via Terraform (testnet)
	cd infra/terraform && terraform apply -auto-approve \
		-var-file=testnet.tfvars \
		-var="evm_ethereum_rpc_url=$${EVM_ETHEREUM_RPC_URL}" \
		-var="evm_base_rpc_url=$${EVM_BASE_RPC_URL:-http://localhost:8546}" \
		-var="evm_polygon_rpc_url=$${EVM_POLYGON_RPC_URL:-http://localhost:8547}" \
		-var="solana_rpc_url=$${SOLANA_RPC_URL:-http://localhost:8899}" \
		-var="str_api_key=$${STR_API_KEY:-change-me}" \
		-var="signer_backend=$${STR_SIGNER_BACKEND:-}" \
		-var="signer_keystore_path=$${STR_SIGNER_KEYSTORE_PATH:-}" \
		-var="signer_password=$${STR_SIGNER_PASSWORD:-}"
	@echo "Waiting for app to be healthy..."
	@until curl -sf http://localhost:8081/actuator/health > /dev/null 2>&1; do sleep 2; done
	@$(MAKE) --no-print-directory _print-urls PROFILE=testnet

terraform-down: ## Destroy all Terraform-managed containers
	cd infra/terraform && terraform destroy -auto-approve

# ---------------------------------------------------------------------------
# Docker Image
# ---------------------------------------------------------------------------
docker-build: ## Build production Docker image via Jib
	./gradlew :stablebridge-tx-recovery:jibDockerBuild \
		-Djib.applicationCache=/tmp/jib-cache \
		-Djib.baseImageCache=/tmp/jib-base-cache

# ---------------------------------------------------------------------------
# Testnet Operations
# ---------------------------------------------------------------------------
check-health: ## Check actuator health
	@curl -s http://localhost:8081/actuator/health | jq .

check-status: ## Check all chain statuses
	@curl -s -H "X-API-Key: $${STR_API_KEY:-change-me}" \
		http://localhost:8080/api/v1/status | jq .

check-redis: ## Show Redis nonce state
	@echo "=== Nonce Keys ==="
	@docker exec str-redis redis-cli KEYS "str:nonce:*" 2>/dev/null || echo "(none)"

check-kafka: ## List Kafka topics and message counts
	@docker exec str-redpanda rpk topic list --brokers localhost:9092

check-temporal: ## List running Temporal workflows
	@docker exec str-temporal temporal workflow list --namespace stablebridge-tx-recovery --address localhost:7233 2>/dev/null || echo "(namespace not yet registered)"

register-address: ## Register a signer address (usage: make register-address ADDR=0x... CHAIN=ethereum_mainnet TIER=HOT)
	@curl -s -X POST http://localhost:8080/api/v1/addresses \
		-H "X-API-Key: $${STR_API_KEY:-change-me}" \
		-H "Content-Type: application/json" \
		-d '{"address": "$(ADDR)", "chain": "$(CHAIN)", "tier": "$(TIER)", "signerEndpoint": "local"}' | jq .

sync-nonce: ## Sync on-chain nonce (usage: make sync-nonce ADDR=0x... CHAIN=ethereum_mainnet)
	@curl -s -X POST "http://localhost:8080/api/v1/addresses/$(ADDR)/nonces/sync?chain=$(CHAIN)" \
		-H "X-API-Key: $${STR_API_KEY:-change-me}" | jq .

generate-key: ## Generate a signing keypair (usage: make generate-key CHAIN_TYPE=solana)
	./gradlew :stablebridge-tx-recovery:generateKey --args='$(CHAIN_TYPE) keys.json'

submit-tx: ## Submit a transaction (usage: make submit-tx INTENT=uuid CHAIN=ethereum_mainnet TO=0x... AMOUNT=20 TOKEN=USDC DECIMALS=6 CONTRACT=0x...)
	@curl -s -X POST http://localhost:8080/api/v1/transactions \
		-H "X-API-Key: $${STR_API_KEY:-change-me}" \
		-H "Content-Type: application/json" \
		-d '{"intentId": "$(INTENT)", "chain": "$(CHAIN)", "toAddress": "$(TO)", "amount": "$(AMOUNT)", "token": "$(TOKEN)", "tokenDecimals": $(DECIMALS), "tokenContractAddress": "$(CONTRACT)"}' | jq .

.DEFAULT_GOAL := help
