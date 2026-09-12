#!/usr/bin/env bash
set -euo pipefail

: "${ACCESS_TOKEN:?}"

API_BASE_URL="${API_BASE_URL:-http://localhost:8080}"

curl --fail-with-body --silent --show-error \
  --request POST "http://localhost:8080/api/artifacts/retrieve" \
  --header "Authorization: Bearer eyJhbGciOiJIUzI1NiJ9.eyJlbWFpbCI6InJvaGFuLm5uMTIwM0BnbWFpbC5jb20iLCJzY29wZSI6ImFkbWluIiwic3ViIjoidV9hZG1pbnVzZXJfNDcxYmM3NDctZDYzZC00ZTk4LTljOTQtMTQ5NTAzOTdhYzczIiwidGVuYW50SWQiOiJ0LWExZDc2NDM4LWRjMjUtNDVkZi1hYmFhLTQ3OTQ4ZGRhZjRkZiIsInByb2ZpbGUiOiJiYXNpYyIsImlhdCI6MTc4OTE5MTQ5MCwiZXhwIjoxNzg5MTk1MDkwfQ.EeFaMgJeaONRHpocCjUIjldIkClzhwpBieVwoohqmQo" \
  --header 'Content-Type: application/json' \
  --data '{
    "query": "order confirmation notification policy, delivery timing, channel rules, and approved message tone",
    "limit": 3,
    "mediaTypes": [],
    "tags": []
  }'
