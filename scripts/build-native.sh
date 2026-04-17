#!/bin/bash
set -euo pipefail
# Repo root (this script lives in scripts/)
cd "$(dirname "$0")/.."

# Script for compiling Quarkus into Native format (native binary for Linux).

# 1. Ask the user for the environment to compile for
echo "Which environment (profile) do you want to build? (prod / staging / dev)"
read -r PROFILE

# If the user just pressed Enter - take prod as default
if [ -z "$PROFILE" ]; then
  PROFILE="prod,secured"
fi

echo "🚀 Starting Native build for environment: $PROFILE"
echo "Wait for it... (GraalVM may take several minutes to compile and will require up to 6GB RAM)"

# 2. Main compilation command. Explanation of flags:
#  -Dnative : enables Native build profile.
#  -Dquarkus.native.container-build=true : compilation inside a Docker container (no need to install GraalVM on the PC).
#  -Dquarkus.profile=$PROFILE : sets your custom profile (e.g. staging). This affects which %staging-variables from application.properties are used.
#  -DskipTests : skip tests. Running native tests on every build takes too long.
#  -Dquarkus.native.native-image-xmx=20g : allocate 20 gigabytes of RAM for the container, otherwise the build might fail with OutOfMemory!!!

./mvnw clean package \
  -Dnative \
  -Dquarkus.native.container-build=true \
  -Dquarkus.profile="$PROFILE" \
  -DskipTests \
  -Dquarkus.native.native-image-xmx=20g

echo "=================================================="
echo "✅ Build completed!"
echo "Your microservice for the [$PROFILE] environment is compiled."
echo "To run it, just execute this command:"
echo "👉 ./target/quarkus-ms-gold-template-1.0.0-SNAPSHOT-runner"
echo "=================================================="
