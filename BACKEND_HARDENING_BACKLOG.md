# TranslateLab Java Backend — Security and Reliability Backlog

Last updated: 2026-08-10

## Purpose

This document is the ordered hardening backlog for the Java backend module.
It is based on a read-only audit of the current source code, database
migrations, tests, Docker image, and local Compose environment.

The scope is limited to the Java backend. Frontend rendering rules and the
internal implementation of the Python ML service remain outside this module,
but the contracts and trust boundaries between those modules are included.

No real credentials, webhook secrets, access tokens, or private provider data
must ever be added to this document.

## Status legend

- `[ ]` — not started.
- `[-]` — in progress.
- `[x]` — completed and verified.
- `P0` — blocks a safe production release.
- `P1` — important for reliability, security, or financial correctness.
- `P2` — operational hardening or maintainability improvement.

## Verified audit baseline

- The application compiles and starts with the local infrastructure.
- Docker Compose configuration is valid and all four local services report
  `healthy`.
- The unmodified `./mvnw test` equivalent exhausted the local PostgreSQL
  connection limit because many cached Spring contexts each created a pool.
- With the Hikari maximum pool size temporarily limited to two, all 1370 tests
  passed with zero failures, errors, or skipped tests.
- Docker Scout found zero critical and two high vulnerabilities in the current
  `translation-backend:local` image:
  - `io.netty:netty-codec-compression:4.2.15.Final`, CVE-2026-59901, fixed in
    `4.2.16.Final`;
  - `org.postgresql:postgresql:42.7.11`, CVE-2026-54291, fixed in `42.7.12`.
- The pgJDBC issue affects connections configured with
  `channelBinding=require`; the current JDBC URL does not enable that mode,
  but the dependency must still be upgraded.

## Required implementation order

Work through the stages in this order. Inside a stage, complete one focused
task at a time and run its focused tests before the complete test suite.

## Stage 0 — Stable verification and vulnerable dependencies

### TST-001 — Stabilize integration-test database connections — P0 — DONE

- [x] Add test-only datasource pool settings so the documented
  `./mvnw test` command succeeds against the local PostgreSQL container without
  additional command-line properties.
- [x] Keep the production datasource pool independent from test settings.
- [x] Prefer one shared test profile and reusable Spring contexts; avoid
  unnecessary context variations.
- [x] Confirm that the complete suite passes twice consecutively without
  `too many clients already`.

Definition of done:

- Plain `./mvnw test` passes.
- PostgreSQL connections return to the expected baseline after the test JVM
  exits.
- The test-only pool limit is documented.

### DEP-001 — Upgrade pgJDBC — P0 — DONE

- [x] Move pgJDBC from `42.7.11` to a non-vulnerable compatible release,
  at least `42.7.12`.
- [x] Verify Flyway, JPA integration tests, pessimistic locks, and the full
  Spring context.
- [x] Re-scan the rebuilt image and confirm CVE-2026-54291 is absent.

### DEP-002 — Upgrade the vulnerable Netty component — P0 — DONE

- [x] Resolve `netty-codec-compression` to at least `4.2.16.Final` through a
  compatible Spring Boot/RabbitMQ client update or a carefully managed BOM
  override.
- [x] Do not override a single Netty module to a version inconsistent with the
  rest of the Netty BOM.
- [x] Verify RabbitMQ publishing, consumption, topology, and integration tests.
- [x] Re-scan the rebuilt image and confirm CVE-2026-59901 is absent.

### DEP-003 — Add repeatable dependency scanning — P1 — DONE

- [x] Add a repeatable Maven or image vulnerability scan to the release
  checklist or automated pipeline.
- [x] Fail a production release on known critical vulnerabilities and require
  an explicit review for high vulnerabilities.
- [x] Produce an SBOM for release images.

## Stage 1 — Safe document ingestion

### FILE-001 — Introduce a configurable document-size limit — P0

- [x] Add a validated document-upload properties record with a positive maximum
  size and a conservative default agreed with the ML-service owner.
- [x] Configure matching Spring multipart request and file limits.
- [x] Reject oversized documents before quota reservation, MinIO upload,
  database persistence, and RabbitMQ publication.
- [x] Map Spring's multipart-size exception to the standard `ApiError`, using
  HTTP `413 Payload Too Large`.
- [x] Add boundary tests for exactly-at-limit and over-limit files.

### FILE-002 — Validate document content instead of trusting the filename — P0

- [x] Keep the extension check, but also inspect the binary signature and
  container structure.
- [x] Verify PDF by its signature and basic parseability.
- [x] Verify DOCX as a valid ZIP/OOXML package with the expected entries and
  content types.
- [x] Define a safe validation strategy for legacy binary DOC; if reliable
  validation is not available, temporarily remove DOC support rather than
  accepting arbitrary bytes.
- [x] Treat the client `Content-Type` as untrusted metadata.
- [x] Add tests for renamed executables, truncated files, extension/content
  mismatches, empty containers, and valid examples of every supported format.

### FILE-003 — Protect DOCX processing from archive bombs — P0

- [x] Enforce limits for ZIP entry count, individual uncompressed entry size,
  total uncompressed size, compression ratio, and nested archives.
- [x] Document equivalent limits in the Java-to-ML contract so Java and ML
  enforce compatible boundaries.
- [x] Reject suspicious archives before MinIO persistence and task publication.
- [x] Add small synthetic archive-bomb tests that do not consume excessive test
  resources.

### FILE-004 — Normalize trusted metadata written to MinIO — P1

- [x] Derive stored MIME type from the verified `FileFormat`, not from
  `MultipartFile.getContentType()`.
- [x] Keep generated internal object keys independent of the original filename.
- [x] Add metadata assertions to MinIO integration tests.

### FILE-005 — Add source/result retention and cleanup — P1

- [x] Decide retention periods for source documents, translated results, failed
  jobs, and abandoned uploads.
- [x] Add a bounded, idempotent cleanup process.
- [x] Ensure cleanup never removes an object referenced by an active job.
- [x] Record cleanup metrics and failures without exposing object keys to API
  clients.

## Stage 2 — Secure the ML-to-Java trust boundary

### MSG-001 — Bind result object keys to the expected job and owner — P0

- [x] Stop accepting an arbitrary MinIO key as authoritative merely because it
  arrived from the status queue.
- [x] Prefer Java generating and persisting the expected result key before task
  publication, then send that key to ML.
- [x] If ML must generate it, validate a strict namespace containing the exact
  user and job identifiers and reject traversal-like or unrelated keys.
- [x] Verify the result object exists and matches the expected format before
  changing a job to `DONE`.
- [x] Add tests proving that a job cannot be linked to another user's source,
  result, or avatar object.

### MSG-002 — Bound and separate ML error details — P1

- [x] Limit the length of `error_message` at the DTO, domain, and database
  boundaries.
- [x] Expose only a safe user-facing error code/message through status and
  history APIs.
- [x] Keep diagnostic detail in controlled logs or a separate internal field.
- [x] Ensure frontend-visible data cannot contain stack traces, credentials, or
  internal storage locations.

### MSG-003 — Make translation status updates concurrency-safe — P0

- [x] Add pessimistic locking or optimistic `@Version` handling for
  `TranslationJob` status updates.
- [x] Define behavior for duplicate, late, and out-of-order events across
  multiple backend instances.
- [x] Keep terminal transitions idempotent.
- [x] Add two-transaction integration tests for concurrent progress, DONE, and
  FAILED events.

## Stage 3 — Reliable RabbitMQ delivery

### RMQ-001 — Add publisher confirms and mandatory returns — P0

- [x] Enable correlated publisher confirms.
- [x] Publish translation tasks as mandatory and handle unroutable returns.
- [x] Consider a task accepted only after a positive broker confirmation and
  successful routing.
- [x] Apply bounded timeouts and return a safe `503` when acceptance cannot be
  established.
- [x] Add tests for ACK, NACK, timeout, missing exchange, and unroutable routing
  key scenarios.

### RMQ-002 — Add dead-letter queues and bounded retry policy — P0

- [x] Configure DLQ/DLX handling for task and status queues.
- [x] Send malformed or permanently invalid status messages to quarantine
  instead of silently discarding them.
- [x] Retry transient infrastructure failures with bounded attempts and
  backoff; prevent a hot infinite redelivery loop.
- [x] Add operational visibility for DLQ depth and retry exhaustion.
- [x] Prefer RabbitMQ policies for mutable DLX configuration where practical.

### RMQ-003 — Add an outbox for translation task creation — P0

- [x] Persist the translation job, quota state, and an outbox event in one
  PostgreSQL transaction.
- [x] Publish pending outbox events asynchronously with publisher confirms.
- [x] Mark an outbox event delivered only after confirmed broker acceptance.
- [x] Make task consumption idempotent by stable job/event identifier.
- [x] Remove the current gap in which the broker accepts a task but quota
  consumption fails afterward.
- [x] Add crash-point integration tests before and after every transaction and
  publish boundary.

### RMQ-004 — Define queue capacity and consumer behavior — P1

- [x] Set and document prefetch, consumer concurrency, maximum message size,
  queue length/overflow policy, and delivery persistence.
- [x] Decide whether classic durable or quorum queues are required for the
  production topology.
- [x] Load-test slow ML processing and backend restarts.

Stage 3 decision: the local single-node topology uses versioned durable classic
queues. Mutable production DLX/limit changes should be applied through RabbitMQ
policies where the deployment platform manages them; the self-contained Compose
topology keeps safe queue arguments in application declarations. A future
replicated production topology must migrate to newly named quorum queues rather
than redeclare an existing classic queue in place. The task contract is
at-least-once and requires ML-side deduplication by stable `event_id`.

## Stage 4 — Financial and subscription correctness

### PAY-001 — Prevent an unsafe second purchase — P0

- [x] Lock the user or current subscription during purchase-intent creation.
- [x] Reject, reuse, or explicitly treat as an upgrade a checkout request when
  the user already has an effective paid subscription.
- [x] Prevent multiple live pending intents for the same user/offer unless the
  product explicitly requires them.
- [x] Return a deterministic conflict response before contacting the provider.
- [x] Add concurrent checkout tests and a paid-user regression test.

### PAY-002 — Reconcile expired `ACTIVE` rows — P0

- [x] Ensure a subscription whose paid period ended cannot remain permanently
  `ACTIVE` and block a new purchase through the partial unique index.
- [x] Process provider expiration events and add a local reconciliation job as
  a safety net.
- [x] Make expiration idempotent and concurrency-safe.
- [x] Test a missed webhook followed by reconciliation and a new purchase.

### PAY-003 — Verify paid amount, currency, product, and period — P0

- [x] Include provider-reported amount, currency, product/offer reference, and
  billing period in the internal completion command.
- [x] Compare all signed provider values with the immutable purchase-intent
  snapshot before activating a subscription.
- [x] Reject mismatches without consuming the intent or recording the event as
  successfully processed.
- [x] Add tests for partial amount, wrong currency, wrong product, wrong period,
  and valid payment.

### PAY-004 — Model real provider subscription identifiers — P0

- [x] Stop using an order UUID as a substitute for an external subscription ID
  unless the provider contract explicitly guarantees that identity.
- [x] Persist separate external event, checkout/order, customer, and subscription
  identifiers when supplied.
- [x] Document which identifier correlates renewal, failure, recovery,
  cancellation, and expiration events.

### PAY-005 — Complete the Tribute lifecycle mapping — P0

- [x] Map and route supported renewal, payment failure, recovery, cancellation,
  and refund/revocation events to the existing provider-independent services;
  use local reconciliation for expiration because Tribute has no separate
  expiration or cancellation-revocation webhook in its documented contract.
- [x] Validate each event type and reject unsupported payloads safely.
- [x] Preserve event idempotency for every lifecycle event.
- [x] Add controller-to-database integration tests for every supported event.

### PAY-006 — Make checkout creation recoverable — P1

- [x] Handle the gap where the external checkout is created but attaching its
  identifier to the local intent fails.
- [x] Use provider idempotency support if available; Tribute does not document
  an idempotency-key contract, so the local intent UUID is used for correlation
  and failed attachment is compensated through provider cancellation.
- [x] Add a reconciliation process for pending intents and orphaned external
  checkouts.
- [x] Expire or cancel stale pending intents in bounded batches.

### PAY-007 — Protect the webhook endpoint from abuse — P0

- [x] Enforce a small maximum request-body size before buffering the raw body.
- [x] Add rate limiting and connection/request timeouts at the edge.
- [x] Continue verifying the signature against the exact raw bytes using a
  constant-time comparison.
- [x] Do not log raw webhook bodies or signatures.
- [x] Add oversized-body and request-flood tests at the appropriate layer.

### PAY-008 — Restrict outbound provider destinations — P1

- [x] In production, allow only the expected Tribute API host and HTTPS scheme.
- [x] Prevent a configuration mistake from sending the API key to an arbitrary
  host.
- [x] Continue rejecting redirects and validating returned checkout URLs.

### PAY-009 — Add account subscription API — P1

- [x] Add the authenticated current-subscription response, service, and
  `GET /api/account/subscription` endpoint.
- [x] Expose FREE fallback or paid plan state without provider identifiers.
- [x] Include only fields required by the private account: plan, status,
  current paid period, and scheduled cancellation.
- [x] Add ownership, authentication, boundary, and JSON-contract tests.

### PAY-010 — Add user-controlled cancellation flow — P1

- [x] Define provider-independent cancellation commands and endpoint behavior.
- [x] Do not report cancellation as complete until provider acceptance is known.
- [x] Reconcile provider callbacks with locally requested cancellation state.
- [x] Keep repeated requests idempotent.

### PAY-011 — Retain and purge payment records safely — P2

- [x] Define retention for processed payment events, purchase intents, and
  provider diagnostics.
- [x] Never purge records still required for dispute handling, idempotency, or
  financial audit.
- [x] Add bounded cleanup and monitoring.

## Stage 5 — Authentication and public API security

### AUTH-001 — Add rate limiting and abuse controls — P0

- [x] Apply separate policies to registration, login, document upload,
  checkout creation, and webhook processing.
- [x] Key limits by a safe combination of account and trusted client address.
- [x] Configure trusted proxy handling before relying on forwarded addresses.
- [x] Return `429` with a consistent `ApiError` and retry guidance.
- [x] Add brute-force and burst tests without logging credentials.

### AUTH-002 — Strengthen JWT validation and configuration — P1

- [x] Require a positive access-token TTL and enforce a reasonable maximum.
- [x] Add and validate issuer and audience claims.
- [x] Add `jti` if revocation or security-event tracking will use it.
- [x] Define key rotation without accepting both old and new secrets forever.
- [x] Use an injected `Clock` for deterministic token tests.

### AUTH-003 — Add token lifecycle beyond a single access token — P1

- [x] Decide between short-lived access tokens plus rotating refresh tokens or
  mandatory re-login.
- [x] Implement logout/revocation appropriate to that decision.
- [x] Revoke all sessions after password reset or account compromise.
- [x] Store refresh credentials hashed and detect reuse if refresh tokens are
  introduced.

### AUTH-004 — Verify email ownership and support recovery — P1

- [x] Add email verification before enabling paid or abuse-sensitive actions.
- [x] Add expiring, single-use password-reset tokens.
- [x] Avoid revealing whether an email exists in reset and login responses.
- [x] Rate-limit delivery and verification endpoints.

### AUTH-005 — Handle concurrent registration deterministically — P1

- [x] Keep the database unique constraint as the final authority.
- [x] Translate the relevant unique-constraint violation into the same safe
  `409` response as the pre-check.
- [x] Add a two-request concurrent registration integration test.

### API-001 — Complete safe REST exception handling — P1

- [x] Map multipart-size failures to `413`.
- [x] Map known database conflicts explicitly without leaking SQL details.
- [x] Add a final safe `500 ApiError` handler with a correlation identifier.
- [x] Ensure validation, security, MVC, and unexpected failures retain a
  consistent JSON shape.
- [x] Keep full diagnostics only in protected logs.

### API-002 — Decide and enforce CORS policy — P1

- [x] If frontend and backend use different origins, allow only the exact
  production and approved local origins, methods, and headers.
- [x] If they share one reverse-proxy origin, explicitly document that CORS is
  intentionally unnecessary.
- [x] Never use wildcard origins together with credentials.

### API-003 — Isolate development-only API documentation — P2

- [x] Keep OpenAPI disabled by default.
- [x] Restrict documentation to a local/development profile or protect it in
  non-local environments.
- [x] Verify that enabling documentation does not make application operations
  unauthenticated.

Stage 5 decisions: access tokens are short-lived and require re-login; refresh
tokens are not issued, so refresh-token storage and reuse detection are not
applicable. Account-wide revocation uses a persisted authentication version,
including password reset and explicit logout-all, so per-token `jti` denylisting
is not currently needed. JWT rotation uses one active key ID and secret; a
rotation immediately invalidates tokens signed with the prior key. Forwarded
client addresses are deliberately ignored until a trusted reverse-proxy policy
is configured. CORS is disabled for same-origin deployments and, when enabled,
accepts only exact validated origins. OpenAPI remains disabled by default and
is authenticated unless local public access is enabled explicitly.

## Stage 6 — Storage and profile consistency

### STO-001 — Replace MinIO root credentials with least privilege — P0

- [x] Create a dedicated backend service account and bucket-scoped policy.
- [x] Permit only the exact object operations required by the Java module.
- [x] Keep MinIO root credentials separate and unavailable to the backend
  container.
- [x] Verify startup, upload, download, and delete operations with the restricted
  account.

### STO-002 — Validate storage endpoint policy — P1

- [x] Allow HTTP only for the explicitly local environment.
- [x] Require HTTPS and an approved host in production configuration.
- [x] Reject unsupported URI schemes and invalid endpoints at startup.

### AVA-001 — Fully decode and constrain avatars — P1

- [x] Decode JPEG/PNG after signature validation to reject truncated and
  malformed images.
- [x] Set maximum pixel dimensions and decoded-image memory limits.
- [x] Consider re-encoding uploads to a canonical safe representation.
- [x] Remove unnecessary metadata if privacy requirements demand it.

### AVA-002 — Make profile/avatar updates concurrency-safe — P1

- [x] Add optimistic versioning or locking to `UserProfile` mutations.
- [x] Prevent concurrent upload/delete operations from losing the current key
  or deleting a newly selected object.
- [x] Add concurrent replacement/delete integration tests.
- [x] Add cleanup for orphaned avatar objects.

## Stage 7 — Production Docker and operations

### OPS-001 — Create a production Compose/deployment configuration — P0

- [x] Keep the current Compose file explicitly local or add a production
  override.
- [x] Publish only the reverse proxy or public backend entrypoint.
- [x] Do not publish PostgreSQL, RabbitMQ AMQP, RabbitMQ Management, MinIO API,
  or MinIO Console to the host in production.
- [x] Put internal services on non-public networks.
- [x] Remove fixed `container_name` values if horizontal scaling is required.

### OPS-002 — Add TLS and trusted reverse-proxy settings — P0

- [x] Terminate HTTPS at a supported reverse proxy or platform load balancer.
- [x] Redirect HTTP to HTTPS.
- [x] Configure trusted forwarded headers and reject spoofed forwarding headers
  from untrusted peers.
- [x] Add edge limits for request body size, header size, idle time, and request
  duration.

### OPS-003 — Move production secrets to a secret manager — P0

- [x] Keep `.env` only for local development.
- [x] Use Docker/Kubernetes/platform secrets or an external secret manager in
  production.
- [x] Define rotation procedures for JWT, PostgreSQL, RabbitMQ, MinIO, and
  provider credentials.
- [x] Confirm secrets never enter images, documentation, error responses, or
  routine logs.

### OPS-004 — Harden containers — P1

- [x] Preserve the existing non-root backend user.
- [x] Add read-only filesystem where compatible, a small writable temp mount,
  dropped Linux capabilities, and `no-new-privileges`.
- [x] Define CPU, memory, PID, and log-size limits.
- [x] Pin release images by immutable digest or controlled version policy.
- [x] Set graceful shutdown timeout and orchestration stop grace period.

### OPS-005 — Add health, readiness, metrics, and alerts — P1

- [x] Separate liveness and readiness probes.
- [x] Include meaningful PostgreSQL, RabbitMQ, and MinIO readiness checks.
- [x] Export request, error, latency, queue, DLQ, outbox, database-pool, payment,
  quota, and storage metrics.
- [x] Add alerts for sustained errors, queue buildup, payment-event failures,
  storage failures, exhausted pools, and low disk space.
- [x] Protect operational endpoints from public access.

### OPS-006 — Add backups and recovery drills — P0

- [x] Back up PostgreSQL and required MinIO data with documented retention.
- [x] Encrypt backups and restrict restore permissions.
- [ ] Test restoration regularly and record recovery-time and recovery-point
  objectives.
- [x] Decide how RabbitMQ/outbox state is recovered without duplicating paid or
  translation work.

### OPS-007 — Define log security and correlation — P1

- [x] Add a request/correlation identifier to HTTP, RabbitMQ, and payment flows.
- [x] Redact authorization headers, signatures, provider credentials, checkout
  URLs containing private data, and raw webhook bodies.
- [x] Avoid exposing MinIO keys and internal exception text to clients.
- [x] Define structured logging, retention, and access controls.

Stage 7 implementation was completed on 2026-08-10. The remaining unchecked
backup item is an operational release gate: a real encrypted production backup
must be restored in an isolated environment and its measured RPO/RTO recorded
before production traffic is enabled, then repeated at least monthly.

## Stage 8 — Architecture and long-term reliability

### ARC-001 — Use one authoritative instant per business operation — P1

- [x] Resolve entitlement and usage period from the same captured `Instant`.
- [x] Apply the same rule to subscription lifecycle operations and payment
  reconciliation.
- [x] Add exact-boundary tests for month and subscription transitions.

### ARC-002 — Add lock timeouts and contention handling — P1

- [x] Configure finite timeouts for pessimistic database locks.
- [x] Map lock timeouts and transient conflicts to safe retryable responses or
  bounded internal retries.
- [x] Add contention metrics and tests.

### ARC-003 — Make tests hermetic and reproducible — P1

- [x] Move integration infrastructure toward Testcontainers or an equivalent
  isolated environment.
- [x] Avoid dependence on mutable developer data and manually prepared queues.
- [x] Keep focused unit tests fast and separate slower infrastructure tests
  when useful.
- [x] Run tests on Java 21 as used by the production image.

### ARC-004 — Enforce tests during release builds — P1

- [x] Do not treat the Dockerfile's `-DskipTests` build as the only release
  quality gate.
- [x] Require the full verified test suite before building/publishing a release
  image.
- [x] Add focused smoke tests against the exact built image.

### ARC-005 — Add static quality and security checks — P2

- [x] Add formatting/style rules appropriate to the existing codebase.
- [x] Add static analysis for correctness and common security mistakes.
- [x] Track meaningful test coverage without optimizing only for a percentage.
- [x] Scan migrations and configuration for unsafe production defaults.

### ARC-006 — Add failure-injection integration scenarios — P1

- [x] Test PostgreSQL failure before and after object upload.
- [x] Test RabbitMQ disconnect, NACK, return, and confirmation timeout.
- [x] Test MinIO timeout and partial failure.
- [x] Test backend termination at each outbox/payment boundary.
- [x] Verify every retry is idempotent and cannot grant extra quota or duplicate
  a paid subscription.

## Release gate

The Java backend must not be considered production-ready until every `P0` task
is complete and verified. Before release, confirm all of the following:

- [x] Plain full test command passes repeatedly.
- [x] Docker image contains no known Critical or unreviewed High CVEs.
- [x] Document size, content, and archive safety checks are enforced.
- [x] ML cannot bind a job to an unrelated MinIO object.
- [x] RabbitMQ publication is confirmed, routable, recoverable, and backed by an
  outbox/idempotency strategy.
- [x] Duplicate payments, lifecycle events, and expired subscriptions are
  handled safely.
- [x] Public endpoints have body limits and abuse controls.
- [x] Production infrastructure ports are private and traffic uses TLS.
- [x] Backend storage credentials follow least privilege.
- [ ] Production secrets use an approved secret-management mechanism.
- [ ] Backups, restoration, monitoring, and alerts have been demonstrated.

## Next task

Stage 8 is complete. The remaining release work is operational: connect the
production deployment to the approved secret-management platform and execute
the first encrypted backup restore drill in an isolated production-like
environment, recording measured RPO/RTO.
