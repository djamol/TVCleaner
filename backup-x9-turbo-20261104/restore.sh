#!/usr/bin/env bash
# Restore packages disabled during X9 Turbo cleanup.
# Usage: ./restore.sh [192.168.1.41:5555]
set -euo pipefail
TV="${1:-192.168.1.41:5555}"
adb connect "$TV" >/dev/null

PACKAGES=(
  # Batch 1 — factory / OEM / recommendations
  com.DeviceTest
  com.softwinner.agingdragonbox
  com.softwinner.dragonbox
  com.softwinner.dragonatt
  com.softwinner.awlogsettings
  com.oranth.memoryclean
  com.google.android.feedback
  com.google.android.tvrecommendations
  com.google.android.partnersetup
  com.ald.appstore
  com.ald.apkmanage
  com.ald.atvapplist
  com.ald.netflix
  com.ald.ccs
  com.droidlogic.launcher.provider
  # Batch 2 — unused streaming / browser
  com.google.android.youtube.tv
  com.netflix.mediaclient
  com.spotify.tv.android
  com.phlox.tvwebbrowser
)

for p in "${PACKAGES[@]}"; do
  echo "re-enable $p"
  adb -s "$TV" shell pm enable --user 0 "$p" || true
done

# Undo Private DNS
adb -s "$TV" shell settings put global private_dns_mode opportunistic

echo "Done. Reboot recommended: adb -s $TV reboot"
