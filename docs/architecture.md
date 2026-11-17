# Architecture overview

The target platform is logically divided into:
1. Angular trading terminal for market data, order entry and RFQ workspaces.
2. API gateway / identity edge (planned: Kong or AWS API Gateway plus OIDC provider such as Keycloak or a managed IdP).
3. Trade Core for instrument, order and trade lifecycle APIs.
4. RFQ / negotiation service (planned).
5. Matching service (planned; deterministic sequencing and single-writer or partition ownership required).
6. PostgreSQL for transactional records; Kafka for durable domain events; Redis for ephemeral cache/session coordination.
7. Observability and security services (planned: OpenTelemetry, Prometheus, Grafana, centralized logs and SIEM).

## Key design principles
- Partition order flow by instrument or market and define deterministic sequencing.
- Persist authoritative state and use transactional outbox/inbox patterns for event publication.
- Make commands idempotent and protect against duplicate client submissions.
- Separate indicative quotes, firm quotes, orders, executions and confirmed trades.
- Enforce tenant, legal entity, country, instrument and participant entitlements server-side.
- Design data residency and retention per jurisdiction before selecting deployment regions.

The current code is a thin UI/API scaffold. The API order endpoint only returns a demo acknowledgement; it does not persist or execute orders.
