#!/system/bin/sh
# ==============================================================================
# StretchX - Samsung Galaxy S25 Ultra Direct Stretch Script
# ==============================================================================

ACTION=$1
MODE=${2:-"ultra"} # "ultra", "fps", "tablet"

if [ "$ACTION" = "reset" ]; then
    echo "[*] S25 Ultra fabrika ayarlarına döndürülüyor (1440x3120, 120Hz)..."
    wm size reset
    wm density reset
    cmd window reset-letterbox-style
    cmd window scaling auto
    settings put system min_refresh_rate 120.0
    echo "[+] Ekran başarıyla sıfırlandı!"
    exit 0
fi

echo "[*] Samsung S25 Ultra True Stretched Başlatılıyor..."

# 1. Android 12L-15 Letterbox Siyah Barlarını Yok Et
cmd window set-letterbox-style --aspectRatio 1.33
cmd window set-letterbox-style --cornerRadius 0
cmd window set-letterbox-style --isLetterboxActivityCornersRounded false
cmd window set-letterbox-style --backgroundType solid_color --backgroundColor 0x00000000
settings put global enable_freeform_support 1
settings put secure force_resizable_activities 1
cmd window scaling off

# 3. Mode Seçimine Göre Çözünürlük Dayat (Dikey Eksende)
if [ "$MODE" = "fps" ]; then
    echo "[*] Mod: 4:3 Yüksek FPS (1080x1440 @ 360 DPI)"
    wm size 1080x1440
    wm density 360
elif [ "$MODE" = "tablet" ]; then
    echo "[*] Mod: 16:10 Geniş Tablet (1440x2304 @ 480 DPI)"
    wm size 1440x2304
    wm density 480
else
    echo "[*] Mod: 4:3 Ultra Netlik (1440x1920 @ 440 DPI)"
    wm size 1440x1920
    wm density 440
fi

echo "[+] True Stretch başarıyla uygulandı! Şimdi oyunu başlatabilirsiniz."
echo "[!] Normale dönmek için: sh quick_stretch.sh reset"
