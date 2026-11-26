#!/usr/bin/env bash
# Builds everything into ONE jar: backend/target/healthdekho.jar
# Needs: Java 21 JDK, Maven 3.9+, Node 20+.
# Usage:  ./build.sh            (runs the unit tests, then builds)
#         ./build.sh skip-tests (build only)
set -euo pipefail
cd "$(dirname "$0")"

echo "==> 1/2 Building the React frontend into backend/src/main/resources/static"
(cd frontend && npm install --no-audit --no-fund && npm run build)

echo "==> 2/2 Building the Spring Boot jar"
if [ "${1:-}" = "skip-tests" ]; then
  (cd backend && mvn -B -q clean package -DskipTests)
else
  (cd backend && mvn -B -q clean package)
fi

echo
echo "Done. Upload this file to Elastic Beanstalk:"
ls -lh backend/target/healthdekho.jar
