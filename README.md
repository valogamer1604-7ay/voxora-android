# Voxora

Speak anywhere. Write instantly.

Voxora is an Android app that puts a small purple voice bubble next to any normal text box
(WhatsApp, Instagram, Gmail, ChatGPT, Chrome, Notes...). Hold the bubble, talk, let go, and your
words are typed into that box. It never presses Send for you.

## Get the app

1. On your Android phone, open this repository's **Releases** page and download `Voxora.apk`.
2. Open the file. If Android asks, allow "Install unknown apps" for your browser / Files app.
3. Open Voxora and do the two steps:
   - **Microphone** → Allow
   - **Voice bubble** → Settings → Accessibility → Installed apps → Voxora → On
4. Open WhatsApp, tap a message box, hold the bubble on the right, speak, release.

Tips
- Drag the bubble up or down to move it.
- If an app blocks typing, Voxora copies the text instead: long-press the box and tap Paste.
- Speech recognition uses your phone's Google speech service, so it usually needs internet.
- Some phones (Xiaomi, Oppo, Vivo, Samsung) may turn the bubble off to save battery. Set Voxora's battery usage to "Unrestricted".

## How it's built

Every push to `main` makes GitHub Actions build a fresh APK and publish it under Releases (tag `latest`).

| File | What it does |
|---|---|
| `MainActivity.kt` / `Ui.kt` | Setup screen, test box, settings (navy + lavender design) |
| `TypingService.kt` | Finds the focused text box, shows the bubble, types the words |
| `BubbleView.kt` | The animated bubble |
| `VoiceEngine.kt` | Press-and-hold speech recognition |
| `Prefs.kt` | Settings and filler-word cleanup |

Privacy: the bubble hides on password, PIN, OTP, card, phone-number and URL fields. Nothing is sent anywhere except your voice to the phone's speech recognizer.
