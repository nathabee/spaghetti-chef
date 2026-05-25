#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

JSON_FILE="banner.json"
TEMPLATE_FILE="banner-template.svg"
SVG_FILE="banner.svg"
OUT_DIR="../media"
ICON_RENDER_FILE="logo.png"

require_cmd() {
  command -v "$1" >/dev/null 2>&1 || {
    echo "Missing required command: $1" >&2
    exit 1
  }
}

require_file() {
  [[ -f "$1" ]] || {
    echo "Missing required file: $1" >&2
    exit 1
  }
}

escape_xml() {
  local s="${1:-}"
  s="${s//&/&amp;}"
  s="${s//</&lt;}"
  s="${s//>/&gt;}"
  printf '%s' "$s"
}

json_get() {
  jq -r "$1 // \"\"" "$JSON_FILE"
}

json_get_num() {
  jq -r "$1 // 0" "$JSON_FILE"
}

echo "Checking requirements..."
require_cmd jq
require_cmd inkscape
require_cmd python3

require_file "$JSON_FILE"
require_file "$TEMPLATE_FILE"

APP_NAME="$(escape_xml "$(json_get '.appName')")"
TAGLINE="$(escape_xml "$(json_get '.tagline')")"
SUBLINE="$(escape_xml "$(json_get '.subline')")"

FEATURE_TITLE="$(escape_xml "$(json_get '.featureTitle')")"
FEATURE_LEFT_1="$(escape_xml "$(json_get '.featureLeft1')")"
FEATURE_LEFT_2="$(escape_xml "$(json_get '.featureLeft2')")"
FEATURE_RIGHT_1="$(escape_xml "$(json_get '.featureRight1')")"
FEATURE_RIGHT_2="$(escape_xml "$(json_get '.featureRight2')")"

CHIP1="$(escape_xml "$(json_get '.chip1')")"
CHIP2="$(escape_xml "$(json_get '.chip2')")"
CHIP3="$(escape_xml "$(json_get '.chip3')")"
CHIP4="$(escape_xml "$(json_get '.chip4')")"

ICON_FILE="$(json_get '.iconFile')"
SCREENSHOT1="$(json_get '.screenshot1')"
SCREENSHOT2="$(json_get '.screenshot2')"
SCREENSHOT1_LABEL="$(escape_xml "$(json_get '.screenshot1Label')")"
SCREENSHOT2_LABEL="$(escape_xml "$(json_get '.screenshot2Label')")"

SHOT1_X="$(json_get_num '.shot1X')"
SHOT1_Y="$(json_get_num '.shot1Y')"
SHOT1_WIDTH="$(json_get_num '.shot1Width')"
SHOT1_HEIGHT="$(json_get_num '.shot1Height')"
SHOT1_RADIUS="$(json_get_num '.shot1Radius')"
SHOT1_FIT="$(escape_xml "$(json_get '.shot1Fit')")"

SHOT2_X="$(json_get_num '.shot2X')"
SHOT2_Y="$(json_get_num '.shot2Y')"
SHOT2_WIDTH="$(json_get_num '.shot2Width')"
SHOT2_HEIGHT="$(json_get_num '.shot2Height')"
SHOT2_RADIUS="$(json_get_num '.shot2Radius')"
SHOT2_FIT="$(escape_xml "$(json_get '.shot2Fit')")"

require_file "$ICON_FILE"
require_file "$SCREENSHOT1"
require_file "$SCREENSHOT2"

mkdir -p "$OUT_DIR"

echo "Rendering logo..."
inkscape "$ICON_FILE" -w 256 -h 256 -o "$ICON_RENDER_FILE"

echo "Generating banner.svg from template..."
python3 - "$TEMPLATE_FILE" "$SVG_FILE" <<PY
from pathlib import Path

template_path = Path(r"$TEMPLATE_FILE")
output_path = Path(r"$SVG_FILE")

content = template_path.read_text(encoding="utf-8")

replacements = {
    "ICON_RENDER_FILE": r"$ICON_RENDER_FILE",
    "APP_NAME": r"$APP_NAME",
    "TAGLINE": r"$TAGLINE",
    "SUBLINE": r"$SUBLINE",
    "FEATURE_TITLE": r"$FEATURE_TITLE",
    "FEATURE_LEFT_1": r"$FEATURE_LEFT_1",
    "FEATURE_LEFT_2": r"$FEATURE_LEFT_2",
    "FEATURE_RIGHT_1": r"$FEATURE_RIGHT_1",
    "FEATURE_RIGHT_2": r"$FEATURE_RIGHT_2",
    "CHIP1": r"$CHIP1",
    "CHIP2": r"$CHIP2",
    "CHIP3": r"$CHIP3",
    "CHIP4": r"$CHIP4",
    "SCREENSHOT1": r"$SCREENSHOT1",
    "SCREENSHOT2": r"$SCREENSHOT2",
    "SCREENSHOT1_LABEL": r"$SCREENSHOT1_LABEL",
    "SCREENSHOT2_LABEL": r"$SCREENSHOT2_LABEL",
    "SHOT1_X": r"$SHOT1_X",
    "SHOT1_Y": r"$SHOT1_Y",
    "SHOT1_WIDTH": r"$SHOT1_WIDTH",
    "SHOT1_HEIGHT": r"$SHOT1_HEIGHT",
    "SHOT1_RADIUS": r"$SHOT1_RADIUS",
    "SHOT1_FIT": r"$SHOT1_FIT",
    "SHOT2_X": r"$SHOT2_X",
    "SHOT2_Y": r"$SHOT2_Y",
    "SHOT2_WIDTH": r"$SHOT2_WIDTH",
    "SHOT2_HEIGHT": r"$SHOT2_HEIGHT",
    "SHOT2_RADIUS": r"$SHOT2_RADIUS",
    "SHOT2_FIT": r"$SHOT2_FIT",
}

for key, value in replacements.items():
    content = content.replace("{{" + key + "}}", value)

output_path.write_text(content, encoding="utf-8")
PY

echo "Rendering PNG banners..."
inkscape "$SVG_FILE" -w 1544 -h 500 -o "$OUT_DIR/banner-1544x500.png"
inkscape "$SVG_FILE" -w 772 -h 250 -o "$OUT_DIR/banner-772x250.png"

echo "Done."
echo "Generated:"
echo "  $SVG_FILE"
echo "  $OUT_DIR/banner-1544x500.png"
echo "  $OUT_DIR/banner-772x250.png"