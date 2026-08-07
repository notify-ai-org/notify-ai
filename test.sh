#!/usr/bin/env bash
set -euo pipefail

endpoint="${ENDPOINT:-http://localhost:8080/api/event}"
payload_file="${1:-event.json}"
parallelism="${PARALLELISM:-20}"
idempotency_key="${IDEMPOTENCY_KEY:-idem-test-$(date +%s)}"


if [[ ! -r "$payload_file" ]]; then
  echo "Payload file is missing or unreadable: $payload_file" >&2
  exit 2
fi

if ! [[ "$parallelism" =~ ^[1-9][0-9]*$ ]]; then
  echo "PARALLELISM must be a positive integer: $parallelism" >&2
  exit 2
fi

result_dir="$(mktemp -d "${TMPDIR:-/tmp}/notify-idempotency.XXXXXX")"

echo "Endpoint: $endpoint"
echo "Idempotency key: $idempotency_key"
echo "Requests: $parallelism"
echo "Results: $result_dir"

for request_number in $(seq 1 "$parallelism"); do
  (
    curl --silent --show-error \
      --output "$result_dir/response-$request_number.json" \
      --write-out '%{http_code}' \
      --request POST "$endpoint" \
      --header "Authorization: Bearer eyJhbGciOiJIUzI1NiJ9.eyJlbWFpbCI6InJvaGFuLm5uMTIwM0BnbWFpbC5jb20iLCJzY29wZSI6ImFkbWluIiwic3ViIjoidV9hZG1pbnVzZXJfNDcxYmM3NDctZDYzZC00ZTk4LTljOTQtMTQ5NTAzOTdhYzczIiwidGVuYW50SWQiOiJ0LWExZDc2NDM4LWRjMjUtNDVkZi1hYmFhLTQ3OTQ4ZGRhZjRkZiIsInByb2ZpbGUiOiJiYXNpYyIsImlhdCI6MTc4NjA3MDczNiwiZXhwIjoxNzg2MDc0MzM2fQ.kOYXNGVpwACw2qqHb_0tYTWzUkLmiyOsJT9v4m03KuI" \
      --header "Content-Type: application/json" \
      --header "X-Idempotency-Key: $idempotency_key" \
      --data-binary "@$payload_file" \
      >"$result_dir/status-$request_number.txt" \
      2>"$result_dir/error-$request_number.txt"
  ) &
done

wait || true

echo
echo "HTTP results:"
for request_number in $(seq 1 "$parallelism"); do
  status_file="$result_dir/status-$request_number.txt"
  error_file="$result_dir/error-$request_number.txt"
  status="$(cat "$status_file" 2>/dev/null || true)"
  printf 'request=%02d status=%s\n' "$request_number" "${status:-CURL_ERROR}"
  if [[ -s "$error_file" ]]; then
    sed 's/^/  /' "$error_file"
  fi
done

echo
echo "Status counts:"
awk 'NF { counts[$1]++ } END { for (status in counts) print status, counts[status] }' \
  "$result_dir"/status-*.txt | sort

echo
echo "Inspect response bodies under: $result_dir"
