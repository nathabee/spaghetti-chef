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

failures=0

check_equals() {
  local label="$1"
  local actual="$2"

  if [[ "${actual}" != "${VERSION}" ]]; then
    echo "Version mismatch: ${label} is ${actual}, expected ${VERSION}" >&2
    failures=$((failures + 1))
  fi
}

POM_VERSION="$(
  sed -n '0,/<version>/{s/.*<version>\([^<]*\)<\/version>.*/\1/p}' "${ROOT_DIR}/pom.xml" | head -n 1
)"
check_equals "pom.xml project version" "${POM_VERSION}"

CARGO_VERSION="$(
  sed -n 's/^version = "\([^"]*\)"/\1/p' "${ROOT_DIR}/rust/img-analyzer/Cargo.toml" | head -n 1
)"
check_equals "rust/img-analyzer/Cargo.toml package version" "${CARGO_VERSION}"

LOCK_VERSION="$(
  awk '
    $0 == "name = \"img-analyzer\"" { found = 1; next }
    found && /^version = / { gsub(/"/, "", $3); print $3; exit }
  ' "${ROOT_DIR}/rust/img-analyzer/Cargo.lock"
)"
check_equals "rust/img-analyzer/Cargo.lock img-analyzer version" "${LOCK_VERSION}"

if rg -n 'engine_version: "[^"]+"' "${ROOT_DIR}/rust/img-analyzer/src" >/dev/null; then
  echo "Rust analyzer engine version must use env!(\"CARGO_PKG_VERSION\"), not a hardcoded string." >&2
  failures=$((failures + 1))
fi

if ! rg -n 'engine_version: env!\("CARGO_PKG_VERSION"\)\.to_string\(\)' \
    "${ROOT_DIR}/rust/img-analyzer/src/analyzer.rs" >/dev/null; then
  echo "Rust analyzer does not appear to use Cargo package version for engine_version." >&2
  failures=$((failures + 1))
fi

if [[ "${failures}" -gt 0 ]]; then
  echo "Version check failed. Update VERSION first, then run tools/sync-version.sh." >&2
  exit 1
fi

echo "Version check passed: ${VERSION}"
