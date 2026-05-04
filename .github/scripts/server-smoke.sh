#!/usr/bin/env bash
# 真实服务器烟雾测试。
#
# 用法：
#   server-smoke.sh <mc_version> <plugin_jar> <expect_line> [timeout_sec]
#
# 环境变量：
#   JAVA_BIN  指定 java 可执行文件，默认从 PATH 解析。
#   LOG_DIR   日志输出目录，默认 $RUNNER_TEMP（CI）或 /tmp。
set -euo pipefail

MC_VERSION="${1:?mc version required (1.12.2|1.20.4)}"
PLUGIN_JAR="${2:?plugin jar required}"
EXPECT="${3:?expected log substring required}"
TIMEOUT_SEC="${4:-240}"
JAVA_BIN="${JAVA_BIN:-java}"
LOG_DIR="${LOG_DIR:-${RUNNER_TEMP:-/tmp}}"

WORK_DIR="$(mktemp -d -t mcserver-XXXXXX)"
LOG_OUT="$LOG_DIR/server-$MC_VERSION.log"
trap 'echo "[smoke] cleanup $WORK_DIR"; rm -rf "$WORK_DIR"' EXIT

echo "[smoke] mc=$MC_VERSION plugin=$PLUGIN_JAR expect=\"$EXPECT\" timeout=${TIMEOUT_SEC}s"
echo "[smoke] work_dir=$WORK_DIR  log_out=$LOG_OUT"
cd "$WORK_DIR"

# ── 1. 准备服务端 jar ───────────────────────────────────────────────
download() {
    local url="$1"
    local dest="$2"
    echo "[smoke] try $url"
    if curl -fSL --connect-timeout 30 --max-time 300 -o "$dest" "$url"; then
        local size
        size=$(stat -c%s "$dest" 2>/dev/null || stat -f%z "$dest")
        if [ "${size:-0}" -gt 5000000 ]; then
            echo "[smoke] downloaded ${size} bytes from $url"
            return 0
        fi
        echo "[smoke] size too small ($size), discarding"
        rm -f "$dest"
    fi
    return 1
}

case "$MC_VERSION" in
    1.20.4)
        BUILD=$(curl -fsSL "https://api.papermc.io/v2/projects/paper/versions/1.20.4" \
            | python3 -c 'import sys,json;print(json.load(sys.stdin)["builds"][-1])')
        URL="https://api.papermc.io/v2/projects/paper/versions/1.20.4/builds/${BUILD}/downloads/paper-1.20.4-${BUILD}.jar"
        download "$URL" server.jar || { echo "[smoke] paper download failed"; exit 1; }
        ;;
    1.12.2)
        ok=0
        for url in \
            "https://download.getbukkit.org/spigot/spigot-1.12.2.jar" \
            "https://cdn.getbukkit.org/spigot/spigot-1.12.2.jar" \
            "https://serverjars.com/api/fetchJar/spigot/1.12.2"; do
            if download "$url" server.jar; then ok=1; break; fi
        done
        [ "$ok" -eq 1 ] || { echo "[smoke] spigot 1.12.2 mirrors unreachable"; exit 1; }
        ;;
    *)
        echo "[smoke] unsupported version: $MC_VERSION" >&2
        exit 2 ;;
esac

# ── 2. 部署插件 ─────────────────────────────────────────────────────
mkdir -p plugins
cp "$PLUGIN_JAR" plugins/
echo "eula=true" > eula.txt
# 缩短世界生成与减少 IO，提速启动
cat > server.properties <<EOF
online-mode=false
spawn-protection=0
view-distance=2
simulation-distance=4
max-players=1
generate-structures=false
EOF

# ── 3. 启动服务端，捕获标准输出/错误 ────────────────────────────────
echo "[smoke] launching: $JAVA_BIN -jar server.jar nogui"
"$JAVA_BIN" -version
mkfifo cmd_pipe
# 保持管道写端打开，避免 server 立即收到 EOF
exec 3>cmd_pipe
"$JAVA_BIN" -Xms512M -Xmx1G -jar server.jar nogui <cmd_pipe >server.log 2>&1 &
SERVER_PID=$!
echo "[smoke] server pid=$SERVER_PID"

# ── 4. 扫描日志，等待目标行出现 ─────────────────────────────────────
SUCCESS=0
for i in $(seq 1 "$TIMEOUT_SEC"); do
    if [ -s server.log ] && grep -q -F "$EXPECT" server.log; then
        SUCCESS=1
        echo "[smoke] ✅ matched at ${i}s: $EXPECT"
        break
    fi
    if ! kill -0 "$SERVER_PID" 2>/dev/null; then
        echo "[smoke] server exited unexpectedly at ${i}s"
        break
    fi
    sleep 1
done

# ── 5. 优雅停止 ────────────────────────────────────────────────────
if kill -0 "$SERVER_PID" 2>/dev/null; then
    echo "[smoke] sending stop"
    echo "stop" >&3 || true
    exec 3>&-
    for _ in $(seq 1 60); do
        kill -0 "$SERVER_PID" 2>/dev/null || break
        sleep 1
    done
    if kill -0 "$SERVER_PID" 2>/dev/null; then
        echo "[smoke] forcing kill"
        kill -9 "$SERVER_PID" || true
    fi
fi
wait "$SERVER_PID" 2>/dev/null || true

# ── 6. 收集日志 ────────────────────────────────────────────────────
mkdir -p "$LOG_DIR"
cp server.log "$LOG_OUT" || true
echo "::group::server.log (tail 300)"
tail -n 300 server.log || true
echo "::endgroup::"

if [ "$SUCCESS" -eq 1 ]; then
    exit 0
else
    echo "[smoke] ❌ expected line never appeared within ${TIMEOUT_SEC}s: $EXPECT"
    exit 1
fi
