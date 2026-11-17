# CommodityCX implementation status

## Development workflows implemented
- Participant registration, approval, RBAC, local JWT, BCrypt, refresh rotation/revocation, access-token revocation and TOTP MFA.
- Optional OIDC JWT issuer/audience validation with verified-email mapping to locally provisioned users and database RBAC.
- Instrument reference data; OTC RFQ, quote and offer/counter/accept/reject/withdraw negotiation history.
- Transactional price-time matching with self-trade prevention, tick/quantity/notional checks, idempotency keys and per-instrument PostgreSQL advisory locks.
- Transactional audit-to-outbox writes and configurable Kafka publisher.
- Bilateral trade confirmation; settlement instructions and balanced double-entry internal ledger journal.
- Scheduled starter surveillance rules, alert workflow and stored trade-activity CSV/JSON reporting.
- Angular 22 sign-in, instrument list, order entry, order book, RFQ creation and seller quote submission.
- Unit and PostgreSQL/Testcontainers integration tests; CI workflow for backend and frontend builds.

## Operational caveats and outstanding production gates
- Build/CI has not yet been executed from this environment. Verify GitHub Actions before merge.
- OIDC interactive authorization-code + PKCE is expected from an approved identity provider/client; this API validates issuer JWTs only. Validate claim mapping, audience, key rotation and logout semantics with the selected IdP.
- MFA needs a strong external `MFA_ENCRYPTION_KEY`; rate limiting, recovery codes, device lifecycle and help-desk controls remain required.
- Kafka publisher is at-least-once; consumer idempotency, schema registry, TLS/SASL, topic ACLs, retention, DLQ and operational replay runbooks remain.
- Matching coordinates replicas through one PostgreSQL primary only. It is not multi-primary or globally distributed consensus.
- Settlement is an internal double-entry ledger/instruction baseline only; external custodian, clearing, banking, commodity title and reconciliation adapters are not included.
- Surveillance rules are illustrative and require model validation, tuning, human case management, evidence retention and regulatory/legal approval.
- Regulatory CSV is generic trade activity, not a jurisdiction-specific filing format.
- Add load, soak, failover, chaos, penetration, DAST, dependency, threat-model and concurrency stress tests; independent audit and legal approval are mandatory before live use.
