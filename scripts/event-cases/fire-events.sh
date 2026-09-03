#!/usr/bin/env bash

set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8090}"
CUSTOMER_ID="${CUSTOMER_ID:-CUST-1}"
RUN_ID="${RUN_ID:-$(date +%s)}"

request() {
  local event_name="$1"
  local endpoint="$2"
  local payload="$3"

  printf '\nFiring %s...\n' "$event_name"
  curl --fail-with-body --silent --show-error \
    --request POST "${BASE_URL}${endpoint}" \
    --header 'Content-Type: application/json' \
    --data "$payload"
  printf '\n'
}

request "ORDER_PLACED" "/api/orders/place" "$(cat <<JSON
{
  "orderId": "ORD-PLACED-${RUN_ID}",
  "customerId": "${CUSTOMER_ID}",
  "amount": 249.99,
  "items": ["Mechanical Keyboard", "Wireless Mouse"],
  "shippingAddress": "42 MG Road, Bengaluru, Karnataka 560001"
}
JSON
)"

request "PAYMENT_FAILED" "/api/orders/payment-failed" "$(cat <<JSON
{
  "orderId": "ORD-PAYMENT-${RUN_ID}",
  "customerId": "${CUSTOMER_ID}",
  "amount": 799.50,
  "items": ["Noise-Cancelling Headphones"],
  "shippingAddress": "42 MG Road, Bengaluru, Karnataka 560001"
}
JSON
)"

# ORDER_SHIPPED resolves its recipient from an order already stored by ORDER_PLACED.
request "ORDER_PLACED (shipment prerequisite)" "/api/orders/place" "$(cat <<JSON
{
  "orderId": "ORD-SHIPPED-${RUN_ID}",
  "customerId": "${CUSTOMER_ID}",
  "amount": 129.99,
  "items": ["Smart Watch"],
  "shippingAddress": "42 MG Road, Bengaluru, Karnataka 560001"
}
JSON
)"

request "ORDER_SHIPPED" "/api/orders/ship" "$(cat <<JSON
{
  "orderId": "ORD-SHIPPED-${RUN_ID}",
  "trackingNumber": "BLUEDART-${RUN_ID}",
  "carrier": "Blue Dart",
  "estimatedDelivery": "2026-09-08"
}
JSON
)"

request "ABANDONED_CART" "/api/orders/abandon-cart" "$(cat <<JSON
{
  "cartId": "CART-${RUN_ID}",
  "customerId": "${CUSTOMER_ID}",
  "items": ["Gaming Chair", "USB-C Hub"],
  "lastActivityAt": "2026-09-03T14:30:00+05:30"
}
JSON
)"

printf '\nAll ecommerce events fired successfully.\n'
