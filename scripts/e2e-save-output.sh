#!/usr/bin/env sh
set -eu

BASE_URL="${BASE_URL:-http://localhost:8080/api}"
OLD_FILE="${OLD_FILE:-./example/A.pdf}"
NEW_FILE="${NEW_FILE:-./example/B.pdf}"

if ! command -v jq >/dev/null 2>&1; then
  echo "jq is required. Install jq and retry." >&2
  exit 1
fi

if [ ! -f "$OLD_FILE" ]; then
  echo "Missing OLD_FILE: $OLD_FILE" >&2
  exit 1
fi

if [ ! -f "$NEW_FILE" ]; then
  echo "Missing NEW_FILE: $NEW_FILE" >&2
  exit 1
fi

STAMP="$(date +%Y%m%d-%H%M%S)"
OUT_DIR="outputs/e2e-${STAMP}"
mkdir -p "$OUT_DIR"

echo "Saving output to: $OUT_DIR"

curl --fail-with-body -sS -X POST "$BASE_URL/projects" \
  -H 'Content-Type: application/json' \
  -d '{"name":"demo"}' > "$OUT_DIR/01-project.json"
PROJECT_ID="$(jq -r '.id' "$OUT_DIR/01-project.json")"

curl --fail-with-body -sS -X POST "$BASE_URL/projects/$PROJECT_ID/assets" \
  -F "file=@$OLD_FILE" > "$OUT_DIR/02-old-asset.json"
OLD_ASSET_ID="$(jq -r '.id' "$OUT_DIR/02-old-asset.json")"

curl --fail-with-body -sS -X POST "$BASE_URL/projects/$PROJECT_ID/assets" \
  -F "file=@$NEW_FILE" > "$OUT_DIR/03-new-asset.json"
NEW_ASSET_ID="$(jq -r '.id' "$OUT_DIR/03-new-asset.json")"

curl --fail-with-body -sS -X POST "$BASE_URL/projects/$PROJECT_ID/comparisons" \
  -H 'Content-Type: application/json' \
  -d "{\"oldAssetId\":\"$OLD_ASSET_ID\",\"newAssetId\":\"$NEW_ASSET_ID\"}" > "$OUT_DIR/04-comparison.json"
COMPARISON_ID="$(jq -r '.id' "$OUT_DIR/04-comparison.json")"

curl --fail-with-body -sS -X POST "$BASE_URL/comparisons/$COMPARISON_ID/runs" > "$OUT_DIR/05-run.json"
RUN_ID="$(jq -r '.id' "$OUT_DIR/05-run.json")"

STATUS="UNKNOWN"
i=0
while [ "$i" -lt 120 ]; do
  curl --fail-with-body -sS "$BASE_URL/runs/$RUN_ID" > "$OUT_DIR/06-run-status.json"
  STATUS="$(jq -r '.status' "$OUT_DIR/06-run-status.json")"
  if [ "$STATUS" = "SUCCEEDED" ] || [ "$STATUS" = "FAILED" ]; then
    break
  fi
  i=$((i + 1))
  sleep 1
done

curl --fail-with-body -sS "$BASE_URL/runs/$RUN_ID/artifacts" > "$OUT_DIR/07-artifacts.json"
curl --fail-with-body -sS "$BASE_URL/runs/$RUN_ID/report" -o "$OUT_DIR/report.html"

ARTIFACT_COUNT="$(jq 'length' "$OUT_DIR/07-artifacts.json")"
idx=0
while [ "$idx" -lt "$ARTIFACT_COUNT" ]; do
  ARTIFACT_ID="$(jq -r ".[$idx].id" "$OUT_DIR/07-artifacts.json")"
  ARTIFACT_FILENAME="$(jq -r ".[$idx].filename" "$OUT_DIR/07-artifacts.json")"
  curl --fail-with-body -sS "$BASE_URL/runs/$RUN_ID/artifacts/$ARTIFACT_ID" \
    -o "$OUT_DIR/$ARTIFACT_FILENAME"
  idx=$((idx + 1))
done

{
  echo "PROJECT_ID=$PROJECT_ID"
  echo "RUN_ID=$RUN_ID"
  echo "STATUS=$STATUS"
  echo "ARTIFACT_COUNT=$ARTIFACT_COUNT"
  echo "BASE_URL=$BASE_URL"
} > "$OUT_DIR/summary.txt"

echo "Done. See $OUT_DIR"
