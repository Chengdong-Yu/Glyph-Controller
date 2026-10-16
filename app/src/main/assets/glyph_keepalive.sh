#!/system/bin/sh
# Glyph Controller: keep the app available and react to charger insertion.
# Installed by the app into /data/adb/service.d/; this entry point exits quickly.

PATH=/system/bin:/system/xbin:/vendor/bin:/product/bin
umask 077

PKG=com.richard.glyphcontroller
SCRIPT=/data/adb/service.d/glyph_keepalive.sh
STATE_DIR=/data/adb/glyphcontroller
LOCK_DIR=$STATE_DIR/keepalive.lockdir
PID_FILE=$LOCK_DIR/pid
TAG=GlyphKA

log_message() {
    log -t "$TAG" "$1"
}

daemon_running() {
    [ -r "$PID_FILE" ] || return 1
    daemon_pid=$(cat "$PID_FILE" 2>/dev/null)
    case "$daemon_pid" in
        ''|*[!0-9]*) return 1 ;;
    esac
    [ -r "/proc/$daemon_pid/cmdline" ] || return 1
    daemon_cmd=$(tr '\000' ' ' < "/proc/$daemon_pid/cmdline")
    case "$daemon_cmd" in
        *"$SCRIPT --daemon"*) return 0 ;;
    esac
    return 1
}

stop_daemon() {
    if daemon_running; then
        kill "$daemon_pid" 2>/dev/null || return 1
    fi

    # Wait until the old process has removed its PID file.
    attempt=0
    while [ "$attempt" -lt 12 ]; do
        daemon_running || return 0
        attempt=$((attempt + 1))
        sleep 1
    done
    return 1
}

acquire_lock() {
    mkdir "$LOCK_DIR" 2>/dev/null && return 0

    # A second start can arrive before the first process writes its PID.
    attempt=0
    while [ "$attempt" -lt 3 ]; do
        daemon_running && return 1
        [ -r "$PID_FILE" ] && break
        attempt=$((attempt + 1))
        sleep 1
    done

    daemon_running && return 1
    rm -f "$PID_FILE"
    rmdir "$LOCK_DIR" 2>/dev/null || return 1
    mkdir "$LOCK_DIR" 2>/dev/null
}

charging_state() {
    known_source=0
    for source in usb ac wireless dc; do
        node=/sys/class/power_supply/$source/online
        [ -r "$node" ] || continue
        known_source=1
        if [ "$(cat "$node" 2>/dev/null)" = 1 ]; then
            echo 1
            return
        fi
    done

    if [ "$known_source" = 1 ]; then
        if [ "$(cat /sys/class/power_supply/battery/status 2>/dev/null)" = Charging ]; then
            echo 1
        else
            echo 0
        fi
        return
    fi

    case "$(cat /sys/class/power_supply/battery/status 2>/dev/null)" in
        Charging|Full) echo 1 ;;
        *) echo 0 ;;
    esac
}

# Keep each Android framework command bounded, including a hard-kill deadline.
bounded() {
    timeout -k 1 6 "$@"
}

record_failure() {
    printf '%s %s\n' "$(date -Iseconds)" "$1" > "$STATE_DIR/last_failure"
    log_message "$1"
}

start_service() {
    result=$(bounded am startservice --user 0 -n "$1" 2>&1)
    result_code=$?
    if [ "$result_code" -ne 0 ]; then
        record_failure "start $1 failed (exit=$result_code): $result"
        return 1
    fi
    case "$result" in
        *Error*|*Exception*)
            record_failure "start $1 failed: $result"
            return 1
            ;;
    esac
    return 0
}

wake_app() {
    # MIUI can mark the package stopped. The no-display activity clears that state.
    result=$(bounded am start --user 0 -n "$PKG/.WakeActivity" 2>&1)
    result_code=$?
    if [ "$result_code" -ne 0 ]; then
        record_failure "wake failed (exit=$result_code): $result"
        return 1
    fi
    case "$result" in
        *Error*|*Exception*) record_failure "wake failed: $result"; return 1 ;;
    esac
    return 0
}

recover_service() {
    start_service "$1" || { wake_app && start_service "$1"; }
}

configure_background_access() {
    bounded dumpsys deviceidle whitelist +"$PKG" >/dev/null 2>&1 || :
    bounded appops set "$PKG" RUN_ANY_IN_BACKGROUND allow >/dev/null 2>&1 || :
    bounded am set-standby-bucket "$PKG" exempted >/dev/null 2>&1 || :
    bounded appops set "$PKG" AUTO_START allow >/dev/null 2>&1 || :
    bounded appops set "$PKG" BOOT_COMPLETED allow >/dev/null 2>&1 || :
}

monotonic_seconds() {
    read -r uptime_value unused < /proc/uptime
    echo "${uptime_value%%.*}"
}

backoff() {
    delay=$(( $1 * 2 ))
    [ "$delay" -gt 120 ] && delay=120
    echo "$delay"
}

check_health() {
    now=$(monotonic_seconds)
    [ "$now" -lt "$next_probe" ] && return
    next_probe=$((now + 10))
    if pidof "$PKG" >/dev/null 2>&1 && [ "$now" -lt "$next_health" ]; then
        return
    fi
    # A living process is not evidence of a healthy notification connection.
    # The service repairs only a disconnected listener, and honors feature settings.
    if recover_service "$PKG/.KeepAliveService"; then
        health_delay=10
        next_health=$(( $(monotonic_seconds) + 60 ))
    else
        next_probe=$(( $(monotonic_seconds) + health_delay ))
        next_health=$next_probe
        health_delay=$(backoff "$health_delay")
    fi
}

check_charger() {
    current=$(charging_state)
    if [ "$current" = 0 ]; then
        previous=0
        next_charge=0
        charge_delay=2
        return
    fi
    now=$(monotonic_seconds)
    [ "$previous" = 0 ] && [ "$now" -ge "$next_charge" ] || return
    if recover_service "$PKG/.ChargingSweepService"; then
        previous=1
        charge_delay=2
        log_message "charger connected"
    else
        next_charge=$(( $(monotonic_seconds) + charge_delay ))
        charge_delay=$(backoff "$charge_delay")
    fi
}

run_daemon() {
    [ "$(id -u)" = 0 ] || exit 1
    mkdir -p "$STATE_DIR" || exit 1
    chmod 700 "$STATE_DIR" || exit 1

    acquire_lock || exit 0

    echo "$$" > "$PID_FILE"
    trap 'rm -f "$PID_FILE"; rmdir "$LOCK_DIR" 2>/dev/null' EXIT
    trap 'exit 0' INT TERM
    log_message "watchdog started (pid=$$)"

    until [ "$(getprop sys.boot_completed)" = 1 ]; do
        sleep 5
    done
    configure_background_access

    previous=$(charging_state)
    health_delay=10
    charge_delay=2
    next_probe=0
    next_health=0
    next_charge=0
    next_log=0
    while :; do
        check_health
        check_charger
        now=$(monotonic_seconds)
        if [ "$now" -ge "$next_log" ]; then
            log_message "watchdog alive"
            next_log=$((now + 300))
        fi
        # Framework broadcasts normally handle charger changes immediately; this is the fallback.
        sleep 5
    done
}

case "$1" in
    --daemon)
        run_daemon
        ;;
    --stop)
        stop_daemon
        ;;
    --status)
        daemon_running
        ;;
    *)
        # Detach so service.d and the installing su session can finish.
        setsid sh "$SCRIPT" --daemon </dev/null >/dev/null 2>&1 &
        ;;
esac
