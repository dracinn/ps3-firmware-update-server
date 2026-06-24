#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd -P)"
vm_name="${PS3_HELPER_VM_NAME:-ps3-helper}"
rendered_config="${TMPDIR:-/tmp}/ps3-helper-lima-${USER}.yaml"

if ! command -v limactl >/dev/null 2>&1; then
  echo "limactl not found. Install Lima with: brew install lima" >&2
  exit 1
fi

sed "s#@@REPO_ROOT@@#$repo_root#g" "$repo_root/ci/vm/ps3-helper-lima.yaml" > "$rendered_config"
limactl start --name "$vm_name" "$rendered_config"
