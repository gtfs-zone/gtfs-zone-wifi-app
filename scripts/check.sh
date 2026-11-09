#!/usr/bin/env bash
# Lint and unit tests, as run by the pre-commit hook and CI.
set -euo pipefail
cd "$(dirname "$0")/.."
./gradlew --console=plain check
