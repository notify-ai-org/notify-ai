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
  --header "X-Idempotency-Key: no-event-$run_id" \
  --data-binary @- <<JSON
[{"timestamp":"$timestamp","correlationId":"no-event-$run_id","payload":{"orderId":"missing-event-$run_id"},"result":{"success":true,"returnValue":"captured without event metadata"},"durationMillis":5,"serviceName":"curl-integration-test"}]
JSON
