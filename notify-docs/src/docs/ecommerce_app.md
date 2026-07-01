##  Overview

The `ecommerce-app` module is a fully functional sample Spring Boot application demonstrating how to integrate the **Notify.ai** client SDK into an e-commerce ecosystem. It defines typical domain structures (Carts, Customers, Orders, Shipments) and illustrates how `@Event`, `@Rule`, `@Callback`, and `@Model` annotations work together to enable agentic notifications.

##  How it is annotated

- **Vocabulary Models**: `Customer` and `OrderPayload` classes are marked with `@Model` or `@Vocabulary` annotations, exposing their properties to the semantic analysis engine.
- **Events**: Order, cart, and shipment operations are decorated with `@Event` to capture transactional activity automatically when the application handles those workflows.
- **Subject Suppliers**: Resolves the target recipient email/phone number for events.

## Running The Example

You can run the e-commerce sample locally to see how annotated business operations become Notify.ai events.

### Prerequisites
- **Notify.ai backend**: Ensure the Access application is running locally.

### Execution
From the root directory of the project, run:
```bash
mvn spring-boot:run -pl examples/ecommerce-app
```
The application starts on its configured local port.

### Testing the Integration
Once the application is running, trigger order and cart operations with sample HTTP requests:
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
These calls are captured by the Notify.ai SDK and sent to the local backend for processing.
