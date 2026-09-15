@echo off
chcp 65001 >nul
title StretchX - S25 Ultra ADB Kontrol Aracı

:menu
cls
echo =======================================================
echo   StretchX - Samsung Galaxy S25 Ultra True Stretch
echo =======================================================
echo.
echo [1] 4:3 Ultra Netlik Modu Uygula (1440x1920 @ 440 DPI)
echo [2] 4:3 Yüksek FPS Modu Uygula   (1080x1440 @ 360 DPI)
echo [3] 16:10 Geniş Tablet Modu       (1440x2304 @ 480 DPI)
echo [4] Mevcut Çözünürlük ve Yedek Durumunu Görüntüle
echo [5] EKRANI SIFIRLA (Orijinal Duruma ve 120Hz LTPO'ya Dön)
echo [6] Çıkış
echo.
set /p opt="Seçiminiz (1-6): "

if "%opt%"=="1" goto opt1
if "%opt%"=="2" goto opt2
if "%opt%"=="3" goto opt3
if "%opt%"=="4" goto opt4
if "%opt%"=="5" goto opt5
if "%opt%"=="6" goto opt6

echo.
echo [!] Geçersiz seçim! Lütfen 1-6 arasında bir değer girin.
pause
goto menu

:opt1
echo.
echo [*] 4:3 Ultra Netlik modu uygulanıyor...
call :backup_display
adb shell "cmd window set-letterbox-style --aspectRatio 1.33 && cmd window set-letterbox-style --cornerRadius 0 && cmd window set-letterbox-style --isLetterboxActivityCornersRounded false && cmd window set-letterbox-style --backgroundType solid_color --backgroundColor 0x00000000"
adb shell "settings put global enable_freeform_support 1 && settings put secure force_resizable_activities 1 && cmd window scaling off"
adb shell "wm size 1440x1920 && wm density 440"
echo [+] Tamamlandı! Şimdi oyuna girebilirsiniz.
pause
goto menu

:opt2
echo.
echo [*] 4:3 Yüksek FPS modu uygulanıyor...
call :backup_display
adb shell "cmd window set-letterbox-style --aspectRatio 1.33 && cmd window set-letterbox-style --cornerRadius 0 && cmd window set-letterbox-style --isLetterboxActivityCornersRounded false && cmd window set-letterbox-style --backgroundType solid_color --backgroundColor 0x00000000"
adb shell "settings put global enable_freeform_support 1 && settings put secure force_resizable_activities 1 && cmd window scaling off"
adb shell "wm size 1080x1440 && wm density 360"
echo [+] Tamamlandı! Şimdi oyuna girebilirsiniz.
pause
goto menu

:opt3
echo.
echo [*] 16:10 Geniş Tablet modu uygulanıyor...
call :backup_display
adb shell "cmd window set-letterbox-style --aspectRatio 1.6 && cmd window set-letterbox-style --cornerRadius 0 && cmd window set-letterbox-style --isLetterboxActivityCornersRounded false && cmd window set-letterbox-style --backgroundType solid_color --backgroundColor 0x00000000"
adb shell "settings put global enable_freeform_support 1 && settings put secure force_resizable_activities 1 && cmd window scaling off"
adb shell "wm size 1440x2304 && wm density 480"
echo [+] Tamamlandı! Şimdi oyuna girebilirsiniz.
pause
goto menu

:opt4
echo.
echo =======================================================
echo   Aktif Ekran ve Yedek Durumu Bilgisi
echo =======================================================
echo [*] Aktif Ekran Çözünürlüğü (wm size):
adb shell wm size
echo.
echo [*] Aktif Ekran Yoğunluğu (wm density):
adb shell wm density
echo.
echo [*] Yenileme Hızı Ayarları (Refresh Rate):
adb shell "echo -n 'Min Refresh Rate: ' && settings get system min_refresh_rate && echo -n 'Peak Refresh Rate: ' && settings get system peak_refresh_rate"
echo.
echo [*] Kayıtlı Yedek Dosyası (/data/local/tmp/stretchx_backup.txt):
adb shell "if [ -f /data/local/tmp/stretchx_backup.txt ]; then cat /data/local/tmp/stretchx_backup.txt; else echo 'Yedek dosyası bulunamadı (Cihaz orijinal modunda veya henüz yedek alınmadı).'; fi"
echo =======================================================
echo.
pause
goto menu

:opt5
echo.
echo [*] S25 Ultra ekranı orijinal durumuna ve 120Hz LTPO ayarlarına döndürülüyor...
adb shell "if [ -f /data/local/tmp/stretchx_backup.txt ]; then S=$(grep '^PRE_SIZE=' /data/local/tmp/stretchx_backup.txt | head -n 1 | cut -d= -f2 | tr -d '\r\n '); SO=$(grep '^PRE_SIZE_OVERRIDE=' /data/local/tmp/stretchx_backup.txt | head -n 1 | cut -d= -f2 | tr -d '\r\n '); D=$(grep '^PRE_DENSITY=' /data/local/tmp/stretchx_backup.txt | head -n 1 | cut -d= -f2 | tr -d '\r\n '); DO=$(grep '^PRE_DENSITY_OVERRIDE=' /data/local/tmp/stretchx_backup.txt | head -n 1 | cut -d= -f2 | tr -d '\r\n '); if [ \"$SO\" = '1' ] && [ -n \"$S\" ]; then echo '[*] Orijinal kullanıcı çözünürlüğü uygulanıyor: '\"$S\"; wm size \"$S\"; else echo '[*] Donanımsal fabrika çözünürlüğüne sıfırlanıyor (wm size reset)...'; wm size reset; fi; if [ \"$DO\" = '1' ] && [ -n \"$D\" ]; then echo '[*] Orijinal kullanıcı DPI değeri uygulanıyor: '\"$D\"; wm density \"$D\"; else echo '[*] Donanımsal fabrika DPI değerine sıfırlanıyor (wm density reset)...'; wm density reset; fi; rm -f /data/local/tmp/stretchx_backup.txt; echo '[+] Yedek dosyası temizlendi.'; else echo '[!] Yedek dosyası bulunamadı. Fabrika ayarlarına sıfırlanıyor...'; wm size reset; wm density reset; fi; cmd window reset-letterbox-style; cmd window scaling auto; settings put system min_refresh_rate 120.0; settings put system peak_refresh_rate 120.0"
echo.
echo [+] Ekran başarıyla sıfırlandı, letterbox kaldırıldı ve 120Hz LTPO aktif edildi!
echo.
pause
goto menu

:opt6
goto end

:backup_display
echo [*] Mevcut ekran durumu kontrol ediliyor ve yedekleniyor...
adb shell "if [ ! -f /data/local/tmp/stretchx_backup.txt ]; then S=$(wm size); D=$(wm density); SO=0; DO=0; if echo \"$S\" | grep -q 'Override size:'; then CS=$(echo \"$S\" | grep 'Override size:' | sed 's/.*Override size: *//' | tr -d '\r\n '); SO=1; else CS=$(echo \"$S\" | grep 'Physical size:' | sed 's/.*Physical size: *//' | tr -d '\r\n '); fi; if echo \"$D\" | grep -q 'Override density:'; then CD=$(echo \"$D\" | grep 'Override density:' | sed 's/.*Override density: *//' | tr -d '\r\n '); DO=1; else CD=$(echo \"$D\" | grep 'Physical density:' | sed 's/.*Physical density: *//' | tr -d '\r\n '); fi; echo \"PRE_SIZE=$CS\" > /data/local/tmp/stretchx_backup.txt; echo \"PRE_SIZE_OVERRIDE=$SO\" >> /data/local/tmp/stretchx_backup.txt; echo \"PRE_DENSITY=$CD\" >> /data/local/tmp/stretchx_backup.txt; echo \"PRE_DENSITY_OVERRIDE=$DO\" >> /data/local/tmp/stretchx_backup.txt; echo \"HAD_OVERRIDE=$SO\" >> /data/local/tmp/stretchx_backup.txt; echo '[+] Ekran durumu yedeklendi (/data/local/tmp/stretchx_backup.txt): '\"$CS\"' @ '\"$CD\"' DPI'; else echo '[i] Mevcut yedek bulundu (/data/local/tmp/stretchx_backup.txt), orijinal baseline durumu korunuyor.'; fi"
exit /b 0

:end
