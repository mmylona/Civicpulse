# Contributing

## Workflow

1. Create a branch per change: `feat/…`, `fix/…`, `test/…`, `docs/…`, `ci/…`.
2. Keep pull requests small and focused on one slice of behaviour.
3. Use [Conventional Commits](https://www.conventionalcommits.org/):
   `feat: implement report lifecycle`, `fix: prevent invalid report transitions`,
   `test: add lifecycle integration tests`, `docs: add ADR-004`.
4. `main` is always green: every check in CI must pass before merging.

## Running checks locally

```bash
# Backend: unit tests, then integration tests (needs Docker for Testcontainers)
cd backend && mvn verify

# Frontend: unit tests and production build
cd frontend && npm ci && npm test && npm run build
```

## Code conventions

- **Backend modules** live in `io.civicpulse.<module>`. Other modules may use only
  a module's public API package and its events, never its repositories.
  `ModularityTests` fails the build when a boundary is crossed.
- **Business rules** go in the domain layer. Controllers translate HTTP and nothing else.
- **Schema changes** are Flyway migrations only. Never edit a migration that has
  been merged; add a new one.
- **User-facing text** in the frontend goes through translation keys in
  `src/assets/i18n/en.json`, never as literals in templates.
- **Tests** cover behaviour that matters (rules, boundaries, failure paths), not
  coverage numbers.

## Architecture decisions

Significant decisions get an ADR in `docs/adr/` using `000-template.md`,
written when the decision is made.
