# StretchX — Samsung Galaxy S25 Ultra True Stretched Game Launcher

Samsung Galaxy S25 Ultra (Snapdragon 8 Elite, Adreno 830, Android 15 / One UI 7) için geliştirilmiş, **siyah barsız (zero black bars)**, **donanımsal olarak gerilmiş (true hardware stretch)** ve **sıfır gecikmeli (0 ms touch latency)** otomatik oyun başlatıcı mimarisi.

---

## 1. Mimari ve "Siyah Barsız Stretch" Nasıl Çalışır?

### Kök Problem:
Android'in standart `wm size 1440x1920` komutu uygulandığında, Android'in görüntü yöneticisi (`LogicalDisplay.java` ve `SurfaceFlinger`) dikey yüksekliği ($1440$) eşitler; ancak S25 Ultra'nın $3120$ piksellik genişliğinden arta kalan $1200$ pikseli ikiye bölüp sağa ve sola **600'er piksellik siyah şerit (pillarbox)** ekler.

### StretchX Çözüm Zinciri:
1. **Letterbox API Override:** Android 12L-15'in `cmd window set-letterbox-style` fonksiyonu manipüle edilerek, en-boy oranı uyuşmazlığında sisteme siyah şerit üretmesi engellenir.
2. **Pencere Özgürleştirme (Freeform & Force Resizable):** `force_resizable_activities` ve `enable_freeform_support` parametreleri açılarak oyun motorunun kilitli ekran oranları ezilir.
3. **Dikey-Öncelikli Boyutlandırma:** S25 Ultra paneli dikey mimarilidir. Çözünürlük `1440x1920` (veya `1080x1440`) olarak dikeyde girilir; telefon yatay tutulduğunda jiroskop ve dokunmatik eksenleri bozulmadan $1920 \times 1440$ (4:3) yatay çalışma alanına $90^\circ$ döner.
4. **Sıfır Gecikmeli Watchdog Servisi:** Oyunun kapandığını Shizuku üzerinden doğrudan `dumpsys window | grep mCurrentFocus` ile sıfır gecikmeyle izler. Oyundan çıkıldığı an ekranı otomatik olarak $3120 \times 1440$ 120Hz moduna geri döndürür.

---

## 2. Desteklenen Modlar ve S25 Ultra Çözünürlük Tablosu

| Profil Adı | Girilen Dikey Çözünürlük | Oyun İçi Yatay Eksen | Oran | Genişleme (Stretch) | Tavsiye Edilen DPI |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **4:3 Ultra Netlik** | `1440x1920` | $1920 \times 1440$ | 4:3 | **%62.5 Genişleme** | 440 DPI |
| **4:3 Yüksek FPS** | `1080x1440` | $1440 \times 1080$ | 4:3 | **%62.5 Genişleme** | 360 DPI |
| **16:10 Geniş Tablet** | `1440x2304` | $2304 \times 1440$ | 16:10 | **%21.8 Genişleme** | 480 DPI |

---

## 3. Telefon Ayarları (Kritik 2 Adım)

1. **Kamera Kesiği (Camera Cutout):**
   * **Ayarlar > Ekran > Kamera Kesiği (veya Tam Ekran Uygulamaları)** bölümüne git.
   * Oynadığın oyunu (PUBG, Standoff 2 vb.) seç ve **"Kamera Kesiğini Göster" (Tam Ekran)** yap.
2. **Kablosuz Hata Ayıklama & Shizuku:**
   * Geliştirici Seçenekleri'nden *Kablosuz Hata Ayıklama*yı aç.
   * Shizuku uygulamasını açıp cihazı eşle ve servisi başlat.

---

## 4. Hızlı Kullanım Araçları (`tools/`)

Eğer APK'yı derlemeden önce doğrudan denemek istersen:
* **Telefondan Tek Tıkla:** `tools/quick_stretch.sh` dosyasını telefonundaki aShell veya Termux'ta `sh quick_stretch.sh` komutuyla çalıştırabilirsin.
* **Bilgisayardan:** Telefonu USB ile bağlayıp `tools/quick_stretch.bat` dosyasına çift tıklayarak 1-4 arasındaki menüden seçim yapabilirsin.
