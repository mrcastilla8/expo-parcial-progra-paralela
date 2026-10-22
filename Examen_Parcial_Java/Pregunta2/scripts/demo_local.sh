#!/usr/bin/env sh
set -eu
sh "$(dirname "$0")/compilar.sh"
python3 "$(dirname "$0")/demo_local.py" "$@"
