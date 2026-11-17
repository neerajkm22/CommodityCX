# CommodityCX core workflow API

Base URL: `http://localhost:8080/api/v1`. Authenticated endpoints require `Authorization: Bearer <accessToken>`.

## Identity and participants
- `POST /auth/register`: create a participant and initial TRADER user in PENDING state.
- `POST /auth/login`: issue a short-lived JWT for enabled users.
- `GET /admin/participants`: ADMIN/COMPLIANCE list.
- `POST /admin/participants/{participantId}/activate`: ADMIN/COMPLIANCE activate participant and users.
- `PUT /admin/users/{userId}/roles`: ADMIN assign roles (ADMIN, COMPLIANCE, TRADER, RISK_OFFICER).

Bootstrap the first administrator only through environment variables `BOOTSTRAP_ADMIN_EMAIL` and `BOOTSTRAP_ADMIN_PASSWORD` (minimum 16 characters). Never commit credentials.

## Reference data
- `GET /instruments`
- `POST /admin/instruments` (ADMIN/COMPLIANCE). Required: symbol, name, commodityType, unit, currency, tickSize, minQuantity.

## OTC RFQ and quotes
- `POST /rfqs`: buyer participant creates RFQ. Required: instrumentId, side, quantity, currency, expiresAt (ISO-8601 offset timestamp); optional sellerParticipantId, targetPrice, deliveryLocation, deliveryDate.
- `POST /rfqs/{rfqId}/quotes`: eligible seller submits price, quantity, optional message and validUntil.
- `POST /quotes/{quoteId}/accept`: buyer accepts quote; creates a pending-confirmation trade.

## Order book and matching
- `POST /orders`: required instrumentId, side, quantity; LIMIT orders require price. Optional orderType (LIMIT/MARKET), timeInForce (GTC/IOC), riskPrice for market-order notional checks.
- Matching uses price priority then FIFO time priority; self-trading is prevented. A transaction locks the instrument row while matching.
- `GET /instruments/{instrumentId}/order-book`: aggregated visible depth.
- `DELETE /orders/{orderId}`: cancel an unfilled remainder belonging to the caller's participant.

## Trade confirmation, risk and audit
- `POST /trades/{tradeId}/confirm`: buyer and seller confirm independently; trade becomes CONFIRMED after both confirmations.
- `PUT /admin/participants/{participantId}/risk-limits`: ADMIN/RISK_OFFICER set maxOrderQuantity and maxOrderNotional.
- `GET /audit/{entityId}`: ADMIN/COMPLIANCE/RISK_OFFICER audit lookup.

## Local setup
Run `docker compose up -d postgres`, then from `services/trade-core` run `mvn spring-boot:run`. Configure a bootstrap administrator before first startup. Flyway creates the schema automatically.

## Important limitations before production
This is a functional development baseline, not a certified exchange or production-ready venue. It currently implements single-database transactional matching; distributed sequencing, idempotency keys, durable outbox/Kafka publication, quote counter-offers, partial RFQ fills, settlement/clearing, KYC/AML integrations, MFA, refresh/revocation, market surveillance, jurisdictional controls, HA/DR, load/penetration testing and independent regulatory review remain required. Configure TLS, secret manager-backed JWT keys, rate limits and operational monitoring before deployment. The sample JWT TTL response should be aligned with configured TTL when changed.
