package com.example.dynamicisland

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import com.example.dynamicisland.databinding.ActivityMainBinding
import com.example.dynamicisland.service.DynamicIslandService

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val RC_OVERLAY      = 1001
    private val RC_NOTIFICATION = 1002
    private val RC_ACCESSIBILITY = 1003

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupUI()
    }

    override fun onResume() {
        super.onResume()
        refreshPermissionStatuses()
        syncSwitchStates()
    }

    private fun setupUI() {
        binding.btnOverlayPermission.setOnClickListener { requestOverlayPermission() }
        binding.btnNotificationPermission.setOnClickListener { requestNotificationPermission() }
        binding.btnStartIsland.setOnClickListener { startDynamicIsland() }
        binding.btnStopIsland.setOnClickListener { stopDynamicIsland() }
        binding.btnDemoCall.setOnClickListener { sendDemoEvent(DynamicIslandService.ACTION_DEMO_CALL) }
        binding.btnDemoMusic.setOnClickListener { sendDemoEvent(DynamicIslandService.ACTION_DEMO_MUSIC) }
        binding.btnDemoNotification.setOnClickListener { sendDemoEvent(DynamicIslandService.ACTION_DEMO_NOTIFICATION) }
        binding.btnDemoTimer.setOnClickListener { sendDemoEvent(DynamicIslandService.ACTION_DEMO_TIMER) }
        binding.btnDemoClear.setOnClickListener { sendDemoEvent(DynamicIslandService.ACTION_CLEAR) }

        // ── Siempre activa (auto-start en boot) ─────────────────────────────
        binding.switchAlwaysOn.setOnCheckedChangeListener { _, checked ->
            prefs().edit().putBoolean(DynamicIslandService.PREF_ALWAYS_ON, checked).apply()
            val msg = if (checked) "✅ Se iniciará automáticamente al encender" else "Auto-inicio desactivado"
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }

        // ── Modo Gaming ──────────────────────────────────────────────────────
        binding.switchGamingMode.setOnCheckedChangeListener { _, checked ->
            prefs().edit().putBoolean(DynamicIslandService.PREF_GAMING_MODE, checked).apply()
            sendDemoEvent(DynamicIslandService.ACTION_TOGGLE_GAMING)
            val msg = if (checked) "🎮 Modo Gaming: isla ultra-pequeña" else "Modo Gaming desactivado"
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }

        // ── Acceso al uso (redes sociales mini mode) ────────────────────────
        binding.btnAccessibilityPermission?.setOnClickListener { requestUsageStatsPermission() }
    }

    private fun hasOverlayPermission() = Settings.canDrawOverlays(this)

    private fun hasNotificationListenerPermission() =
        NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)

    private fun hasAccessibilityPermission(): Boolean = hasUsageStatsPermission()

    private fun hasUsageStatsPermission(): Boolean {
        return runCatching {
            val um = getSystemService(android.app.usage.UsageStatsManager::class.java)
            val now = System.currentTimeMillis()
            val stats = um.queryUsageStats(android.app.usage.UsageStatsManager.INTERVAL_DAILY, now - 1000, now)
            stats != null && stats.isNotEmpty()
        }.getOrDefault(false)
    }

    private fun refreshPermissionStatuses() {
        val overlayOk = hasOverlayPermission()
        val notifOk   = hasNotificationListenerPermission()
        val usageOk   = hasUsageStatsPermission()

        binding.tvOverlayStatus.text      = if (overlayOk) "✅ Overlay: Concedido" else "❌ Overlay: Requerido"
        binding.tvNotificationStatus.text = if (notifOk)   "✅ Notificaciones: Concedido" else "❌ Notificaciones: Requerido"
        binding.tvAccessibilityStatus?.text = if (usageOk) "✅ Uso de apps: Activo (modo social OK)" else "⚠️ Uso de apps: Opcional (modo redes sociales)"

        binding.btnOverlayPermission.isEnabled      = !overlayOk
        binding.btnNotificationPermission.isEnabled = !notifOk
        binding.btnAccessibilityPermission?.isEnabled = !usageOk

        val coreGranted = overlayOk && notifOk
        binding.btnStartIsland.isEnabled = coreGranted
        binding.tvPermissionHint.text = when {
            coreGranted && usageOk -> "✅ Todo listo. Dynamic Island completamente funcional."
            coreGranted -> "⚠️ Permisos básicos OK. Activa 'Acceso al uso de apps' para modo redes sociales."
            else -> "❌ Concede los permisos necesarios para activar Dynamic Island."
        }
    }

    private fun syncSwitchStates() {
        binding.switchAlwaysOn.isChecked  = prefs().getBoolean(DynamicIslandService.PREF_ALWAYS_ON,  false)
        binding.switchGamingMode.isChecked = prefs().getBoolean(DynamicIslandService.PREF_GAMING_MODE, false)
    }

    private fun requestOverlayPermission() {
        startActivityForResult(
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")), RC_OVERLAY)
        Toast.makeText(this, "Activa 'Aparecer encima de otras apps'", Toast.LENGTH_LONG).show()
    }

    private fun requestNotificationPermission() {
        startActivityForResult(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS), RC_NOTIFICATION)
        Toast.makeText(this, "Activa '${getString(R.string.app_name)}' en acceso a notificaciones", Toast.LENGTH_LONG).show()
    }

    private fun requestUsageStatsPermission() {
        startActivityForResult(
            Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS), RC_ACCESSIBILITY
        )
        Toast.makeText(this, "Activa 'Dynamic Island' en Acceso al uso de apps", Toast.LENGTH_LONG).show()
    }

    private fun requestAccessibilityPermission() = requestUsageStatsPermission()

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        refreshPermissionStatuses()
    }

    private fun startDynamicIsland() {
        if (!hasOverlayPermission()) {
            Toast.makeText(this, "❌ Overlay no concedido", Toast.LENGTH_LONG).show()
            return
        }
        
        // Solicitar permiso de llamadas si falta
        if (androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.ANSWER_PHONE_CALLS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            androidx.core.app.ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.ANSWER_PHONE_CALLS), 2001)
        }

        try {
            startForegroundService(Intent(this, DynamicIslandService::class.java).apply {
                action = DynamicIslandService.ACTION_START
            })
            Toast.makeText(this, "Dynamic Island iniciada ✨", Toast.LENGTH_SHORT).show()
            binding.btnStartIsland.isEnabled = false
            binding.btnStopIsland.isEnabled = true
            binding.cardDemoControls.visibility = android.view.View.VISIBLE
        } catch (e: Exception) {
            Toast.makeText(this, "ERROR: ${e.javaClass.simpleName}: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun stopDynamicIsland() {
        startService(Intent(this, DynamicIslandService::class.java).apply {
            action = DynamicIslandService.ACTION_STOP
        })
        Toast.makeText(this, "Dynamic Island detenida", Toast.LENGTH_SHORT).show()
        binding.btnStartIsland.isEnabled = hasOverlayPermission() && hasNotificationListenerPermission()
        binding.btnStopIsland.isEnabled = false
        binding.cardDemoControls.visibility = android.view.View.GONE
    }

    private fun sendDemoEvent(action: String) {
        startService(Intent(this, DynamicIslandService::class.java).apply { this.action = action })
    }

    private fun prefs() = getSharedPreferences(DynamicIslandService.PREFS_NAME, Context.MODE_PRIVATE)
}
