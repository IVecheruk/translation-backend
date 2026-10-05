# Local MinIO source builds

Local development and isolated tests build two targets from this Dockerfile:

- `server`: MinIO `RELEASE.2025-04-22T22-12-26Z`, commit
  `0d7408fc9969caf07de6a8c3a84f9fbb10a6739e`.
- `client`: mc `RELEASE.2025-04-08T15-39-49Z`, commit
  `e929f89ceeedc48a45611382be9882db0bf1921d`.

These retain the versions used by the existing local data volume. They are
archived development dependencies, not maintained production storage. Building
them from source does not fix upstream vulnerabilities. Production storage is
managed separately through `compose.production.yaml`; do not deploy these
images there.

Tags are checked against full commit IDs before compilation. Go and Alpine
bases are pinned by multi-platform digest; Go dependencies use upstream go.sum
and readonly module resolution. BuildKit compiles for the selected target
architecture, including Apple Silicon (`linux/arm64`). Upstream license files
are included in `/licenses`. Alpine package repositories are still consulted
during the build, so this is not a claim of byte-identical offline builds.

Build and start from the repository root:

```sh
docker compose config --quiet
docker compose up --build -d
docker compose ps -a
```

For infrastructure without rebuilding Java:

```sh
docker compose up -d postgres rabbitmq minio minio-init
```

Compose always builds the local MinIO targets (normally reusing the cache)
instead of attempting to pull them from a registry. To pull other infrastructure
images separately, name those services explicitly:

```sh
docker compose pull postgres rabbitmq
```

The server should become `healthy`; the one-shot `minio-init` should finish with
`Exited (0)`. The initializer still creates the application bucket and its
restricted backend account. Root credentials are only used by MinIO and its
initializer. Existing environment variables and the `minio_data` volume remain
in use. Do not delete the volume to fix an image download error.

Only localhost can reach the host-published API/console ports. Other Compose
services continue to use `http://minio:9000` on the private Docker network.
The server keeps the old image's root user to support existing volume ownership
without a recursive permission change.

The first build requires GitHub, official Go/Alpine base images, Alpine package
repositories, and Go module proxy/checksum services. Registry passwords for
MinIO images are not needed. Go is only installed in the builder containers.
