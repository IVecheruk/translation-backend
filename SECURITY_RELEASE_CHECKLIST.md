# TranslateLab Java Backend — Security Release Checklist

Last updated: 2026-08-07

## Scope

This checklist applies to a Java backend image proposed for release. It scans
the exact runtime image, including Java and operating-system packages, and
generates a CycloneDX software bill of materials.

Never place registry credentials, API keys, tokens, webhook secrets, or values
from `.env` in reports or this document.

## Prerequisites

- Docker Engine is running.
- Docker Scout is installed and can analyze local images.
- The intended release image has been built as
  `translation-backend:local`, or its tag is passed to the script explicitly.

## Required commands

Build the image from the current Java sources:

```powershell
docker compose build backend
```

Run the release security gate:

```powershell
& .\scripts\security-scan.ps1
```

To scan another local image tag:

```powershell
& .\scripts\security-scan.ps1 -Image "translation-backend:candidate"
```

Generated artifacts are placed under `target/security/`:

- `translation-backend.cdx.json` — CycloneDX SBOM;
- `translation-backend-cves.md` — Critical/High vulnerability report.

The script exits with a non-zero status when Docker Scout finds a Critical or
High vulnerability or cannot complete the analysis.

## Release policy

- Any known Critical vulnerability blocks release.
- Any High vulnerability blocks release until it is fixed or explicitly
  reviewed.
- An exception for a High vulnerability requires a written affected-code-path
  analysis, compensating controls, responsible owner, and expiration date.
- Do not weaken or bypass the scan script to approve an exception. Use the
  vulnerability-management exception mechanism available to the release
  environment and retain the review record outside secret-bearing files.
- Medium and Low findings are reviewed and scheduled according to exposure and
  available fixes even though this script does not fail the release on them.

## Verification checklist

- [ ] Plain `./mvnw test` equivalent passed.
- [ ] The intended runtime image was rebuilt from the verified sources.
- [ ] Security scan script exited successfully.
- [ ] CycloneDX SBOM was retained with the release artifacts.
- [ ] Critical count is zero.
- [ ] High count is zero or every finding has a current approved review.
- [ ] The resulting container still runs as a non-root user.
- [ ] Actuator and required integration smoke tests pass for the exact image.

## Latest local verification

On 2026-08-07, image
`sha256:cc0bf82559ef8ee5e9903c5e889a1c09b0d5a7028494936f2e7464804fb9b9a2`
was scanned successfully:

- 386 packages indexed;
- zero Critical findings;
- zero High findings;
- CycloneDX SBOM generated;
- the exact image started through Compose, reported `healthy`, returned
  Actuator status `UP`, and ran as the non-root `app` user.
