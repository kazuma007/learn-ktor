# Diff as a Service

A Kotlin/Ktor API to manage image comparison runs and serve generated artifacts.

The service lets you:
- create projects
- upload source assets (PDFs)
- create comparisons between two assets
- queue runs
- execute `visualdiff` in a background worker
- access run status and generated artifacts (`report.html`, `diff.json`, images)

## Tech stack

- Kotlin `2.3.10`
- Ktor `3.4.0`
- Exposed + PostgreSQL
- Docker / Docker Compose

## Visual Diff JAR source

This repository includes `visualdiff/visualdiff.jar` for local and container execution.
The JAR comes from https://github.com/kazuma007/visual-diff/tree/main

## Prerequisites

- Docker + Docker Compose, or
- JDK 21 + PostgreSQL (if running outside Docker)

## Environment variables

- `DB_URL`
- `DB_USER`
- `DB_PASSWORD`
- `DATA_DIR` (default for local runs: `./data`; Docker Compose sets `/data`)
- `VISUAL_DIFF_CMD` (default for local runs: `java -jar ./visualdiff/visualdiff.jar`; Docker Compose sets `/app/visualdiff/visualdiff.jar`)
- `PORT` (default: `8080`)

## Build

From repository root:

```bash
./gradlew clean build
```

Build fat jar:

```bash
./gradlew clean buildFatJar
```

## Run

### Option 1: Docker Compose (recommended)

From repository root:

```bash
docker compose up --build
```

API base URL:

```text
http://localhost:8080/api
```

### Option 2: Local Gradle run

1) Start PostgreSQL and set env vars.
2) Run API from repository root:

```bash
./gradlew run
```

## Test

Run unit/integration tests:

```bash
./gradlew test
```

Run full API curl scenario and save outputs:

```bash
./scripts/e2e-save-output.sh
```

Notes:
- Requires `jq` (used for reliable JSON parsing).
- Downloads `report.html` plus all registered artifacts into the output directory, preserving original filenames.

Saved artifacts:

```text
outputs/e2e-YYYYMMDD-HHMMSS/
```

## Repository layout

```text
.
|-- src/main/kotlin/com/visualdiffserver
|   |-- app/          # service wiring
|   |-- config/       # environment and path resolution
|   |-- domain/       # API DTOs and enums
|   |-- http/         # routes, request parsing, error handling
|   |-- persistence/  # Exposed repository and schema
|   |-- storage/      # filesystem operations
|   `-- worker/       # background run processing
|-- src/test/kotlin/com/visualdiffserver
|   |-- app/
|   |-- config/
|   |-- http/
|   |-- support/
|   `-- worker/
|-- visualdiff/
|-- example/
`-- scripts/
```

## Runtime storage layout

With `DATA_DIR=/data`:

```text
data/
  assets/
  runs/
    {runId}/
      report.html
      diff.json
      *.png|*.jpg|*.jpeg|*.webp|*.gif
```

## API endpoints

- `POST /api/projects`
- `POST /api/projects/{projectId}/assets`
- `GET /api/assets/{assetId}`
- `GET /api/assets/{assetId}/download`
- `POST /api/projects/{projectId}/comparisons`
- `POST /api/comparisons/{comparisonId}/runs`
- `GET /api/runs/{runId}`
- `GET /api/runs/{runId}/artifacts`
- `GET /api/runs/{runId}/artifacts/{artifactId}`
- `GET /api/runs/{runId}/report`

## Example API flow (curl)

```bash
BASE=http://localhost:8080/api
```

1. Create project

```bash
curl -s -X POST "$BASE/projects" \
  -H 'Content-Type: application/json' \
  -d '{"name":"demo"}'
```

2. Upload old asset

```bash
curl -s -X POST "$BASE/projects/$PROJECT_ID/assets" \
  -F 'file=@./example/A.pdf'
```

3. Upload new asset

```bash
curl -s -X POST "$BASE/projects/$PROJECT_ID/assets" \
  -F 'file=@./example/B.pdf'
```

4. Create comparison

```bash
curl -s -X POST "$BASE/projects/$PROJECT_ID/comparisons" \
  -H 'Content-Type: application/json' \
  -d "{\"oldAssetId\":\"$OLD_ASSET_ID\",\"newAssetId\":\"$NEW_ASSET_ID\"}"
```

5. Queue run

```bash
curl -s -X POST "$BASE/comparisons/$COMPARISON_ID/runs"
```

6. Check run

```bash
curl -s "$BASE/runs/$RUN_ID"
```

7. Download report

```bash
curl -s "$BASE/runs/$RUN_ID/report" -o report.html
```
