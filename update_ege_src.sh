#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
python3 scripts/package_ege_source.py "${1:-../xege}"
