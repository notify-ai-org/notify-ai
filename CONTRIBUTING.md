# Contributing to Notify.ai

Thanks for helping improve Notify.ai. This repository contains a multi-module
Java backend, a React/Vite admin UI, and example SDK integrations, so small,
well-scoped changes are easiest to review.

## Project Layout

- `access` - Spring Boot web API and static portal host.
- `acp-server` - agent orchestration and event processing.
- `engine` - notification scheduling, dispatch, and DLQ handling.
- `api`, `common`, `annotations`, `client` - shared models, utilities,
  annotations, and Java SDK code.
- `notify-ui` - React microfrontend portals.
- `examples` - sample client applications.
- `adk-java` - Google ADK Java code. Follow `adk-java/CONTRIBUTING.md` for
  changes scoped to that module.

## Development Setup

Build the Java modules from the repository root:

```bash
mvn clean install
```

Run the main backend:

```bash
mvn spring-boot:run -pl access
```

Build all UI portals and copy them into Spring Boot static resources:

```bash
./notify-ui/build-all.sh
```

For frontend development, use the dev shell:

```bash
cd notify-ui/dev
npm install
npm run dev
```

## Validation

Before opening a pull request, run the narrowest checks that cover your change.

For backend changes:

```bash
mvn -pl <module> -am test
```

For compile-only verification:

```bash
mvn -pl <module> -am -DskipTests compile
```

For UI portal changes:

```bash
cd notify-ui/<portal>
npm run build
npx tsc --noEmit
```

For shared UI changes, also build `notify-ui/shared` and any portals importing
the changed shared component.

## Coding Guidelines

- Keep changes focused on the requested behavior.
- Follow existing module boundaries and local code style.
- Prefer typed models and structured parsing over ad hoc string handling.
- Add tests when touching shared logic, orchestration behavior, persistence, or
  user-facing workflows.
- Do not commit generated build output unless the repository already serves it
  as a checked-in artifact, such as built portal assets under `access`.
- Do not include secrets, API keys, database passwords, or local `.env` files.

## Pull Requests

Please include:

- What changed.
- Why the change is needed.
- How it was validated.
- Any migrations, deployment steps, or operational notes.

If your change affects Docker, EC2 deployment, database schema, Quartz jobs,
agent orchestration, or notification dispatch, call that out clearly in the PR
description.
