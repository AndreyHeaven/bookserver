#!/usr/bin/env bash
# Build the backend + frontend images for Unraid (linux/amd64) and push them
# to a registry. Run this on your DEV machine, not on Unraid.
#
# Unraid runs on amd64. If you build on an Apple Silicon / ARM machine you MUST
# target linux/amd64 (this script does it via buildx), otherwise the images
# won't start on the server ("exec format error").
#
# Usage:
#   REGISTRY=docker.io/youruser ./scripts/build-and-push.sh
#   REGISTRY=ghcr.io/youruser IMAGE_TAG=v1 PLATFORMS=linux/amd64 ./scripts/build-and-push.sh
#   REGISTRY=192.168.88.2:5000/bookserver ./scripts/build-and-push.sh   # insecure/HTTP registry
#
# Insecure (HTTP) registry note:
#   The buildx "docker-container" builder runs its own BuildKit and does NOT
#   read the Docker daemon's "insecure-registries" from daemon.json. This script
#   auto-detects a host:port registry and configures BuildKit with http=true so
#   the push over plain HTTP works. Override detection with INSECURE_REGISTRY=1/0.
#
# Requires: docker login <registry> beforehand (if the registry needs auth).
set -euo pipefail

REGISTRY="${REGISTRY:?Set REGISTRY, e.g. docker.io/youruser or ghcr.io/youruser}"
IMAGE_TAG="${IMAGE_TAG:-latest}"
PLATFORMS="${PLATFORMS:-linux/amd64}"
BUILDER_NAME="bookserver-builder"

# Resolve repository root (this script lives in <root>/scripts).
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

BACKEND_IMAGE="${REGISTRY}/bookserver-backend:${IMAGE_TAG}"
FRONTEND_IMAGE="${REGISTRY}/bookserver-frontend:${IMAGE_TAG}"

# Registry host is everything before the first '/'. E.g. 192.168.88.2:5000
REGISTRY_HOST="${REGISTRY%%/*}"

# Auto-detect an insecure (HTTP) registry: a host that carries an explicit port
# (contains ':') is almost always a private/local registry served over HTTP.
# Public registries (docker.io, ghcr.io) have no port and stay on HTTPS.
if [[ -z "${INSECURE_REGISTRY:-}" ]]; then
  if [[ "${REGISTRY_HOST}" == *:* ]]; then INSECURE_REGISTRY=1; else INSECURE_REGISTRY=0; fi
fi

echo ">> (Re)creating buildx builder '${BUILDER_NAME}' (platforms: ${PLATFORMS})..."
if [[ "${INSECURE_REGISTRY}" == "1" ]]; then
  echo ">> Configuring BuildKit for INSECURE (HTTP) registry: ${REGISTRY_HOST}"
  BUILDKITD_CONFIG="$(mktemp -t buildkitd-XXXXXX.toml)"
  trap 'rm -f "${BUILDKITD_CONFIG}"' EXIT
  cat >"${BUILDKITD_CONFIG}" <<EOF
[registry."${REGISTRY_HOST}"]
  http = true
  insecure = true
EOF
  # Recreate so the config is guaranteed to apply (an existing builder keeps its old config).
  docker buildx rm "${BUILDER_NAME}" >/dev/null 2>&1 || true
  docker buildx create --name "${BUILDER_NAME}" --driver docker-container \
    --config "${BUILDKITD_CONFIG}" --use
else
  docker buildx inspect "${BUILDER_NAME}" >/dev/null 2>&1 \
    || docker buildx create --name "${BUILDER_NAME}" --driver docker-container --use
  docker buildx use "${BUILDER_NAME}"
fi

echo ">> Building & pushing backend -> ${BACKEND_IMAGE}"
docker buildx build \
  --platform "${PLATFORMS}" \
  -f "${ROOT_DIR}/backend/Dockerfile" \
  -t "${BACKEND_IMAGE}" \
  --push \
  "${ROOT_DIR}"

echo ">> Building & pushing frontend -> ${FRONTEND_IMAGE}"
docker buildx build \
  --platform "${PLATFORMS}" \
  -f "${ROOT_DIR}/frontend/Dockerfile" \
  -t "${FRONTEND_IMAGE}" \
  --push \
  "${ROOT_DIR}/frontend"

echo ">> Done. Set REGISTRY=${REGISTRY} and IMAGE_TAG=${IMAGE_TAG} in deploy/unraid/.env"
