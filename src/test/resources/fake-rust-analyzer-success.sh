#!/usr/bin/env sh
echo "fake analyzer diagnostic" >&2
cat <<'JSON'
{
  "engineName": "RUST_CLI_DELTA",
  "engineVersion": "0.5.5",
  "algorithmVariant": "FRAME_DELTA",
  "confidence": 0.78,
  "suspected": true,
  "reasonCodes": [
    "large_delta_area",
    "high_average_pixel_delta"
  ],
  "message": "Large visual difference detected between snapshots.",
  "metrics": {
    "changedPixelRatio": 0.34,
    "averagePixelDelta": 0.27
  }
}
JSON
