#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd -P)"
vm_name="${PS3_HELPER_VM_NAME:-ps3-helper}"
server_ip="${1:-192.168.1.50}"
ps3dk_ref="${PS3DK_REF:-24a59feab0d3fa176ed1ecb051367741acd0bce0}"

if ! command -v limactl >/dev/null 2>&1; then
  echo "limactl not found. Install Lima with: brew install lima" >&2
  exit 1
fi

if ! limactl list "$vm_name" >/dev/null 2>&1; then
  "$repo_root/ci/vm/start-ps3-helper-vm.sh"
else
  limactl start "$vm_name" >/dev/null
fi

limactl shell "$vm_name" bash -lc "set -euo pipefail
export PATH=\"\$HOME/.cargo/bin:\$PATH\"
if ! command -v rustc >/dev/null 2>&1; then
  curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs \
    | sh -s -- -y --default-toolchain stable
  export PATH=\"\$HOME/.cargo/bin:\$PATH\"
fi
PS3DK_DIR=\"\$HOME/PS3DK\"
if [[ ! -d \"\$PS3DK_DIR/.git\" ]]; then
  git clone https://github.com/FirebirdTA01/PS3DK.git \"\$PS3DK_DIR\"
fi
cd \"\$PS3DK_DIR\"
git fetch --depth 1 origin \"$ps3dk_ref\"
git checkout --detach \"$ps3dk_ref\"
source ./scripts/env.sh
if [[ ! -x \"\$PS3DEV/ppu/bin/powerpc64-ps3-elf-gcc\" ]]; then
  ./scripts/bootstrap.sh
  ./scripts/build-ppu-toolchain.sh
fi
if [[ ! -x \"\$PS3DEV/spu/bin/spu-elf-gcc\" ]]; then
  ./scripts/build-spu-toolchain.sh
fi
if [[ ! -f \"\$PS3DK/.ps3dk-install-manifest\" ]]; then
  ./scripts/build-psl1ght.sh
  ./scripts/install-host-tools.sh
  make -C sdk install-headers install-spu-headers install-shared-spurs-spu-headers install-spu-only-headers clean-stale-spu-spurs install-version
  make -C sdk/librsx install
  make -C sdk/libdbgfont install
  make -C sdk install-stub-archives
  if [[ ! -f \"\$PS3DK/ppu/lib/lp64/liblv2.a\" && -f \"\$PS3DK/ppu/lib/liblv2.a\" ]]; then
    install -d \"\$PS3DK/ppu/lib/lp64\"
    install -m 0644 \"\$PS3DK/ppu/lib/liblv2.a\" \"\$PS3DK/ppu/lib/lp64/liblv2.a\"
  fi
  make -C sdk install-native-aliases install-manifest
fi
if [[ ! -f \"\$PS3DEV/bin/pkg.py\" || ! -f \"\$PS3DEV/bin/sfo.py\" ]]; then
  make -C src/ps3dev/PSL1GHT/tools/ps3py install
fi
cd /workspace
rm -rf homebrew/ps3-update-helper/cmake-build
cmake -S homebrew/ps3-update-helper \
  -B homebrew/ps3-update-helper/cmake-build \
  -G Ninja \
  -DCMAKE_BUILD_TYPE=Release \
  -DCMAKE_TOOLCHAIN_FILE=\"\$PS3_TOOLCHAIN_ROOT/cmake/ps3-ppu-toolchain.cmake\" \
  -DSERVER_IP=\"$server_ip\"
cmake --build homebrew/ps3-update-helper/cmake-build --verbose
out=/workspace/artifacts/ps3-update-helper-vm
mkdir -p \"\$out\"
cp homebrew/ps3-update-helper/ps3-update-helper.elf \"\$out/\"
cp homebrew/ps3-update-helper/ps3-update-helper.self \"\$out/\"
cp homebrew/ps3-update-helper/ps3-update-helper.fake.self \"\$out/\"
cp homebrew/ps3-update-helper/ps3-update-helper.pkg \"\$out/\"
cp homebrew/ps3-update-helper/ps3-update-helper.gnpdrm.pkg \"\$out/\"
cp homebrew/ps3-update-helper/nexus-update-plugin.elf \"\$out/\"
cp homebrew/ps3-update-helper/nexus-update-plugin.sprx \"\$out/\"
(cd \"\$out\" && sha256sum * > SHA256SUMS.txt)
echo \"Artifacts written to \$out\"
"
