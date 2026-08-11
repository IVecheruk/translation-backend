# Production secrets

Create the following files in a protected directory outside the repository.
Each scalar secret must contain one value without surrounding quotes. Grant
read access only to the deployment operator/runtime.

- `postgres_password`
- `rabbitmq_password`
- `jwt_secret`
- `minio_backend_access_key`
- `minio_backend_secret_key`
- `tribute_api_key`
- `smtp_username`
- `smtp_password`
- `alertmanager.yml`

`jwt_secret` must contain a cryptographically random Base64-encoded key of at
least 32 decoded bytes. Service passwords should be independent random values.
The alertmanager configuration may contain a private notification URL and is
therefore mounted as a secret as well.

Never copy real values into this directory, `.env`, image layers, Compose
files, build arguments, documentation, or support messages.
