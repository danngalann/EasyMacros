.DEFAULT_GOAL := help

.PHONY: help setup dev-db-up dev-db-down backend-dev format format-check lint test test-backend test-android build openapi-check compose-prod-check migrate

help:
	@printf '%s\n' 'Targets: setup dev-db-up dev-db-down backend-dev format format-check lint test test-backend test-android build openapi-check compose-prod-check'

setup:
	pnpm install --frozen-lockfile

dev-db-up:
	docker compose -f docker-compose.dev.yml up -d

dev-db-down:
	docker compose -f docker-compose.dev.yml down

backend-dev:
	pnpm --filter backend dev

format:
	pnpm format

format-check:
	pnpm format:check

lint:
	pnpm lint

test: test-backend test-android

test-backend:
	pnpm test:backend

test-android:
	cd android && ./gradlew test

build:
	pnpm build
	cd android && ./gradlew assembleDebug

openapi-check:
	pnpm --filter backend exec vitest run -t 'OpenAPI'

compose-prod-check:
	@printf '%s\n' 'Production Compose begins in Phase 10.'

migrate:
	pnpm --filter backend migrate