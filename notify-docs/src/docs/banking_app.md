The `banking-app` module is a sample Spring Boot application demonstrating how to integrate the **Notify.ai** client SDK into a financial/banking services architecture. It defines banking operations (Logins, OTP verification, Transactions, Balance summaries) and showcases event interception using annotations.

## 🚀 Running Locally

You can run the banking sample application locally to test the event logging flow.

### Prerequisites
- **Notify.ai Control Plane**: Ensure the backend application (`access` module) is running on `http://localhost:8080`.

### Execution
From the root directory of the project, run:
```bash
mvn spring-boot:run -pl examples/banking-app
```
By default, the application runs on port **8091**.

### Testing the Integration
Once the application is running, trigger transactional events via HTTP requests:

```bash
# Trigger a secure login event
curl -X POST http://localhost:8091/banking/login \
  -H "Content-Type: application/json" \
  -d '{"accountId":"act-10123","device":"iPhone 15","ipAddress":"192.168.1.5"}'

# Submit a transaction (which generates events and triggers rules)
curl -X POST http://localhost:8091/banking/transaction \
  -H "Content-Type: application/json" \
  -d '{"accountId":"act-10123","amount":750.0,"targetAccount":"act-99220","description":"Monthly Rent Payment"}'
```
These events are intercepted by the client SDK and batched/pushed to the central control plane (`access` module) at port `8080` for processing.
