#!/usr/bin/env bash
set -euo pipefail

endpoint="${ENDPOINT:-http://localhost:8080/api/event}"
: "${TOKEN:?Set TOKEN to a tenant bearer token}"
timestamp="$(date -u +"%Y-%m-%dT%H:%M:%SZ")"
run_id="$(date +%s)-$$"

curl --silent --show-error --include \
  --request POST "$endpoint" \
  --header "Authorization: Bearer $TOKEN" \
  --header "Content-Type: application/json" \
  --header "X-Idempotency-Key: empty-payload-$run_id" \
  --data-binary @- <<JSON
[{"timestamp":"$timestamp","correlationId":"empty-payload-$run_id","payload":{},"event":{"name":"EMPTY_PAYLOAD_TEST","description":"Event with an empty payload","priority":1,"eventType":"TEST"},"result":{"success":true,"returnValue":"no payload values"},"durationMillis":1,"serviceName":"curl-integration-test"}]
JSON
