#!/usr/bin/env sh
set -eu

: "${PRODUCTION_VARIABLES_FILE:?Set PRODUCTION_VARIABLES_FILE}"
: "${PRODUCTION_SECRETS_DIR:?Set PRODUCTION_SECRETS_DIR}"
: "${BACKUP_DIRECTORY:?Set BACKUP_DIRECTORY to a protected absolute path}"
: "${AGE_RECIPIENT:?Set AGE_RECIPIENT}"
: "${MINIO_MC_IMAGE:?Set a digest-pinned MINIO_MC_IMAGE}"
: "${MINIO_ENDPOINT:?Set MINIO_ENDPOINT}"
: "${MINIO_BUCKET:?Set MINIO_BUCKET}"

case "${BACKUP_DIRECTORY}" in
    /*) ;;
    *) echo "BACKUP_DIRECTORY must be absolute" >&2; exit 1 ;;
esac
if [ "${BACKUP_DIRECTORY}" = "/" ]; then
    echo "BACKUP_DIRECTORY must not be the filesystem root" >&2
    exit 1
fi

mkdir -p "${BACKUP_DIRECTORY}"
staging_directory="$(mktemp -d "${BACKUP_DIRECTORY}/.staging.XXXXXX")"
cleanup() {
    case "${staging_directory}" in
        "${BACKUP_DIRECTORY}"/.staging.*) rm -rf -- "${staging_directory}" ;;
        *) echo "Refusing to remove unexpected staging path" >&2 ;;
    esac
}
trap cleanup EXIT INT TERM

timestamp="$(date -u +%Y%m%dT%H%M%SZ)"
archive="${BACKUP_DIRECTORY}/translatelab-${timestamp}.tar.age"

docker compose \
    --env-file "${PRODUCTION_VARIABLES_FILE}" \
    -f compose.production.yaml \
    exec -T postgres \
    pg_dump \
    --username "${POSTGRES_USER:-translation_app}" \
    --dbname "${POSTGRES_DB:-translation_lab}" \
    --format custom \
    > "${staging_directory}/postgres.dump"

mkdir -p "${staging_directory}/minio"
docker run --rm --read-only \
    --tmpfs /tmp:size=32m \
    --mount "type=bind,src=${PRODUCTION_SECRETS_DIR},dst=/run/secrets,readonly" \
    --mount "type=bind,src=${staging_directory}/minio,dst=/backup" \
    --entrypoint /bin/sh \
    -e MINIO_ENDPOINT="${MINIO_ENDPOINT}" \
    -e MINIO_BUCKET="${MINIO_BUCKET}" \
    "${MINIO_MC_IMAGE}" \
    -ec '
        mc alias set source "$MINIO_ENDPOINT" \
          "$(cat /run/secrets/minio_backend_access_key)" \
          "$(cat /run/secrets/minio_backend_secret_key)" >/dev/null
        mc mirror --overwrite "source/$MINIO_BUCKET" /backup >/dev/null
    '

(
    cd "${staging_directory}"
    sha256sum postgres.dump > manifest.sha256
    find minio -type f -print0 \
        | sort -z \
        | xargs -0 -r sha256sum >> manifest.sha256
    printf 'created_at=%s\npostgres_format=custom\nminio_bucket=%s\n' \
        "${timestamp}" "${MINIO_BUCKET}" > manifest.txt
)

tar -C "${staging_directory}" -cf - . \
    | age --recipient "${AGE_RECIPIENT}" --output "${archive}"

test -s "${archive}"
printf 'Encrypted backup created: %s\n' "${archive}"
