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
    while [ "$attempt" -lt 5 ]; do
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

start_service() {
    if ! result=$(am startservice --user 0 -n "$1" 2>&1); then
        log_message "Unable to start $1: $result"
        return 1
    fi
    case "$result" in
        *Error*|*Exception*)
            log_message "Unable to start $1: $result"
            return 1
            ;;
    esac
    return 0
}

wake_app() {
    # MIUI marks the package stopped after a swipe-away and rejects service
    # starts. A no-display activity launch clears that state without opening UI.
    if ! result=$(am start --user 0 -n "$PKG/.WakeActivity" 2>&1); then
        log_message "Unable to wake app: $result"
        return 1
    fi
    case "$result" in
        *Error*|*Exception*)
            log_message "Unable to wake app: $result"
            return 1
            ;;
    esac
    return 0
}

rebind_listener() {
    # A MIUI swipe-away force-stops the package and drops the notification
    # listener binding. Starting the process alone does not restore it.
    if ! result=$(cmd notification allow_listener "$PKG/.GlyphNotificationService" 0 2>&1); then
        log_message "Unable to rebind notification listener: $result"
        return 1
    fi
    return 0
}

configure_background_access() {
    dumpsys deviceidle whitelist +"$PKG" >/dev/null 2>&1 || :
    appops set "$PKG" RUN_ANY_IN_BACKGROUND allow >/dev/null 2>&1 || :
    am set-standby-bucket "$PKG" exempted >/dev/null 2>&1 || :
    appops set "$PKG" AUTO_START allow >/dev/null 2>&1 || :
    appops set "$PKG" BOOT_COMPLETED allow >/dev/null 2>&1 || :
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
    tick=0
    while :; do
        if [ $((tick % 10)) -eq 0 ] && ! pidof "$PKG" >/dev/null 2>&1; then
            if start_service "$PKG/.KeepAliveService"; then
                rebind_listener
            elif wake_app && start_service "$PKG/.KeepAliveService"; then
                rebind_listener
            fi
        elif [ $((tick % 60)) -eq 0 ]; then
            # Also repair a listener that was dropped while the process lived.
            rebind_listener
        fi

        current=$(charging_state)
        if [ "$current" = 1 ] && [ "$previous" = 0 ]; then
            if start_service "$PKG/.ChargingSweepService"; then
                log_message "charger connected"
                previous=1
            elif wake_app && start_service "$PKG/.ChargingSweepService"; then
                rebind_listener
                log_message "charger connected"
                previous=1
            fi
        else
            previous=$current
        fi

        tick=$((tick + 1))
        if [ $((tick % 300)) -eq 0 ]; then
            log_message "watchdog alive"
        fi
        sleep 1
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
