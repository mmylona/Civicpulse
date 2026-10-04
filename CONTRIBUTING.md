# Contributing

## Workflow

- One branch per change (`feat/...`, `fix/...`, `docs/...`).
- Small pull requests.
- [Conventional Commits](https://www.conventionalcommits.org/) for messages,
  e.g. `feat: add report lifecycle`, `fix: reject invalid transitions`.
- `main` must stay green; CI has to pass before merging.

## Checks

```bash
cd backend && mvn verify                       # needs Docker
cd frontend && npm ci && npm test && npm run build
```

## Conventions

- Backend modules live in `io.civicpulse.<module>`. A module may only use another
  module's public API and events, not its repositories. `ModularityTests`
  enforces this.
- Business rules belong in the domain layer, not in controllers.
- Schema changes go in a new Flyway migration. Don't edit merged migrations.
- UI text goes in `frontend/public/i18n/en.json`, not in templates.
- Record significant decisions as an ADR in `docs/adr/`.
