# Local development runbook

1. Copy `.env.example` to `.env` and replace local-only credentials.
2. Start dependencies: `docker compose up -d`.
3. Start API: `cd services/trade-core && mvn spring-boot:run`.
4. Start UI: `cd frontend/trading-terminal && npm install && npm start`.
5. Open `http://localhost:4200`; API health: `http://localhost:8080/api/v1/health`.

## Troubleshooting
- PostgreSQL: `docker compose logs postgres`; verify port 5432 is free.
- API: verify Java/Maven versions and database credentials.
- UI: verify Node/npm compatibility with the installed Angular CLI and that the API is reachable.

## Production warning
Compose defaults are for local development only. Do not reuse sample credentials, plaintext local broker settings, single-node Kafka or this demo API in production.
