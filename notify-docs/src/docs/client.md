The **Notify.ai Client SDK** is a lightweight Java library that integrates into Spring Boot applications. It captures annotated domain events, packages method context as semantic event payloads, and sends those events to the Notify.ai control plane.

> **Note**: Spring Boot is the first supported runtime. Additional language and framework SDKs are planned.

##  Integration Guide

### 1. Add Dependencies
Add the client SDK dependency to your application's `pom.xml`:

```xml
<dependency>
  <groupId>dev.notify-ai</groupId>
  <artifactId>notify-ai-agent-client</artifactId>
  <version>1.0.1</version>
</dependency>
```

### 2. Enable the SDK
Annotate a configuration class with `@EnableNotify` and specify the packages to scan:

```java
@Configuration
@EnableNotify(basePackage = "com.myapp")
public class NotifyConfig {}
```

### 3. Application Properties
Configure connection parameters in your `application.yml` or `application.properties`:

```yaml
notify:
  base-package: com.myapp
  application-name: my-service
  client-token: ${NOTIFY_CLIENT_TOKEN}
```

---

# Steps for integrating the SDK

- Add dependencies to your `pom.xml`
- Sign in to the Notify.ai portal and generate a client token
- Enable the SDK by annotating a configuration class with `@EnableNotify` and specify the packages to scan
- Configure connection parameters in your `application.yml` or `application.properties`
- Add relevant annotations to your business logic

##  Annotation Reference

### `@EnableNotify`
**Target:** Class (on a `@Configuration` class)

Bootstraps the Notify.ai SDK in your application. The SDK scans the specified `basePackage` (or the value of `notify.base-package` in your properties) for all other Notify annotations and registers the necessary AOP interceptors and beans.

**Attributes**

| Attribute | Type | Default | Description |
|-----------|------|---------|-------------|
| `basePackage` | String | `""` | Root package to scan for Notify annotations. Falls back to `notify.base-package` property if empty. |

**Example**
```java
@Configuration
@EnableNotify(basePackage = "com.myapp.orders")
public class NotifyConfig {}
```

---

### `@Event`
**Target:** Method

The primary annotation. Wrap any service method with `@Event` to have the SDK capture the method arguments and return value as a structured payload, then forward the event to the Notify.ai control plane for processing.

**Attributes**

| Attribute | Type | Required | Description |
|-----------|------|----------|-------------|
| `key` | String | ✅ | Unique event key/name (e.g. `"order.placed"`). Used to identify and route the event. |
| `eventType` | String | ✅ | Broad category of the event (e.g. `"TRANSACTIONAL"`, `"ENGAGEMENT"`, `"SYSTEM"`). |
| `description` | String | ❌ | Human-readable description of what this event represents. Helps agents generate better templates and schedules. |
| `scheduleIntent` | String | ❌ | Natural-language hint for the scheduler agent (e.g. `"immediately"`, `"next business day morning"`). |
| `preferredTimeWindow` | String | ❌ | Preferred delivery window (e.g. `"morning"`, `"09:00-12:00"`). |
| `priority` | int | ❌ | Numeric priority for processing order. Lower values are processed first. |
| `version` | String | ❌ | Schema version for the event payload (default `"v1"`). |
| `payload` | Class<?> | ❌ | Explicit payload type override. Defaults to `Void.class` (auto-inferred from method parameters). |

**Example**
```java
@Event(
    key = "order.placed",
    description = "Fired when a customer completes checkout",
    eventType = "TRANSACTIONAL",
    scheduleIntent = "immediately after order confirmation",
    preferredTimeWindow = "anytime",
    priority = 1
)
public Order placeOrder(Cart cart, User user) {
    // your business logic
}
```

---

### `@Rule`
**Target:** Method

Associates a method with a named rule that gates or modifies event behaviour. The method is invoked by the SDK's rule evaluation engine in relation to a specific event. It can return a boolean (gate/suppress) or mutate context before the event is emitted.

**Attributes**

| Attribute | Type | Required | Description |
|-----------|------|----------|-------------|
| `name` | String |  | Unique rule name. Matched against rules configured in the Notify.ai control plane. |
| `description` | String |  | Human-readable description of what this rule enforces. |
| `event` | String |  | Event key this rule applies to. If empty, the rule is considered global. |

**Example**
```java
@Rule(
    name = "high-value-order",
    description = "Only notify for orders above $500",
    event = "order.placed"
)
public boolean isHighValueOrder(Order order) {
    return order.getTotal() > 500.0;
}
```

---

### `@Callback`
**Target:** Method

Defines a lifecycle hook that runs **before** or **after** the AOP intercept for a given event. Use callbacks to inject pre-processing logic (e.g. enriching the payload with additional context) or post-processing logic (e.g. logging, cleanup) without modifying the event-producing method itself.

**Attributes**

| Attribute | Type | Required | Description |
|-----------|------|----------|-------------|
| `event` | String |  | Event key this callback is bound to. |
| `when` | `When` enum |  | `When.BEFORE` runs before the intercepted method executes; `When.AFTER` runs after it returns. |

**Example**
```java
@Callback(event = "order.placed", when = Callback.When.BEFORE)
public void enrichOrderContext(Cart cart, User user) {
    // e.g. attach geo-location or session metadata
}

@Callback(event = "order.placed", when = Callback.When.AFTER)
public void auditOrderEvent(Order result) {
    log.info("Order event captured: {}", result.getId());
}
```

---

### `@Vocabulary`
**Target:** Field (on fields of a `@Model`-annotated class)

Marks a field as a named vocabulary attribute. The SDK registers this field's name, type, and description with Notify.ai, enabling agents to reason semantically about the data.

**Attributes**

| Attribute | Type | Default | Description |
|-----------|------|---------|-------------|
| `name` | String | `""` | Override the attribute name sent to the vocabulary graph. Defaults to the Java field name. |
| `description` | String | `""` | Human-readable description of what this field represents. Used by agents to understand data semantics. |

**Example**
```java
@Model(description = "Represents a customer order")
public class Order {

    @Vocabulary(name = "order_id", description = "Unique identifier for the order")
    private String id;

    @Vocabulary(name = "total_amount", description = "Total monetary value of the order in USD")
    private double total;

    @Vocabulary(description = "Current fulfilment status of the order")
    private String status;
}
```

---

### `@Model`
**Target:** Class

Marks a class as a vocabulary model. All fields annotated with `@Vocabulary` within the class are registered as attributes of this model. This powers the agent's understanding of your domain entities.

**Attributes**

| Attribute | Type | Default | Description |
|-----------|------|---------|-------------|
| `description` | String | `""` | Description of the model's purpose, helping agents understand the domain entity it represents. |

**Example**
```java
@Model(description = "Represents a product in the e-commerce catalogue")
public class Product {

    @Vocabulary(description = "The product's display name")
    private String name;

    @Vocabulary(description = "Price in USD")
    private double price;

    @Vocabulary(description = "Current stock quantity")
    private int stockLevel;
}
```

---

### `@VocabularySupplier`
**Target:** Method

Marks a method that dynamically supplies additional payload context for a specific event. The method is called by the SDK at capture time and its return value is merged into the event payload before it is sent to Notify.ai. Use this to enrich events with computed or session-derived data that is not available as method parameters.

**Attributes**

| Attribute | Type | Required | Description |
|-----------|------|----------|-------------|
| `event` | String |  | The event key this supplier provides additional payload for. |
| `description` | String |  | Description of what additional context this method supplies. |

**Example**
```java
@VocabularySupplier(
    event = "order.placed",
    description = "Enriches the order event with customer loyalty tier and session metadata"
)
public Map<String, Object> supplyOrderContext() {
    return Map.of(
        "loyaltyTier", loyaltyService.getCurrentTier(),
        "sessionId", sessionContext.getId(),
        "region", geoService.getRegion()
    );
}
```

---

### `@SubjectSupplier`
**Target:** Method

Marks a method that returns the list of **notification recipients** or subject identifiers for a specific event. The method typically returns email addresses, phone numbers, user IDs, account IDs, or other identifiers used by Notify.ai to resolve the notification audience.

**Attributes**

| Attribute | Type | Required | Description |
|-----------|------|----------|-------------|
| `event` | String |  | The event key this supplier provides subjects for. |
| `description` | String |  | Description of the recipient resolution logic. |

**Example**
```java
@SubjectSupplier(
    event = "order.placed",
    description = "Returns the email address of the customer who placed the order"
)
public List<String> getOrderSubjects(Order order) {
    return List.of(order.getCustomer().getEmail());
}
```

---

##  Local Compilation

As a client SDK library, this module cannot be run on its own. It is compiled and installed locally, then imported by your applications.

To compile and package the client SDK locally:
```bash
mvn clean install -pl client
```

For examples of how this SDK is used, see the e-commerce and banking example guides in these docs.

**APIs to directly emit events and dispatch notifications coming soon**
