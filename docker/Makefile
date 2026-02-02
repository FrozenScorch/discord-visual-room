# ========================================================================
# Makefile - Discord Visual Room Docker Commands
# ========================================================================
#
# This Makefile provides convenient shortcuts for common Docker operations.
#
# Usage:
#   make help          # Show all available commands
#   make build         # Build production images
#   make dev           # Start development environment
#   make up            # Start production environment
#   make down          # Stop all services
#   make logs          # View logs
#
# ========================================================================

# Configuration
COMPOSE_FILE_PROD=docker/docker-compose.yml
COMPOSE_FILE_DEV=docker/docker-compose.dev.yml
ENV_FILE=docker/.env

# Docker Compose command
DC_PROD=docker compose -f $(COMPOSE_FILE_PROD)
DC_DEV=docker compose -f $(COMPOSE_FILE_DEV)

# Colors for output (disable with NO_COLOR=1)
ifndef NO_COLOR
    BLUE=\033[0;34m
    GREEN=\033[0;32m
    YELLOW=\033[1;33m
    RED=\033[0;31m
    NC=\033[0m
else
    BLUE=
    GREEN=
    YELLOW=
    RED=
    NC=
endif

# ========================================================================
# Help Command
# ========================================================================

.PHONY: help
help:
	@echo "$(BLUE)Discord Visual Room - Docker Commands$(NC)"
	@echo ""
	@echo "$(GREEN)Development Commands:$(NC)"
	@echo "  make dev          - Start development environment with hot-reload"
	@echo "  make dev-build    - Build development images"
	@echo "  make dev-down     - Stop development environment"
	@echo ""
	@echo "$(GREEN)Production Commands:$(NC)"
	@echo "  make build        - Build production images"
	@echo "  make up           - Start production environment"
	@echo "  make down         - Stop production environment"
	@echo "  make restart      - Restart all services"
	@echo "  make ps           - Show running containers"
	@echo ""
	@echo "$(GREEN)Logs & Debugging:$(NC)"
	@echo "  make logs         - View all logs"
	@echo "  make logs-be      - View backend logs"
	@echo "  make logs-fe      - View frontend logs"
	@echo "  make shell-be     - Shell into backend container"
	@echo "  make shell-fe     - Shell into frontend container"
	@echo ""
	@echo "$(GREEN)Utilities:$(NC)"
	@echo "  make health       - Check service health"
	@echo "  make clean        - Remove containers, volumes, and images"
	@echo "  make prune        - Remove unused Docker resources"
	@echo "  make env          - Create .env from .env.example"
	@echo ""
	@echo "$(GREEN)Deployment:$(NC)"
	@echo "  make deploy       - Deploy to production server"
	@echo "  make build-all    - Build both dev and prod images"

# ========================================================================
# Development Commands
# ========================================================================

.PHONY: dev
dev:
	@echo "$(BLUE)Starting development environment...$(NC)"
	@$(DC_DEV) up

.PHONY: dev-build
dev-build:
	@echo "$(BLUE)Building development images...$(NC)"
	@$(DC_DEV) build

.PHONY: dev-down
dev-down:
	@echo "$(YELLOW)Stopping development environment...$(NC)"
	@$(DC_DEV) down

.PHONY: dev-rebuild
dev-rebuild:
	@echo "$(BLUE)Rebuilding development images (no cache)...$(NC)"
	@$(DC_DEV) build --no-cache

# ========================================================================
# Production Commands
# ========================================================================

.PHONY: build
build:
	@echo "$(BLUE)Building production images...$(NC)"
	@$(DC_PROD) build

.PHONY: build-no-cache
build-no-cache:
	@echo "$(BLUE)Building production images (no cache)...$(NC)"
	@$(DC_PROD) build --no-cache

.PHONY: up
up:
	@echo "$(BLUE)Starting production environment...$(NC)"
	@$(DC_PROD) up -d
	@echo "$(GREEN)Services started!$(NC)"
	@echo "  Frontend: http://localhost:8000"
	@echo "  Backend WebSocket: ws://localhost:8080"

.PHONY: down
down:
	@echo "$(YELLOW)Stopping production environment...$(NC)"
	@$(DC_PROD) down

.PHONY: restart
restart: down up

.PHONY: ps
ps:
	@$(DC_PROD) ps

# ========================================================================
# Logs & Debugging
# ========================================================================

.PHONY: logs
logs:
	@$(DC_PROD) logs -f

.PHONY: logs-be
logs-be:
	@$(DC_PROD) logs -f backend

.PHONY: logs-fe
logs-fe:
	@$(DC_PROD) logs -f frontend

.PHONY: logs-dev
logs-dev:
	@$(DC_DEV) logs -f

.PHONY: logs-dev-be
logs-dev-be:
	@$(DC_DEV) logs -f backend-dev

.PHONY: logs-dev-fe
logs-dev-fe:
	@$(DC_DEV) logs -f frontend-dev

.PHONY: shell-be
shell-be:
	@$(DC_PROD) exec backend /bin/bash

.PHONY: shell-fe
shell-fe:
	@$(DC_PROD) exec frontend /bin/sh

.PHONY: shell-dev-be
shell-dev-be:
	@$(DC_DEV) exec backend-dev /bin/bash

.PHONY: shell-dev-fe
shell-dev-fe:
	@$(DC_DEV) exec frontend-dev /bin/sh

# ========================================================================
# Health & Status
# ========================================================================

.PHONY: health
health:
	@echo "$(BLUE)Checking service health...$(NC)"
	@echo ""
	@echo "$(GREEN)Backend:$(NC)"
	@curl -sf http://localhost:8080/health && echo "  $(GREEN)Healthy$(NC)" || echo "  $(RED)Unhealthy$(NC)"
	@echo ""
	@echo "$(GREEN)Frontend:$(NC)"
	@curl -sf http://localhost:8000/ && echo "  $(GREEN)Healthy$(NC)" || echo "  $(RED)Unhealthy$(NC)"
	@echo ""

.PHONY: stats
stats:
	@echo "$(BLUE)Container resource usage:$(NC)"
	@docker stats --no-stream $(shell docker compose -f $(COMPOSE_FILE_PROD) ps -q)

# ========================================================================
# Utilities
# ========================================================================

.PHONY: clean
clean:
	@echo "$(YELLOW)Cleaning up...$(NC)"
	@$(DC_PROD) down -v --rmi all --remove-orphans
	@$(DC_DEV) down -v --rmi all --remove-orphans
	@echo "$(GREEN)Cleanup complete$(NC)"

.PHONY: prune
prune:
	@echo "$(YELLOW)Pruning unused Docker resources...$(NC)"
	@docker system prune -f
	@echo "$(GREEN)Prune complete$(NC)"

.PHONY: env
env:
	@if [ ! -f .env ]; then \
		echo "$(YELLOW)Creating .env from .env.example...$(NC)"; \
		cp docker/.env.example .env; \
		echo "$(GREEN)Created .env - please edit with your configuration$(NC)"; \
	else \
		echo "$(YELLOW).env already exists$(NC)"; \
	fi

.PHONY: check-env
check-env:
	@echo "$(BLUE)Checking environment configuration...$(NC)"
	@if [ -f .env ]; then \
		echo "$(GREEN).env exists$(NC)"; \
		if grep -q "your_discord_bot_token_here" .env; then \
			echo "$(YELLOW)WARNING: Discord bot token not configured$(NC)"; \
		fi; \
	else \
		echo "$(RED)ERROR: .env not found$(NC)"; \
		echo "Run 'make env' to create it"; \
		exit 1; \
	fi

# ========================================================================
# Build All
# ========================================================================

.PHONY: build-all
build-all: build dev-build
	@echo "$(GREEN)All images built successfully$(NC)"

# ========================================================================
# Deployment
# ========================================================================

.PHONY: deploy
deploy:
	@echo "$(BLUE)Deploying to production server...$(NC)"
	@ssh frozenscorch@192.168.68.56 "cd ~/discord-visual-room && ./docker/deploy.sh"

.PHONY: deploy-no-build
deploy-no-build:
	@echo "$(BLUE)Deploying to production server (without rebuild)...$(NC)"
	@ssh frozenscorch@192.168.68.56 "cd ~/discord-visual-room && ./docker/deploy.sh --no-build"

# ========================================================================
# Testing
# ========================================================================

.PHONY: test
test:
	@echo "$(BLUE)Running tests...$(NC)"
	@cd backend/scala && sbt test

.PHONY: test-backend
test-backend:
	@echo "$(BLUE)Running backend tests...$(NC)"
	@cd backend/scala && sbt test

# ========================================================================
# Type Checking
# ========================================================================

.PHONY: typecheck
typecheck:
	@echo "$(BLUE)Type checking frontend...$(NC)"
	@cd frontend && npm run typecheck

# ========================================================================
# Linting
# ========================================================================

.PHONY: lint
lint: lint-backend lint-frontend

.PHONY: lint-backend
lint-backend:
	@echo "$(BLUE)Linting backend...$(NC)"
	@cd backend/scala && sbt scalastyle

.PHONY: lint-frontend
lint-frontend:
	@echo "$(BLUE)Linting frontend...$(NC)"
	@cd frontend && npm run lint || echo "No lint script configured"

# ========================================================================
# Quick Start
# ========================================================================

.PHONY: quickstart
quickstart: check-env up
	@echo ""
	@echo "$(GREEN)=== Discord Visual Room is ready! ===$(NC)"
	@echo ""
	@echo "Frontend: $(BLUE)http://localhost:8000$(NC)"
	@echo "Backend WebSocket: $(BLUE)ws://localhost:8080$(NC)"
	@echo ""
	@echo "Useful commands:"
	@echo "  make logs        - View logs"
	@echo "  make health      - Check service health"
	@echo "  make down        - Stop services"
	@echo ""

