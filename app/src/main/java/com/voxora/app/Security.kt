package com.voxora.app

import android.app.KeyguardManager
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PersistableBundle
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Encrypts everything personal (your last dictation, your shortcuts) with a key locked inside the phone's secure hardware. */
object Vault {
    private const val ALIAS = "voxora_vault_v1"
    private const val STORE = "AndroidKeyStore"

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance(STORE).apply { load(null) }
        (ks.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, STORE)
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return gen.generateKey()
    }

    fun encrypt(plain: String): String {
        if (plain.isEmpty()) return ""
        return try {
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.ENCRYPT_MODE, key())
            val out = c.iv + c.doFinal(plain.toByteArray(Charsets.UTF_8))
            Base64.encodeToString(out, Base64.NO_WRAP)
        } catch (_: Exception) { "" }
    }

    fun decrypt(enc: String): String {
        if (enc.isEmpty()) return ""
        return try {
            val raw = Base64.decode(enc, Base64.NO_WRAP)
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, raw, 0, 12))
            String(c.doFinal(raw, 12, raw.size - 12), Charsets.UTF_8)
        } catch (_: Exception) { "" }
    }
}

object Guard {
    /** SHA-256 of the official Voxora signing certificate. A modified/re-signed copy will not match. */
    private const val OFFICIAL_CERT = "59872271ae8749a29b8704f0ead8ebcf2ee4db71e830833bee80a77122747965"

    fun isGenuine(c: Context): Boolean {
        if (BuildConfig.DEBUG) return true
        return try {
            val sigs = if (Build.VERSION.SDK_INT >= 28) {
                val info = c.packageManager.getPackageInfo(c.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                val si = info.signingInfo ?: return false
                if (si.hasMultipleSigners()) si.apkContentsSigners else si.signingCertificateHistory
            } else {
                @Suppress("DEPRECATION")
                c.packageManager.getPackageInfo(c.packageName, PackageManager.GET_SIGNATURES).signatures
            } ?: return false
            sigs.any { sha256(it.toByteArray()) == OFFICIAL_CERT }
        } catch (_: Exception) { false }
    }

    private fun sha256(b: ByteArray) =
        MessageDigest.getInstance("SHA-256").digest(b).joinToString("") { "%02x".format(it) }

    fun isLocked(c: Context): Boolean =
        (c.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager).isKeyguardLocked

    /** Apps where the bubble never appears: payments, banking, passwords, authenticators, system screens. */
    val blockedApps = setOf(
        "com.android.settings", "com.android.systemui", "com.google.android.packageinstaller",
        "com.android.packageinstaller", "com.google.android.permissioncontroller",
        "com.google.android.apps.nbu.paisa.user", "com.phonepe.app", "net.one97.paytm",
        "in.org.npci.upiapp", "in.amazon.mShop.android.shopping.upi", "com.mobikwik_new",
        "com.sbi.lotusintouch", "com.sbi.SBIFreedomPlus", "com.csam.icici.bank.imobile",
        "com.snapwork.hdfc", "com.axis.mobile", "com.msf.kbank.mobile", "com.bankofbaroda.mconnect",
        "com.x8bit.bitwarden", "com.onepassword.android", "com.lastpass.lpandroid",
        "com.dashlane", "keepass2android.keepass2android", "com.kunzisoft.keepass.free",
        "com.google.android.apps.authenticator2", "com.azure.authenticator", "com.authy.authy",
        "com.google.android.gms",
    )

    private val main = Handler(Looper.getMainLooper())

    /** Copies text marked as sensitive (hidden from clipboard previews) and wipes it after 60 seconds. */
    fun safeCopy(c: Context, text: String) {
        val cm = c.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Voxora", text)
        if (Build.VERSION.SDK_INT >= 33) {
            clip.description.extras = PersistableBundle().apply { putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true) }
        }
        cm.setPrimaryClip(clip)
        var replaced = false
        val listener = ClipboardManager.OnPrimaryClipChangedListener { replaced = true }
        main.postDelayed({ cm.addPrimaryClipChangedListener(listener) }, 300)
        main.postDelayed({
            cm.removePrimaryClipChangedListener(listener)
            if (!replaced) {
                try {
                    if (Build.VERSION.SDK_INT >= 28) cm.clearPrimaryClip()
                    else cm.setPrimaryClip(ClipData.newPlainText("", ""))
                } catch (_: Exception) {}
            }
        }, 60_000)
    }
}
