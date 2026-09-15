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
4. **Dirençli Watchdog Servisi:** Oyunun kapandığını Shizuku üzerinden `dumpsys activity activities | grep -E 'topResumedActivity|mResumedActivity'` ile güvenli biçimde izler (ses seviyesi, bildirim veya gelen arama gibi geçici pencerelerde yanlış sıfırlama yapmaz). Oyundan çıkıldığı an ekranı otomatik olarak kullanıcının oyun öncesi orijinal çözünürlük ve DPI moduna (FHD+ veya QHD+ 120Hz) geri döndürür.

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
* **Telefondan Tek Tıkla:** `tools/quick_stretch.sh` dosyasını telefonundaki aShell veya Termux'ta `sh quick_stretch.sh` komutuyla çalıştırabilirsin. Komut dosyası işlem öncesinde mevcut çözünürlük ve DPI durumunu `/data/local/tmp/stretchx_backup.txt` içerisine otomatik yedekler; `sh quick_stretch.sh reset` dendiğinde kullanıcı ayarlarını (FHD+/QHD+ fark etmeksizin) eksiksiz geri yükler.
* **Bilgisayardan:** Telefonu USB ile bağlayıp `tools/quick_stretch.bat` dosyasına çift tıklayarak menüden (1-6) seçim yapabilir, aktif ekran/yedek durumunu görüntüleyebilir ve 120Hz LTPO fabrika ayarlarına güvenle dönebilirsin.

---

## 5. Anti-Cheat ve Güvenlik Analizi (Tencent ACE, Standoff 2, BattlEye)

StretchX'in çalışma prensibi, üçüncü taraf hile veya bellek modifikasyon araçlarından mimari olarak tamamen farklıdır:

### 1. Neden Sistem Düzeyinde WindowManager Değişiklikleri Tespit Edilemez?
* **Sıfır Bellek ve Paket Müdahalesi (Zero Memory/Hook Tampering):** StretchX, oyunun `.apk` paketine, DEX bayt koduna veya native `.so` kütüphanelerine müdahale etmez; bellek alanına (`ptrace`, `inline hook`, DLL/SO injection) sızmaz.
* **Resmi Android OS / DisplayManager API'leri:** `wm size`, `wm density` ve `cmd window set-letterbox-style` komutları, Android işletim sisteminin çekirdek `WindowManagerService` (WMS) bileşeni tarafından yönetilen meşru sistem çağrılarıdır.
* **Oyun Motoru Algısı:** Tencent ACE (PUBG Mobile), Axlebolt (Standoff 2), Unreal Engine veya Unity motorları ekran boyutunu sorguladıklarında (`DisplayMetrics`, `Configuration.screenWidthDp`/`screenHeightDp`), Android işletim sistemi onlara doğrudan yapılandırılmış çözünürlüğü raporlar. Oyun motoru açısından bu durum, cihazın **iPad Mini**, **Xiaomi Pad** veya katlanabilir bir tablet gibi doğal 4:3 en-boy oranına sahip bir cihazda çalışmasından farksızdır.

### 2. Kayan Baloncuk (Floating Overlay) Neden Dikkatli Kullanılmalıdır?
* **Overlay Tarayıcıları ve Yanlış Pozitifler:** Modern anti-cheat motorları (özellikle Tencent ACE / Protect), ekranda çizim yapan pencereleri (`TYPE_APPLICATION_OVERLAY`) tarayabilir. Overlay pencereleri oyun üzerinde nişangah (crosshair), ESP çizimi veya makro tetikleyici olarak kullanılabildiğinden, agresif sezgisel (heuristic) taramalar kayan pencereleri şüpheli olarak etiketleyebilir.
* **StretchX Sıfır-Tespit Güvenlik Mimarisi (Zero-Detection Model):**
  1. **Bildirim Çubuğu Butonu (Primary Zero-Detection):** Oyun esnasında ekran üzerinde tek bir piksel dahi çizilmez. Sıfırlama işlemi Android sistem bildirim çubuğundaki kalıcı buton (`ACTION_STOP_AND_RESET`) üzerinden tek dokunuşla gerçekleştirilir.
  2. **Güvenli Floating Overlay Tasarımı:** Arayüzde kayan buton varsayılan olarak **kapalı** tutulur. Etkinleştirildiğinde `FLAG_NOT_TOUCH_MODAL` ve `FLAG_NOT_FOCUSABLE` bayraklarıyla dokunma alanını kısıtlamaz, oyun içi dokunma girişlerine müdahale etmez. Ancak sıkı rekabetçi maçlarda ve turnuvalarda sıfır risk için bildirim paneli veya otomatik watchdog tavsiye edilir.

---

## 6. Oyun İçi X/Y Hassasiyet ve HUD Kalibrasyonu

True Stretched moduna geçildiğinde panelin fiziksel boyutları ile işlenen pikseller arasındaki matematiksel dönüşüm oyuncu ergonomisini doğrudan etkiler:

### 1. %62.5 Yatay Genişleme (Horizontal Stretch) Etkisi
* **Matematiksel Dönüşüm:** S25 Ultra'nın doğal paneli $3120 \times 1440$ çözünürlüğünde (~19.5:9) dikey mimariye sahiptir. 4:3 modunda ($1920 \times 1440$), dikey yükseklik korunurken yatay eksendeki pikseller fiziksel panel genişliğine tam olarak gerilir.
* **Hedef Modelleri:** Rakiplerin karakter modelleri, kafa ve gövde vuruş alanları (hitbox) yatayda görsel olarak **%62.5 oranında genişler**. Bu durum uzaktaki hedeflerin tespitini ve nişan almayı (tracking) belirgin şekilde kolaylaştırır.

### 2. X/Y Kamera Hassasiyeti Dengelemesi (Sensitivity Compensation)
* **Görsel Açısal Hız Farkı:** Ekran yatayda %62.5 gerildiği için parmağınızı yatayda 1 cm kaydırdığınızda oluşan görsel hareket, dikeydeki 1 cm kaydırmaya kıyasla gözünüze çok daha hızlı görünür. Bu durum kas hafızasında "aşırı savrulma" (overshoot) hissi yaratabilir.
* **Kalibrasyon Kuralı:**
  * **Yatay Kamera Hassasiyeti (X Ekseni):** Oyun içi genel kamera ve serbest bakış hassasiyetinizi yatay eksende yaklaşık **%15-%20 oranında düşürün** (Örn: Normalde 100 olan hassasiyeti 80-85 bandına çekin).
  * **Dikey Kamera Hassasiyeti (Y Ekseni):** Dikey eksende herhangi bir basıklık veya gerilme olmadığı için dikey hassasiyet değerinizi değiştirmeyin (birebir koruyun).
  * **Ayrı X/Y Ayarı Olmayan Oyunlar:** Genel kamera hassasiyetini %10-%15 düşürüp jiroskop dikey çarpanını sabit tutarak mükemmel denge elde edebilirsiniz.

### 3. HUD (Arayüz Butonları) ve Dokunmatik Haritalama
* **Dokunmatik Hassasiyeti ve Konum Doğruluğu:** StretchX, çözünürlük ile DPI değerlerini senkronize ettiği için Android `InputManager` dokunmatik koordinatları 1:1 donanımsal doğrulukla dönüştürür. Dokunmatik noktalarda piksel kayması (touch offset) veya gecikme (0 ms latency) yaşanmaz.
* **Eliptik Buton Biçimleri:** Ateş, zıplama ve çömelme gibi dairesel butonlar ekranda hafif eliptik (oval) görünecektir. Dokunma alanı butonun yeni genişleyen sınırlarıyla birebir örtüşür.
* **Kenar Butonları Kalibrasyonu:** 19.5:9 en-boy oranına göre ekranın en sağ ve en sol köşelerine yapıştırılmış butonlar, gerilmiş görüntü alanında panelin kavis/çerçeve sınırlarına çok yaklaşabilir. Antrenman moduna (Training Grounds) girerek kenardaki butonları ekran merkezine doğru **%5-%10 içe çekmek**, ergonomik tutuş ve başparmak erişimi açısından tavsiye edilir.
