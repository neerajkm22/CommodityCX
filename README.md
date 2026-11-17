# CommodityCX — Distributed Commodities OTC Trading Foundation

A starter foundation for a multi-country commodities electronic trading platform supporting bilateral OTC RFQ/negotiation and a centralized order-book workflow.

## Technology baseline
- UI: Angular 22
- Services: Java 25 / Spring Boot 3.5
- Data: PostgreSQL
- Messaging/cache: Kafka and Redis
- Target deployment: AWS (EKS templates are illustrative)

## Local infrastructure
Prerequisites: Docker Compose, Node.js compatible with Angular 22, and Java 25.

```bash
docker compose up -d
```

Backend:
```bash
cd services/trade-core
mvn spring-boot:run
```

Frontend:
```bash
cd frontend/trading-terminal
npm install
npm start
```

The API listens on port 8080; the Angular dev server uses port 4200.

## Scope and limitations
This repository is an architectural starter, not a production trading venue. The order endpoint persists submitted orders; it does **not** implement a production-grade matching engine. RFQ negotiation, authentication/OIDC, authorization, KYC/AML, credit/risk controls, settlement, market surveillance, regulatory reporting, country-specific controls, disaster recovery and production observability require implementation and independent validation.

Never connect this starter to live markets or real customer funds without completing the security, legal, operational, resilience, performance and market-integrity workstreams. Review [architecture](docs/architecture.md), [security/regulatory gates](docs/security-and-regulatory-gates.md) and [runbook](docs/runbook.md) before extending it.
