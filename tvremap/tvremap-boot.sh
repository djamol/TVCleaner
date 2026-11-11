#!/system/bin/sh
# X9 Turbo finishes BOOT_COMPLETED before sideloaded receivers run, and the
# accessibility service stays enabled but unbound until something toggles it.
# Init starts this after boot so both the root listener and the key filter are up.
sleep 8
comp=com.patilweb.tvremap/com.patilweb.tvremap.RemapService
i=0
while [ "$i" -lt 12 ]; do
    am start-foreground-service -n com.patilweb.tvremap/.RootKeyService >/dev/null 2>&1
    settings put secure enabled_accessibility_services "$comp" >/dev/null 2>&1
    settings put secure accessibility_enabled 0 >/dev/null 2>&1
    settings put secure accessibility_enabled 1 >/dev/null 2>&1
    dumpsys deviceidle whitelist +com.patilweb.tvremap >/dev/null 2>&1
    if dumpsys accessibility 2>/dev/null | grep -A 6 "Bound services" | grep -q RemapService; then
        exit 0
    fi
    i=$((i + 1))
    sleep 5
done
exit 0
