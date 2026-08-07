.DEFAULT_GOAL := help

.PHONY: help setup dev-db-up dev-db-down backend-dev format format-check lint test test-backend test-android build openapi-check compose-prod-check migrate migration-new

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
	./android/gradlew test

build:
	pnpm build
	./android/gradlew assembleDebug

openapi-check:
	@printf '%s\n' 'OpenAPI generation begins in Phase 2.'

compose-prod-check:
	@printf '%s\n' 'Production Compose begins in Phase 10.'

migrate:
	@printf '%s\n' 'Database migrations begin in Phase 2.'

migration-new:
	@printf '%s\n' 'Database migrations begin in Phase 2.'