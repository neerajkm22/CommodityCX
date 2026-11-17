# Core trading milestone — implementation status

## Implemented in this milestone
- BCrypt password storage, JWT bearer authentication and stateless Spring Security.
- Participant self-registration with pending approval, compliance activation and role assignment.
- Commodity instrument reference-data creation and listing, including tick/minimum quantity validation.
- OTC RFQ creation, seller quote submission and buyer quote acceptance into a trade.
- Limit/market order intake, participant quantity/notional checks, self-trade prevention, price-time matching, IOC cancellation and aggregated order-book depth.
- Bilateral trade confirmation, risk-limit administration and audit event recording.
- PostgreSQL Flyway migration and API workflow documentation.

## Deliberately not represented as complete
MFA/OIDC federation, KYC/AML screening, maker-checker approval, refresh-token rotation/revocation, idempotency, counter-offer negotiation, distributed matching across partitions, outbox/event replay, settlement, clearing, surveillance, regulatory reporting and production resilience/performance certification.
