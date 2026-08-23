#!/usr/bin/env bash
# resolve-trusted-proxy-cidr.sh의 회귀 테스트.
# 실제 Docker 데몬 없이, PATH 앞에 가짜 docker 실행 파일을 두어
# `docker network inspect`의 성공/실패/빈 IPAM 세 가지 응답을 흉내낸다.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TARGET="$SCRIPT_DIR/resolve-trusted-proxy-cidr.sh"

pass_count=0
fail_count=0

# $1: 가짜 docker가 흉내낼 시나리오 ("found" | "missing" | "no-ipam")
# $2: expect ("pass" | "fail")
# $3: 검증할 network 인자 (기본 kraft-app)
run_case() {
  local scenario="$1"
  local expect="$2"
  local network="${3:-kraft-app}"

  local fake_bin
  fake_bin="$(mktemp -d)"
  cat >"$fake_bin/docker" <<EOF
#!/usr/bin/env bash
set -euo pipefail
scenario="$scenario"
if [[ "\$1" == "network" && "\$2" == "inspect" ]]; then
  if [[ "\$scenario" == "missing" ]]; then
    exit 1
  fi
  if [[ "\$*" == *" -f "* ]]; then
    if [[ "\$scenario" == "no-ipam" ]]; then
      echo ""
      exit 0
    fi
    echo "172.30.0.0/16"
    exit 0
  fi
  exit 0
fi
exit 1
EOF
  chmod +x "$fake_bin/docker"

  local output
  local status=0
  output=$(PATH="$fake_bin:$PATH" bash "$TARGET" "$network" 2>&1) && status=0 || status=$?
  rm -rf "$fake_bin"

  local actual="pass"
  [[ $status -ne 0 ]] && actual="fail"

  if [[ "$actual" == "$expect" ]]; then
    pass_count=$((pass_count + 1))
    echo "PASS: scenario=$scenario expect=$expect"
  else
    fail_count=$((fail_count + 1))
    echo "FAIL: scenario=$scenario expect=$expect actual=$actual"
    echo "  output: $output"
  fi
}

run_case "found" "pass"
run_case "missing" "fail"
run_case "no-ipam" "fail"

echo
echo "Results: $pass_count passed, $fail_count failed"
[[ $fail_count -eq 0 ]]
