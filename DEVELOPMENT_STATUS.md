# TranslateLab Java Backend — Development Status

Last updated: 2026-08-10

## Current objective

Build the Java module of the document translation service described in
`Проект_сервис_перевода_документов.md`.

The current flow under development is:

1. An authenticated user uploads a DOCX, DOC, or PDF document.
2. Java stores the source file in MinIO.
3. Java creates a `TranslationJob` in PostgreSQL with status `PENDING`.
4. Java publishes a JSON task to RabbitMQ for the Python ML service.
5. The ML service publishes progress and terminal status events to RabbitMQ.
6. Java consumes the events, validates state transitions, and updates PostgreSQL.
7. The frontend polls Java for the authenticated user's current job status.
8. The authenticated owner downloads a completed result through Java, which
   streams the object from MinIO.

The durable architecture for the private account, profile, plans,
subscriptions, and usage limits is documented in
`ACCOUNT_PROFILE_SUBSCRIPTION_ARCHITECTURE.md`. Read it before changing those
areas.

The ordered security, reliability, payment-correctness, messaging, storage,
testing, and production-readiness backlog is documented in
`BACKEND_HARDENING_BACKLOG.md`. Read it before continuing implementation and
complete its stages in order.

## Current local application snapshot

- Milestone: private profile/avatar MVP, usage limits, hardened subscription
  checkout and lifecycle, authentication/public API hardening, storage/profile
  consistency, and the implemented Stage 7 production operations contour.
- Docker tag used by Compose: `translation-backend:local`.
- Local image ID:
  `sha256:9133832920acbc3ee45efaabf980cd25d9a8e7236ce42f5ee1deae2b7d54e54d`.
- On 2026-08-10 the Compose `backend` service was recreated from this Stage 7
  image without restarting PostgreSQL, RabbitMQ, or MinIO. All four services
  reported `healthy`; `/actuator/health`, `/livez`, and `/readyz` reported
  `UP`; the process ran as uid/gid `app`; and the container image ID matched
  the newly built local image.
- The Maven artifact version remains `0.0.1-SNAPSHOT`; choosing a semantic
  release number is intentionally a separate decision.

## Verified baseline

- Maven build succeeds.
- Maven Surefire now limits each test Hikari pool to two connections without
  changing production datasource settings. Two consecutive plain full Maven
  test runs each passed all 1370 tests with zero failures, errors, or skips;
  PostgreSQL returned to the normal application connection baseline after each
  test JVM exited. This completes `TST-001` in
  `BACKEND_HARDENING_BACKLOG.md`.
- Stage 0 of the hardening backlog is complete:
  - pgJDBC is explicitly managed at `42.7.13`;
  - the complete Netty BOM is explicitly managed at `4.2.16.Final`;
  - the focused PostgreSQL, Flyway, pessimistic-lock, and RabbitMQ suite passed
    all 11 tests;
  - the complete suite passed all 1370 tests;
  - `scripts/security-scan.ps1` generates a CycloneDX SBOM and fails the
    release gate on Critical or High findings;
  - the rebuilt image contains 386 indexed packages with zero Critical and
    zero High findings;
  - the exact rebuilt image was started through Compose, reported `healthy`,
    returned Actuator status `UP`, and ran as the non-root `app` user.
- Stage 1 safe document ingestion is complete (`FILE-001` through `FILE-005`).
  PDF, DOCX, and legacy DOC are validated from binary content before external
  side effects; DOCX archive limits protect entry count, entry and total
  expansion, compression ratio, nested archives, and unsafe entry paths.
- Client MIME is ignored as a trust signal. MinIO receives MIME derived from
  verified `FileFormat`, confirmed by a real MinIO metadata assertion.
- Source, result, failed-job, abandoned-job, and orphan-upload retention is
  configurable. Cleanup is scheduled, bounded, idempotent, metrics-enabled,
  and protects every object referenced by an active job. Expired results return
  the standard `410 Gone` `ApiError`.
- Stage 2 ML-to-Java trust-boundary hardening is complete (`MSG-001` through
  `MSG-003`). Java now generates and persists the expected result key before
  task publication, accepts only an exact key match, validates the stored
  result's binary format before `DONE`, bounds internal ML diagnostics to 2,000
  characters, exposes only a safe public error code/message, and serializes
  status updates with a PostgreSQL pessimistic row lock.
- Duplicate and late terminal events are idempotent, stale progress cannot
  decrease stored progress, and a committed `DONE` or `FAILED` state cannot be
  replaced by the other terminal state. Two-thread integration coverage proves
  the behavior with real transactions.
- `JAVA_ML_DOCUMENT_CONTRACT.md` records the shared ingestion limits and trust
  boundary for the external ML service.
- The final full suite passes all 1429 tests with zero failures, errors, or
  skips. The rebuilt image contains 398 indexed packages with no detected
  Critical or High vulnerabilities, runs as `app`, and reports Actuator
  status `UP`; all four Compose services are healthy.
- Flyway successfully validates and applies all 16 database migrations.
- The Java backend Docker image builds successfully using Java 21 and runs as
  a non-root user.
- The backend has been started through Docker Compose and verified with
  Actuator status `UP` while using the internal PostgreSQL, RabbitMQ, and MinIO
  service addresses. Docker Compose reports the container as `healthy`.
- `README.md` now reflects the implemented profile, avatar, usage, subscription
  offer, checkout, OpenAPI, Docker, and integration capabilities. It documents
  only user-facing endpoints and the high-level payment redirect flow; secrets,
  provider callback routes, signature mechanics, internal queue names, object
  keys, and payment-event persistence details are intentionally omitted.
- On 2026-07-29 the backend image was rebuilt with the completed profile and
  avatar endpoints, the running backend container was recreated, and Actuator
  again reported `UP`.
- On 2026-07-30 the image was rebuilt from the current sources, the backend
  container was recreated without replacing PostgreSQL, RabbitMQ, or MinIO,
  and all four Compose services were verified healthy.
- PostgreSQL, RabbitMQ, and MinIO containers have been verified as healthy.
- Spring Boot has been smoke-tested with Actuator status `UP`.
- RabbitMQ topology has been verified through the Management API:
  - direct exchange: `translation.exchange`;
  - durable task queue: `translation.tasks`;
  - task routing key: `translation.task`;
  - durable status queue: `translation.status`;
  - status routing key: `translation.status`;
  - both matching bindings.
- MinIO bucket initialization has been verified against the local MinIO server.
- The authenticated multipart upload flow has been smoke-tested end to end:
  - HTTP response: `202 Accepted` with `job_id`;
  - PostgreSQL row: `PENDING` with normalized languages and file format;
  - MinIO object: present with the generated internal key and content type;
  - RabbitMQ queue: one matching task message.

## Completed implementation

### Authentication and security

- `User` entity and `UserRepository`.
- Registration request/response, service, and controller flow.
- Login request/response and `LoginService`.
- `EmailAlreadyExistsException` and `InvalidCredentialsException`.
- JWT configuration, validated properties, token generation, and tests.
- Stateless Spring Security OAuth2 Resource Server configuration using HS256.
- Uniform JSON responses for authentication and access-denied failures.
- Auth controller tests for registration, login, validation, and security.

### Common REST errors

- `ApiError`.
- `GlobalExceptionHandler`.
- Handlers for:
  - duplicate email → 409;
  - DTO validation → 400;
  - unreadable request body → 400;
  - invalid credentials → 401;
  - unsupported document format → 400;
  - invalid document upload → 400;
  - oversized document or multipart request → 413;
  - missing user → 404;
  - invalid pagination → 400;
  - malformed query parameters and path variables → 400;
  - missing required multipart parts → 400 with the missing part name;
  - MinIO or RabbitMQ failure → 503 with a safe public message.
- `MockMvc` coverage for unsupported formats, invalid uploads, and missing users.
- Infrastructure-error tests verify that internal object keys and broker details
  are not exposed to API clients.

### Translation domain

- `TranslationStatus`.
- `FileFormat` with lowercase JSON serialization and uppercase JPA storage.
- `TranslationJob` entity with guarded state transitions and entity tests.
- `TranslationJobRepository` queries scoped by user.
- `UnsupportedFileFormatException`.
- `InvalidDocumentUploadException`.
- `InvalidDocumentContentException` for extension/content mismatches and
  malformed document containers.
- `UserNotFoundException`.
- `FileFormatResolver` for DOCX, DOC, and PDF with edge-case tests.
- Strategy-based content validators for PDF, DOCX, and legacy DOC. PDFBox
  verifies PDF parseability; Apache POI verifies Word packages and legacy OLE2
  Word streams.
- DOCX validation streams every archive entry through configurable limits and
  rejects duplicate entries, unsafe paths, nested ZIPs, suspicious compression,
  missing OOXML parts, and non-DOCX Word content types.
- `DocumentUploadResponse` serialized as `{ "job_id": "..." }`.
- Flyway V3 adds persisted translation progress with default `0` and a
  database constraint limiting values to `0..100`.
- `TranslationJob` owns progress invariants:
  - new and newly processing jobs start at `0`;
  - processing updates are monotonic and limited to `0..99`;
  - completed jobs end at `100`;
  - failed jobs retain the last reported progress.
- `DocumentUploadService` coordinating input validation, MinIO upload,
  PostgreSQL persistence, and RabbitMQ publication.
- Document upload size is configured through validated
  `DocumentUploadProperties`; the default maximum file size is 20 MiB, while
  Spring's multipart request limit leaves room for multipart metadata.
- Oversized documents are rejected before user lookup, quota reservation,
  MinIO upload, database persistence, or RabbitMQ publication. Both the
  application-level guard and Spring's multipart exception map to the same
  safe `413 ApiError` contract.
- The upload flow now reserves one `DOCUMENT_TRANSLATION` unit after all cheap
  request validation and before its first external side effect, consumes the
  reservation only after RabbitMQ publication succeeds, and releases it on
  every earlier MinIO, persistence, stream, or publication failure.
- A failure while releasing usage is preserved as a suppressed exception on
  the original upload failure; a consumption failure after successful broker
  publication intentionally does not release usage because the task may
  already be executing.
- Upload compensation policy:
  - delete the MinIO object when database persistence fails;
  - retain the source object and mark the saved job as `FAILED` when RabbitMQ
    publication fails;
  - preserve secondary compensation failures as suppressed exceptions.
- Unit coverage for the complete upload-service flow, validation,
  infrastructure failures, compensation behavior, quota exhaustion, usage
  lifecycle ordering, release failures, and post-publication consumption
  failures, including exactly-at-limit and over-limit file boundaries.
- A real HTTP integration test starts embedded Tomcat with a 10-byte test
  limit, sends an authenticated 11-byte multipart file, verifies the complete
  `413 ApiError`, and proves that `DocumentUploadService` is not invoked.
- End-to-end upload integration coverage using real local infrastructure:
  - an authenticated multipart request passes through Spring Security and the
    REST controller;
  - the returned `job_id` identifies a `PENDING` PostgreSQL row;
  - MinIO contains the exact uploaded bytes under the generated internal key;
  - RabbitMQ contains the matching JSON task contract;
  - the usage reservation is persisted as `CONSUMED`, linked to the same
    translation job, and has its expiration cleared;
  - test data, stored objects, and queued task messages are cleaned up.
- A dedicated authenticated HTTP integration test proves the complete FREE
  limit contract: five uploads in the same monthly period return `202`, the
  sixth returns the standard `429 ApiError`, and PostgreSQL and RabbitMQ contain
  exactly five linked jobs, `CONSUMED` usage records, and task messages.
- Protected `POST /api/documents/upload` multipart endpoint returning
  `202 Accepted`.
- Upload-controller tests covering JWT authentication, multipart delegation,
  successful responses, invalid uploads, and missing users.
- `DocumentStatusResponse` with explicit snake_case JSON fields.
- `DocumentStatusService` that scopes every lookup by both job and authenticated
  user ID and returns the persisted progress value.
- Protected `GET /api/documents/{jobId}/status` endpoint.
- Missing and foreign jobs intentionally produce the same `404` response.
- Status-controller tests cover authentication, successful polling, and hidden
  missing/foreign jobs.
- `DocumentDownloadResult` carrying the result stream, safe generated file
  name, and MIME type without coupling the service layer to HTTP response types.
- `TranslationResultNotReadyException` mapped to `409 Conflict`.
- `DocumentDownloadService` that:
  - scopes the lookup by both job and authenticated user ID;
  - only downloads jobs in `DONE`;
  - reads the internal result object key from MinIO;
  - generates a safe file name and the correct DOCX, DOC, or PDF MIME type.
- Download-service unit tests cover all supported formats, unfinished jobs,
  missing or foreign jobs, expired results, and null identifiers.
- Flyway V14 adds source/result deletion markers and partial cleanup indexes.
  The retention scheduler expires stale active jobs, deletes terminal source
  and result objects according to policy, and removes old unreferenced
  `uploads/` objects left by failed compensation. Fixed-cardinality Micrometer
  counters record successful cleanup categories and failures without adding
  object keys as metric tags.
- Protected `GET /api/documents/{jobId}/download` endpoint streams the MinIO
  result with the correct MIME type and attachment file name.
- Download-controller tests cover exact bytes and headers, authentication,
  missing or foreign jobs, and unfinished results.
- `DocumentHistoryItemResponse` and `DocumentHistoryResponse` with explicit
  snake_case JSON fields and immutable page contents.
- `InvalidPaginationException` mapped to `400 Bad Request`.
- `DocumentHistoryService` that:
  - validates page and size, with a maximum page size of `100`;
  - scopes every query to the authenticated user;
  - sorts jobs by creation time in descending order;
  - maps persisted jobs and Spring Data page metadata to the API response.
- Protected `GET /api/documents/history` endpoint with default `page=0` and
  `size=20`.
- History DTO, service, controller, authentication, pagination, malformed
  parameter, and invalid path-variable tests.

### User profiles (completed)

- A balanced personal-profile MVP has been selected:
  - authentication data remains in `users`;
  - editable profile data lives in a separate one-to-one `user_profiles`
    table;
  - avatar objects will be stored in MinIO while PostgreSQL stores only the
    internal object key.
- The product is a private SaaS account, not a social network:
  - users cannot search for or interact with other users;
  - UUID and email identify the account;
  - profile fields never participate in authentication, ownership, or access;
  - all profile fields are optional and do not block document translation.
- Flyway V4 creates `user_profiles` with:
  - `user_id` as both primary key and cascading foreign key to `users`;
  - a nullable unique lowercase `username` as originally implemented;
  - display name, nickname, profession, biography, and avatar object key;
  - creation and update timestamps;
  - empty profile rows for users that existed when the migration ran.
- The accepted architecture supersedes V4's username rule:
  - username is private, optional, case-preserving, and non-unique;
  - uppercase and lowercase Latin letters are allowed;
  - V4 remains immutable because it has already been applied;
  - Flyway V5 removes the unique constraint and replaces the lowercase-only
    check with the accepted mixed-case format.
- `UserProfile` JPA entity maps the shared `users.id` primary key through
  `@MapsId`, keeps the user association lazy, and provides focused methods for
  editing profile details and replacing or removing an avatar key. Its username
  mapping matches V5 and no longer declares uniqueness.
- Entity tests cover profile creation, null-user rejection, detail updates, and
  avatar replacement and removal.
- `UserProfileRepository` provides only the shared-primary-key persistence
  operations inherited from `JpaRepository`; private usernames are not queried
  for uniqueness.
- `RegistrationService` now creates an empty `UserProfile` for every newly
  registered user in the same transaction as the `User`, so either both rows
  are persisted or neither is.
- Registration-service tests verify that the profile belongs to the saved user
  and that no profile is created when the email is already registered.
- `UpdateProfileRequest` supports an entirely optional profile, strips
  surrounding whitespace, converts blank values to `null`, preserves username
  case, validates the accepted mixed-case format and field lengths, and maps
  `display_name` explicitly.
- Request-DTO tests cover normalization, empty profiles, username validation,
  all text length limits, and snake_case JSON deserialization.
- `ProfileResponse` exposes the account UUID and email, editable profile
  fields, `has_avatar`, and account/profile timestamps without exposing the
  internal MinIO avatar object key.
- The response-DTO test verifies the complete snake_case JSON contract.
- `ProfileService` reads and updates profiles strictly by authenticated user
  UUID, flushes updates before mapping timestamps, and never exposes the
  internal avatar object key.
- Profile-service tests cover complete response mapping, detail updates,
  timestamp flushing, missing profiles, and null arguments.
- Protected `GET /api/profile` and `PUT /api/profile` endpoints derive the
  account UUID exclusively from the authenticated JWT subject.
- Profile-controller tests cover response JSON, full profile replacement and
  normalization, validation, missing profiles, and authentication for both
  endpoints.
- `InvalidAvatarException` carries safe, specific avatar-validation messages
  and is mapped by `GlobalExceptionHandler` to `400 Bad Request`.
- The exception-handler test verifies the avatar error status, message, path,
  timestamp, and empty field-error map.
- `AvatarFormat` centralizes the only accepted avatar formats: canonical
  `jpg`/`image/jpeg` and `png`/`image/png` pairs.
- Avatar-format tests lock down both mappings and prevent accidental expansion
  of the accepted format set.
- `AvatarValidator` accepts only non-empty JPEG or PNG files up to and including
  2 MiB, requires the canonical media type to match the binary file signature,
  and reads only the leading bytes needed for validation.
- Avatar-validator tests cover both valid formats, the exact size boundary,
  empty and oversized files, unsupported media types, mismatched and truncated
  signatures, and safe handling of file-read failures.
- `AvatarStorageKeyGenerator` creates unique internal keys in the form
  `avatars/{userId}/{randomUuid}.{extension}` without accepting or using an
  original user-supplied file name.
- Avatar-key-generator tests cover both accepted extensions, exact key
  structure, UUID object identifiers, uniqueness, and null arguments.
- `AvatarDownloadResult` carries an open MinIO input stream and avatar media
  type from the service layer without coupling it to HTTP response classes.
- Avatar-download-result tests cover both canonical media types and reject a
  missing stream or blank media type.
- `AvatarNotFoundException` represents an existing profile without a current
  avatar and is mapped by `GlobalExceptionHandler` to `404 Not Found`.
- The exception-handler test verifies the missing-avatar status, safe message,
  request path, timestamp, and empty field-error map.
- `AvatarService.uploadAvatar(...)` validates the authenticated profile and
  image, uploads the new object before persisting its key, compensates a
  persistence or post-upload stream-close failure by deleting the new object,
  and deletes the previous object only after the new key is saved.
- Failure to clean up the previous object is logged without turning an already
  committed replacement into a misleading client error.
- Avatar-service upload tests cover first upload, replacement order, validation
  and stream failures, MinIO failure, database compensation, suppressed cleanup
  failures, and best-effort removal of the previous object.
- `AvatarService.downloadAvatar(...)` scopes lookup to the authenticated
  profile UUID, returns `404` when no avatar is configured, resolves the
  canonical JPEG/PNG media type from the trusted internal key, and returns the
  open MinIO stream through `AvatarDownloadResult`.
- Download tests cover both formats, null and missing users, profiles without
  an avatar, corrupted stored extensions, and MinIO download failures.
- `AvatarService.deleteAvatar(...)` is idempotent, commits removal of the
  database key before attempting best-effort MinIO cleanup, and never deletes
  the object when persistence fails.
- Delete tests cover successful ordering, already absent avatars, null and
  missing users, persistence failure, and non-fatal MinIO cleanup failure.
- Protected `PUT`, `GET`, and `DELETE /api/profile/avatar` endpoints derive the
  account UUID exclusively from the authenticated JWT subject.
- Avatar upload and deletion return `204 No Content`; retrieval streams the
  exact MinIO bytes with the canonical JPEG/PNG media type and
  `Cache-Control: no-store`.
- Avatar-controller tests cover upload, retrieval, deletion, missing avatars,
  invalid images, missing multipart data, and authentication for all three
  methods.

### Plans, subscriptions, and usage limits (in progress)

- Anonymous visitors may access the public frontend, but Java feature endpoints
  require authentication.
- Flyway V10 creates `subscription_purchase_intents` as the durable,
  provider-independent link between an authenticated user, selected plan, and
  external checkout. It defines the `PENDING`, `CONSUMED`, `EXPIRED`, and
  `CANCELED` lifecycle, enforces expiration and consumption-time consistency,
  and indexes user history, pending expiration, and unique provider checkout
  references. Flyway applied V10 successfully to local PostgreSQL; its 10
  columns, eight constraints, four indexes (including the primary key), and the
  complete 987-test suite were verified.
- `SubscriptionPurchaseIntentStatus` exactly maps the four V10 lifecycle values.
- `SubscriptionPurchaseIntent` maps the V10 lifecycle plus the nullable V12
  legacy offer link and owns the guarded `PENDING` transitions for checkout
  attachment, successful consumption, expiration, and cancellation. Every new
  intent requires an active `PlanPaymentOffer` and derives its plan and provider
  from that single trusted source, while old rows may still load with no offer.
  Thirty-seven focused scenarios cover offer, plan, provider, timestamp, and
  transition invariants; PostgreSQL tests verify persisted links and downstream
  locking and completion flows.
- `SubscriptionPurchaseIntentRepository` provides ownership-scoped and
  provider-scoped `PESSIMISTIC_WRITE` lookups for user and payment-event flows.
  Two PostgreSQL scenarios verify persistence, owner/provider isolation, and
  that an owner-scoped lock blocks a concurrent provider-scoped transaction for
  the same intent until the first transaction completes.
- `SubscriptionPurchaseIntentCreationCommand` is the provider-independent
  internal creation input containing only the authenticated user ID, selected
  plan code, and server-selected provider. Twenty-four focused scenarios verify
  exact values and all UUID, format, character, case, and length boundaries.
- Validated `PaymentProperties`, registered by `PaymentConfig`, supplies the
  positive purchase-intent TTL and the validated server-selected provider code.
  Runtime configuration maps `PAYMENT_PROVIDER` with a local `TRIBUTE` default
  and
  `PAYMENT_PURCHASE_INTENT_TTL` to `app.payment.purchase-intent-ttl` with a
  `30m` local default, and `.env.example` documents the non-secret setting.
  Sixteen focused validation and binding scenarios plus the full Spring context
  verify the configuration.
- `SubscriptionPlanNotFoundException` gives the same safe not-found result for
  missing and unavailable subscription plans. `GlobalExceptionHandler` maps it
  to the standard `404 ApiError`, with a focused MockMvc contract test.
- `SubscriptionPurchaseIntentCreationResult` is the immutable service boundary
  containing only the saved intent ID, validated provider code, and expiration
  instant, so creation does not expose a mutable JPA entity. It is also the
  public purchase-start response with explicit `intent_id` and `expires_at`
  JSON properties. Fifteen focused
  scenarios verify its values and all null, character, case, and length
  boundaries plus its exact JSON contract.
- `SubscriptionPurchaseIntentCreationService` transactionally resolves the
  authenticated user and the single active `MONTH` offer for the requested plan
  and server-selected provider. It rejects unavailable and `FREE` offers, takes
  the trusted plan and provider from the resolved entity, derives expiration
  from the injected clock and TTL, and flushes a new `PENDING` intent without an
  external provider call. Six unit and four PostgreSQL scenarios verify lookup
  ordering, exact TTL persistence, and rejection without orphan intent rows.
- `SubscriptionPurchaseIntentCheckoutAttachmentCommand` is the ownership-scoped
  internal input containing only user ID, intent ID, and a stripped non-blank
  external checkout ID of at most 255 characters. Nine focused scenarios verify
  exact UUIDs, normalization, null/blank handling, and both length boundaries.
- `SubscriptionPurchaseIntentNotFoundException` intentionally represents both
  an absent intent and an intent outside the authenticated user's ownership.
  `GlobalExceptionHandler` maps it to the standard safe `404 ApiError`, verified
  by a focused MockMvc contract test.
- `SubscriptionPurchaseIntentCheckoutAttachmentService` locks by intent and
  authenticated owner, reads the injected clock only after acquiring the lock,
  and delegates to the entity through transactional dirty checking. Five unit
  and four PostgreSQL scenarios verify ordering, commit, same-ID idempotency,
  hidden foreign ownership, exact-expiry rollback, and checkout-replacement
  protection.
- `SubscriptionPurchaseIntentRepository` resolves a checkout-bound intent by
  the V10-unique `provider + externalCheckoutId` pair under
  `PESSIMISTIC_WRITE`. PostgreSQL integration checks verify exact pair matching
  and prove that concurrent owner and provider operations serialize on the
  same intent row. The complete 895-test suite passes.
- `SubscriptionPurchaseCompletionCommand` carries only verified provider event,
  checkout, optional customer, external subscription, and paid-period data. It
  intentionally excludes user and plan identities, normalizes all external
  identifiers, and validates provider and period boundaries. Thirty-six focused
  scenarios cover valid values and every null, blank, format, and length edge.
- `SubscriptionPurchaseCompletionService` atomically reserves the initial
  payment event, locks the checkout-bound intent, builds a provider-managed
  subscription from the intent's trusted user and plan, and consumes the intent
  using the injected clock. Seven unit and four PostgreSQL scenarios verify
  ordering, duplicate-event short-circuiting, successful persistence, exact
  expiry, missing intent, and full rollback on active-subscription conflicts.
- `SubscriptionPurchaseStartRequest` is the narrow public purchase-start JSON
  contract: it accepts only normalized `plan_code`, leaving authenticated user
  identity and provider selection to the server. Fourteen focused Bean
  Validation and Jackson scenarios verify snake_case mapping, format, and exact
  length boundaries.
- Authenticated `POST /api/subscription-purchases` derives the user ID from the
  JWT subject, takes only validated `plan_code`, selects the provider through
  `PaymentProperties`, and returns the existing creation result with HTTP 201.
  Eight WebMvc scenarios verify the exact response, server-owned command data,
  safe unavailable-offer handling, request validation, and 401 protection.
- Flyway V11 creates provider-neutral `plan_payment_offers` with positive minor-
  unit prices, ISO-style currency codes, monthly billing, optional external
  product bindings, active-history support, and restricted plan deletion. Its
  active-scope and provider-product uniqueness rules, defaults, field checks,
  and foreign key were verified by five real PostgreSQL scenarios; Flyway is at
  version 11 and the complete 987-test suite passes.
- Flyway V12 adds a nullable legacy-compatible `offer_code` to purchase intents
  and enforces `(offer_code, plan_code, provider)` against an immutable offer
  scope with restricted deletion. A partial child index supports lookups and FK
  maintenance. Flyway is at version 12; four PostgreSQL scenarios verify legacy
  rows, matching links, both mismatch dimensions, and deletion protection.
- Payment-domain `BillingPeriod` currently contains exactly the V11-supported
  `MONTH` value. A focused contract test keeps the Java enum synchronized with
  the database check constraint and separates charging cadence from entitlement-
  limit reset periods.
- `PlanPaymentOffer` maps the provider-neutral V11 offer catalog. Its commercial
  identity and terms are immutable, its plan association is lazy, and only its
  active state can change; activation is rejected while the parent plan is
  inactive. Forty-one focused scenarios cover exact schema boundaries,
  normalization, lifecycle behavior, and invalid construction.
- `PlanPaymentOfferRepository` resolves the single active offer for an active
  plan by plan code, provider, and billing period while retaining inherited
  code-based lookup for historical or administrative use. Its explicit JPQL
  catalog query fetches plans in the same SQL statement, filters both active
  states by provider and billing period, and orders by price then plan code.
  Five PostgreSQL scenarios verify persistence, both loading strategies,
  filtering, deterministic ordering, timestamps, and inactive retrieval.
- `PlanPaymentOfferNotFoundException` represents a safe unavailable-offer
  failure without exposing provider configuration. `GlobalExceptionHandler`
  maps it to the standard `404 ApiError`; a focused MockMvc contract verifies
  status, message, request path, timestamp, and empty field errors.
- `SubscriptionOfferResponse` is the immutable public pricing contract. It
  exposes only plan code and display name, positive minor-unit price, currency,
  and billing period while keeping provider, offer, and external product IDs
  internal. Thirty-four scenarios verify normalization, schema boundaries, and
  the exact snake_case JSON representation.
- `SubscriptionOfferCatalogService` reads the server-configured provider's
  active monthly catalog, preserves the repository's deterministic ordering,
  maps offers to immutable public response records, and does not expose JPA
  entities or provider-specific identifiers. Four focused tests verify exact
  mapping, configured-provider lookup, empty results, immutable output, and the
  read-only transaction contract; all 991 project tests pass.
- Public `GET /api/subscription-offers` delegates to the read-only catalog
  service and returns active monthly offers without requiring JWT. Spring
  Security permits only that exact GET route; other methods remain protected.
  Three WebMvc scenarios verify the ordered JSON contract, an empty array, and
  method-specific security. All 994 project tests pass.
- Flyway V13 seeds the first paid `PRO` plan, its limited monthly
  `DOCUMENT_TRANSLATION` entitlement of 100 units, and the active
  `PRO_TRIBUTE_MONTH` offer priced at 49900 minor RUB units. Flyway applied the
  migration to local PostgreSQL and is now at version 13. Two real-PostgreSQL
  tests verify the seeded plan, entitlement, offer, timestamps, and catalog
  visibility; the catalog-ordering test now uses an isolated provider. All 996
  project tests pass.
- `PaymentCheckoutResult` is the immutable provider-boundary result containing
  the normalized external checkout identifier and a trusted absolute HTTPS
  redirect URI. It rejects missing or oversized identifiers, relative and
  non-HTTPS links, missing hosts, and URLs over 2048 characters. Sixteen
  focused boundary scenarios pass; all 1012 project tests pass.
- `PaymentCheckoutCreationCommand` is the immutable, JPA-independent commercial
  snapshot supplied to a checkout provider: intent, offer and plan identity,
  display name, minor-unit price, currency, billing period, optional external
  product binding, and expiration. Fifty-one focused scenarios verify exact
  values, normalization, all schema-aligned formats and length boundaries, and
  the absence of wall-clock validation; all 1063 project tests pass.
- `PaymentCheckoutGateway` defines the provider-neutral checkout port: every
  adapter exposes its stable provider code and creates a `PaymentCheckoutResult`
  from `PaymentCheckoutCreationCommand`. The interface has no Spring, JPA,
  Tribute, Telegram, or checked-exception coupling; the complete 1063-test suite
  and Java 21 compilation pass.
- `PaymentProviderUnavailableException` exposes one fixed safe message for both
  missing gateway configuration and future provider failures while retaining a
  technical cause only for server-side diagnostics. Two focused tests prevent
  cause messages containing internal addresses or secrets from becoming the
  public exception message; all 1065 project tests pass.
- `GlobalExceptionHandler` maps payment-provider unavailability to the standard
  `503 Service Unavailable` `ApiError`, logs the full exception server-side, and
  returns only the fixed payment-service message, request path, timestamp, and
  empty field errors. MockMvc verifies that an internal provider URL and token
  from the cause do not enter JSON; all 1066 project tests pass.
- `PaymentCheckoutGatewayResolver` builds an immutable provider-code index from
  all injected gateway implementations, allows an empty registry before an
  adapter is installed, rejects nulls, invalid codes, and duplicates, and
  resolves exact codes without invoking a provider. Missing or malformed
  requested providers produce the safe availability exception. Twenty-four
  focused scenarios and the full 1090-test suite pass.
- `SubscriptionPurchaseStartResponse` is the public snake_case checkout-start
  contract containing only the Java intent ID, provider, trusted absolute HTTPS
  redirect URL, and expiration. It intentionally omits the provider checkout
  identifier. Twenty-five scenarios verify exact JSON, provider boundaries,
  redirect security and length, and deterministic expiration handling; all 1115
  project tests pass.
- `SubscriptionPurchasePreparationResult` is the internal immutable pair of the
  persisted intent result and provider checkout command. It rejects missing
  parts and guarantees matching intent IDs and expiration instants before an
  external call. Five focused consistency scenarios pass; all 1120 project
  tests pass.
- `SubscriptionPurchaseIntentCreationService.prepare(...)` now persists the
  pending intent and builds both boundary results from the same trusted active
  offer and plan snapshot inside one transaction. After the controller moved to
  the checkout orchestrator, the temporary compatibility-only `create(...)`
  method was removed, leaving `prepare(...)` as the single creation boundary.
  Unit and PostgreSQL integration coverage verify exact commercial checkout
  data, nullable external product binding, transaction boundaries, and
  persistence; all 1133 project tests pass.
- `SubscriptionPurchaseCheckoutService` now orchestrates the complete
  provider-neutral checkout start sequence: commit local preparation, resolve
  the trusted provider gateway, create the external checkout outside a database
  transaction, attach its external ID in a separate transaction, and return the
  safe public redirect response. Nine focused scenarios verify exact ordering,
  trusted provider selection, short-circuiting and error propagation, null
  gateway defense, attachment data, and the absence of an outer transaction;
  all 1133 project tests pass.
- Protected `POST /api/subscription-purchases` now delegates to the checkout
  orchestration service and returns `201 Created` with the safe public
  `intent_id`, provider, HTTPS `redirect_url`, and expiration contract. The
  authenticated JWT subject and server-configured provider remain authoritative;
  controller coverage includes success, validation, authentication, unavailable
  offers, and safe provider-unavailable `503` responses. All 1134 project tests
  pass.
- The official Tribute Shop API was selected for the first concrete gateway.
  Checkout creation uses `POST https://tribute.tg/api/v1/shop/orders` with the
  secret `Api-Key` header, minor-unit amount, lowercase currency, and
  `period=monthly`; the returned order UUID becomes `externalCheckoutId` and
  the Telegram `webappPaymentUrl` becomes the public redirect. Tribute signs
  webhook bodies with HMAC-SHA256 in `trbt-signature`. Its current official
  create-order contract does not document an idempotency header, so none will
  be invented; the Java intent UUID will still be supplied as the provider
  customer/correlation value.
- Validated `TributeProperties` now models the disabled-by-default integration,
  HTTPS API base URL, conditionally required API key, and positive bounded
  connect/read timeouts. Its explicit `toString()` always redacts the API key.
  Twenty-six focused scenarios cover exact values, conditional secret handling,
  URL safety, timeout boundaries, Jakarta null validation, and secret redaction;
  all 1159 project tests pass.
- `PaymentConfig` now registers `TributeProperties`, and
  `application.properties` binds safe disabled defaults for the official API
  base URL plus 3-second connect and 10-second read timeouts. The API key has no
  real or fallback value. Isolated binding coverage verifies disabled startup
  without a key, enabled startup with a synthetic key, and rejection of enabled
  startup without a key; the full Spring context and all 1161 tests pass.
- `.env.example` now documents all five Tribute variables with integration
  disabled, the official non-secret API base URL, a clearly fake key placeholder,
  and the validated timeout defaults. No real Tribute secret is stored or
  documented.
- `compose.yaml` now forwards the two general `PAYMENT_*` and all five
  `TRIBUTE_*` variables to the backend container with safe provider, TTL,
  disabled-integration, official URL, empty-key, and timeout defaults. The
  rendered Compose configuration validates successfully without exposing its
  environment values.
- `TributeCreateOrderRequest` is the immutable external Shop API request under
  `payment.provider.tribute.dto`. It serializes the exact `amount`, lowercase
  supported currency, normalized title and description, intent UUID as
  `customerId`, and fixed `monthly` period contract. Forty focused scenarios
  cover exact JSON, positive amount boundaries, supported currencies, text
  normalization and Tribute's UTF-16 limits, required correlation, and monthly
  period enforcement; all 1201 project tests pass.
- `TributeCreateOrderResponse` deserializes the official order UUID, browser
  `paymentUrl`, and Telegram `webappPaymentUrl` while ignoring unrelated future
  fields. It admits only absolute HTTPS URLs on the exact trusted
  `web.tribute.tg` and `t.me` hosts, with standard ports, meaningful paths, no
  user-info or fragments, and bounded length. Thirty focused deserialization
  and security-boundary scenarios pass; all 1231 project tests pass.
- Conditional `TributeHttpClientConfig` creates the named `tributeRestClient`
  only when Tribute is enabled. It clones the shared builder, applies the
  validated base URL and secret `Api-Key`, accepts JSON, uses the configured
  connect/read timeouts, and explicitly disables redirects to prevent credential
  forwarding. Four focused context, transport, conditional-gateway, and exact
  base-path composition scenarios pass.
- `TributeCheckoutMapper` converts the trusted internal commercial snapshot into
  the dynamic Tribute monthly Shop Order contract and converts the validated
  response UUID plus Telegram WebApp URL into `PaymentCheckoutResult`. It maps
  only EUR/RUB/USD, keeps the intent UUID as customer correlation, ignores legacy
  external product bindings, and never exposes the browser payment URL. Ten
  focused mapping scenarios pass; all 1244 project tests pass.
- Conditional `TributePaymentCheckoutGateway` is the first concrete
  `PaymentCheckoutGateway`. It sends the exact JSON Shop Order request through
  the named Tribute client, rejects an empty success body, maps transport, HTTP,
  and decoding failures to the safe `PaymentProviderUnavailableException`, and
  returns only the provider-neutral checkout result. Thirteen focused HTTP
  contract, mapping, validation, and failure scenarios pass; all 1259 project
  tests pass with no failures, errors, or skips.
- Conditional `TributeWebhookSignatureVerifier` authenticates the exact raw
  webhook bytes with HMAC-SHA256 and the configured Tribute API key, strictly
  decodes the 64-character hexadecimal `trbt-signature`, and compares byte
  arrays with `MessageDigest.isEqual`. It rejects missing, empty, malformed,
  changed, or incorrectly signed input without exposing the key, signature, or
  payload. Eighteen focused standard-vector, tampering, validation, and
  conditional-registration scenarios pass; all 1277 project tests pass with no
  failures, errors, or skips.
- `TributeWebhookEvent` deserializes the provider-owned `name`, explicit
  `created_at` and `sent_at` timestamps, and required object-shaped JSON payload
  only after raw-body signature verification. It validates a forward-compatible
  lowercase event-name format and timestamp ordering while ignoring unrelated
  future envelope fields. Twenty focused official-envelope, Jackson 3,
  normalization, boundary, timestamp, and payload-shape scenarios pass; all
  1297 project tests pass with no failures, errors, or skips.
- `TributeShopOrderPayload` deserializes only the order UUID, positive minor-unit
  amount, supported lowercase currency, paid status, recurrence marker, and
  monthly period needed from an initial successful `shop_order` event. It
  ignores provider-owned email, payment token, card metadata, fee, and future
  fields instead of copying them into the Java payment domain. Thirty focused
  official-payload, Jackson, supported-value, recurrence, and invalid-boundary
  scenarios pass; all 1327 project tests pass with no failures, errors, or
  skips.
- `InvalidTributeWebhookException` exposes one fixed safe message for a signed
  Tribute request whose envelope or payload cannot be accepted while optionally
  retaining a technical cause. `GlobalExceptionHandler` maps it to the standard
  `400 Bad Request` `ApiError` without logging or returning provider payload,
  email, token, or cause details. Two exception scenarios and one MockMvc leakage
  contract pass; all 1330 project tests pass with no failures, errors, or skips.
- Conditional `TributeWebhookPayloadDecoder` accepts only an exact
  `shop_order` envelope in its initial method and converts its object payload to
  the validated `TributeShopOrderPayload` through the shared Jackson 3
  `ObjectMapper`. Unsupported event names, malformed provider values, DTO
  invariant failures, and direct mapper failures become the fixed safe webhook
  exception without payload serialization or logging. Seven focused decoding,
  error-safety, argument, and interaction scenarios plus conditional Spring
  registration pass; all 1337 project tests pass with no failures, errors, or
  skips.
- Conditional `TributeWebhookCommandMapper` converts the validated initial
  `shop_order` envelope and payload into the existing provider-independent
  `SubscriptionPurchaseCompletionCommand`. It uses `TRIBUTE`, a deterministic
  `shop_order:{orderUuid}` event identity, the order UUID for both checkout and
  external subscription identity, a null unavailable customer identity, and an
  exact UTC calendar month beginning at the provider event creation instant.
  Nine focused mapping, idempotency, recurrence-independence, leap-year,
  year-transition, invalid-event, and null-contract scenarios plus conditional
  Spring registration pass; all 1346 project tests pass with no failures,
  errors, or skips.
- `InvalidTributeWebhookSignatureException` exposes one fixed safe message for a
  missing, malformed, or mismatched Tribute signature. `GlobalExceptionHandler`
  maps it separately to the standard `401 Unauthorized` `ApiError`, while signed
  but invalid webhook data remains the existing `400` contract; neither path
  logs or returns signature, body, key, or cause details. One exception scenario
  and one MockMvc security-boundary scenario pass; all 1348 project tests pass
  with no failures, errors, or skips.
- Conditional provider-specific `TributeWebhookService` orchestrates the initial
  successful payment without opening its own database transaction: it verifies
  the signature against exact raw bytes before any parsing, deserializes the
  signed envelope, decodes and maps the validated `shop_order`, and delegates the
  provider-independent command to the transactional purchase-completion service.
  It preserves the completion service's `true` new-event and `false` duplicate
  result, maps only envelope decoding failures to the safe webhook error, and
  lets downstream business failures propagate unchanged. Eight focused ordering,
  signature-boundary, decoding, duplicate, propagation, and dependency scenarios
  plus conditional Spring registration pass; all 1356 project tests pass with no
  failures, errors, or skips.
- Conditional, OpenAPI-hidden `TributeWebhookController` exposes the exact
  `POST /api/payments/webhooks/tribute` callback, forwards the untouched request
  bytes and optional `trbt-signature` once to `TributeWebhookService`, and
  returns an empty `200 OK` for both newly processed and duplicate valid events.
  Spring Security permits only this exact callback method and path without JWT;
  signature authentication remains inside the webhook service, while `GET`,
  nested callback paths, and authenticated purchase endpoints remain protected.
  Standalone MVC, conditional-registration, and security-slice coverage verifies
  raw-byte preservation plus the `200`, `400`, `401`, `404`, and `415` contracts;
  all 1370 project tests pass with no failures, errors, or skips.
- The runtime now declares Spring Boot's dedicated `spring-boot-restclient`
  module, so enabling Tribute in the complete application provides the
  auto-configured `RestClient.Builder` required by `TributeHttpClientConfig`.
  This closes a configuration gap that isolated client tests had previously
  hidden while Tribute remained disabled in the ordinary full context.
- `TributeWebhookFlowIntegrationTest` starts the complete application with
  Tribute enabled and a synthetic key, persists a real pending checkout intent,
  sends the exact HMAC-signed `shop_order` bytes through Spring Security and
  MVC twice, and verifies one active subscription, one consumed intent, and one
  durable processed-event row. The duplicate callback remains an empty
  `200 OK` and does not duplicate state; all 1371 project tests pass with no
  failures, errors, or skips.
- Flyway V9 creates `processed_payment_events` as the minimal durable webhook
  idempotency ledger, keyed uniquely by provider and external event ID without
  retaining payloads, signatures, secrets, card data, or payment details.
- V9 validates internal provider codes and non-blank external event IDs/types,
  timestamps committed processing, and indexes processing time for audit or a
  future retention policy. It was syntax-checked in a rolled-back PostgreSQL
  transaction before Flyway applied it; its history entry, five constraints,
  three indexes, and the complete 987-test suite were verified.
- `ProcessedPaymentEvent` maps the V9 ledger as a Hibernate-immutable JPA entity:
  all columns are non-updatable, the database supplies `processed_at`, and the
  class exposes no setters or public construction path for ordinary JPA writes.
  The mapping was verified against the local V9 schema by the complete 987-test
  suite.
- `ProcessedPaymentEventRepository` deliberately exposes no ordinary JPA
  `save`/`delete` API. Its native PostgreSQL insert atomically returns `1` for a
  newly reserved provider event and `0` for a duplicate provider/event-ID pair.
  Three transactional integration scenarios verify both outcomes, preservation
  of the original row on a duplicate, database-generated processing time, and
  provider-scoped uniqueness.
- `SubscriptionRenewalCommand` is the provider-independent input for a paid
  period renewal. It validates the internal provider code and period boundaries,
  normalizes bounded external event/subscription IDs, and carries no Tribute- or
  HTTP-specific annotations. Twenty-five focused scenarios cover its valid and
  invalid boundaries.
- `SubscriptionRenewalService` atomically reserves a normalized renewal event,
  ignores a committed duplicate before loading the subscription, locks a new
  event's provider-managed subscription, and applies `renewPaidPeriod(...)`
  through JPA dirty checking. Seven unit scenarios verify ordering and failure
  propagation; three PostgreSQL scenarios prove successful joint commit and
  rollback of the ledger reservation on missing subscriptions or domain failure.
- `SubscriptionPaymentFailureCommand` is the provider-independent input for a
  failed recurring payment. It validates the provider code and normalizes bounded
  external event/subscription IDs without carrying HTTP, Tribute, user, plan, or
  billing-period details. Eighteen focused scenarios verify its boundaries.
- `SubscriptionPaymentFailureService` atomically reserves a normalized failed-
  payment event, ignores duplicates before loading state, locks the external
  subscription, and applies `markPastDue()`. Seven unit scenarios verify ordering
  and failure propagation; three PostgreSQL scenarios prove the joint commit,
  duplicate no-op, and ledger rollback for missing or already-`PAST_DUE`
  subscriptions while preserving the paid-period boundaries.
- `SubscriptionPaymentRecoveryCommand` carries a provider-confirmed replacement
  paid period for recovery from `PAST_DUE`. It validates period ordering and the
  normalized provider/event/subscription reference without requiring contiguity
  with the previous period. Twenty-five focused scenarios verify its boundaries.
- `SubscriptionPaymentRecoveryService` atomically reserves the normalized
  recovery event, ignores duplicates before loading state, locks the external
  subscription, and applies `recoverAfterPayment(...)`. Seven unit scenarios and
  three PostgreSQL scenarios prove ordering, replacement-period commit,
  duplicate no-op, and ledger rollback for missing or still-`ACTIVE`
  subscriptions.
- `SubscriptionCancellationScheduleCommand` is the provider-independent input
  for cancellation at the current paid-period boundary. It carries only the
  validated provider/event/subscription reference; the effective cancellation
  instant remains owned by the subscription's persisted period. Eighteen focused
  scenarios verify its boundaries.
- `SubscriptionCancellationScheduleService` atomically reserves the cancellation
  event, ignores duplicates before loading state, locks the external subscription,
  and applies `scheduleCancellationAtPeriodEnd()`. Seven unit scenarios and three
  PostgreSQL scenarios prove ordering, rollback, preserved period/status, and that
  the scheduled subscription remains effective for paid access until period end.
- `SubscriptionCancellationRevocationCommand` is the provider-independent input
  for withdrawing a scheduled cancellation. It validates and normalizes only the
  provider/event/subscription reference because the existing paid period remains
  unchanged. Eighteen focused scenarios verify its boundaries.
- `SubscriptionCancellationRevocationService` atomically reserves the revocation
  event, ignores duplicates before loading state, locks the external subscription,
  and applies `revokeCancellationAtPeriodEnd()`. Seven unit scenarios and three
  PostgreSQL scenarios prove ordering, rollback, preserved active period/status,
  and removal of the scheduled-cancellation flag.
- `SubscriptionImmediateCancellationCommand` is the provider-independent input
  for an early terminal cancellation. It validates and normalizes the provider/
  event/subscription reference without changing or retaining billing details.
  Eighteen focused scenarios verify its boundaries.
- `SubscriptionImmediateCancellationService` atomically reserves the terminal
  cancellation event, ignores duplicates before loading state, locks the external
  subscription, and applies `cancelImmediately()`. Seven unit scenarios and four
  PostgreSQL scenarios prove cancellation from both `ACTIVE` and `PAST_DUE`,
  immediate effective-entitlement revocation, duplicate no-op, and rollback for
  missing or already-terminal subscriptions.
- `SubscriptionExpirationCommand` is the provider-independent reference for a
  provider-reported period-end expiry. It intentionally carries no external time:
  local entitlement expiry remains governed by the persisted period and the Java
  service clock. Eighteen focused scenarios verify its identifier boundaries.
- `SubscriptionExpirationService` atomically reserves the normalized expiration
  event, ignores duplicates before loading state, locks the external subscription,
  reads the injected clock only after acquiring the lock, and applies
  `expire(clock.instant())`. Seven unit scenarios and five PostgreSQL scenarios
  prove exact period-end expiry from both `ACTIVE` and `PAST_DUE`, immediate
  entitlement revocation, duplicate no-op, and ledger rollback for early,
  missing, or already-terminal subscriptions.
- Flyway V8 creates the provider-independent `user_subscriptions` table with
  explicit lifecycle statuses, bounded billing periods, optional external
  provider identifiers, and foreign keys to users and subscription plans.
- V8 enforces one `ACTIVE` subscription per user, unique provider subscription
  identifiers, consistent provider binding, non-empty external identifiers,
  valid provider codes, and an end instant strictly after the period start.
- V8 was syntax-checked in a rolled-back PostgreSQL transaction before Flyway
  applied it; its Flyway history entry, all nine constraints, all five indexes,
  and the then-current complete application suite were verified.
- `SubscriptionStatus` defines the exact V8 lifecycle values `ACTIVE`,
  `PAST_DUE`, `CANCELED`, and `EXPIRED` for string-based JPA persistence.
- `UserSubscription` maps the complete V8 subscription table, creates either
  manual or provider-managed active subscriptions, validates active plans and
  period boundaries, and preserves the database provider-binding invariant.
- Its 27 focused entity tests cover both creation modes, initial state,
  normalization, optional customer identifiers, all null and period failures,
  provider-code boundaries, inactive plans, and external-ID constraints. The
  then-current complete suite and Spring JPA context pass with the new mapping.
- `UserSubscription` now schedules and revokes cancellation at the end of the
  paid period only while `ACTIVE`; both operations are idempotent and preserve
  the current status and period. Three focused tests cover scheduling,
  revocation, repeated calls, and state preservation.
- `UserSubscription.markPastDue()` strictly transitions `ACTIVE` to `PAST_DUE`,
  revokes paid entitlement immediately through the existing active-only lookup,
  clears scheduled cancellation, and preserves the paid-period boundaries.
- `recoverAfterPayment(...)` strictly restores `PAST_DUE` to `ACTIVE` with
  provider-confirmed period boundaries, validates the complete replacement
  before mutating state, and clears scheduled cancellation. Nine additional
  tests cover successful transitions, invalid source states, invalid periods,
  exact messages, and failed-transition state preservation.
- `cancelImmediately()` terminates either `ACTIVE` or `PAST_DUE` as `CANCELED`,
  while `expire(now)` terminates either state as `EXPIRED` only at or after the
  exclusive paid-period end. Both clear scheduled cancellation and preserve
  historical period boundaries; terminal records reject repeated transitions.
- Eight additional lifecycle tests cover both source states, exact expiration
  boundary behavior, early and missing instants, exact error messages, repeated
  terminal operations, and failed-transition state preservation.
- `renewPaidPeriod(...)` extends only an `ACTIVE` subscription without scheduled
  cancellation, requires the next paid period to be valid and exactly contiguous
  with the current period, preserves state on every validation failure, and
  rejects duplicate renewal events until webhook idempotency is introduced.
- Ten additional renewal scenarios cover successful advancement, cancellation,
  gaps, overlaps, null and invalid bounds, all non-active states, duplicate
  renewal, exact messages, and state preservation. The complete
  `UserSubscription` lifecycle now has 57 focused tests, and all 486 project
  tests pass.
- `UserSubscriptionRepository.findEffectiveActiveByUserIdAt(...)` resolves an
  `ACTIVE` subscription only within its inclusive-start/exclusive-end period,
  excludes inactive plans, and eagerly loads the associated plan.
- Five real-PostgreSQL repository tests verify persisted provider data and
  timestamps, plan loading, exact period boundaries, user isolation, status
  filtering, inactive-plan filtering, and transactional rollback. The complete
  then-current suite passes.
- `findByProviderAndExternalSubscriptionIdForUpdate(...)` resolves any lifecycle
  status by the V8-unique provider binding under `PESSIMISTIC_WRITE`, without
  loading unrelated plan data or filtering late provider events.
- A real two-transaction PostgreSQL test proves that a second handler for the
  same external subscription waits until the first transaction releases its row
  lock and then proceeds. Test data is explicitly cleaned up, and all 987 tests
  pass.
- Flyway V6 creates the provider-independent `subscription_plans` and
  `plan_entitlements` tables with database constraints for technical codes,
  period type, and mutually consistent limited/unlimited entitlements.
- V6 seeds the active `FREE` plan with a monthly
  `DOCUMENT_TRANSLATION` entitlement limited to 5 units.
- The migration has been applied to local PostgreSQL and its Flyway history,
  table contents, and full application test suite have been verified.
- `FeatureCode` and `PeriodType` define the stable Java codes
  `DOCUMENT_TRANSLATION` and `MONTH` used by the entitlement model.
- `SubscriptionPlan` maps `subscription_plans`, validates stable uppercase plan
  codes and display names, and exposes explicit rename, activation, and
  deactivation operations without coupling plans to provider-specific prices.
- Subscription-plan tests cover normalization, code and name boundaries,
  state changes, failed-renaming state preservation, and the complete JPA
  context mapping.
- `PlanEntitlementId` maps the composite `plan_code` and `feature_code`
  primary key, validates both components, and implements value-based equality
  required by JPA identifiers.
- Plan-entitlement-identifier tests cover valid construction, equality and
  hashing, invalid plan codes, and missing feature codes.
- `PlanEntitlement` maps `plan_entitlements` through the composite identifier
  and `@MapsId` plan association, and maintains mutually consistent limited
  and unlimited states through explicit factories and domain operations.
- Plan-entitlement tests cover both creation modes, state transitions,
  invalid limits, failed-transition state preservation, null dependencies,
  timestamps before persistence, and the complete JPA context mapping.
- `SubscriptionPlanRepository` provides persistence for plans by their stable
  string code.
- `PlanEntitlementRepository` looks up an entitlement by its composite ID only
  when the associated plan is active and loads that plan through an entity
  graph.
- Real-PostgreSQL repository integration tests verify the seeded FREE plan,
  its limited translation entitlement, composite-key persistence, unlimited
  entitlements, timestamp generation, eager plan loading, inactive-plan
  filtering, and transactional test-data rollback.
- `ResolvedEntitlement` is the immutable, persistence-independent result of
  effective-plan resolution and preserves the limited/unlimited invariant
  without exposing JPA entities to callers.
- Resolved-entitlement tests cover both valid states, all required fields, and
  missing, non-positive, or contradictory limit configurations.
- `FeatureNotAvailableException` represents a feature absent from the
  effective active plan without exposing plan, user, or persistence details.
- `GlobalExceptionHandler` maps unavailable plan features to `403 Forbidden`
  with the standard `ApiError` body; its full REST contract is covered by
  `MockMvc`.
- `EntitlementService` resolves the authenticated user's effective feature
  entitlement from an `ACTIVE`, time-effective subscription and uses FREE only
  when no such subscription exists. One injected UTC `Clock` instant is used
  for each subscription lookup, and callers remain persistence-independent.
- The service returns `ResolvedEntitlement` rather than JPA entities and
  rejects missing users, missing feature codes, inactive plans, and absent
  features through explicit contracts.
- Entitlement-service unit tests cover FREE fallback, paid-plan override,
  limited and unlimited mapping, missing entitlements, argument validation,
  the exact lookup instant, and repository interaction. Real-PostgreSQL tests
  verify both the seeded FREE translation limit and a persisted active paid
  subscription overriding it. The complete 456-test suite passes.
- Flyway V7 creates `feature_usage_records` for generic feature reservations
  with `RESERVED`, `CONSUMED`, and `RELEASED` states, positive units, explicit
  period boundaries, reservation expiry, and an optional translation-job
  association.
- V7 enforces user cascading, translation-job detachment, valid feature codes,
  consistent status/expiry combinations, and one usage record per associated
  translation job.
- PostgreSQL indexes support per-user quota calculation, expired-reservation
  cleanup, and unique translation-job association. V7 was syntax-checked in a
  rolled-back transaction before Flyway applied it, and the resulting history,
  constraints, indexes, empty initial table, and full test suite were verified.
- `UsageStatus` defines the exact persisted reservation states `RESERVED`,
  `CONSUMED`, and `RELEASED`.
- `FeatureUsageRecord` maps the V7 usage table, creates only valid active
  reservations, protects the two allowed terminal transitions, clears
  reservation expiry on completion, and prevents associating a translation job
  owned by another user.
- Entity tests cover valid reservation creation, all constructor invariants,
  consumption, release, owner validation, terminal-state protection, and state
  preservation after rejected operations. The full Spring context verifies the
  JPA mapping against the applied V7 schema.
- `FeatureUsageRecordRepository` calculates occupied quota as the sum of
  `RESERVED` and `CONSUMED` units for one user, feature, and exact period while
  excluding `RELEASED` records.
- Real-PostgreSQL repository integration tests verify JPQL parsing, persisted
  entity mapping and timestamps, status filtering, user and period isolation,
  and a zero result when no usage records match.
- `UserRepository.findByIdForUpdate(...)` applies `PESSIMISTIC_WRITE` to one
  account row without changing ordinary user lookups.
- A two-transaction PostgreSQL integration test verifies that a second quota
  operation for the same user waits until the first transaction releases its
  lock and then continues successfully.
- `UsageLimitExceededException` provides a fixed safe message for an exhausted
  periodic quota.
- `GlobalExceptionHandler` maps exhausted usage limits to the standard
  `ApiError` response with HTTP `429 Too Many Requests`; its status, message,
  path, timestamp, field errors, and JSON content type are covered by MockMvc.
- `UsagePeriod` is an immutable pair of period boundaries that rejects missing,
  equal, or reversed instants and is covered by focused value-object tests.
- `UsagePeriodCalculator` deterministically resolves `MONTH` to an inclusive
  UTC month start and exclusive next-month start from an explicit reference
  instant.
- Its focused tests cover an ordinary month, the exact start boundary,
  leap-year February, the December-to-January transition, and missing
  arguments.
- `UsageProperties` provides a validated positive reservation TTL, exposed as
  `USAGE_RESERVATION_TTL` with a safe local default of 15 minutes.
- The same configuration record validates the expired-reservation cleanup
  interval and batch size, exposed as `USAGE_CLEANUP_INTERVAL` and
  `USAGE_CLEANUP_BATCH_SIZE` with safe local defaults of one minute and 100
  records.
- `UsageConfig` registers those properties and a UTC `Clock`; focused Spring
  binding tests verify all duration and batch conversions and the clock zone.
- `UsageLimitService.reserve(...)` validates the request, locks the user row,
  resolves the current entitlement, calculates the UTC period, rejects quota
  overflow, and persists a TTL-bound `RESERVED` usage record in one short
  transaction.
- Its unit tests cover the exact limited boundary, exceeded quota, unlimited
  usage without a count query, missing users, invalid arguments, call order,
  and the persisted reservation state.
- `FeatureUsageRecordRepository.findByIdForUpdate(...)` loads any usage status
  under a `PESSIMISTIC_WRITE` lock so concurrent terminal transitions cannot
  both modify the same reservation.
- A two-transaction PostgreSQL integration test verifies that the second lock
  waits until the first transaction completes.
- `UsageLimitService.consume(...)` locks the reservation, loads only a
  translation job owned by the same user, and changes the managed entity to
  `CONSUMED` through its domain transition without a redundant repository
  save.
- Its tests cover successful consumption, missing reservations, missing or
  foreign jobs, repeated consumption, missing arguments, call order, retained
  state on failure, and dirty-checking behavior.
- `UsageLimitService.release(...)` locks the reservation and changes only an
  active `RESERVED` record to `RELEASED`, clearing its expiration through the
  entity transition and relying on dirty checking.
- Its tests cover successful release, missing reservations, repeated release,
  attempts to release consumed usage, missing identifiers, unchanged terminal
  state, and the absence of redundant repository saves.
- A real-PostgreSQL `UsageLimitService` integration test verifies persisted
  `RESERVED`, `CONSUMED`, and `RELEASED` states, translation-job linkage,
  expiration clearing, and the occupied-unit result.
- The same integration test launches six simultaneous FREE-plan reservations;
  the user-row lock admits exactly five unique records and rejects the sixth
  with `UsageLimitExceededException`.
- `FeatureUsageRecordRepository.findExpiredReservationsForUpdate(...)`
  selects only expired `RESERVED` records in deterministic oldest-first
  batches and locks them for update inside the cleanup transaction.
- Its real-PostgreSQL integration test verifies status and time filtering, the
  inclusive expiration boundary, deterministic ordering, and batch limiting.
- `UsageLimitService.releaseExpiredReservations(...)` validates the requested
  batch size, locks one bounded batch at a single clock instant, releases each
  managed reservation through its domain transition, and returns the number of
  released records without redundant repository saves.
- Unit tests cover successful batch release, an empty batch, exact repository
  arguments, invalid batch sizes, state transitions, and dirty-checking
  behavior.
- `UsageReservationCleanupScheduler` periodically delegates one configured
  cleanup batch to `UsageLimitService`, waits for the configured delay both
  before its first run and after each completed run, and logs only non-empty
  cleanup results.
- `UsageConfig` enables Spring Scheduling; focused tests verify scheduler
  delegation, both delay expressions, empty cleanup handling, and registration
  of the scheduled-annotation processor.
- `AccountUsageResponse` defines the immutable snake_case account contract for
  limited and unlimited entitlements, including plan identity, feature,
  period, used and remaining units, and reset time with guarded invariants.
- `AccountUsageService` validates the account, resolves the current
  `DOCUMENT_TRANSLATION` entitlement, calculates the UTC period, counts the
  same occupied units enforced by reservation, clamps limited remaining usage
  to zero, and preserves nullable limit fields for unlimited plans.
- Protected `GET /api/account/usage` derives the user UUID only from JWT and
  returns the Java-owned effective plan and current usage state.
- DTO, service, MVC security/error, and real-infrastructure tests cover exact
  JSON, limited and unlimited states, missing accounts, `401`, `404`, and the
  observed `5 used / 0 remaining` state after exhausting the FREE upload limit.
- FREE is a real plan with limited entitlements; an active paid subscription
  overrides it.
- Subscription plans are not Spring Security roles.
- Current subscription and usage are not embedded in JWT because they can
  change before token expiry.
- Java, not frontend, owns every entitlement and quota decision.
- The target usage flow reserves a unit transactionally before external side
  effects, consumes it after RabbitMQ acceptance, and releases it on earlier
  infrastructure failure.
- Full decisions, target tables, API behavior, payment flow, and staged
  implementation are in `ACCOUNT_PROFILE_SUBSCRIPTION_ARCHITECTURE.md`.

### MinIO storage

- MinIO and OkHttp JVM dependencies.
- Validated `StorageProperties`.
- `StorageConfig` and `MinioClient`.
- MinIO service, persistent volume, ports, and healthcheck in `compose.yaml`.
- `StorageInitializer` that creates the configured bucket if absent.
- `StorageException`.
- `StorageService.upload(...)` using streamed `PutObjectArgs`.
- `StorageService.delete(...)` using `RemoveObjectArgs` for compensation when a
  later upload-flow step fails.
- `StorageService.download(...)` using `GetObjectArgs` and returning the MinIO
  object stream without closing it prematurely.
- `StorageKeyGenerator` producing:

```text
uploads/{userId}/{randomUuid}.{extension}
```

- Unit tests for initialization, upload, download and deletion arguments,
  failures, validation, key structure, and uniqueness.

### RabbitMQ messaging

- Validated `MessagingProperties`.
- `RabbitConfig` declaring:
  - durable direct exchange;
  - durable task queue;
  - routing-key binding;
  - Jackson 3 JSON message converter.
- `TranslationTaskMessage` contract:

```json
{
  "job_id": "uuid",
  "file_key": "uploads/user-id/file-id.docx",
  "result_file_key": "results/user-id/result-id.docx",
  "source_lang": "en",
  "target_lang": "ru",
  "format": "docx"
}
```

- Exact JSON serialization test.
- `MessagePublishingException`.
- `TranslationTaskPublisher` using `RabbitTemplate.convertAndSend`.
- Publisher unit tests for routing, null validation, and AMQP failure wrapping.
- Durable `translation.status` queue and binding for ML-to-Java events.
- `TranslationStatusMessage` contract:

```json
{
  "job_id": "uuid",
  "status": "PROCESSING",
  "progress": 43,
  "result_file_key": null,
  "error_message": null
}
```

- `TranslationStatusListener` consuming the reverse status queue.
- `TranslationStatusUpdateService` validating and applying ML-owned progress:
  - ML cannot restore `PENDING`;
  - `PROCESSING` progress is monotonic and limited to `0..99`;
  - `DONE` requires progress `100`, the exact Java-generated expected result
    key, and a present MinIO object whose binary format matches the job;
  - source, avatar, unrelated result, and another user's keys are rejected;
  - `FAILED` requires a diagnostic message of at most 2,000 characters and
    retains the latest progress;
  - REST status/history responses expose only `TRANSLATION_FAILED` and a safe
    public message, never the internal ML diagnostic;
  - a pessimistic row lock makes concurrent updates deterministic;
  - duplicate and late progress events do not corrupt terminal jobs;
  - malformed domain messages are rejected without requeue, while unexpected
    infrastructure failures remain eligible for requeue.
- Exact status-message JSON test, listener tests, transition tests, RabbitMQ
  topology tests, and an end-to-end RabbitMQ consumer persistence test.

### Docker deployment

- Multi-stage `Dockerfile` using Java 21:
  - Maven Wrapper and a JDK image build the executable Spring Boot archive;
  - a separate JRE image contains only the runtime application;
  - the application runs as the non-root `app` user.
- Allowlist-based `.dockerignore` keeps `.env`, local build output, IDE files,
  and documentation out of the Docker build context.
- The `backend` service in `compose.yaml`:
  - builds the local Java image;
  - receives secrets and runtime configuration through environment variables;
  - uses Docker DNS names and internal ports for PostgreSQL, RabbitMQ, and
    MinIO;
  - waits for all three infrastructure healthchecks;
  - exposes the configurable public backend port;
  - checks `/actuator/health` and reports `healthy`.

### OpenAPI documentation

- springdoc-openapi `3.0.3`, compatible with the project's Spring Boot 4
  baseline, generates the OpenAPI document and Swagger UI.
- Documentation is disabled by default and can be enabled through the
  non-secret `OPENAPI_DOCS_ENABLED` environment variable for local use.
- Spring Security permits only the OpenAPI and Swagger UI resources; protected
  application endpoints still require JWT authentication.
- The OpenAPI contract declares HTTP Bearer JWT authentication for document,
  profile, and avatar endpoints while registration and login remain public.
- Integration tests explicitly enable documentation and verify the generated
  metadata, security scheme, per-operation security, public auth operations,
  and unauthenticated Swagger UI access.

## Stage 3 — Reliable RabbitMQ delivery completed

- Correlated publisher confirms, mandatory publishing, returned-message
  detection, and bounded confirm timeouts are enabled. ACK, NACK, timeout,
  missing-exchange, and unroutable cases are covered by unit/integration tests.
- Task and status topology now uses versioned durable classic queues
  `translation.tasks.v2` and `translation.status.v2`, dedicated DLX/DLQs,
  bounded queue lengths, `reject-publish-dlx`, persistent delivery, a 64 KB
  message limit, prefetch 10, and listener concurrency 1..4.
- Status processing uses bounded retry/backoff. Permanent validation and JSON
  conversion failures skip retry and are quarantined. Retry exhaustion and DLQ
  depth have Micrometer counters/gauges and content-free operational logs.
- Migration V17 adds `translation_outbox_events`. Translation job, quota
  consumption, and outbox creation now commit in one PostgreSQL transaction.
- The outbox worker claims rows with `FOR UPDATE SKIP LOCKED`, publishes with
  asynchronous confirms, applies bounded exponential backoff, recovers expired
  claims, rejects stale confirms, and marks an event published only after ACK
  plus successful routing.
- The task contract now contains a stable `event_id`; the ML service must
  persistently deduplicate by it because delivery is intentionally at-least-once.
- Crash-boundary tests cover upload cleanup after transaction failure, atomic
  rollback when outbox persistence fails, broker failure, ACK-before-DB-update,
  expired claim recovery, and late confirms. A 100-message Rabbit load test
  completed without loss.
- Verification: Flyway reached V17; all 1431 tests pass; Compose was rebuilt;
  backend health is `UP`; RabbitMQ reports `max_message_size=65536`; all four
  v2 source/DLQ queues are durable and have the expected arguments.

## Stage 4 — Financial and subscription correctness completed

- Purchase-intent creation locks the account row, rejects effective paid
  subscriptions and unexpired pending intents before any provider call, and
  reconciles expired state. PostgreSQL also enforces one pending intent and one
  live paid subscription per user.
- Every intent stores an immutable commercial snapshot. Signed completion and
  recurring events verify amount, currency, billing period, and product
  identity before granting or extending access.
- Provider event, checkout/order, customer, and subscription identifiers are
  modelled separately. Tribute's documented recurring identity is stored as
  the external order ID rather than being mislabeled as a subscription ID.
- Tribute lifecycle routing covers initial success/failure, recurring charge
  success/failure and recovery, cancellation, and refund/revocation. All
  events use durable database idempotency; local reconciliation handles a
  missed expiration event.
- Checkout attachment failure triggers provider compensation and local intent
  cancellation. Stale intents and expired subscriptions are reconciled in
  bounded scheduled batches.
- The webhook reads a bounded raw body, verifies HMAC over the exact bytes,
  uses constant-time signature comparison, applies a local request-rate guard,
  and returns safe `413`/`429` responses without logging bodies or signatures.
- Tribute outbound configuration accepts only the official HTTPS API origin,
  rejects redirects, and validates returned Telegram checkout URLs.
- The private account now exposes `GET /api/account/subscription` and
  provider-independent `POST /api/account/subscription/cancellation`, without
  leaking provider identifiers.
- Processed event and terminal intent retention is configurable, bounded, and
  observable through content-free maintenance logs.
- Flyway V18 contains the commercial snapshots, separated provider IDs,
  uniqueness constraints, and cleanup/reconciliation indexes.
- Verification: all 1458 tests pass with zero failures, errors, or skips;
  concurrent checkout and controller-to-database Tribute lifecycle tests pass;
  Compose configuration is valid; the rebuilt container reports Actuator
  status `UP`.

## Stage 5 — Authentication and public API security completed

- Registration, login, account-email actions, document upload, checkout, and
  Tribute webhook processing have separate bounded request policies. Limits
  use the authenticated account where available and the direct client address;
  forwarded addresses remain ignored until a trusted proxy policy exists.
- All rate-limit failures return the standard `429 ApiError`, a correlation
  identifier, and `Retry-After`. Login also applies an email-keyed limiter
  without storing or logging the submitted address.
- JWT access tokens default to 15 minutes and validate signature, active key
  ID, issuer, audience, account version, and email-verification state. TTL is
  constrained to a positive maximum of one hour, and time-dependent behavior
  uses an injected `Clock`.
- The lifecycle decision is short-lived access tokens with mandatory re-login,
  without refresh tokens. Password reset and `POST /api/auth/logout-all`
  increment the persisted account authentication version, immediately
  invalidating every earlier access token. Rotation keeps one active signing
  key and does not accept retired secrets indefinitely.
- Email verification and password recovery use cryptographically random,
  expiring, single-use tokens; only token hashes are stored. Delivery happens
  after transaction commit and can be enabled through SMTP configuration.
  Missing accounts receive the same public request/login behavior and are not
  disclosed.
- New accounts must verify email before document upload or subscription
  checkout. Existing accounts were safely grandfathered by migration V19.
- Concurrent registration is resolved by the PostgreSQL unique constraint and
  mapped deterministically to the same safe `409` response as the pre-check.
- REST errors now include a correlation ID. Multipart limits return `413`,
  unsupported media types return `415`, data conflicts return safe `409`, and
  unexpected failures return a non-leaking `500`; full causes remain only in
  protected logs.
- CORS is disabled by default for same-origin deployment. When enabled, only
  exact validated origins are accepted; wildcard origins with credentials are
  rejected. OpenAPI is disabled by default and requires authentication unless
  its separate local public-access switch is enabled.
- Flyway validates and applies V19 and V20 for account security state and
  hashed action tokens. The complete suite passes all 1485 tests with zero
  failures, errors, or skips.
- Compose configuration is valid. The rebuilt image runs as non-root user
  `app`; the running container matches the recorded image ID, all four Compose
  services are healthy, and Actuator reports `UP`.

## Stage 6 — Storage and profile consistency completed

- MinIO bootstrap now creates the document bucket, a dedicated Java-backend
  account, and a bucket-scoped policy containing only the required location,
  list, multipart, get, put, and delete object operations. Root credentials
  remain available only to MinIO and the one-shot bootstrap container.
- Application startup verifies that the configured bucket is reachable but no
  longer attempts privileged bucket creation. A real integration test confirms
  upload, download, and delete with the restricted account and denial of bucket
  creation.
- Storage endpoint configuration accepts only HTTP/HTTPS origins without
  credentials, path, query, or fragment. HTTP requires an explicit local flag,
  and every endpoint host must be present in the exact allowlist; production
  configuration can therefore require HTTPS and its approved host.
- JPEG and PNG avatars are now fully decoded under upload-size, dimension,
  pixel-count, and decoded-memory limits. They are re-encoded to canonical
  JPEG/PNG bytes, removing source metadata and trailing untrusted data before
  storage.
- Profile and avatar mutations take a pessimistic row lock. Replacement and
  deletion update PostgreSQL first, remove superseded objects only after a
  successful transaction commit, and compensate a failed database write by
  deleting the newly uploaded object.
- A bounded scheduled cleanup removes old unreferenced objects under the
  `avatars/` prefix while retaining keys still referenced by a profile.
- Verification: all 1483 tests pass with zero failures, errors, or skips. The
  restricted-MinIO test and concurrent replacement/delete tests pass against
  local PostgreSQL and MinIO. Compose configuration is valid; the rebuilt
  backend container is healthy, Actuator reports `UP`, and inspection confirms
  that no MinIO root variable is passed to the Java container. Local root and
  backend credentials were rotated independently; the focused integration
  tests also pass with the rotated backend account.

## Stage 7 — Production Docker and operations implemented

- Local `compose.yaml` is explicitly separated from the standalone
  `compose.production.yaml`. The production topology publishes only Caddy on
  ports 80/443, keeps application, PostgreSQL, RabbitMQ, Prometheus, and
  Alertmanager endpoints private, uses internal data/operations networks, and
  contains no fixed container names or image builds.
- Caddy terminates TLS, redirects HTTP, replaces untrusted forwarding headers,
  blocks operational paths on the public route, applies body/header/time
  limits, and emits JSON access logs. Spring trusts forwarded headers only in
  the production private-proxy configuration.
- Production secrets are loaded through config-tree/Docker secrets rather
  than `.env`. Safe templates and validators cover required secret files,
  minimum strength, JWT decoding, credential separation, and non-placeholder
  Alertmanager configuration. Rotation procedures are documented for JWT,
  PostgreSQL, RabbitMQ, MinIO, Tribute, and SMTP.
- The backend image and production services run with non-root/read-only and
  least-privilege controls where compatible: dropped capabilities,
  `no-new-privileges`, bounded tmpfs, CPU/memory/PID/log limits, graceful
  shutdown, stop grace periods, and immutable release-image requirements. The
  Java 21 build/runtime bases are pinned by digest.
- `/livez` is independent of external dependencies. `/readyz` checks
  PostgreSQL, RabbitMQ, and access to the configured MinIO bucket without
  exposing health details. The production management port privately exposes
  Prometheus metrics only when the exporter is explicitly enabled.
- Prometheus collects HTTP/error/latency, RabbitMQ queue, DLQ, outbox,
  database-pool, payment, quota, and storage signals. Ten validated alert rules
  cover sustained failures, latency, queue buildup, payment/storage failures,
  quota rejection spikes, pool exhaustion, and low disk.
- HTTP requests, RabbitMQ tasks/statuses, and payment webhook processing carry
  correlation identifiers. Production logs use structured ECS JSON with
  bounded Docker retention; the runbook defines redaction and central-log
  access requirements.
- `scripts/backup-production.sh` creates PostgreSQL and MinIO application-level
  backups with SHA-256 manifests and age encryption. The operations runbook
  defines retention, permissions, RabbitMQ/outbox recovery, RPO/RTO objectives,
  and an isolated monthly restore drill. A successful real restore drill is an
  external release gate and remains intentionally unchecked in the backlog
  until production infrastructure exists.
- Verification: all 1488 Maven tests pass. Production Compose validation,
  Caddy validation, and Prometheus validation all pass; Prometheus reports ten
  valid alert rules. The rebuilt local backend and all infrastructure services
  are healthy, and health/liveness/readiness each report `UP`. Docker Scout
  generated the SBOM but could not complete the vulnerability query because
  the local CLI requires Docker ID authentication; the release scan must be
  repeated in an authenticated pipeline.

## Configuration

- Local runtime values are loaded from `.env`. Production must not use `.env`;
  it loads secrets from the platform secret store through Docker/config-tree
  secret files and obtains non-secret deployment variables separately.
- `.env.example` documents PostgreSQL, JWT, MinIO, RabbitMQ connection,
  confirms, DLX/DLQ, capacity, retry, outbox, observation settings, reservation
  and cleanup settings,
  document file/request size limits, the local OpenAPI switch, and the public
  backend port, using placeholders.
- Never copy real secrets into this status file, `AGENTS.md`, logs, or answers.

## Next implementation step

Stage 8 is complete. The next release task requires a production-like external
environment: connect the deployment to the approved secret-management
platform, execute the first encrypted backup restore drill, and record measured
RPO/RTO. Until that environment exists, further Java feature work can return to
the usual collaborative mode one focused class or method at a time.

## Remaining major work

- Execute and record the first encrypted production restore drill when the
  production database/object store exist; repeat it at least monthly.
- Complete demo Compose topology once the frontend and ML-service modules are
  supplied by their owners.

## Stage 8 — Architecture and long-term reliability completed

- Entitlement resolution, usage periods, subscription lifecycle operations,
  and payment reconciliation use one captured authoritative `Instant` per
  business operation. Exact month, leap-year, subscription-start, and
  subscription-end boundaries are covered by tests.
- PostgreSQL pessimistic locks have a finite `DB_LOCK_TIMEOUT_MS` setting.
  Transient lock/contention failures return a safe retryable `503` with
  `Retry-After`, and increment the tagged
  `translatelab.database.contention` metric. A real two-transaction integration
  test verifies that PostgreSQL aborts the waiter within the configured bound.
- `compose.test.yaml` provides disposable, digest-pinned PostgreSQL, RabbitMQ,
  MinIO and Java 21 infrastructure without reading `.env`, mutable developer
  data, persistent application volumes, published host ports, or manually
  prepared queues. `scripts/test-isolated.ps1` runs the complete Maven quality
  profile and exports JaCoCo/Surefire reports to `target/isolated-quality`.
- The `quality` Maven profile applies baseline-compatible Checkstyle rules,
  SpotBugs plus FindSecBugs, and JaCoCo reporting. The release workflow also
  scans Flyway migrations and production configuration for destructive SQL,
  unsafe documentation/storage defaults, environment-passed secrets, and a
  missing finite lock timeout.
- `scripts/release-verify.ps1` runs configuration scanning and the complete
  isolated Java 21 gate before it builds an image, then probes health,
  liveness, and readiness on that exact image. The Dockerfile's skipped tests
  are therefore only a packaging optimization, not the release gate.
- `FAILURE_INJECTION_MATRIX.md` maps PostgreSQL-before/after-upload, MinIO
  timeout/partial cleanup, RabbitMQ disconnect/NACK/return/confirm-timeout,
  outbox interruption/replay, payment rollback/deduplication, and concurrent
  quota scenarios to executable tests.
- Verification on 2026-08-11: the isolated Java 21.0.11 quality gate passed all
  1494 tests with zero failures/errors/skips; Checkstyle reported zero
  violations, SpotBugs/FindSecBugs reported zero High findings, and JaCoCo
  analyzed 237 classes with 92.4% instruction and 79.2% branch coverage. These
  values are tracked as a diagnostic baseline, not as a target to game. Exact image
  `sha256:4d44253f3c1597ab61a8a4809f56071a18191d3766eb0a928d88fdb817bab7`
  passed `/actuator/health`, `/livez`, and `/readyz`, all with status `UP`.
