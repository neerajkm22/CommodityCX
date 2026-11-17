# Security and regulatory implementation gates

Before any production or live-market use, complete a jurisdiction-by-jurisdiction legal assessment and implement at minimum:

- OIDC/OAuth 2.1 authentication, MFA, short-lived tokens, secure session handling and service identity.
- RBAC/ABAC entitlements for participant, legal entity, desk, instrument, country and action.
- KYC/KYB, sanctions screening, AML workflows and beneficial ownership checks as applicable.
- Pre-trade credit, position, quantity, price-band, fat-finger and market-state controls.
- Immutable, tamper-evident audit trail for quote, negotiation, order, cancel, amend and execution events.
- Market-abuse surveillance, trade reporting, record retention and dispute workflows.
- Encryption in transit and at rest, managed secrets, key rotation, vulnerability management and SBOM.
- Threat modelling, penetration testing, SAST, dependency scanning, DAST and secure SDLC.
- Data residency, cross-border transfer, privacy, retention and regulatory reporting controls.
- HA/DR, tested backups, RPO/RTO, capacity testing, chaos/failover tests and operational runbooks.

Do not treat this checklist as legal advice or as evidence of compliance. Obtain qualified counsel and regulatory sign-off in every operating jurisdiction.
