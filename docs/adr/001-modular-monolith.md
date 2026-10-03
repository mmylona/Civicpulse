# 001 — Build CivicPulse as a modular monolith

- **Status:** accepted
- **Date:** 2026-10-03

## Context

CivicPulse has ten areas of responsibility (identity, reports, storage, geo,
classification, duplicates, prioritization, notifications, analytics, audit).
Several of them must change data consistently in one step: changing a report's
status writes the report, its timeline and an outgoing event together.

The team is one developer. Expected load is one city: thousands of open reports,
not millions of requests per second. There is no independent scaling need and
no second team that needs to deploy separately.

The risk of a monolith is erosion: modules quietly reaching into each other's
tables until nothing can be changed safely.

## Decision

- Ship **one Spring Boot deployable** with one PostgreSQL database.
- Each module is a top-level package `io.civicpulse.<module>`. Other modules may
  use only its published API types and its domain events, never its repositories
  or internal classes.
- Boundaries are **verified by a test** (`ModularityTests`, Spring Modulith's
  `ApplicationModules.verify()`), so a violation fails the build rather than a
  code review.
- `shared` is an open module (the shared kernel): error model, request id,
  clock, paging. It must not depend on any other module.
- Cross-module side effects travel as domain events persisted in the same
  transaction (transactional outbox, see ADR-006 when written).

## Alternatives

- **Microservices.** Independent deployment and scaling we do not need, paid for
  with distributed transactions, network failure handling, and a much heavier
  local and CI setup. Rejected.
- **Classic layered monolith** (`controllers/`, `services/`, `repositories/` at the
  top). Simple at first, but nothing stops any service calling any repository.
  Rejected.
- **Maven multi-module build** (one Maven module per domain module). Gives
  compile-time boundaries, but adds build ceremony for every new module and
  makes refactoring module boundaries slow. Kept as an option if the test-based
  verification proves too weak.

## Consequences

- One deployment, one transaction manager, trivial local setup (`docker compose up`).
- Boundary rules are explicit and enforced; the module structure can be rendered
  as documentation by Spring Modulith.
- A module could later be extracted into a service because its only coupling is
  its API package and its events.
- **Revisit when:** a module needs to scale or deploy independently, or a second
  team owns part of the system.
