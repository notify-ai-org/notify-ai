#!/usr/bin/env bash
set -euo pipefail

endpoint="${ENDPOINT:-http://localhost:8080/api/event}"
timestamp="$(date -u +"%Y-%m-%dT%H:%M:%SZ")"
run_id="$(date +%s)-$$"
result_dir="$(mktemp -d "${TMPDIR:-/tmp}/notify-two-tenants.XXXXXX")"

fire() {
  local tenant_label="$1"
  local token="$2"
  local payload
  local event
  if [[ "$tenant_label" == "tenant-one" ]]; then
    payload="{\"customerId\":\"customer-$tenant_label\",\"accountId\":\"account-$run_id\",\"phoneNumber\":\"+15550001001\",\"otpPurpose\":\"LOGIN\"}"
    event='{"name":"OTP_REQUESTED","description":"Customer requested a one-time password","priority":4,"eventType":"DOMAIN","scheduleIntent":"immediate","preferredTimeWindow":"00:00-23:59"}'
  else
    payload="{\"orderId\":\"order-$tenant_label-$run_id\",\"customerId\":\"customer-$tenant_label\",\"amount\":202,\"currency\":\"USD\"}"
    event='{"name":"ORDER_PLACED","description":"Customer placed an order","priority":2,"eventType":"DOMAIN","scheduleIntent":"immediate","preferredTimeWindow":"09:00-18:00"}'
  fi
  curl --silent --show-error \
    --output "$result_dir/$tenant_label.json" \
    --write-out "$tenant_label status=%{http_code}\n" \
    --request POST "$endpoint" \
    --header "Authorization: Bearer $token" \
    --header "Content-Type: application/json" \
    --header "X-Idempotency-Key: fully-captured-$tenant_label-$run_id" \
    --data-binary @- <<JSON
[{"timestamp":"$timestamp","correlationId":"fully-captured-$tenant_label-$run_id","payload":$payload,"event":$event,"result":{"success":true,"returnValue":"{\"accepted\":true}"},"durationMillis":25,"serviceName":"curl-integration-test"}]
JSON
}

fire tenant-one "eyJhbGciOiJIUzI1NiJ9.eyJlbWFpbCI6InJveWFscm9ubnkxMkBnbWFpbC5jb20iLCJzY29wZSI6ImFkbWluIiwic3ViIjoidV9hZG1pbnVzZXJfZmI1MTI5ZmMtODM4Mi00ZjBlLWJlMjUtZjZjMjZjMWJjNjYxIiwidGVuYW50SWQiOiJ0LTUzZjAyYmIwLTNjMzgtNDlhNy1hNzE5LWE3NzBjYmU4ZmUzYyIsInByb2ZpbGUiOiJiYXNpYyIsImlhdCI6MTc4Njg4NDk3OSwiZXhwIjoxNzg2ODg4NTc5fQ.tYF_4jrx5E-Nj6PrbqMtEfT7GX6W24uDjLCTSLXedTg" &
pid_one=$!
fire tenant-two "eyJhbGciOiJIUzI1NiJ9.eyJlbWFpbCI6InJvaGFuLm5uMTIwM0BnbWFpbC5jb20iLCJzY29wZSI6ImFkbWluIiwic3ViIjoidV9hZG1pbnVzZXJfNDcxYmM3NDctZDYzZC00ZTk4LTljOTQtMTQ5NTAzOTdhYzczIiwidGVuYW50SWQiOiJ0LWExZDc2NDM4LWRjMjUtNDVkZi1hYmFhLTQ3OTQ4ZGRhZjRkZiIsInByb2ZpbGUiOiJiYXNpYyIsImlhdCI6MTc4Njg4NDMyNiwiZXhwIjoxNzg2ODg3OTI2fQ.2YYhaXlSDSDmicQ0X82BYKpju-4atLbwgdWCbDrNHus" &
pid_two=$!

status=0
wait "$pid_one" || status=1
wait "$pid_two" || status=1
for response in "$result_dir"/*.json; do
  printf '%s: ' "$(basename "$response")"
  cat "$response"
  printf '\n'
done
exit "$status"
