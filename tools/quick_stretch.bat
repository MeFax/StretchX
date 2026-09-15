@echo off
chcp 65001 >nul
title StretchX - S25 Ultra ADB Kontrol Aracı

echo =======================================================
echo   StretchX - Samsung Galaxy S25 Ultra True Stretch
echo =======================================================
echo.
echo [1] 4:3 Ultra Netlik Modu Uygula (1440x1920 @ 440 DPI)
echo [2] 4:3 Yüksek FPS Modu Uygula   (1080x1440 @ 360 DPI)
echo [3] 16:10 Geniş Tablet Modu       (1440x2304 @ 480 DPI)
echo [4] EKRANI SIFIRLA (3120x1440 120Hz Fabrika Ayarlarına Dön)
echo [5] Çıkış
echo.
set /p opt="Seçiminiz (1-5): "

if "%opt%"=="1" (
    echo.
    echo [*] 4:3 Ultra Netlik modu uygulanıyor...
    adb shell "cmd window set-letterbox-style --aspectRatio 1.33 && cmd window set-letterbox-style --cornerRadius 0 && cmd window set-letterbox-style --backgroundType solid_color --backgroundColor 0x00000000"
    adb shell "settings put global enable_freeform_support 1 && settings put secure force_resizable_activities 1 && cmd window scaling off"
    adb shell "wm size 1440x1920 && wm density 440"
    echo [+] Tamamlandı! Şimdi oyuna girebilirsiniz.
    pause
    goto end
)

if "%opt%"=="2" (
    echo.
    echo [*] 4:3 Yüksek FPS modu uygulanıyor...
    adb shell "cmd window set-letterbox-style --aspectRatio 1.33 && cmd window set-letterbox-style --cornerRadius 0 && cmd window set-letterbox-style --backgroundType solid_color --backgroundColor 0x00000000"
    adb shell "settings put global enable_freeform_support 1 && settings put secure force_resizable_activities 1 && cmd window scaling off"
    adb shell "wm size 1080x1440 && wm density 360"
    echo [+] Tamamlandı! Şimdi oyuna girebilirsiniz.
    pause
    goto end
)

if "%opt%"=="3" (
    echo.
    echo [*] 16:10 Geniş Tablet modu uygulanıyor...
    adb shell "cmd window set-letterbox-style --aspectRatio 1.6 && cmd window set-letterbox-style --cornerRadius 0 && cmd window set-letterbox-style --backgroundType solid_color --backgroundColor 0x00000000"
    adb shell "settings put global enable_freeform_support 1 && settings put secure force_resizable_activities 1 && cmd window scaling off"
    adb shell "wm size 1440x2304 && wm density 480"
    echo [+] Tamamlandı! Şimdi oyuna girebilirsiniz.
    pause
    goto end
)

if "%opt%"=="4" (
    echo.
    echo [*] S25 Ultra ekranı fabrika ayarlarına döndürülüyor...
    adb shell "wm size reset && wm density reset && cmd window reset-letterbox-style && cmd window scaling auto && settings put system min_refresh_rate 120.0"
    echo [+] Ekran başarıyla sıfırlandı!
    pause
    goto end
)

:end
