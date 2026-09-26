# B2B Inventory Management - Makefile
# Quick commands for managing the application

.PHONY: help up down restart build clean logs db-shell backend-shell status test

help: ## Show this help message
	@echo "B2B Inventory Management - Available Commands:"
	@echo ""
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | sort | awk 'BEGIN {FS = ":.*?## "}; {printf "  \033[36m%-15s\033[0m %s\n", $$1, $$2}'

up: ## Start all services
	docker compose up -d
	@echo "Services started. Backend available at http://localhost:8080"

down: ## Stop all services
	docker compose down

restart: ## Restart all services
	docker compose restart

build: ## Build services from scratch
	docker compose build --no-cache

clean: ## Remove all containers, volumes, and images
	docker compose down -v
	docker system prune -f

logs: ## Show logs (follow mode)
	docker compose logs -f

logs-backend: ## Show backend logs only
	docker compose logs -f backend

logs-db: ## Show database logs only
	docker compose logs -f postgres

status: ## Show status of all services
	docker compose ps

db-shell: ## Connect to PostgreSQL database
	docker compose exec postgres psql -U postgres -d b2b_inventory

backend-shell: ## Connect to backend container shell
	docker compose exec backend sh

db-backup: ## Backup database to file
	docker compose exec -T postgres pg_dump -U postgres b2b_inventory > backup_$$(date +%Y%m%d_%H%M%S).sql
	@echo "Database backed up"

db-restore: ## Restore database from backup (use: make db-restore FILE=backup.sql)
	docker compose exec -T postgres psql -U postgres -d b2b_inventory < $(FILE)

test-api: ## Test API health
	@curl -s http://localhost:8080/api/auth/login || echo "Backend not ready"

rebuild: ## Rebuild and restart services
	docker compose down
	docker compose build --no-cache
	docker compose up -d
	@echo "Services rebuilt and restarted"

dev: ## Start in development mode with live logs
	docker compose up

prod: ## Start in production mode (detached)
	docker compose up -d

.DEFAULT_GOAL := help
