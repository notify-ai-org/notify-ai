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
  --header "X-Idempotency-Key: execution-success-$run_id" \
  --data-binary @- <<JSON
[{"timestamp":"$timestamp","correlationId":"execution-success-$run_id","payload":{"operationId":"success-$run_id","value":42},"event":{"name":"EXECUTION_RESULT_TEST","description":"Successful execution result test","priority":1,"eventType":"TEST"},"result":{"success":true,"returnValue":"{\"status\":\"completed\",\"code\":200}"},"durationMillis":12,"serviceName":"curl-integration-test"}]
JSON
