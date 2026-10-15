package com.voxora.app

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableIntStateOf

class MainActivity : ComponentActivity() {

    private val tick = mutableIntStateOf(0)
    private val micLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { tick.intValue++ }

    override fun onCreate(savedInstanceState: Bundle?) {
        // No screenshots, screen recording or recent-apps preview of your dictations.
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Ignore taps when another app draws a fake screen on top (tapjacking protection).
        window.decorView.filterTouchesWhenObscured = true

        val genuine = Guard.isGenuine(this)
        setContent {
            VoxoraRoot(
                genuine = genuine,
                tick = tick.intValue,
                hasMic = { hasMic() },
                hasAccess = { hasAccessibility() },
                askMic = { micLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                openAccess = {
                    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                },
                onChanged = { TypingService.instance?.refreshBubbleSetting(); tick.intValue++ },
                onDecline = { finishAndRemoveTask() },
            )
        }
    }

    override fun onResume() {
        super.onResume()
        tick.intValue++
    }

    private fun hasMic() =
        checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun hasAccessibility(): Boolean {
        val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        val me = ComponentName(this, TypingService::class.java)
        return enabled.split(':').any {
            it.equals(me.flattenToString(), true) || it.equals(me.flattenToShortString(), true)
        }
    }
}
