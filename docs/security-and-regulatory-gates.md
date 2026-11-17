# Security and regulatory production gates

The repository includes development implementations of TOTP MFA, short-lived JWTs, refresh-token rotation, access-token revocation and optional OIDC issuer validation. These do not replace the following production controls:

- Configure OIDC authorization-code + PKCE in the chosen identity provider/client; validate issuer, audience, signing-key rotation, role claims, MFA policy, session logout and account lifecycle.
- Put API rate limits and brute-force/credential-stuffing protection at the edge; add MFA recovery codes, enrollment notifications and secure help-desk recovery.
- Use managed KMS/HSM-backed encryption keys and secret rotation for JWT signing, MFA secrets, DB credentials and Kafka credentials.
- Complete KYC/KYB, sanctions screening, AML workflows and beneficial ownership checks as applicable.
- Implement participant/legal entity/desk/instrument/country/action entitlements and maker-checker approvals.
- Add credit, position, price-band, fat-finger, market-state and aggregate exposure controls.
- Make audit records tamper-evident with restricted database roles, append-only retention, signed exports and immutable backups.
- Add consumer deduplication, schema registry, Kafka TLS/SASL/ACLs, DLQ, retention and event replay controls.
- Integrate real settlement, clearing, custody, banking and commodity title systems; reconcile external statements and internal ledger daily.
- Validate surveillance scenarios against market-abuse typologies and implement investigator workflow, evidence handling and escalation.
- Map trade reports to each operating jurisdiction's rules, schemas, submission channels, deadlines and record-retention mandates.
- Complete threat modelling, SAST, dependency scanning, DAST, penetration tests, SBOM, signed artifacts and incident response.
- Test HA/DR, RPO/RTO, database failover, matching recovery, Kafka replay, capacity, latency and operational runbooks.

This checklist is not legal advice or a compliance attestation. Obtain qualified counsel and regulator/venue approval for every market and jurisdiction before live operation.
