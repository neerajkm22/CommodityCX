# Advanced CommodityCX workflows

Base path: `/api/v1`. Send `Authorization: Bearer <accessToken>` on protected endpoints.

## Authentication, MFA and OIDC
- `POST /auth/login` accepts email, password and (when MFA is enabled) a six-digit `mfaCode`.
- `POST /auth/refresh` accepts a refresh token and rotates it. Access JWTs last 10 minutes by default; refresh tokens last seven days.
- `POST /auth/logout` revokes the current access token and supplied refresh token.
- `POST /auth/mfa/setup` returns a one-time TOTP secret and otpauth URI; add it to an authenticator.
- `POST /auth/mfa/enable` and `POST /auth/mfa/disable` require a valid TOTP code.
- Set `MFA_ENCRYPTION_KEY` to a long random secret before allowing MFA setup. Secrets are encrypted with AES-GCM; keep this key in a managed secret store and back it up securely.
- Optional OIDC JWT validation is enabled by `OIDC_ISSUER_URI` and `OIDC_AUDIENCE`. Tokens must have the configured issuer/audience and a verified `email` claim. That email maps to a pre-provisioned, enabled local user; roles and participant membership are loaded from PostgreSQL. The identity provider/client performs interactive authorization-code + PKCE.

## RFQ negotiation
- `POST /rfqs/{rfqId}/negotiations`: `{ "type":"OFFER|COUNTER", "price": 75.25, "quantity": 100, "currency":"USD", "note":"..." }`
- `POST /rfqs/{rfqId}/negotiations` with `{ "type":"ACCEPT" }` accepts the latest outstanding offer from the other counterparty and creates a trade in PENDING_CONFIRMATION.
- `type=REJECT` rejects the RFQ; `type=WITHDRAW` lets the RFQ initiator withdraw.
- `GET /rfqs/{rfqId}/negotiations` returns the negotiation message history.
- Existing quote workflow remains available through `POST /rfqs/{rfqId}/quotes` and `POST /quotes/{quoteId}/accept`.

## Idempotency and event publication
- `POST /orders` accepts `Idempotency-Key` (maximum 120 chars). Repeating the same key and payload replays the first response; reusing a key with a different payload returns HTTP 409.
- Domain audit actions append to the PostgreSQL `event_outbox` in the same database transaction.
- Enable the publisher with `EVENTS_ENABLED=true`. It publishes pending events to `EVENTS_TOPIC` and marks them published after broker acknowledgement. Consumers must still deduplicate because delivery is at-least-once (a crash after broker ack but before DB commit can replay an event).

## Distributed matching and recovery
- The matching critical section takes a PostgreSQL transaction-scoped advisory lock derived from instrument UUID. All application replicas sharing the same PostgreSQL primary serialize matching for the same instrument while different instruments can proceed concurrently.
- The `matching_partitions` table records a fencing counter/lease diagnostic per instrument. PostgreSQL releases advisory locks automatically on transaction end or connection loss; uncommitted order/trade/outbox writes roll back together.
- This is a single-PostgreSQL-primary coordination model, not a multi-region consensus engine. Do not run independent writable database primaries for the same instrument. HA/failover and replay must be rehearsed before production.

## Settlement and clearing ledger
- `POST /trades/{tradeId}/settlement` (ADMIN/RISK_OFFICER/COMPLIANCE) creates a settlement instruction only after both counterparties confirm.
- It posts balanced double-entry journal lines for buyer, seller and clearing control accounts.
- `POST /settlements/{id}/complete` records an external settlement reference after confirmation from the actual custodian/clearing rail.
- The current integration is an internal ledger and instruction stub; it does not move cash, commodities, title or custody assets and is not a clearing-house integration.

## Surveillance and regulatory reports
- `GET /admin/surveillance/alerts` lists alerts. The scheduled starter rules detect rapid cancellation and concentrated buyer/counterparty activity.
- `POST /admin/surveillance/alerts/{id}/resolve` records an alert disposition.
- `POST /admin/reports/trades` creates a stored trade-activity report for `from` and `to` ISO dates.
- `GET /admin/reports/trades.csv?from=YYYY-MM-DD&to=YYYY-MM-DD` downloads trade-activity CSV.
- These are sample surveillance indicators and a generic trade extract, not a jurisdiction-specific regulatory reporting schema or evidence of compliance.

## Frontend
Angular terminal: `cd frontend/trading-terminal && npm install && npm start`. Dev server proxies `/api` to Spring Boot at `localhost:8080`. Sign in using an approved participant. Register/activate users using the API/admin bootstrap flow.
