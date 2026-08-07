# syntax=docker/dockerfile:1
FROM node:24.13.0-bookworm-slim AS base

ENV PNPM_HOME=/pnpm
ENV PATH=$PNPM_HOME:$PATH

RUN corepack enable

FROM base AS dependencies

WORKDIR /workspace

COPY package.json pnpm-lock.yaml pnpm-workspace.yaml ./
COPY backend/package.json backend/package.json

RUN pnpm install --frozen-lockfile

FROM dependencies AS build

COPY backend backend
COPY tsconfig.json tsconfig.json

RUN pnpm --filter @easymacros/backend build \
  && pnpm --filter @easymacros/backend deploy --legacy --prod /opt/easymacros

FROM node:24.13.0-bookworm-slim AS runtime

ENV NODE_ENV=production
ENV HOST=0.0.0.0
ENV PORT=3000

WORKDIR /app

COPY --from=build --chown=node:node /opt/easymacros ./
COPY --from=build --chown=node:node /workspace/backend/dist ./dist

USER node

VOLUME ["/tmp"]

EXPOSE 3000

CMD ["sh", "-c", "node dist/src/migrate.js && exec node dist/src/server.js"]