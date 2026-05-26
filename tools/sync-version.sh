#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
VERSION_FILE="${ROOT_DIR}/VERSION"

if [[ ! -f "${VERSION_FILE}" ]]; then
  echo "Missing VERSION file at ${VERSION_FILE}" >&2
  exit 1
fi

VERSION="$(tr -d '[:space:]' < "${VERSION_FILE}")"

if [[ ! "${VERSION}" =~ ^[0-9]+\.[0-9]+\.[0-9]+([-.][0-9A-Za-z.-]+)?$ ]]; then
  echo "VERSION must contain one semantic version, got: ${VERSION}" >&2
  exit 1
fi

VERSION="${VERSION}" perl -0pi -e 's|(<artifactId>spaghetti-chef</artifactId>\s*<version>)[^<]*(</version>)|$1$ENV{VERSION}$2|s' \
  "${ROOT_DIR}/pom.xml"

VERSION="${VERSION}" perl -0pi -e 's|(\[package\]\s*name = "img-analyzer"\s*version = ")[^"]*(")|$1$ENV{VERSION}$2|s' \
  "${ROOT_DIR}/rust/img-analyzer/Cargo.toml"

VERSION="${VERSION}" perl -0pi -e 's|(name = "img-analyzer"\s*version = ")[^"]*(")|$1$ENV{VERSION}$2|s' \
  "${ROOT_DIR}/rust/img-analyzer/Cargo.lock"

VERSION="${VERSION}" perl -0pi -e 's|("engineVersion": ")[^"]*(")|$1$ENV{VERSION}$2|g; s|(Current version:\s*```text\s*)[^`\s]+|$1$ENV{VERSION}|s' \
  "${ROOT_DIR}/rust/img-analyzer/README.md"

if [[ -f "${ROOT_DIR}/rust/img-analyzer/docs/result-contract.md" ]]; then
  VERSION="${VERSION}" perl -0pi -e 's|^Version: `[^`]*`|Version: `$ENV{VERSION}`|m; s|("engineVersion": ")[^"]*(")|$1$ENV{VERSION}$2|g' \
    "${ROOT_DIR}/rust/img-analyzer/docs/result-contract.md"
fi

"${ROOT_DIR}/tools/check-version.sh"
