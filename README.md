# Diff as a Service

A Kotlin/Ktor API that manages PDF comparison runs and serves generated artifacts.

The service lets you:

- create projects
- upload source assets
- create comparisons between two assets
- queue runs
- execute `visualdiff` in a background worker
- inspect run status
- download generated artifacts such as `report.html`, `diff.json`, and images

## Tech stack

- Kotlin `2.3.10`
- Ktor `3.4.0`
- Koin
- Exposed + PostgreSQL
- Docker / Docker Compose

## Visual Diff JAR source

This repository includes `visualdiff/visualdiff.jar` for local and container execution.
The JAR comes from:

- https://github.com/kazuma007/visual-diff/tree/main

## Prerequisites

- Docker + Docker Compose, or
- JDK 21 + PostgreSQL

## Environment variables

- `DB_URL`
- `DB_USER`
- `DB_PASSWORD`
- `DATA_DIR`
- `VISUAL_DIFF_CMD`
- `PORT`

Defaults used by the app:

- `DATA_DIR=./data` for local runs
- `DATA_DIR=/data` in containers
- `VISUAL_DIFF_CMD=java -jar ./visualdiff/visualdiff.jar` for local runs
- `VISUAL_DIFF_CMD=java -jar /app/visualdiff/visualdiff.jar` in containers
- `PORT=8080`

## Build

Build and test the project:

```bash
./gradlew clean build
```

Build a fat jar:

```bash
./gradlew buildFatJar
```

Build a shadow jar directly:

```bash
./gradlew shadowJar
```

## Run

### Option 1: Docker Compose

```bash
docker compose up --build
```

API base URL:

```text
http://localhost:8080/api
```

### Option 2: Local Gradle run

Start PostgreSQL, export the required environment variables, then run:

```bash
./gradlew run
```

### Option 3: Run the fat jar

```bash
./gradlew runFatJar
```

## Test and format

Run tests:

```bash
./gradlew test
```

Run all checks:

```bash
./gradlew check
```

Format sources:

```bash
./gradlew ktfmtFormat
```

Verify formatting only:

```bash
./gradlew ktfmtCheck
```

Run the end-to-end helper script and save outputs:

```bash
./scripts/e2e-save-output.sh
```

Notes:

- `jq` is required by the script.
- The script downloads `report.html` and all registered artifacts into a timestamped output directory.

Saved outputs:

```text
outputs/e2e-YYYYMMDD-HHMMSS/
```

## Project structure

The code is organized by responsibility:

```text
src/main/kotlin/com/visualdiffserver
├─ plugins/              # Ktor setup: routing, serialization, status pages, DB init
├─ routes/               # HTTP endpoints, request parsing, path params, API exceptions
├─ application/          # use cases and service orchestration
├─ domain/               # domain models, repository abstraction, enums
├─ infrastructure/
│  └─ db/
│     ├─ tables/         # Exposed table definitions
│     ├─ repository/     # Exposed repository implementation
│     └─ mapper/         # ResultRow -> domain model mapping
├─ api/
│  ├─ request/           # HTTP request DTOs
│  └─ response/          # HTTP response DTOs and API mappers
├─ config/               # typed application config
├─ storage/              # filesystem storage helpers
├─ worker/               # background run processing
└─ Application.kt        # application entrypoint and wiring
```

Layering rules:

- `routes`: HTTP only
- `application`: use cases
- `domain`: framework-independent core types and abstractions
- `infrastructure`: database implementation details
- `api`: external request/response contract
- `plugins`: Ktor initialization

## Runtime storage layout

With `DATA_DIR=./data` or `DATA_DIR=/data`, files are stored like this:

```text
data/
  assets/
  runs/
    {runId}/
      report.html
      diff.json
      *.png|*.jpg|*.jpeg|*.webp|*.gif
      *.css
      *.js
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

## Example API flow

Set the base URL:

```bash
BASE=http://localhost:8080/api
```

Create a project:

```bash
curl -s -X POST "$BASE/projects" \
  -H 'Content-Type: application/json' \
  -d '{"name":"demo"}'
```

Upload the first asset:

```bash
curl -s -X POST "$BASE/projects/$PROJECT_ID/assets" \
  -F 'file=@./example/A.pdf'
```

Upload the second asset:

```bash
curl -s -X POST "$BASE/projects/$PROJECT_ID/assets" \
  -F 'file=@./example/B.pdf'
```

Create a comparison:

```bash
curl -s -X POST "$BASE/projects/$PROJECT_ID/comparisons" \
  -H 'Content-Type: application/json' \
  -d "{\"oldAssetId\":\"$OLD_ASSET_ID\",\"newAssetId\":\"$NEW_ASSET_ID\"}"
```

Queue a run:

```bash
curl -s -X POST "$BASE/comparisons/$COMPARISON_ID/runs"
```

Check run status:

```bash
curl -s "$BASE/runs/$RUN_ID"
```

List run artifacts:

```bash
curl -s "$BASE/runs/$RUN_ID/artifacts"
```

Download the HTML report:

```bash
curl -s "$BASE/runs/$RUN_ID/report" -o report.html
```

## Configuration notes

- The Ktor module entrypoint is `com.visualdiffserver.ApplicationKt.module`.
- Database schema creation is handled on startup through Exposed.
- The background worker starts with the application and polls for queued runs.
