#!/usr/bin/env bash
# Starts the NeoForge dedicated server from the dev environment and waits until it
# reaches "Done". Fails if the server crashes, logs an ERROR from mod loading, or
# does not finish starting within the timeout.
set -uo pipefail

TIMEOUT_SECONDS="${SMOKE_TIMEOUT_SECONDS:-600}"
RUN_DIR="neoforge/run"
LOG_FILE="${SMOKE_LOG_FILE:-neoforge-server-smoke.log}"

mkdir -p "$RUN_DIR"
echo "eula=true" > "$RUN_DIR/eula.txt"
cat > "$RUN_DIR/server.properties" <<PROPS
online-mode=false
server-port=25599
level-seed=galacticraft
spawn-protection=0
PROPS

# Run Gradle in its own process group so the forked server JVM can be stopped with it.
setsid ./gradlew :neoforge:runServer --console=plain --no-daemon > "$LOG_FILE" 2>&1 &
GRADLE_PID=$!

cleanup() {
    kill -TERM -- "-$GRADLE_PID" 2>/dev/null
    sleep 5
    kill -KILL -- "-$GRADLE_PID" 2>/dev/null
    wait "$GRADLE_PID" 2>/dev/null
}
trap cleanup EXIT

result="timeout"
for ((elapsed = 0; elapsed < TIMEOUT_SECONDS; elapsed += 5)); do
    sleep 5
    if grep -qE 'Done \([0-9.]+s\)!' "$LOG_FILE"; then
        result="done"
        break
    fi
    if grep -qE 'Crash report|ModLoadingException|Failed to start the minecraft server|BUILD FAILED' "$LOG_FILE"; then
        result="crash"
        break
    fi
    if ! kill -0 "$GRADLE_PID" 2>/dev/null; then
        result="exited"
        break
    fi
done

errors=$(grep -cE '/ERROR\]' "$LOG_FILE" || true)
echo "Server smoke result: $result, ERROR lines: $errors"
grep -E '/ERROR\]' "$LOG_FILE" | head -50

if [[ "$result" != "done" || "$errors" -ne 0 ]]; then
    echo "--- last 80 log lines ---"
    tail -n 80 "$LOG_FILE"
    exit 1
fi
