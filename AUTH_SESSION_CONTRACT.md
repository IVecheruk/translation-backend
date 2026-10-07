# Сессии и обновление JWT

Контракт Java backend, обновлён 2026-10-07. Frontend реализуется отдельно.

## Сроки и хранение

- Access token: по умолчанию 15 минут; JSON-поле `expiresIn` содержит секунды.
- Refresh-сессия: по умолчанию 7 дней с момента входа, максимум 30 дней.
  Обновление не продлевает первоначальный срок сессии.
- Refresh token: случайные 32 байта, Base64 URL без padding. В PostgreSQL
  сохраняется только SHA-256-хеш, а браузер получает секрет только в cookie.
- Cookie: `translatelab_refresh`, `HttpOnly`, `Path=/api/auth`, без `Domain`,
  `SameSite=Lax` по умолчанию. `Max-Age` равен оставшемуся сроку сессии.
- В production cookie всегда `Secure`. Для локального HTTP Compose использует
  `REFRESH_TOKEN_COOKIE_SECURE=false`; при запуске из IDE это значение нужно
  задать в локальном `.env`. Не переносите локальное HTTP-разрешение в production.
- Новые JWT содержат `sid`: backend проверяет сессию при каждом защищённом
  запросе. JWT, выданные до внедрения refresh, действуют до своего истечения
  с прежними проверками версии аккаунта, issuer, audience и подписи.

## Endpoint

| Запрос | Вход | Успех |
| --- | --- | --- |
| `POST /api/auth/login` | JSON с `email`, `password` | `200`, access token в JSON, refresh cookie |
| `POST /api/auth/refresh` | refresh cookie и `X-Refresh-Request: true`, тело не требуется | `200`, новый access token и новая refresh cookie |
| `POST /api/auth/logout` | refresh cookie и `X-Refresh-Request: true`, тело не требуется | `204`, отзыв текущей сессии и удаление cookie |
| `POST /api/auth/logout-all` | действующий Bearer JWT | `204`, отзыв всех access token и refresh-сессий аккаунта, удаление cookie |

Ответ login и refresh сохраняет прежнюю структуру:

```json
{
  "accessToken": "<access-token>",
  "tokenType": "Bearer",
  "expiresIn": 900
}
```

Refresh token в JSON не возвращается. Ответы с токенами имеют `Cache-Control:
no-store`. Login принимает только `application/json`, чтобы исключить простой
межсайтовый запрос входа. Cookie-действия требуют нестандартный заголовок:
браузер проверяет разрешение через CORS preflight. CORS допускает только точные
настроенные origins, разрешает credentials и `X-Refresh-Request`.

Refresh/logout не требуют access token и игнорируют Bearer-заголовок, если
interceptor по привычке передал истёкший JWT. Остальные endpoint проверяют JWT.

## Поведение frontend

1. Login, refresh, logout и logout-all выполняйте с `credentials: "include"`
   (Axios: `withCredentials: true`). Для login задавайте JSON Content-Type.
2. Access token храните в памяти и передавайте как `Authorization: Bearer ...`.
   Refresh cookie управляет браузер; JS не должен читать или сохранять её секрет.
3. При загрузке страницы восстановите access token через refresh, если нужно
   сохранить вход после перезагрузки страницы.
4. Перед истечением access token либо после `401` защищённого запроса выполните
   один refresh. При успехе повторите исходный запрос не более одного раза.
5. Все ожидающие запросы должны использовать общий результат одного refresh.
   Нужна одна общая promise внутри вкладки и координация между вкладками,
   например Web Locks и BroadcastChannel. Не запускайте несколько refresh
   одновременно с одной cookie.
6. `401` от refresh означает, что нужен повторный вход. `403` без обязательного
   заголовка, `429` с `Retry-After`, сетевые ошибки и `5xx` нужно обрабатывать
   отдельно; они не подтверждают истечение сессии.
7. Не повторяйте refresh вслепую после потери ответа: старый токен мог быть уже
   использован. Строгая ротация отзывает сессию при повторном использовании.

Минимальный запрос обновления (не полный frontend interceptor):

```javascript
const response = await fetch("http://localhost:8080/api/auth/refresh", {
  method: "POST",
  credentials: "include",
  headers: { "X-Refresh-Request": "true" }
});

if (response.ok) {
  const { accessToken, expiresIn } = await response.json();
  // Сохранить accessToken в памяти и обновить таймер истечения.
} else if (response.status === 401) {
  // Очистить локальное состояние входа и открыть страницу входа.
}
```

Для локальных frontend/backend используйте один hostname (например localhost
с разными портами). localhost и 127.0.0.1 имеют разные cookie/site-контексты.
Для frontend на другом site потребуется `SameSite=None`, HTTPS и разрешённый
точный CORS origin; браузерные ограничения сторонних cookie всё равно могут
помешать. Общий site через reverse proxy подходит для production.

## Отзыв и параллельные запросы

Ротация, вход, выход, logout-all и смена пароля сериализуются блокировкой строки
пользователя. Один refresh token можно успешно использовать только один раз.
При повторе backend фиксирует отзыв всей соответствующей сессии и возвращает
`401`; отзыв сохраняется, несмотря на ошибочный HTTP-ответ. Токены других
устройств при этом не отзываются.

Logout идемпотентен: неизвестная или отсутствующая cookie также даёт `204`.
При валидной cookie он немедленно отзывает access token текущей сессии.
Logout-all и сброс пароля увеличивают `auth_version`: все ранее созданные
сессии аккаунта сразу становятся недействительными. Refresh после подтверждения
email выдаёт JWT с актуальным состоянием пользователя.

Хеши использованных токенов сохраняются до окончания сессии для обнаружения
повторов. Плановая очистка удаляет истёкшие сессии пакетами до 500 строк и
каскадно удаляет их историю токенов. Refresh и logout имеют отдельные ключи
лимитера, используют лимит `RATE_LIMIT_LOGIN_PER_MINUTE` по адресу клиента.

## Настройки

```dotenv
JWT_ACCESS_TOKEN_TTL=15m
REFRESH_TOKEN_SESSION_TTL=7d
REFRESH_TOKEN_COOKIE_SECURE=false
REFRESH_TOKEN_COOKIE_SAME_SITE=Lax
REFRESH_TOKEN_CLEANUP_INTERVAL=1h
CORS_ENABLED=true
CORS_ALLOWED_ORIGINS=http://localhost:5173,http://localhost:3000
```

Это пример для локальной HTTP-среды. Production Compose принудительно включает
Secure; `SameSite=None` без Secure не проходит проверку конфигурации.

## Проверка в PowerShell

После сборки обновлённого backend:

```powershell
docker compose build backend
docker compose up -d --no-deps backend
Invoke-RestMethod -Uri "http://localhost:8080/actuator/health"
```

Используйте ранее созданный локальный тестовый аккаунт:

```powershell
$body = @{ email = "test@example.com"; password = "LocalTest123!" } | ConvertTo-Json
$login = Invoke-RestMethod -Method Post -Uri "http://localhost:8080/api/auth/login" -ContentType "application/json" -Body $body -SessionVariable authSession
$login | Select-Object tokenType, expiresIn

$refreshHeaders = @{ "X-Refresh-Request" = "true" }
$login = Invoke-RestMethod -Method Post -Uri "http://localhost:8080/api/auth/refresh" -Headers $refreshHeaders -WebSession $authSession

$headers = @{ Authorization = "Bearer $($login.accessToken)" }
Invoke-RestMethod -Uri "http://localhost:8080/api/profile" -Headers $headers

Invoke-RestMethod -Method Post -Uri "http://localhost:8080/api/auth/logout" -Headers $refreshHeaders -WebSession $authSession
```

После logout сохранённый access token и refresh cookie этой сессии больше не
дают доступ. При первом запуске Flyway автоматически применяет V21; вручную
создавать таблицы или менять существующего тестового пользователя не требуется.

Обоснование CSRF-защиты нестандартным заголовком: [OWASP CSRF Prevention](https://cheatsheetseries.owasp.org/cheatsheets/Cross-Site_Request_Forgery_Prevention_Cheat_Sheet.html#employing-custom-request-headers-for-ajaxapi).
Обоснование ротации и обнаружения повторов: [RFC 9700, 4.14](https://www.rfc-editor.org/rfc/rfc9700.html#section-4.14).
