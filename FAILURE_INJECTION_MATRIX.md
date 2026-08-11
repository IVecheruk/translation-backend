# Java backend failure-injection matrix

This matrix is the focused reliability gate for Stage 8. The scenarios model
process interruption at transactional boundaries by stopping execution before
commit, replaying the same stable external identifier, and verifying the
persisted state after retry. Infrastructure services are disposable and start
from `compose.test.yaml`.

| Failure boundary | Verification |
| --- | --- |
| MinIO fails before PostgreSQL job/outbox creation | `DocumentUploadServiceTest.shouldNotCreateDatabaseStateWhenStorageUploadFails` |
| PostgreSQL job/outbox transaction fails after upload | `DocumentUploadServiceTest.shouldDeleteObjectWhenDatabaseTransactionFails`, `TranslationJobCreationRollbackIntegrationTest` |
| Upload succeeds and compensating MinIO delete also fails | `DocumentUploadServiceTest.shouldPreserveCleanupFailureAsSuppressed` |
| MinIO request times out | `StorageServiceTest.shouldMapMinioUploadTimeoutToStorageException` |
| RabbitMQ disconnect/immediate failure | `TranslationTaskPublisherTest.shouldWrapImmediateAmqpFailure`, `RabbitReliabilityIntegrationTest.shouldFailForMissingExchange` |
| Broker NACK | `TranslationTaskPublisherTest.shouldFailOnBrokerNack` |
| Mandatory message is returned | `TranslationTaskPublisherTest.shouldFailWhenMessageIsReturnedAsUnroutable`, `RabbitReliabilityIntegrationTest.shouldFailForUnroutableMandatoryMessage` |
| Publisher confirmation times out | `TranslationTaskPublisherTest.shouldFailWhenConfirmTimesOut` |
| Backend stops before outbox publish | `TranslationOutboxTransactionServiceTest.shouldReuseStableEventIdWhenExpiredClaimIsRecovered` |
| Backend stops after broker confirmation but before outbox state update | `TranslationOutboxTransactionServiceTest.shouldIgnoreLateConfirmationFromOlderAttempt`, `TranslationOutboxPublisherServiceTest.shouldNotCountStaleConfirmationAsPublished` |
| Payment transaction stops after idempotency marker but before commit | rollback tests in `SubscriptionPurchaseCompletionServiceIntegrationTest` |
| Payment event is delivered repeatedly | duplicate-event tests in all `Subscription*ServiceIntegrationTest` lifecycle suites |
| Concurrent quota reservation retries | `UsageLimitServiceIntegrationTest.shouldAllowOnlyFiveParallelFreeReservations` |

The release gate runs these tests again as part of the complete isolated Java
21 suite. Assertions cover database state, stable event identifiers, bounded
retry state, and absence of duplicate subscriptions or quota consumption; a
percentage-only coverage target is intentionally not used.
