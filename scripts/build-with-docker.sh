#!/usr/bin/env bash
set -euo pipefail

echo "Building shaded jar using Docker..."
docker build -t dynamodb-stream-lambda-build .
mkdir -p target
docker run --rm -v "$PWD/target:/workspace/target" dynamodb-stream-lambda-build
echo "Done. Output in ./target"
