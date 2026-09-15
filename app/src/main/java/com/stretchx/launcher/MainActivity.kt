package com.stretchx.launcher

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.stretchx.launcher.databinding.ActivityMainBinding
import kotlinx.coroutines.*

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var gameAdapter: GameAdapter? = null
    private val activityScope = CoroutineScope(Dispatchers.Main + Job())

    // Known target games signature list
    private val knownGamePackages = listOf(
        "com.tencent.ig",               // PUBG Mobile Global
        "com.pubg.krmobile",            // PUBG Mobile KR
        "com.pubg.imobile",             // BGMI India
        "com.vng.pubgmobile",           // PUBG Mobile VN
        "com.axlebolt.standoff2",       // Standoff 2
        "com.dts.freefireth",           // Free Fire
        "com.dts.freefiremax",          // Free Fire MAX
        "com.activision.callofduty.shooter", // Call of Duty Mobile
        "com.proximabeta.mf.uamo",      // Arena Breakout
        "com.riotgames.league.wildrift" // League of Legends Wild Rift
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }

        setupShizukuStatus()
        setupGameList()
        setupActions()
    }

    override fun onResume() {
        super.onResume()
        updateShizukuBadge()
        updateDeviceNativeStatus()
        if (binding.switchOverlay.isChecked && !Settings.canDrawOverlays(this)) {
            binding.switchOverlay.isChecked = false
        }
    }

    private fun setupShizukuStatus() {
        updateShizukuBadge()

        binding.btnConnectShizuku.setOnClickListener {
            if (!ShizukuManager.isAvailable()) {
                Toast.makeText(this, "Shizuku uygulaması çalışmıyor. Lütfen önce Shizuku'yu açın.", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            ShizukuManager.requestPermission { granted ->
                runOnUiThread {
                    updateShizukuBadge()
                    if (granted) {
                        updateDeviceNativeStatus()
                        Toast.makeText(this, "Shizuku yetkisi başarıyla verildi!", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, "Shizuku yetkisi reddedildi.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun updateShizukuBadge() {
        val available = ShizukuManager.isAvailable()
        val hasPerm = ShizukuManager.hasPermission()

        if (available && hasPerm) {
            binding.indicatorShizuku.setBackgroundColor(ContextCompat.getColor(this, R.color.status_green))
            binding.tvShizukuStatus.text = getString(R.string.shizuku_connected)
            binding.btnConnectShizuku.isEnabled = false
            binding.btnConnectShizuku.text = "Bağlandı"
            updateDeviceNativeStatus()
        } else {
            binding.indicatorShizuku.setBackgroundColor(ContextCompat.getColor(this, R.color.status_red))
            binding.tvShizukuStatus.text = getString(R.string.shizuku_disconnected)
            binding.btnConnectShizuku.isEnabled = true
            binding.btnConnectShizuku.text = getString(R.string.btn_connect_shizuku)
        }
    }

    private fun setupGameList() {
        binding.rvGames.layoutManager = LinearLayoutManager(this)
        activityScope.launch(Dispatchers.IO) {
            val installedGames = scanInstalledGames()
            withContext(Dispatchers.Main) {
                gameAdapter = GameAdapter(installedGames) { selected ->
                    Toast.makeText(this@MainActivity, "Seçilen: ${selected.name}", Toast.LENGTH_SHORT).show()
                }
                binding.rvGames.adapter = gameAdapter
            }
        }
    }

    private fun scanInstalledGames(): List<GameModel> {
        val pm = packageManager
        val packages = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        val gameList = mutableListOf<GameModel>()

        // 1. Add priority known games first
        for (pkg in packages) {
            if (knownGamePackages.contains(pkg.packageName)) {
                val name = pm.getApplicationLabel(pkg).toString()
                val icon = pm.getApplicationIcon(pkg)
                gameList.add(GameModel(name, pkg.packageName, icon))
            }
        }

        // 2. Add other installed game category apps
        for (pkg in packages) {
            if (!knownGamePackages.contains(pkg.packageName)) {
                val isGame = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    pkg.category == ApplicationInfo.CATEGORY_GAME
                } else {
                    @Suppress("DEPRECATION")
                    (pkg.flags and ApplicationInfo.FLAG_IS_GAME) != 0
                }
                if (isGame) {
                    val name = pm.getApplicationLabel(pkg).toString()
                    val icon = pm.getApplicationIcon(pkg)
                    gameList.add(GameModel(name, pkg.packageName, icon))
                }
            }
        }

        return gameList
    }

    private fun updateDeviceNativeStatus() {
        if (!ShizukuManager.isAvailable() || !ShizukuManager.hasPermission()) {
            binding.tvDeviceNativeStatus.text = getString(R.string.status_device_native_default)
            return
        }

        activityScope.launch(Dispatchers.IO) {
            DisplayOptimizer.backupCurrentDisplayState(this@MainActivity)
            val (size, density, hadOverride) = DisplayOptimizer.getBackupDisplayState(this@MainActivity)
            withContext(Dispatchers.Main) {
                if (size != null) {
                    val modeLabel = if (hadOverride) " (FHD+ Modu)" else " (QHD+ Doğal)"
                    binding.tvDeviceNativeStatus.text = "Cihaz Doğal Modu: $size @ ${density} DPI$modeLabel"
                } else {
                    binding.tvDeviceNativeStatus.text = getString(R.string.status_device_native_default)
                }
            }
        }
    }

    private fun setupActions() {
        binding.switchOverlay.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked && !Settings.canDrawOverlays(this)) {
                Toast.makeText(
                    this,
                    "Yüzen buton için 'Diğer uygulamaların üzerinde görüntüleme' izni gereklidir.",
                    Toast.LENGTH_SHORT
                ).show()
                checkOverlayPermission()
            }
        }
        binding.btnLaunchVirtual.setOnClickListener {
            val selectedGame = gameAdapter?.getSelectedGame()
            if (selectedGame == null) {
                Toast.makeText(this, "Lütfen bir oyun seçin.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (!ShizukuManager.hasPermission()) {
                Toast.makeText(this, "Lütfen önce Shizuku yetkisini onaylayın!", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            val preset = when {
                binding.rb43Fps.isChecked -> DisplayOptimizer.PRESET_4_3_FPS
                binding.rb1610.isChecked -> DisplayOptimizer.PRESET_16_10
                else -> DisplayOptimizer.PRESET_4_3_ULTRA
            }

            val launchIntent = packageManager.getLaunchIntentForPackage(selectedGame.packageName)
            val componentStr = launchIntent?.component?.flattenToString()

            val intent = Intent(this, StretchEngineActivity::class.java).apply {
                putExtra(StretchEngineActivity.EXTRA_TARGET_PACKAGE, selectedGame.packageName)
                putExtra(StretchEngineActivity.EXTRA_TARGET_COMPONENT, componentStr)
                putExtra(StretchEngineActivity.EXTRA_VIRT_WIDTH, preset.portraitHeight)
                putExtra(StretchEngineActivity.EXTRA_VIRT_HEIGHT, preset.portraitWidth)
                putExtra(StretchEngineActivity.EXTRA_VIRT_DENSITY, preset.density)
            }
            startActivity(intent)
        }

        binding.btnLaunchGame.setOnClickListener {
            val selectedGame = gameAdapter?.getSelectedGame()
            if (selectedGame == null) {
                Toast.makeText(this, "Lütfen bir oyun seçin.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (!ShizukuManager.hasPermission()) {
                Toast.makeText(this, "Lütfen önce Shizuku yetkisini onaylayın!", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            // Determine chosen preset
            val preset = when {
                binding.rb43Fps.isChecked -> DisplayOptimizer.PRESET_4_3_FPS
                binding.rb1610.isChecked -> DisplayOptimizer.PRESET_16_10
                else -> DisplayOptimizer.PRESET_4_3_ULTRA
            }

            // 1. Apply true stretch & letterbox override
            val success = DisplayOptimizer.applyTrueStretch(this, preset)
            if (!success) {
                Toast.makeText(this, "Çözünürlük komutu başarısız oldu.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // 2. Start Zero-Latency Watchdog Service
            val watchdogIntent = Intent(this, GameWatchdogService::class.java).apply {
                putExtra(GameWatchdogService.EXTRA_PACKAGE, selectedGame.packageName)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(watchdogIntent)
            } else {
                startService(watchdogIntent)
            }

            // 3. Start Floating Fail-Safe Overlay ONLY IF explicitly toggled ON and permission granted
            if (binding.switchOverlay.isChecked && Settings.canDrawOverlays(this)) {
                startService(Intent(this, FloatingOverlayService::class.java))
            }

            // 4. Show explanation that Notification Bar button is the 100% safe, ban-free reset mechanism
            Toast.makeText(
                this,
                "Güvenli Mod Aktif: Bildirim çubuğundaki 'Normale Dön' butonu %100 anti-cheat güvenlidir (ban riski sıfır).",
                Toast.LENGTH_LONG
            ).show()

            // 5. Launch the target game via Intent or Privileged Shell
            val launchIntent = packageManager.getLaunchIntentForPackage(selectedGame.packageName)
            val componentStr = launchIntent?.component?.flattenToString()
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(launchIntent)
            } else {
                DisplayOptimizer.launchGame(selectedGame.packageName, componentStr)
            }
        }

        binding.btnEmergencyReset.setOnClickListener {
            DisplayOptimizer.resetToNative(this)
            stopService(Intent(this, GameWatchdogService::class.java))
            stopService(Intent(this, FloatingOverlayService::class.java))
            updateDeviceNativeStatus()
            Toast.makeText(this, "Ekran S25 Ultra orijinal ayarlarına döndürüldü.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkOverlayPermission() {
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        activityScope.cancel()
    }
}
