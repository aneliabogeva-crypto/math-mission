#!/usr/bin/env bash
# Builds the PWA into the backend and starts everything on http://localhost:8080
set -euo pipefail
cd "$(dirname "$0")"
( cd frontend && npm install --no-audit --no-fund && npm run build )
rm -rf backend/src/main/resources/static && cp -r frontend/dist backend/src/main/resources/static
cd backend && mvn -q -B spring-boot:run
