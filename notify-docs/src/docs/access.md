The `access` module is the primary executable web application for **Notify.ai**. It encapsulates the Spring Boot application entrypoint (`VocabAgentApplication`), exposes external REST controllers for both the client SDK and the administrative portals, and serves the compiled static front-end microfrontend assets.

##  Running Locally

The `access` module can be run locally as a Spring Boot application:

### Prerequisites
1. **MySQL**: Running on `localhost:3306` with a database named `notify`.
   - Update credentials in `src/main/resources/application-local.properties` if needed.
2. **Redis**: Running on `localhost:6379`.
3. **OpenAI API Key**: Set `openai.api.key` in your properties or environment.

### Execution
From the root directory of the project, run:
```bash
mvn spring-boot:run -pl access
```
Alternatively, open `VocabAgentApplication.java` in your IDE and run the main method. By default, the application runs on port **8080** under the `local` profile.

---

##  REST API Reference

All endpoints are served under the base URL `http://localhost:8080`. Authenticated endpoints require a valid `Authorization: Bearer <jwt_token>` header.

---

###  Client Authentication

#### `POST /client/register`
Register a new client application with the Notify.ai platform. Returns a JWT access token and refresh token for use in subsequent SDK calls.

**Request Body**
```json
{
  "applicationName": "my-service",
  "tenantId": "t-xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"
}
```

**Response `200 OK`**
```json
{
  "clientId": "client-xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx",
  "accessToken": "<jwt>",
  "refreshToken": "<jwt>",
  "expiresAt": "NEVER"
}
```

---

#### `POST /auth/token/refresh`
Refresh an expired JWT access token using a valid refresh token.

**Request Body**
```json
{
  "refreshToken": "<refresh_jwt>"
}
```

**Response `200 OK`**
```json
{
  "accessToken": "<new_jwt>",
  "refreshToken": "<new_refresh_jwt>"
}
```

---

###  Admin Authentication

#### `POST /api/admin/auth/register`
Register a new admin user with email/password credentials. Auto-provisions a tenant ID.

**Request Body**
```json
{
  "email": "admin@example.com",
  "password": "s3cur3P@ss",
  "name": "Jane Doe"
}
```

**Response `200 OK`**
```json
{
  "message": "User registered successfully",
  "tenantId": "t-xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"
}
```

**Response `409 Conflict`** — Email already registered.

---

#### `POST /api/admin/auth/custom-login`
Authenticate an admin user with email and password. Returns access and refresh tokens.

**Request Body**
```json
{
  "email": "admin@example.com",
  "password": "s3cur3P@ss"
}
```

**Response `200 OK`**
```json
{
  "accessToken": "<jwt>",
  "refreshToken": "<jwt>",
  "tenantId": "t-xxxxxxxx",
  "email": "admin@example.com",
  "name": "Jane Doe"
}
```

**Response `401 Unauthorized`** — Invalid credentials.

---

#### `POST /api/admin/auth/google-login`
Authenticate an admin user via a Google OAuth ID token. Auto-provisions the user and tenant if they don't yet exist.

**Request Body**
```json
{
  "idToken": "<google_id_token>"
}
```

**Response `200 OK`**
```json
{
  "accessToken": "<jwt>",
  "refreshToken": "<jwt>",
  "tenantId": "t-xxxxxxxx",
  "email": "user@gmail.com",
  "name": "Jane Doe"
}
```

---

#### `POST /api/admin/auth/logout`
Invalidate the current access and refresh token pair, effectively ending the admin session.

**Request Body**
```json
{
  "accessToken": "<jwt>",
  "refreshToken": "<jwt>"
}
```

**Response `200 OK`**
```json
{
  "message": "Logged out successfully"
}
```

---

#### `POST /api/admin/auth/generate`
>  **Requires:** `Authorization: Bearer <admin_jwt>`

Generate a new client credential pair (Confluent Kafka API key + secret) scoped to the authenticated admin's tenant.

**Request Body**
```json
{
  "applicationName": "my-service",
  "tenantId": "t-xxxxxxxx"
}
```

**Response `200 OK`**
```json
{
  "clientId": "client-xxxxxxxx",
  "tenantId": "t-xxxxxxxx",
  "expiresAt": "NEVER"
}
```

---

###  Dead Letter Queue (DLQ)

#### `GET /api/admin/dead-letter`
>  **Requires:** `Authorization: Bearer <admin_jwt>`

List all pending dead-letter records, paginated and ordered by creation time descending.

**Query Parameters**

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `page` | integer | `0` | Page number (0-indexed) |
| `size` | integer | `20` | Number of records per page |

**Response `200 OK`**
```json
{
  "content": [
    {
      "id": 1,
      "notificationId": "notif-xxxx",
      "channel": "EMAIL",
      "failureReason": "SMTP timeout",
      "createdAt": "2024-01-15T10:30:00Z",
      "status": "PENDING"
    }
  ],
  "totalElements": 42,
  "totalPages": 3,
  "number": 0
}
```

---

#### `GET /api/admin/dead-letter/{id}`
>  **Requires:** `Authorization: Bearer <admin_jwt>`

Retrieve a single dead-letter record by its ID.

**Path Parameters**

| Parameter | Type | Description |
|-----------|------|-------------|
| `id` | long | The dead-letter record ID |

**Response `200 OK`** — Returns the full `DeadLetterRecord` object.

---

#### `GET /api/admin/dead-letter/search`
>  **Requires:** `Authorization: Bearer <admin_jwt>`

Search dead-letter records by the original notification ID.

**Query Parameters**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `notificationId` | string |  | The original notification job ID |
| `page` | integer |  | Page number (default `0`) |
| `size` | integer |  | Page size (default `20`) |

**Response `200 OK`** — Paginated list of matching `DeadLetterRecord` objects.

---

#### `POST /api/admin/dead-letter/{id}/replay`
>  **Requires:** `Authorization: Bearer <admin_jwt>`

Re-dispatch a failed notification by replaying its stored job payload through the normal processing pipeline.

**Path Parameters**

| Parameter | Type | Description |
|-----------|------|-------------|
| `id` | long | The dead-letter record ID |

**Query Parameters**

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `actor` | string | `admin` | Identifier of the person triggering the replay |

**Response `200 OK`**
```json
{
  "status": "replayed",
  "id": 1,
  "replayedBy": "admin"
}
```

---

#### `POST /api/admin/dead-letter/{id}/discard`
>  **Requires:** `Authorization: Bearer <admin_jwt>`

Permanently discard a dead-letter record without re-dispatching it.

**Path Parameters**

| Parameter | Type | Description |
|-----------|------|-------------|
| `id` | long | The dead-letter record ID |

**Query Parameters**

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `actor` | string | `admin` | Identifier of the person discarding the record |
| `reason` | string | — | Human-readable reason for discarding |

**Response `200 OK`**
```json
{
  "status": "discarded",
  "id": 1,
  "discardedBy": "admin"
}
```

---

###  Templates & Schedules

#### `GET /api/admin/templates-schedules/templates`
>  **Requires:** `Authorization: Bearer <admin_jwt>`

Retrieve all stored message templates.

**Response `200 OK`**
```json
[
  {
    "id": 1,
    "eventName": "order.placed",
    "eventType": "TRANSACTIONAL",
    "channel": "EMAIL",
    "subject": "Your order has been placed!",
    "template": "Hi {{name}}, your order #{{orderId}} is confirmed."
  }
]
```

---

#### `POST /api/admin/templates-schedules/templates`
>  **Requires:** `Authorization: Bearer <admin_jwt>`

Create or update a message template.

**Request Body**
```json
{
  "eventName": "order.placed",
  "eventType": "TRANSACTIONAL",
  "channel": "EMAIL",
  "subject": "Your order has been placed!",
  "template": "Hi {{name}}, your order #{{orderId}} is confirmed."
}
```

**Response `200 OK`** — Returns the saved `MessageTemplate` object.

---

#### `GET /api/admin/templates-schedules/schedules`
>  **Requires:** `Authorization: Bearer <admin_jwt>`

Retrieve all stored event schedules.

**Response `200 OK`**
```json
[
  {
    "id": "sched-xxxx",
    "eventName": "order.placed",
    "triggerType": "cron",
    "cronExpression": "0 9 * * MON",
    "description": "Send weekly order summary every Monday at 9am",
    "validated": true
  }
]
```

---

#### `POST /api/admin/templates-schedules/schedules`
>  **Requires:** `Authorization: Bearer <admin_jwt>`

Create or update an event delivery schedule.

**Request Body**
```json
{
  "eventName": "order.placed",
  "triggerType": "cron",
  "cronExpression": "0 9 * * MON",
  "description": "Send weekly order summary every Monday at 9am"
}
```

**Response `200 OK`** — Returns the saved `EventSchedule` object.

---

###  Managed Configuration

#### `POST /api/admin/config/apply`
>  **Requires:** `Authorization: Bearer <admin_jwt>`

Dynamically update runtime configuration values (e.g. agent pool sizes, buffer timeouts) without restarting the application. Keys map to `@ManagedConfiguration` annotated fields.

**Request Body**
```json
{
  "agent.orchestrator.core-pool-size": 15,
  "agent.orchestrator.max-pool-size": 30,
  "agent.buffer.timeout": "20s"
}
```

**Response `200 OK`**
```json
{
  "message": "Configuration dynamically updated"
}
```

**Response `500 Internal Server Error`**
```json
{
  "message": "Failed to update configuration"
}
```