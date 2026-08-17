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
  --header "X-Idempotency-Key: execution-failure-$run_id" \
  --data-binary @- <<JSON
[{"timestamp":"$timestamp","correlationId":"execution-failure-$run_id","payload":{"operationId":"failure-$run_id","value":-1},"event":{"name":"EXECUTION_RESULT_TEST","description":"Failed execution result test","priority":3,"eventType":"TEST"},"result":{"success":false,"returnValue":null},"exception":{"exceptionType":"java.lang.IllegalStateException","message":"Simulated execution failure","stackTrace":"java.lang.IllegalStateException: Simulated execution failure\\n\\tat curl.integration.TestCase.execute(TestCase.java:1)"},"durationMillis":9,"serviceName":"curl-integration-test"}]
JSON
