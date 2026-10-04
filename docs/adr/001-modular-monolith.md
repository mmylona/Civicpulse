# ADR 001: Modular monolith

Status: accepted
Date: 2026-10-03

## Context

The system has about ten areas of responsibility (identity, reports, storage,
geo, classification, duplicates, prioritization, notifications, analytics,
audit). Some changes have to touch several of them in one transaction, e.g. a
status change writes the report, its timeline entry and an outgoing event.

It is built by one developer and serves one city, so there is no need to scale
or deploy parts independently. The main risk of a monolith is that modules slowly
start using each other's internals.

## Decision

- One Spring Boot application and one PostgreSQL database.
- Each module is a top-level package `io.civicpulse.<module>`. Other modules may
  use its public API and events only.
- Boundaries are checked by `ModularityTests` (Spring Modulith), so a violation
  fails the build.
- `shared` is an open module with the error model, request id handling and
  clock. It does not depend on other modules.
- Side effects between modules go through events stored in the same transaction
  as the change (outbox).

## Alternatives

- Microservices: independent deployment we don't need, at the cost of
  distributed transactions and a much heavier local setup.
- Layered packages (`controllers/`, `services/`, `repositories/`): nothing stops
  any service from using any repository.
- One Maven module per domain module: stronger compile-time boundaries, but more
  build setup for each module. Can be revisited if the test-based check is not
  enough.

## Consequences

- Single deployment, simple local setup.
- Module rules are explicit and enforced in CI.
- A module could be extracted later, since it is only coupled through its API
  and events.
- Revisit if a module needs to scale on its own or another team takes it over.
