##  Overview

The `ecommerce-app` module is a fully functional sample Spring Boot application demonstrating how to integrate the **Notify.ai** client SDK into an e-commerce ecosystem. It defines typical domain structures (Carts, Customers, Orders, Shipments) and illustrates how `@Event`, `@Rule`, `@Callback`, and `@Model` annotations work together to enable agentic notifications.

##  How it is annotated

- **Vocabulary Models**: `Customer` and `OrderPayload` classes are marked with `@Model` or `@Vocabulary` annotations, exposing their properties to the semantic analysis engine.
- **Events**: Methods in `OrderService` (like `createOrder`, `processShipment`) are decorated with `@Event` to capture transactional events automatically when triggered by controllers.
- **Subject Suppliers**: Resolves the target recipient email/phone number for events.

##  Running Locally

You can run the e-commerce sample locally to test the event capture and dispatch flow.

### Prerequisites
- **Notify.ai Control Plane**: Ensure the backend application (`access` module) is running on `http://localhost:8080`.

### Execution
From the root directory of the project, run:
```bash
mvn spring-boot:run -pl examples/ecommerce-app
```
By default, the application runs on port **8090**.

### Testing the Integration
Once the application is running, trigger order/cart events by making HTTP requests:
```bash
# Add an item to the shopping cart
curl -X POST http://localhost:8090/order/cart \
  -H "Content-Type: application/json" \
  -d '{"customerId":"cust-99","item":"Mechanical Keyboard","price":120.0}'

# Check out/place an order
curl -X POST http://localhost:8090/order/checkout \
  -H "Content-Type: application/json" \
  -d '{"customerId":"cust-99","items":["Mechanical Keyboard"],"total":120.0}'
```
These calls will be intercepted by the Notify SDK and pushed as event payloads to the main control plane (`access` module) on port `8080`.
