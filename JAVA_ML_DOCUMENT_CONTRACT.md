# Java ↔ ML document-ingestion contract

This document fixes the document safety boundary shared by the Java backend
and the Python ML translation service. It contains no credentials or internal
deployment addresses.

## Accepted input

Java accepts only `DOCX`, `DOC`, and `PDF` after checking both the filename
extension and the binary content. The client-provided HTTP `Content-Type` is
ignored as a trust signal.

- `PDF`: must start with `%PDF-`, be parseable as PDF, and contain at least one
  page.
- `DOC`: must be an OLE2 compound document that Apache POI can open as an
  `HWPFDocument`; an arbitrary OLE2 container is not sufficient.
- `DOCX`: must be a parseable OOXML Word package with
  `[Content_Types].xml`, `_rels/.rels`, and `word/document.xml`. The main part
  must use
  `application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml`.

Java validates the document before writing it to MinIO, creating a translation
job, reserving quota, or publishing a RabbitMQ task. ML may therefore assume
that Java performed these checks, but ML must still enforce the same or
stricter limits because stored objects and messages remain an external trust
boundary for the worker.

## Size and DOCX archive limits

The default Java boundary is:

| Limit | Default | Java configuration |
|---|---:|---|
| Uploaded file size | 20 MB | `DOCUMENT_MAX_FILE_SIZE` |
| ZIP entry count | 2,000 | `DOCUMENT_DOCX_MAX_ENTRIES` |
| One uncompressed entry | 50 MB | `DOCUMENT_DOCX_MAX_ENTRY_SIZE` |
| Total uncompressed content | 100 MB | `DOCUMENT_DOCX_MAX_TOTAL_UNCOMPRESSED_SIZE` |
| Minimum compressed/uncompressed ratio | 0.01 | `DOCUMENT_DOCX_MIN_COMPRESSION_RATIO` |

Nested ZIP archives, duplicate entry names, absolute paths, backslashes, and
path traversal segments are rejected. Limits are measured while streaming
entry data; declared ZIP sizes are not trusted.

Before changing any limit, the Java and ML owners must agree on compatible
values. The worker must never configure a weaker maximum than it can safely
parse in its memory and CPU budget.

## Stored metadata and keys

Java generates the source object key independently of the original filename:

```text
uploads/{userId}/{randomUuid}.{verifiedExtension}
```

Java also generates and persists the only permitted result object key before
publishing the task:

```text
results/{userId}/{randomUuid}.{verifiedExtension}
```

The database enforces this exact owner/UUID/format namespace. ML must write the
translated document only to the `result_file_key` supplied by Java. ML must
never derive a different key from a filename, user input, source key, or its
own identifier.

The MinIO MIME type is derived only from the verified format:

| Format | MIME type |
|---|---|
| DOCX | `application/vnd.openxmlformats-officedocument.wordprocessingml.document` |
| DOC | `application/msword` |
| PDF | `application/pdf` |

The RabbitMQ task carries stable `event_id`, `job_id`, the generated source
`file_key`, the Java-generated `result_file_key`, normalized source and target
languages, and the verified lowercase format. The ML worker must persistently
deduplicate task consumption by `event_id`. Java uses at-least-once delivery:
the same event may be delivered again after a backend or broker boundary
failure, while its payload remains unchanged.

The default task payload maximum is 64 KB. The current durable classic queues
are `translation.tasks.v2` and `translation.status.v2`; their names are
configurable and must be coordinated between Java and ML deployments.

## Status events and result acceptance

- `PROCESSING` carries progress `0..99` and no result or error field.
- `DONE` carries progress `100`, repeats the exact Java-supplied
  `result_file_key`, and carries no error field.
- Before accepting `DONE`, Java downloads that exact object and validates its
  binary content as the job's verified `DOCX`, `DOC`, or `PDF` format.
- A source, avatar, another user's result, an absent object, or a malformed
  document can never become the downloadable result of the job.
- `FAILED` carries progress `0..99`, no result key, and a diagnostic
  `error_message` of at most 2,000 characters.

The ML diagnostic is stored only as internal data. Status and history REST
responses expose the stable `TRANSLATION_FAILED` code and the safe public
message `Не удалось перевести документ`; they never return ML stack traces,
credentials, storage keys, or other diagnostic details.

Java locks the translation-job row while applying an event. Progress is
monotonic, duplicate terminal events are idempotent, late progress is ignored,
and the first committed terminal state (`DONE` or `FAILED`) cannot be replaced
by the other terminal state.

## Retention defaults

- source of a successful job: 1 day after the latest terminal update;
- source of a failed job: 1 day after the latest terminal update;
- translated result: 30 days after the latest terminal update;
- unreferenced source upload: 1 day after its MinIO modification time;
- `PENDING` or `PROCESSING` job without updates: failed after 1 day;
- cleanup interval: 10 minutes;
- maximum processed items per run: 100.

Cleanup never deletes an object while its job remains `PENDING` or
`PROCESSING`. Successful deletion is marked in PostgreSQL, making retries
idempotent. An expired result is reported by Java as HTTP `410 Gone`.
