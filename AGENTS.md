# TranslateLab Java Backend — Agent Guidance

## Scope

- Work only on the Java backend module in this repository.
- The product specification is `Проект_сервис_перевода_документов.md`.
- Frontend code and the Python ML translation service are outside the current implementation scope.
- The Java module owns authentication, document REST endpoints, PostgreSQL metadata, MinIO storage integration, and RabbitMQ message contracts.

## Collaboration style

- Communicate with the user in Russian.
- Guide the user one production class or focused step at a time.
- By default, explain what production code should do and let the user write it.
- Write or edit production code only when the user explicitly asks for implementation or correction.
- The assistant is authorized to create and edit test code independently.
- When checking user code, inspect the actual files and run relevant tests.
- Explain concrete problems and their effects before suggesting corrections.
- Do not discuss version-control operations unless the user explicitly asks.

## Technical baseline

- Java language target: 21.
- Spring Boot: 4.1.0.
- Build tool: Maven Wrapper.
- PostgreSQL with Flyway and Spring Data JPA.
- Spring Security OAuth2 Resource Server with HS256 JWT.
- RabbitMQ through Spring AMQP 4 and `JacksonJsonMessageConverter`.
- MinIO Java SDK 9.0.1 with `okhttp-jvm` 5.3.2.
- Local infrastructure is defined in `compose.yaml`.

## Project conventions

- Root package: `com.translatelab.backend`.
- Prefer constructor injection.
- Use records for immutable request, response, configuration, and messaging DTOs where appropriate.
- Validate configuration records with `@Validated` and Jakarta validation annotations.
- Use `ApiError` and `GlobalExceptionHandler` for REST errors.
- Security-level 401 and 403 responses use `RestSecurityErrorHandler`.
- Use explicit `@JsonProperty` for external snake_case contracts instead of a global naming strategy.
- `FileFormat` serializes to lowercase JSON through `@JsonValue`, while JPA stores enum names as uppercase strings.
- Internal MinIO object keys do not contain user-supplied filenames.
- Never read out, print, copy, or document real values from `.env`.
- Keep real secrets only in `.env`; use placeholders in `.env.example`.

## Verification

- Run all tests with:

```powershell
.\mvnw.cmd test
```

- The full Spring context test currently expects local PostgreSQL and MinIO infrastructure to be available.
- For infrastructure checks, use `docker compose config --quiet` and `docker compose ps`.
- Preserve existing user changes and avoid unrelated production edits.

## Current status

- Read `DEVELOPMENT_STATUS.md` before continuing implementation.
- Update `DEVELOPMENT_STATUS.md` after each meaningful completed milestone.

