# Build archived, local-development versions from verified upstream commits.
# No official MinIO container images or binary download endpoints are needed.
FROM --platform=$BUILDPLATFORM golang:1.26-alpine@sha256:8ac98ca534ac3f51e1f420a1dd2c15e74c75cfa0f23f3ad27eb5d7236c349a0c AS builder

RUN apk add --no-cache git ca-certificates
ENV GOTOOLCHAIN=local

FROM builder AS server-builder
WORKDIR /src/minio
RUN git init . \
    && git remote add origin https://github.com/minio/minio.git \
    && git fetch --depth 1 origin refs/tags/RELEASE.2025-04-22T22-12-26Z \
    && git checkout --detach FETCH_HEAD \
    && test "$(git rev-parse HEAD)" = "0d7408fc9969caf07de6a8c3a84f9fbb10a6739e"

ARG TARGETOS
ARG TARGETARCH
RUN --mount=type=cache,id=minio-server-modules,target=/go/pkg/mod \
    --mount=type=cache,id=minio-server-build,target=/root/.cache/go-build \
    flags="$(MINIO_RELEASE=RELEASE go run buildscripts/gen-ldflags.go 2025-04-22T22:12:26Z)" \
    && CGO_ENABLED=0 GOOS=$TARGETOS GOARCH=$TARGETARCH \
       go build -mod=readonly -trimpath -tags kqueue -ldflags "$flags" -o /out/minio .

FROM builder AS client-builder
WORKDIR /src/mc
RUN git init . \
    && git remote add origin https://github.com/minio/mc.git \
    && git fetch --depth 1 origin refs/tags/RELEASE.2025-04-08T15-39-49Z \
    && git checkout --detach FETCH_HEAD \
    && test "$(git rev-parse HEAD)" = "e929f89ceeedc48a45611382be9882db0bf1921d"

ARG TARGETOS
ARG TARGETARCH
RUN --mount=type=cache,id=minio-client-modules,target=/go/pkg/mod \
    --mount=type=cache,id=minio-client-build,target=/root/.cache/go-build \
    flags="$(MC_RELEASE=RELEASE go run buildscripts/gen-ldflags.go 2025-04-08T15:39:49Z)" \
    && CGO_ENABLED=0 GOOS=$TARGETOS GOARCH=$TARGETARCH \
       go build -mod=readonly -trimpath -tags kqueue -ldflags "$flags" -o /out/mc .

FROM alpine:3.22@sha256:5291449c3df73caf6ed85e649dec1b9e818b39a5d8c871e97afc13e9cd5e8fa8 AS runtime
RUN apk add --no-cache ca-certificates curl \
    && mkdir -p /data /tmp/.mc
ENV HOME=/tmp MC_CONFIG_DIR=/tmp/.mc

FROM runtime AS client
COPY --from=client-builder /out/mc /usr/local/bin/mc
COPY --from=client-builder /src/mc/LICENSE /licenses/mc-LICENSE
LABEL org.opencontainers.image.source="https://github.com/minio/mc" \
      org.opencontainers.image.revision="e929f89ceeedc48a45611382be9882db0bf1921d" \
      org.opencontainers.image.version="RELEASE.2025-04-08T15-39-49Z" \
      org.opencontainers.image.licenses="AGPL-3.0-or-later"
ENTRYPOINT ["mc"]

FROM runtime AS server
COPY --from=server-builder /out/minio /usr/local/bin/minio
COPY --from=server-builder /src/minio/LICENSE /licenses/minio-LICENSE
LABEL org.opencontainers.image.source="https://github.com/minio/minio" \
      org.opencontainers.image.revision="0d7408fc9969caf07de6a8c3a84f9fbb10a6739e" \
      org.opencontainers.image.version="RELEASE.2025-04-22T22-12-26Z" \
      org.opencontainers.image.licenses="AGPL-3.0-or-later"
# Keep the existing local volume's root ownership compatible; no data migration.
EXPOSE 9000 9001
ENTRYPOINT ["minio"]
CMD ["server", "/data", "--console-address", ":9001"]
