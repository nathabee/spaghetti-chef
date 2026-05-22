#!/usr/bin/env bash
set -euo pipefail

PRINTER_ID="${1:-p1}"

BASE_DIR="${CAMERA_BASE_DIR:-./data/camera}"
INTERVAL_SECONDS="${CAMERA_INTERVAL_SECONDS:-2}"
ARCHIVE_INTERVAL_SECONDS="${CAMERA_ARCHIVE_INTERVAL_SECONDS:-300}"
RETENTION_HOURS="${CAMERA_RETENTION_HOURS:-24}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CAPTURE_ONCE="${SCRIPT_DIR}/camera-capture-once.sh"

PRINTER_DIR="${BASE_DIR}/${PRINTER_ID}"
ARCHIVE_DIR="${PRINTER_DIR}/snapshots"

LATEST="${PRINTER_DIR}/latest.jpg"
PREVIOUS="${PRINTER_DIR}/previous.jpg"

if [ ! -x "${CAPTURE_ONCE}" ]; then
  echo "[camera] missing executable capture script: ${CAPTURE_ONCE}" >&2
  exit 1
fi

mkdir -p "${ARCHIVE_DIR}"

last_archive_epoch=0

echo "[camera] loop started"
echo "[camera] printer=${PRINTER_ID}"
echo "[camera] baseDir=${BASE_DIR}"
echo "[camera] latest=${LATEST}"
echo "[camera] previous=${PREVIOUS}"
echo "[camera] archiveDir=${ARCHIVE_DIR}"
echo "[camera] intervalSeconds=${INTERVAL_SECONDS}"
echo "[camera] archiveIntervalSeconds=${ARCHIVE_INTERVAL_SECONDS}"
echo "[camera] retentionHours=${RETENTION_HOURS}"
echo "[camera] device=${CAMERA_DEVICE:-/dev/video0}"
echo "[camera] size=${CAMERA_WIDTH:-1280}x${CAMERA_HEIGHT:-720}"

while true; do
  now_epoch="$(date +%s)"
  now_stamp="$(date +%Y%m%d_%H%M%S)"

  if [ -f "${LATEST}" ]; then
    cp -f "${LATEST}" "${PREVIOUS}"
  fi

  "${CAPTURE_ONCE}" "${LATEST}"

  if [ $((now_epoch - last_archive_epoch)) -ge "${ARCHIVE_INTERVAL_SECONDS}" ]; then
    cp -f "${LATEST}" "${ARCHIVE_DIR}/${now_stamp}.jpg"
    last_archive_epoch="${now_epoch}"
    echo "[camera] archived ${ARCHIVE_DIR}/${now_stamp}.jpg"
  fi

  find "${ARCHIVE_DIR}" \
    -type f \
    -name '*.jpg' \
    -mmin +"$((RETENTION_HOURS * 60))" \
    -delete

  sleep "${INTERVAL_SECONDS}"
done