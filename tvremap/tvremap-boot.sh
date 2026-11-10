#!/system/bin/sh
# X9 Turbo finishes BOOT_COMPLETED before sideloaded receivers are dispatched,
# so TV Remap never hears the boot broadcast. Start the listener from init.
sleep 12
i=0
while [ "$i" -lt 8 ]; do
    am start-foreground-service -n com.patilweb.tvremap/.RootKeyService >/dev/null 2>&1
    settings put secure accessibility_enabled 1 >/dev/null 2>&1
    dumpsys deviceidle whitelist +com.patilweb.tvremap >/dev/null 2>&1
    if pidof com.patilweb.tvremap >/dev/null 2>&1; then
        exit 0
    fi
    i=$((i + 1))
    sleep 8
done
exit 0
