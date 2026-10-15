package com.voxora.app

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.PixelFormat
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import kotlin.math.abs

/**
 * Watches for a normal text box being focused in any app, shows the Voxora bubble,
 * and types the dictated words into that box when you release the bubble.
 */
class TypingService : AccessibilityService() {

    private val main = Handler(Looper.getMainLooper())
    private lateinit var wm: WindowManager
    private var bubble: BubbleView? = null
    private var params: WindowManager.LayoutParams? = null
    private var shown = false
    private var target: AccessibilityNodeInfo? = null
    private var engine: VoiceEngine? = null
    private var busy = false
    private var genuine = true

    // for "scratch that": what the box looked like before our last insert (kept in memory only)
    private var undoNode: AccessibilityNodeInfo? = null
    private var undoText: String? = null

    private val hideRunnable = Runnable { if (!busy) hideBubble() }

    override fun onServiceConnected() {
        super.onServiceConnected()
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        genuine = Guard.isGenuine(this)
        if (!genuine) {
            // A modified copy of Voxora: refuse to run.
            toast("This copy of Voxora was modified. Install the official version.")
            disableSelf()
            return
        }
        engine = VoiceEngine(
            this,
            onLevel = { bubble?.setLevel(it) },
            onResult = { text -> main.post { handleResult(text) } },
            onError = { msg -> main.post { handleError(msg) } },
        )
        instance = this
    }

    override fun onDestroy() {
        instance = null
        engine?.destroy()
        hideBubble()
        super.onDestroy()
    }

    override fun onInterrupt() {}

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (busy || !genuine) return
        if (!Prefs.bubbleOn(this) || !Prefs.consented(this) || Guard.isLocked(this)) { hideBubble(); return }
        val pkg = event?.packageName?.toString()
        if (pkg != null && pkg in Guard.blockedApps) { hideBubble(); return }
        val node = findEditableFocus(event)
        if (node != null && isAllowed(node)) {
            target = node
            main.removeCallbacks(hideRunnable)
            showBubble()
        } else if (shown) {
            main.removeCallbacks(hideRunnable)
            main.postDelayed(hideRunnable, 700)
        }
    }

    // ---------- finding the text box ----------

    private fun findEditableFocus(event: AccessibilityEvent?): AccessibilityNodeInfo? {
        try {
            val focused = rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            if (focused != null && focused.isEditable && focused.isFocused) return focused
            // check other interactive windows (e.g. dialogs, split screen)
            for (w in windows) {
                val f = w.root?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: continue
                if (f.isEditable && f.isFocused) return f
            }
            val src = event?.source
            if (src != null && src.isEditable && src.isFocused) return src
        } catch (_: Exception) {}
        return null
    }

    /** Never appear on passwords, PINs, OTPs, card numbers or phone-number boxes. */
    private fun isAllowed(n: AccessibilityNodeInfo): Boolean {
        if (n.isPassword) return false
        val pkg = n.packageName?.toString()
        if (pkg != null && pkg in Guard.blockedApps) return false
        val type = n.inputType
        val cls = type and InputType.TYPE_MASK_CLASS
        val variation = type and InputType.TYPE_MASK_VARIATION
        if (cls == InputType.TYPE_CLASS_NUMBER || cls == InputType.TYPE_CLASS_PHONE || cls == InputType.TYPE_CLASS_DATETIME) return false
        if (cls == InputType.TYPE_CLASS_TEXT && (
                variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
                variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD ||
                variation == InputType.TYPE_TEXT_VARIATION_URI)) return false
        val clues = listOfNotNull(n.viewIdResourceName, n.hintText?.toString(), n.contentDescription?.toString())
            .joinToString(" ").lowercase()
        val blocked = listOf("password", "passcode", "otp", "pin", "cvv", "cvc", "card number", "url_bar", "upi")
        if (blocked.any { clues.contains(it) }) return false
        return true
    }

    // ---------- bubble window ----------

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun showBubble() {
        if (shown) return
        val view = bubble ?: BubbleView(this).also { bubble = it; attachTouch(it) }
        val screenH = resources.displayMetrics.heightPixels
        val p = WindowManager.LayoutParams(
            dp(68), dp(68),
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = dp(4)
            val saved = Prefs.bubbleY(this@TypingService)
            y = if (saved in 0..screenH) saved else (screenH * 0.42f).toInt()
        }
        try {
            view.mode = BubbleView.Mode.IDLE
            wm.addView(view, p)
            params = p
            shown = true
        } catch (_: Exception) { shown = false }
    }

    private fun hideBubble() {
        val v = bubble ?: return
        if (shown) { try { wm.removeView(v) } catch (_: Exception) {} }
        shown = false
    }

    private fun attachTouch(view: BubbleView) {
        var downY = 0f
        var startY = 0
        var dragging = false
        var pressed = false
        val slop = dp(14)

        view.setOnTouchListener { v, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downY = e.rawY; startY = params?.y ?: 0; dragging = false; pressed = true
                    beginListening(v)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dy = e.rawY - downY
                    if (!dragging && abs(dy) > slop) {
                        dragging = true
                        engine?.cancel(); busy = false
                        view.mode = BubbleView.Mode.IDLE
                    }
                    if (dragging) {
                        params?.let { p ->
                            p.y = (startY + dy).toInt().coerceAtLeast(0)
                            try { wm.updateViewLayout(view, p) } catch (_: Exception) {}
                        }
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (pressed) {
                        pressed = false
                        if (dragging) params?.let { Prefs.setBubbleY(this, it.y) }
                        else if (busy) { view.mode = BubbleView.Mode.PROCESSING; engine?.finish() }
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun beginListening(v: android.view.View) {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            toast("Open Voxora and allow the microphone first")
            return
        }
        busy = true
        main.removeCallbacks(hideRunnable)
        v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        bubble?.mode = BubbleView.Mode.LISTENING
        engine?.start()
    }

    // ---------- results ----------

    private fun handleResult(raw: String) {
        if (Prefs.voiceCommands(this) && TextCleaner.isUndo(raw)) { undo(); return }
        val text = TextCleaner.clean(
            raw, Prefs.removeFillers(this), Prefs.voiceCommands(this),
            Languages.isEnglish(Prefs.language(this)), Prefs.shortcuts(this)
        )
        if (text.isBlank()) { flash(BubbleView.Mode.ERROR); return }
        Prefs.setLastText(this, text)
        Prefs.addWords(this, text.split(Regex("\\s+")).count { it.isNotBlank() })
        val ok = target?.let { insert(it, text) } ?: false
        if (!ok) {
            Guard.safeCopy(this, text)
            toast("Copied. Long-press the text box and tap Paste")
        }
        flash(if (ok) BubbleView.Mode.DONE else BubbleView.Mode.ERROR)
    }

    private fun handleError(msg: String) {
        toast(msg)
        flash(BubbleView.Mode.ERROR)
    }

    private fun flash(m: BubbleView.Mode) {
        bubble?.mode = m
        main.postDelayed({
            busy = false
            bubble?.mode = BubbleView.Mode.IDLE
        }, 700)
    }

    private fun undo() {
        val n = undoNode; val before = undoText
        if (n == null || before == null) { toast("Nothing to undo"); flash(BubbleView.Mode.ERROR); return }
        val args = Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, before) }
        val ok = try { n.refresh(); n.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args) } catch (_: Exception) { false }
        undoNode = null; undoText = null
        flash(if (ok) BubbleView.Mode.DONE else BubbleView.Mode.ERROR)
    }

    /** Puts the words at the cursor. Does NOT press send. */
    private fun insert(node: AccessibilityNodeInfo, text: String): Boolean {
        try {
            node.refresh()
            val existing = if (node.isShowingHintText) "" else (node.text?.toString() ?: "")
            var s = node.textSelectionStart
            var e = node.textSelectionEnd
            if (s < 0 || s > existing.length) s = existing.length
            if (e < 0 || e > existing.length) e = s
            if (e < s) { val t = s; s = e; e = t }
            val before = existing.substring(0, s)
            val after = existing.substring(e)
            val sep = if (before.isNotEmpty() && !before.endsWith(" ") && !before.endsWith("\n")) " " else ""
            val piece = sep + text
            val newText = before + piece + after

            val args = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, newText)
            }
            undoNode = node; undoText = existing
            if (node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) {
                val cursor = (before + piece).length
                val sel = Bundle().apply {
                    putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, cursor)
                    putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, cursor)
                }
                node.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, sel)
                node.refresh()
                if (node.text?.toString()?.contains(text) == true) return true
            }
            // fallback: paste (clipboard is marked sensitive and wiped after 60s)
            Guard.safeCopy(this, piece)
            return node.performAction(AccessibilityNodeInfo.ACTION_PASTE)
        } catch (_: Exception) {
            return false
        }
    }

    private fun toast(msg: String) = main.post { Toast.makeText(this, msg, Toast.LENGTH_SHORT).show() }

    fun refreshBubbleSetting() { if (!Prefs.bubbleOn(this)) hideBubble() }

    companion object {
        @Volatile var instance: TypingService? = null
    }
}
