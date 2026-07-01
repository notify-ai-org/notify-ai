The **ACP Server** (Agent Control Plane) is the decision-making layer of Notify.ai. It receives application events from the client SDK, enriches them with domain context, coordinates AI agents, and produces notification decisions, templates, schedules, and memory updates.

The control plane is designed to sit behind authenticated access and process events asynchronously, so upstream applications can emit business events without owning notification intelligence, personalization logic, or delivery planning.

---

## Key Responsibilities

### 1. Event Intake

The ACP Server accepts structured events emitted by registered client SDKs. Each event carries metadata about the source application, the event type, and the contextual payload needed for notification decisions.

Events can be ingested through low-latency APIs or through streaming infrastructure for higher-throughput deployments. Both paths feed the same processing pipeline, so teams can start with simple integration and move to streaming as scale increases.

### 2. Domain Understanding

Notify.ai uses vocabulary metadata from the client SDK to understand the meaning of application-specific payloads. This lets the control plane reason about domain objects such as orders, accounts, carts, customers, transactions, appointments, or any other model exposed by the application.

This domain context helps agents decide:

- whether an event is notification-worthy
- which user or subject the event relates to
- what facts should be remembered
- which message tone and content are appropriate
- when a notification is most useful

### 3. Agent Orchestration

The ACP Server coordinates specialized AI agents for event analysis, rule interpretation, memory extraction, template generation, and schedule planning.

Instead of treating notifications as static text, the control plane evaluates each event in context. It can combine the current payload with domain vocabulary, historical facts, configured rules, and channel-specific delivery constraints.

Agent work is handled asynchronously so event intake remains responsive while deeper reasoning happens in the background.

### 4. Rules And Policies

Business rules can guide notification decisions without hardcoding every case in the upstream application. Rules are interpreted against the domain vocabulary and applied during event processing.

This allows teams to express notification behavior in business terms, such as priority, eligibility, timing preference, escalation behavior, or suppression conditions.

### 5. Memory And Personalization

The control plane builds long-term context from event history. It extracts durable facts and behavioral patterns that can improve future notification decisions.

Examples include:

- user preferences
- repeated behavior patterns
- risk or severity signals
- engagement context
- recent activity summaries

This memory layer helps Notify.ai avoid treating every event as isolated. Notifications can become more relevant over time as the system learns from prior interactions.

### 6. Template And Schedule Planning

Once an event qualifies for notification, the ACP Server can generate or select a message template and determine the most appropriate schedule.

Template generation considers the event, recipient context, configured channel, and available domain content. Schedule planning can support immediate, delayed, or rule-driven delivery depending on the event intent.

### 7. Multi-Tenant Operation

Hosted deployments are designed for tenant-aware operation. Higher-scale tiers can isolate workloads and scale processing capacity independently based on tenant needs and traffic volume.

This keeps event processing, agent capacity, and delivery planning aligned with the operational requirements of each customer environment.

---

## Deployment Model

The ACP Server is packaged as part of the Notify.ai backend runtime. The backend application provides the web application, administrative APIs, authentication boundary, and operational endpoints around the control plane.

For local development, run the full backend application. For production, place the backend behind a reverse proxy or load balancer and expose only the intended public routes.

Use the local development guide for startup and operational setup.
