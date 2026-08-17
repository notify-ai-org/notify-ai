# Event capture curl cases

The scripts target `http://localhost:8080/api/event` by default. Override it with `ENDPOINT`.

For single-tenant cases:

```bash
TOKEN='<bearer-token>' ./scripts/event-cases/empty-payload.sh
```

For the simultaneous two-tenant case, tenant one sends `OTP_REQUESTED` while tenant two sends `ORDER_PLACED`:

```bash
TENANT_ONE_TOKEN='<tenant-one-token>' \
TENANT_TWO_TOKEN='<tenant-two-token>' \
./scripts/event-cases/fully-captured-two-tenants.sh
```

Each invocation creates unique correlation and idempotency keys. The two-tenant script starts both requests in the background before waiting for either response.
