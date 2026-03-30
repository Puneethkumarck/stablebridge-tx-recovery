.PHONY: build test integration-test run infra-up infra-down clean format check help

.DEFAULT_GOAL := help

build: ## Run Gradle build (compile + test + spotless check)
	./gradlew build

test: ## Run unit tests
	./gradlew test

integration-test: ## Run integration tests with Testcontainers
	./gradlew integrationTest

run: ## Start the application with development profile
	./gradlew bootRun --args='--spring.profiles.active=dev'

infra-up: ## Start all infrastructure services via Docker Compose
	docker compose up -d

infra-down: ## Stop all infrastructure services
	docker compose down

clean: ## Clean Gradle build output and optionally prune Docker volumes
	./gradlew clean
	@echo "Run 'docker volume prune -f' to also remove Docker volumes"

format: ## Auto-format code with Spotless
	./gradlew spotlessApply

check: ## Run Spotless check and full build
	./gradlew spotlessCheck build

help: ## Show this help message
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | sort | awk 'BEGIN {FS = ":.*?## "}; {printf "\033[36m%-20s\033[0m %s\n", $$1, $$2}'
