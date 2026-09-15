#!/system/bin/sh
# ==============================================================================
# StretchX - Samsung Galaxy S25 Ultra Direct Stretch Script
# ==============================================================================

BACKUP_FILE="/data/local/tmp/stretchx_backup.txt"

ARG1=$1
ARG2=$2

if [ "$ARG1" = "reset" ]; then
    ACTION="reset"
elif [ "$ARG1" = "apply" ]; then
    ACTION="apply"
    MODE=${ARG2:-"ultra"}
elif [ "$ARG1" = "fps" ] || [ "$ARG1" = "tablet" ] || [ "$ARG1" = "ultra" ]; then
    ACTION="apply"
    MODE="$ARG1"
else
    ACTION="apply"
    MODE=${ARG1:-"ultra"}
fi

if [ "$ACTION" = "reset" ]; then
    echo "[*] S25 Ultra ekranı orijinal durumuna döndürülüyor..."
    if [ -f "$BACKUP_FILE" ]; then
        echo "[*] Yedek dosyası bulundu: $BACKUP_FILE"
        RESTORE_SIZE=$(grep '^PRE_SIZE=' "$BACKUP_FILE" | head -n 1 | cut -d'=' -f2 | tr -d '\r\n ')
        SIZE_OVERRIDE=$(grep '^PRE_SIZE_OVERRIDE=' "$BACKUP_FILE" | head -n 1 | cut -d'=' -f2 | tr -d '\r\n ')
        RESTORE_DENSITY=$(grep '^PRE_DENSITY=' "$BACKUP_FILE" | head -n 1 | cut -d'=' -f2 | tr -d '\r\n ')
        DENSITY_OVERRIDE=$(grep '^PRE_DENSITY_OVERRIDE=' "$BACKUP_FILE" | head -n 1 | cut -d'=' -f2 | tr -d '\r\n ')

        # Kullanıcının önceden FHD+ (1080x2340) gibi bir override'ı varsa onu geri yükle,
        # aksi halde donanımsal fabrika çözünürlüğüne dön
        if [ "$SIZE_OVERRIDE" = "1" ] && [ -n "$RESTORE_SIZE" ]; then
            echo "[*] Orijinal kullanıcı çözünürlüğü uygulanıyor: $RESTORE_SIZE"
            wm size "$RESTORE_SIZE"
        else
            echo "[*] Fabrika çözünürlüğüne sıfırlanıyor (wm size reset)..."
            wm size reset
        fi

        if [ "$DENSITY_OVERRIDE" = "1" ] && [ -n "$RESTORE_DENSITY" ]; then
            echo "[*] Orijinal kullanıcı DPI değeri uygulanıyor: $RESTORE_DENSITY"
            wm density "$RESTORE_DENSITY"
        else
            echo "[*] Fabrika DPI değerine sıfırlanıyor (wm density reset)..."
            wm density reset
        fi

        rm -f "$BACKUP_FILE"
        echo "[+] Yedek dosyası temizlendi."
    else
        echo "[!] Yedek dosyası bulunamadı. Donanımsal fabrika ayarlarına sıfırlanıyor..."
        wm size reset
        wm density reset
    fi

    echo "[*] Letterbox stilleri ve pencere ölçeklendirmesi sıfırlanıyor..."
    cmd window reset-letterbox-style
    cmd window scaling auto

    echo "[*] 120Hz LTPO yenileme hızı güvenceye alınıyor..."
    settings put system min_refresh_rate 120.0
    settings put system peak_refresh_rate 120.0

    echo "[+] Ekran başarıyla sıfırlandı ve 120Hz LTPO aktif edildi!"
    exit 0
fi

echo "[*] Samsung S25 Ultra True Stretched Başlatılıyor..."

# 0. Ön Durum Yedeği (Pre-stretch state backup)
if [ ! -f "$BACKUP_FILE" ]; then
    echo "[*] Mevcut ekran durumu taranıyor ve yedekleniyor..."
    RAW_SIZE=$(wm size)
    RAW_DENSITY=$(wm density)

    HAD_OVERRIDE=0
    if echo "$RAW_SIZE" | grep -q "Override size:"; then
        PRE_SIZE=$(echo "$RAW_SIZE" | grep "Override size:" | sed 's/.*Override size: *//' | tr -d '\r\n ')
        PRE_SIZE_OVERRIDE=1
        HAD_OVERRIDE=1
    else
        PRE_SIZE=$(echo "$RAW_SIZE" | grep "Physical size:" | sed 's/.*Physical size: *//' | tr -d '\r\n ')
        PRE_SIZE_OVERRIDE=0
    fi

    if echo "$RAW_DENSITY" | grep -q "Override density:"; then
        PRE_DENSITY=$(echo "$RAW_DENSITY" | grep "Override density:" | sed 's/.*Override density: *//' | tr -d '\r\n ')
        PRE_DENSITY_OVERRIDE=1
        HAD_OVERRIDE=1
    else
        PRE_DENSITY=$(echo "$RAW_DENSITY" | grep "Physical density:" | sed 's/.*Physical density: *//' | tr -d '\r\n ')
        PRE_DENSITY_OVERRIDE=0
    fi

    cat <<EOF > "$BACKUP_FILE"
PRE_SIZE=$PRE_SIZE
PRE_SIZE_OVERRIDE=$PRE_SIZE_OVERRIDE
PRE_DENSITY=$PRE_DENSITY
PRE_DENSITY_OVERRIDE=$PRE_DENSITY_OVERRIDE
HAD_OVERRIDE=$HAD_OVERRIDE
BACKUP_DATE=$(date "+%Y-%m-%d %H:%M:%S" 2>/dev/null || echo "N/A")
EOF
    echo "[+] Ekran durumu yedeklendi ($BACKUP_FILE):"
    echo "    Çözünürlük: $PRE_SIZE (Override: $PRE_SIZE_OVERRIDE) | DPI: $PRE_DENSITY (Override: $PRE_DENSITY_OVERRIDE)"
else
    echo "[i] Önceki yedek bulundu ($BACKUP_FILE), orijinal baseline durumu korunuyor."
fi

# 1. Android 12L-15 Letterbox Siyah Barlarını Yok Et
cmd window set-letterbox-style --aspectRatio 1.33
cmd window set-letterbox-style --cornerRadius 0
cmd window set-letterbox-style --isLetterboxActivityCornersRounded false
cmd window set-letterbox-style --backgroundType solid_color --backgroundColor 0x00000000
settings put global enable_freeform_support 1
settings put secure force_resizable_activities 1
cmd window scaling off

# 2. Mode Seçimine Göre Çözünürlük Dayat (Dikey Eksende)
if [ "$MODE" = "fps" ]; then
    echo "[*] Mod: 4:3 Yüksek FPS (1080x1440 @ 360 DPI)"
    wm size 1080x1440
    wm density 360
elif [ "$MODE" = "tablet" ]; then
    echo "[*] Mod: 16:10 Geniş Tablet (1440x2304 @ 480 DPI)"
    cmd window set-letterbox-style --aspectRatio 1.6
    wm size 1440x2304
    wm density 480
else
    echo "[*] Mod: 4:3 Ultra Netlik (1440x1920 @ 440 DPI)"
    wm size 1440x1920
    wm density 440
fi

echo "[+] True Stretch başarıyla uygulandı! Şimdi oyunu başlatabilirsiniz."
echo "[!] Normale dönmek için: sh quick_stretch.sh reset"
