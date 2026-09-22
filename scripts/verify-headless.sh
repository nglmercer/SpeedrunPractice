#!/usr/bin/env bash
# Headless verification runner for Linux/macOS (plan phase 4/15).
# Mirrors scripts/verify-headless.ps1: boots the real <version> dedicated
# server via Loom runServer, drives `practiceverify run <suite>` over RCON,
# waits for build/verification/<version>/report.json, and exits non-zero on
# any failure. Bootstraps run/eula.txt and the RCON keys in
# run/server.properties so a fresh clone works without manual setup.
#
# Usage:
#   scripts/verify-headless.sh --version 116|121|263 [--suite SUITE]
#       [--startup-timeout SEC] [--suite-timeout SEC]
#       [--server-java-version N] [--java-home DIR]
#
# NOTE: run lava suites for different versions sequentially, not side by
# side: a 1.21.1 lava run crashed with tick-scheduler corruption while a
# second lava suite ran concurrently (2026-09-22). `verifyAll` already
# runs every suite sequentially.
set -u

VERSION=""
SUITE="lava"
STARTUP_TIMEOUT=1800
SUITE_TIMEOUT=3600
SERVER_JAVA_VERSION=""
JAVA_HOME_ARG=""
SERVER_HEAP="6G"

while [ $# -gt 0 ]; do
  case "$1" in
    --version) VERSION="$2"; shift 2;;
    --suite) SUITE="$2"; shift 2;;
    --startup-timeout) STARTUP_TIMEOUT="$2"; shift 2;;
    --suite-timeout) SUITE_TIMEOUT="$2"; shift 2;;
    --server-java-version) SERVER_JAVA_VERSION="$2"; shift 2;;
    --java-home) JAVA_HOME_ARG="$2"; shift 2;;
    --server-heap) SERVER_HEAP="$2"; shift 2;;
    *) echo "Unknown argument: $1" >&2; exit 2;;
  esac
done

case "$VERSION" in
  116|121|263) ;;
  *) echo "Missing or invalid --version (want 116, 121 or 263)" >&2; exit 2;;
esac

case "$SUITE" in
  worlds|structures|portals|dragon|registries|seed-search|scenarios|resets|checkpoints|lava|fixtures|all) ;;
  *) echo "Invalid --suite: $SUITE" >&2; exit 2;;
esac

if ! command -v python3 >/dev/null 2>&1; then
  echo "verify-headless.sh requires python3 (RCON client + JSON parsing)" >&2
  exit 2
fi

REPO="$(cd "$(dirname "$0")/.." && pwd)"
GRADLE="$REPO/gradlew"

case "$VERSION" in
  116) MC_VERSION="1.16.1"; MODULE="versions/fabric-1.16.1"; PORT=25577; GAME_PORT=25566; DEFAULT_JAVA=17;;
  121) MC_VERSION="1.21.1"; MODULE="versions/fabric-1.21.1"; PORT=25575; GAME_PORT=25565; DEFAULT_JAVA=21;;
  # 26.3 targets Java 25+; like the Windows runner, leave the server JVM at
  # the default Gradle runtime unless --server-java-version overrides it.
  263) MC_VERSION="26.3"; MODULE="versions/fabric-26.3"; PORT=25576; GAME_PORT=25567; DEFAULT_JAVA="";;
esac
PASSWORD="verify116"
RUN_DIR="$REPO/$MODULE/run"
MODS_DIR="$RUN_DIR/mods"
JAR="$REPO/$MODULE/build/libs/speedrun-practice-verification-$MC_VERSION-2.0.0.jar"
REPORT_DIR="$REPO/build/verification/$MC_VERSION"
REPORT_PATH="$REPORT_DIR/report.json"
SERVER_LOG="$RUN_DIR/logs/latest.log"
CONSOLE_LOG="$REPORT_DIR/server-console.log"
ERROR_LOG="$REPORT_DIR/server-console.error.log"

if [ -n "$JAVA_HOME_ARG" ]; then
  if [ ! -x "$JAVA_HOME_ARG/bin/java" ]; then
    echo "Requested --java-home does not contain bin/java: $JAVA_HOME_ARG" >&2
    exit 2
  fi
fi
if [ -z "$SERVER_JAVA_VERSION" ]; then
  SERVER_JAVA_VERSION="$DEFAULT_JAVA"
fi

if [ ! -f "$JAR" ]; then
  echo "Verification jar is missing: $JAR. Run the version verificationJar task first." >&2
  exit 1
fi
mkdir -p "$MODS_DIR" "$REPORT_DIR"
cp -f "$JAR" "$MODS_DIR/"
if [ -f "$REPORT_PATH" ]; then
  mv -f "$REPORT_PATH" "$REPORT_DIR/report.previous.$(date +%Y%m%d%H%M%S).json"
fi
if [ -f "$SERVER_LOG" ]; then
  mv -f "$SERVER_LOG" "$REPORT_DIR/server-log.previous.$(date +%Y%m%d%H%M%S).log"
fi

# Fresh-clone bootstrap: Mojang EULA + headless RCON settings. Existing
# server.properties values are preserved except the keys we require.
if [ ! -f "$RUN_DIR/eula.txt" ]; then
  echo "eula=true" > "$RUN_DIR/eula.txt"
fi
python3 - "$RUN_DIR/server.properties" "$PORT" "$PASSWORD" "$GAME_PORT" <<'EOF'
import sys
path, port, password, game_port = sys.argv[1], sys.argv[2], sys.argv[3], sys.argv[4]
required = {
    "enable-rcon": "true",
    "rcon.password": password,
    "rcon.port": port,
    "broadcast-rcon-to-ops": "true",
    "online-mode": "false",
    # Distinct game ports so verification servers can run side by side.
    "server-port": game_port,
    # Verification suites run heavy synchronous worldgen (spawn search,
    # Stage-B lava scans) on the server thread; the vanilla watchdog would
    # kill slow-but-healthy runs. Hangs are still bounded by --suite-timeout.
    "max-tick-time": "-1",
}
lines = []
try:
    with open(path, "r", encoding="utf-8") as handle:
        lines = handle.read().splitlines()
except FileNotFoundError:
    lines = ["# Headless verification run. RCON drives console commands."]
seen = set()
out = []
for line in lines:
    stripped = line.strip()
    if not stripped or stripped.startswith("#") or "=" not in line:
        out.append(line)
        continue
    key = line.split("=", 1)[0].strip()
    if key in required:
        out.append("%s=%s" % (key, required[key]))
        seen.add(key)
    else:
        out.append(line)
for key, value in sorted(required.items()):
    if key not in seen:
        out.append("%s=%s" % (key, value))
with open(path, "w", encoding="utf-8") as handle:
    handle.write("\n".join(out) + "\n")
EOF

if python3 -c "import socket; s=socket.socket(); s.settimeout(1); s.connect(('127.0.0.1', $PORT)); s.close()" 2>/dev/null; then
  echo "RCON port $PORT is already in use. Stop the existing $MC_VERSION verification server first." >&2
  exit 1
fi

rcon() {
  python3 - "$PORT" "$PASSWORD" "$1" <<'EOF'
import socket, struct, sys
port, password, command = int(sys.argv[1]), sys.argv[2], sys.argv[3]
def packet(req_id, kind, body):
    payload = body.encode("utf-8")
    return struct.pack("<iii", 10 + len(payload), req_id, kind) + payload + b"\x00\x00"
def read_packet(stream):
    header = stream.recv(4)
    if len(header) < 4:
        raise RuntimeError("RCON authentication returned no response")
    (length,) = struct.unpack("<i", header)
    data = b""
    while len(data) < length:
        chunk = stream.recv(length - len(data))
        if not chunk:
            raise RuntimeError("RCON connection closed mid-packet")
        data += chunk
    req_id, kind = struct.unpack("<ii", data[:8])
    return req_id, data[8:-2].decode("utf-8", "replace")
sock = socket.create_connection(("127.0.0.1", port), timeout=8)
sock.settimeout(60)
try:
    sock.sendall(packet(1, 3, password))
    req_id, _ = read_packet(sock)
    if req_id == -1:
        raise RuntimeError("RCON authentication failed")
    sock.sendall(packet(2, 2, command))
    _, response = read_packet(sock)
    print(response)
finally:
    sock.close()
EOF
}

export SPEEDRUN_PRACTICE_VERIFICATION_OUTPUT="$REPO/build/verification"

GRADLE_TASK=":${MODULE//\//:}:runServer"
GRADLE_ARGS=("$GRADLE_TASK" "--console=plain" "--no-daemon")
if [ -n "$SERVER_JAVA_VERSION" ]; then
  GRADLE_ARGS+=("-PspeedrunPracticeServerJavaVersion=$SERVER_JAVA_VERSION")
fi
if [ -n "$SERVER_HEAP" ]; then
  GRADLE_ARGS+=("-PspeedrunPracticeServerHeap=$SERVER_HEAP")
fi
if [ -n "$JAVA_HOME_ARG" ]; then
  # Point toolchain auto-detection at the requested JDK (e.g. a Temurin 17
  # install outside the standard JVM locations) for the nested server build.
  GRADLE_ARGS+=("-Porg.gradle.java.installations.paths=$JAVA_HOME_ARG")
fi

CHILD_PID=""

port_open() {
  python3 -c "import socket; s=socket.socket(); s.settimeout(1); s.connect(('127.0.0.1', $PORT)); s.close()" 2>/dev/null
}

# PIDs listening on the RCON port (the verification server JVM). The
# pre-flight check guarantees nothing else binds it first.
server_pids() {
  if command -v ss >/dev/null 2>&1; then
    ss -ltnp 2>/dev/null | sed -n "s/.*:$PORT .*pid=\([0-9][0-9]*\).*/\1/p" | sort -u
  elif command -v lsof >/dev/null 2>&1; then
    lsof -ti "tcp:$PORT" 2>/dev/null
  fi
}

cleanup() {
  if port_open; then
    rcon "stop" >/dev/null 2>&1 || true
  fi
  # Wait for the server JVM to release the RCON port.
  for _ in $(seq 1 30); do
    port_open || break
    sleep 1
  done
  # Reap a hung server JVM that ignored 'stop'. Killing only the Gradle
  # client below would orphan it (observed once after a heavy 1.16.1
  # aggregate run); stacked orphans would starve later suites.
  for pid in $(server_pids); do
    if ps -p "$pid" -o comm= 2>/dev/null | grep -qi java; then
      kill -9 "$pid" 2>/dev/null || true
    fi
  done
  if [ -n "$CHILD_PID" ] && kill -0 "$CHILD_PID" 2>/dev/null; then
    for _ in $(seq 1 30); do
      kill -0 "$CHILD_PID" 2>/dev/null || break
      sleep 1
    done
    kill -9 "$CHILD_PID" 2>/dev/null || true
  fi
}
trap cleanup EXIT

: > "$CONSOLE_LOG"
: > "$ERROR_LOG"
"$GRADLE" "${GRADLE_ARGS[@]}" >"$CONSOLE_LOG" 2>"$ERROR_LOG" &
CHILD_PID=$!

READY=0
START_DEADLINE=$(( $(date +%s) + STARTUP_TIMEOUT ))
while [ "$(date +%s)" -lt "$START_DEADLINE" ]; do
  if ! kill -0 "$CHILD_PID" 2>/dev/null; then
    echo "Minecraft server process exited early. See $CONSOLE_LOG" >&2
    exit 1
  fi
  if [ -f "$SERVER_LOG" ] && grep -q 'Done (' "$SERVER_LOG" 2>/dev/null; then
    READY=1
    break
  fi
  sleep 2
done
if [ "$READY" -ne 1 ]; then
  echo "Minecraft server did not become ready within $STARTUP_TIMEOUT seconds. See $CONSOLE_LOG" >&2
  exit 1
fi

RESPONSE="$(rcon "practiceverify run $SUITE" 2>&1)" || {
  echo "Verification command failed: $RESPONSE" >&2
  exit 1
}
case "$RESPONSE" in
  *"Verification suite"*) ;;
  *) echo "Verification command was not accepted: $RESPONSE" >&2; exit 1;;
esac

SUITE_DEADLINE=$(( $(date +%s) + SUITE_TIMEOUT ))
while [ "$(date +%s)" -lt "$SUITE_DEADLINE" ]; do
  if ! kill -0 "$CHILD_PID" 2>/dev/null; then
    echo "Minecraft server process exited during suite '$SUITE'. See $CONSOLE_LOG" >&2
    exit 1
  fi
  if [ -f "$REPORT_PATH" ]; then
    STATUS="$(python3 -c "import json; r=json.load(open('$REPORT_PATH')); print('%d %d' % (r.get('passed',0), r.get('failed',0)))" 2>/dev/null)" || STATUS=""
    if [ -n "$STATUS" ]; then
      PASSED="${STATUS%% *}"; FAILED="${STATUS##* }"
      if [ "$FAILED" -gt 0 ] 2>/dev/null; then
        echo "Verification failed; see $REPORT_PATH" >&2
        exit 1
      fi
      if [ "$PASSED" -gt 1 ] 2>/dev/null; then
        break
      fi
    fi
  fi
  sleep 2
done
if [ ! -f "$REPORT_PATH" ]; then
  echo "Verification did not produce $REPORT_PATH" >&2
  exit 1
fi
FINAL="$(python3 -c "import json; r=json.load(open('$REPORT_PATH')); print('%d %d' % (r.get('passed',0), r.get('failed',0)))")"
PASSED="${FINAL%% *}"; FAILED="${FINAL##* }"
if [ "$FAILED" -gt 0 ] || [ "$PASSED" -lt 2 ]; then
  echo "Verification did not complete successfully; see $REPORT_PATH" >&2
  exit 1
fi
cat "$REPORT_DIR/summary.txt"
