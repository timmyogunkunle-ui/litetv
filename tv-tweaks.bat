@echo off
REM LiteTV speed tweaks - safe and reversible. Needs ADB (Android platform-tools) on your PC.
REM On the box: Settings > About > tap "Build" 7 times, then Developer options > enable USB/Network debugging.
set /p IP=Enter the TV box IP address (Settings ^> Network): 
adb connect %IP%:5555
echo Accept the "Allow debugging" prompt on the TV, then press a key.
pause
echo --- Faster animations (0.5x) ---
adb shell settings put global window_animation_scale 0.5
adb shell settings put global transition_animation_scale 0.5
adb shell settings put global animator_duration_scale 0.5
echo --- Ad-blocking Private DNS (AdGuard). Undo: settings put global private_dns_mode opportunistic ---
adb shell settings put global private_dns_mode hostname
adb shell settings put global private_dns_specifier dns.adguard-dns.com
echo --- Keep Wi-Fi awake / stop scanning while streaming ---
adb shell settings put global wifi_sleep_policy 2
adb shell settings put global wifi_scan_always_enabled 0
adb shell settings put global ble_scan_always_enabled 0
echo --- Install LiteTV (put LiteTV.apk next to this script) ---
if exist LiteTV.apk adb install -r LiteTV.apk
echo Done. Press HOME on the remote and choose LiteTV, then "Always".
pause
