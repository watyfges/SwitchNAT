#!/usr/bin/env bash
set -euo pipefail

readonly VERSION="v26.11.9"
readonly SHA256="cdfef146218233a31a8b52c8b1016557730d3b63fc121d4c84cacea50e669155"
readonly URL="https://github.com/2dust/AndroidLibXrayLite/releases/download/${VERSION}/libv2ray.aar"
readonly ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
readonly OUTPUT="${ROOT}/android/app/libs/libv2ray.aar"

mkdir -p "$(dirname "${OUTPUT}")"
if [[ -f "${OUTPUT}" ]] && echo "${SHA256}  ${OUTPUT}" | sha256sum --check --status; then
  echo "AndroidLibXrayLite ${VERSION} is already available."
  exit 0
fi

readonly TEMP="${OUTPUT}.download"
rm -f "${TEMP}"
curl --fail --location --retry 3 --output "${TEMP}" "${URL}"
echo "${SHA256}  ${TEMP}" | sha256sum --check --status
mv "${TEMP}" "${OUTPUT}"
echo "Downloaded AndroidLibXrayLite ${VERSION}."
