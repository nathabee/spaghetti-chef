#!/usr/bin/env bash
set -euo pipefail

DEVICE="${CAMERA_DEVICE:-/dev/video0}"
WIDTH="${CAMERA_WIDTH:-1280}"
HEIGHT="${CAMERA_HEIGHT:-720}"
OUTPUT_FILE="${1:-./data/camera/p1/latest.jpg}"

if ! command -v ffmpeg >/dev/null 2>&1; then
  echo "[camera] ffmpeg is not installed or not available in PATH" >&2
  exit 1
fi

OUTPUT_DIR="$(dirname "${OUTPUT_FILE}")"
OUTPUT_NAME="$(basename "${OUTPUT_FILE}")"
TMP_FILE="${OUTPUT_DIR}/.${OUTPUT_NAME}.tmp.jpg"

mkdir -p "${OUTPUT_DIR}"

rm -f "${TMP_FILE}"

ffmpeg -hide_banner -loglevel error -y \
  -f v4l2 \
  -video_size "${WIDTH}x${HEIGHT}" \
  -i "${DEVICE}" \
  -frames:v 1 \
  -update 1 \
  "${TMP_FILE}"

mv -f "${TMP_FILE}" "${OUTPUT_FILE}"

echo "[camera] captured ${OUTPUT_FILE}"