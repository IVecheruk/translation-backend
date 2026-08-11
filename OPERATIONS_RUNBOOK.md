# TranslateLab Java Backend — Production Operations Runbook

## Deployment boundary

`compose.yaml` is local-development only. Production uses
`compose.production.yaml` and prebuilt, reviewed images pinned by immutable
digest. Only Caddy publishes host ports 80/443. PostgreSQL, RabbitMQ, backend
application/management ports, Prometheus, and Alertmanager remain private.

Caddy terminates TLS, performs the HTTP-to-HTTPS redirect, discards untrusted
forwarding headers before creating its own `X-Forwarded-*` values, limits
request bodies to 21 MB, limits headers to 32 KB, and applies connection and
upstream timeouts. The backend trusts forwarded headers only inside the private
edge network.

## Secrets

Production must not use `.env`. Supply the files listed in
`deploy/production/secrets.example/README.md` from Docker secrets, Kubernetes,
or the approved platform secret manager. Run
`scripts/validate-production-secrets.ps1` without printing file contents before
deployment.

Rotation procedure:

1. JWT: create a new random Base64 key, choose a new key ID, replace both
   secret and `JWT_KEY_ID`, deploy, and verify that old tokens are rejected.
2. PostgreSQL: create/rotate the application role password in PostgreSQL,
   update the secret atomically, restart backend instances, then revoke the old
   credential.
3. RabbitMQ: create a replacement application user with permissions only for
   the translation vhost, update the secret, roll backend instances, verify
   confirms/listeners, then delete the old user.
4. MinIO: create a new service account with the bucket-scoped policy, update
   both backend secret files, roll and verify object operations, then revoke the
   prior account.
5. Tribute and SMTP: rotate at the provider, update the secret manager, deploy,
   perform a safe health/contract check, then revoke the prior credential.

Record owner, time, affected key ID/account, validation result, and next
rotation date outside this repository. Never log or paste secret values.

## Health and monitoring

- Liveness: `/livez` checks only Spring/JVM availability.
- Readiness: `/readyz` includes PostgreSQL, RabbitMQ, and the configured MinIO
  bucket.
- Prometheus scrapes `/actuator/prometheus` on private port 8081 and RabbitMQ's
  private Prometheus endpoint.
- Caddy denies `/actuator/*`, `/livez`, and `/readyz` from the public route.

The committed alert rules cover sustained HTTP errors, latency, queue/DLQ
buildup, exhausted outbox events, payment processing failures, storage
failures, quota rejection spikes, exhausted database pools, and low disk
space. Alertmanager configuration is a secret because notification endpoints
may contain credentials. Every production deployment must route critical
alerts to an owned on-call destination and test delivery.

Structured ECS logs go only to stdout. Docker rotates local log files at 10 MB
with five retained files. The central log platform must enforce access control
and the approved retention period. Authorization headers remain redacted;
webhook bodies, signatures, provider credentials, and secret-bearing checkout
URLs must never be logged. HTTP and RabbitMQ flows carry a bounded correlation
identifier in MDC.

## Backups and recovery

Target objectives:

- PostgreSQL/payment/outbox RPO: at most 15 minutes; RTO: at most 2 hours.
- MinIO document RPO: at most 1 hour; RTO: at most 4 hours.
- Configuration/secret recovery RPO: latest approved deployment; RTO: 1 hour.

Use continuous encrypted PostgreSQL WAL archiving plus a daily full backup.
Enable versioning/replication or provider snapshots for the production MinIO
bucket. `scripts/backup-production.sh` creates an additional application-level
PostgreSQL dump and MinIO mirror, writes SHA-256 manifests, and encrypts the
entire artifact to an age recipient. Store it in an immutable backup repository
separate from the application account. Recommended retention is 7 daily,
5 weekly, and 12 monthly recovery points; legal/payment retention requirements
may require longer database archives.

At least monthly, restore the newest artifact into an isolated project and a
new bucket whose name ends in `-restore-drill`. Never restore a drill over the
production database or bucket. Verify:

1. `age` decrypts the archive and every `manifest.sha256` entry matches.
2. `pg_restore --list` succeeds, followed by restore into a newly created
   isolated PostgreSQL database.
3. Flyway validation and the backend readiness probe pass against the restored
   database.
4. A sample of MinIO objects matches the manifest and can be downloaded through
   the Java service account.
5. Purchase intents, processed payment events, subscriptions, usage records,
   jobs, and outbox rows satisfy their uniqueness/idempotency constraints.
6. Pending outbox rows are republished normally. RabbitMQ queues are not copied
   blindly from an old backup: PostgreSQL outbox is the authority for pending
   translation publication, and payment event IDs remain the authority for
   payment deduplication.

Record the measured RPO/RTO, artifact ID, sample counts, failures, owner, and
date. A release is blocked if no successful drill exists within the required
period.

## Production verification

1. Run the full Maven test suite on Java 21.
2. Build, scan, and publish the backend image by immutable digest.
3. Run `scripts/validate-production-compose.ps1` with the non-secret production
   variables supplied by the platform.
4. Validate Caddy and Prometheus configuration with their exact release images.
5. Start the stack and verify `/livez`, `/readyz`, metrics scraping, alert
   delivery, upload/download/delete, RabbitMQ confirms, and a non-financial
   provider sandbox flow.
6. Verify that only ports 80/443 are published and operational endpoints are
   unreachable through the public domain.
7. Confirm a current encrypted backup and successful restore drill before
   enabling traffic.
