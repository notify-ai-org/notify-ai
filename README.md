<div align="center">
  <a href="https://www.freepik.com/free-photos-vectors/notification-symbols">
    <img src="https://cdn-icons-png.flaticon.com/512/3119/3119338.png" width="80" alt="Notification Symbol">
  </a>
  <h1>Notify</h1>
  <p><b>Agentic Notification & Event Orchestration System</b></p>
</div>

---

**Notify** is an enterprise-grade, multi-module intelligent notification orchestration platform built with **Spring Boot**, **RxJava**, and **Google ADK (GenAI)**. 

Unlike traditional notification services that simply dispatch static text, Notify **understands your user's context**. Designed for seamless integration into existing businesses (e-commerce, banking, healthcare), it securely intercepts domain events from your upstream applications. Utilizing advanced LLM orchestration, it intelligently extracts underlying facts over time, builds a comprehensive memory graph of user behavior, dynamically generates highly contextual message templates, and reliably dispatches them across multiple out-bound channels (Email, SMS, Push) when the user is most receptive.

## 🏗️ Architecture & Project Structure

This repository is built as a multi-module Maven project to enforce clear separation of concerns:

- **acp-server (Agent Control Plane)**: The "brain" of the operation. Orchestrates GenAI agents (using Google ADK) to process incoming events, extract actionable facts, generate targeted schedules, and formulate intelligent notification templates.
- **engine**: The robust delivery mechanism. Handles the reliable execution of notification jobs, interacts with external channel providers, and manages failures via an advanced Dead Letter Queue (DLQ) mechanism with manual/automated replay capabilities.
- **client**: A lightweight Java SDK meant to be embedded directly into your source applications. It uses Aspect-Oriented Programming (AOP) to intercept method executions, securely packages them as semantic events, and pushes them to the `acp-server`.
- **annotations**: Custom diagnostic annotations (`@Event`, `@Vocabulary`, `@Rule`, etc.) used seamlessly by the client SDK to strictly define event schemas and vocabulary rules with minimal boilerplate.
- **api**: A shared module containing core data models (Entities, DTOs), JPA repositories, and common event interfaces used across both the Engine and ACP Server.
- **examples**: Fully functional sample applications (e.g., `ecommerce-app`, `banking-app`) demonstrating how effortlessly you can integrate the client SDK into existing architectures.

## 🚀 Key Technologies

- **Java 17** & **Spring Boot 3.2.x**: Providing a solid, battle-tested standard application foundation.
- **Google ADK (GenAI)**: Agentic framework ensuring dynamic, intelligent template and contextual content generation.
- **RxJava 3**: Ensures robust, non-blocking reactive event pipelines inside the internal orchestrator.
- **PostgreSQL & Redis**: Reliable relational data persistence and sub-millisecond state caching/session management.
- **Kafka**: Facilitating asynchronous scheduled and deferred event streaming between the Agent Control Plane and the Execution Engine.

## 🛠️ Building the Project

From the root directory, build all modules and run tests easily via Maven:

```bash
# Clean, compile, and install all submodules locally
mvn clean install
```
