# CommodityCX — Distributed Commodities OTC Trading Foundation

A starter foundation for a multi-country commodities electronic trading platform supporting bilateral OTC RFQ/negotiation and a centralized order-book workflow.

## Technology baseline
- UI: Angular 22
- Services: Java 25 / Spring Boot 3.5
- Data: PostgreSQL schema and seed data
- Messaging/cache: Kafka and Redis for local infrastructure
- Target deployment: AWS; Kubernetes manifests are illustrative EKS starting points

## Repository layout
- `frontend/trading-terminal`: Angular trading workspace
- `services/trade-core`: Spring Boot API scaffold
- `database/init`: PostgreSQL schema and seed data
- `infra/k8s`: Kubernetes deployment templates
- `docs`: architecture, security/regulatory gates and runbook

## Local infrastructure
Prerequisites: Docker Compose, Java 25, Maven and a Node.js release supported by Angular 22.

```bash
docker compose up -d
```

Start the API:
```bash
cd services/trade-core
mvn spring-boot:run
```

Start the UI in another terminal:
```bash
cd frontend/trading-terminal
npm install
npm start
```

The API listens on port 8080 and the Angular development server on port 4200. See [docs/runbook.md](docs/runbook.md).

## Current implementation boundary
This repository is a development foundation, not a production trading venue. The current order endpoint validates a small demo payload and returns a receipt; it does **not** persist, match, execute or settle orders. The UI is a starter trading terminal, not a complete market data or execution interface. The RFQ workflow is not implemented.

Authentication/OIDC, MFA, authorization, participant onboarding, KYC/AML, credit and pre-trade risk controls, production-grade matching, settlement, market surveillance, regulatory reporting, jurisdiction-specific data residency, HA/DR and production observability remain implementation and validation work.

Do not connect this starter to live markets, real participants or customer funds until security, legal, operational, resilience, performance and market-integrity reviews are complete. Obtain qualified legal and regulatory advice in every operating jurisdiction. Sample credentials and single-node local infrastructure must never be reused in production.


## Core trading workflows
The backend now includes a first implementation of participant registration/approval, JWT authentication and role-based access, instrument reference data, OTC RFQ/quote acceptance, transactional price-time order matching, bilateral trade confirmation, participant risk limits and audit events.

See [core workflow API](docs/api/core-workflows.md) and [implementation status](docs/implementation-status.md). Configure `BOOTSTRAP_ADMIN_EMAIL`, `BOOTSTRAP_ADMIN_PASSWORD` (16+ characters), and a strong `JWT_SECRET` before first startup. Flyway owns the backend schema; the legacy Docker init SQL is intentionally disabled to avoid creating incompatible duplicate tables.

This is a development milestone, not production certification. Review the limitations in the implementation status and API documentation before any live-market use.
