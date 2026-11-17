# CommodityCX

CommodityCX is a development foundation for a commodities OTC electronic trading platform.

## Stack
- Angular 22 trading terminal
- Java 25 / Spring Boot 3.5 trade-core
- PostgreSQL 17 with Flyway migrations
- Kafka transactional outbox publisher (at-least-once delivery)
- Redis available in local infrastructure for future cache/session integrations

## Run locally
1. Copy `.env.example` to `.env`. Replace every example password/key with unique random secrets. Set `BOOTSTRAP_ADMIN_EMAIL` and `BOOTSTRAP_ADMIN_PASSWORD`.
2. Start PostgreSQL, Kafka and the API: `docker compose up --build -d`.
3. Start Angular: `cd frontend/trading-terminal && npm install && npm start`.
4. Open `http://localhost:4200` and sign in using the bootstrap admin. New participant registrations require ADMIN/COMPLIANCE activation.

The API listens on `http://localhost:8080`. Flyway creates and upgrades the database on API startup. Kafka is available to the host on `localhost:9092` and to the API container on `kafka:9092`.

## Test
- Backend: `mvn -f services/trade-core/pom.xml verify` (Docker is required for PostgreSQL/Testcontainers integration tests).
- Frontend: `cd frontend/trading-terminal && npm install && npm run build`.
- GitHub Actions runs backend verification, Angular production build and CodeQL analysis on pull requests.

## Workflow documentation
- [Core API](docs/api/core-workflows.md)
- [Advanced API](docs/api/advanced-workflows.md)
- [Implementation status and production caveats](docs/implementation-status.md)
- [Security and regulatory gates](docs/security-and-regulatory-gates.md)

## Safety
This is a development/demo baseline. It is not a certified exchange, broker, clearing system or regulated trading venue. Do not connect it to live markets, customer funds or production personal data before independent security, operational, market-integrity and legal reviews.
