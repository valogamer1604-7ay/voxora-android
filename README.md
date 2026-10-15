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

## Why use Voxora instead of keyboard voice typing?

- Works with **any keyboard** (Samsung, SwiftKey, Gboard...), you don't have to switch keyboards.
- **Hold as long as you want**: it keeps listening through pauses.
- **Cleans your speech**: removes um/uh, capitalises, fixes "i" to "I".
- **Voice commands**: "comma", "new line", "question mark", "heart emoji", "scratch that" (undo).
- **Shortcuts**: say "my email" and it types your full email address.
- **174 languages and accents (115 languages)**, Indian languages first.
- **Private**: no internet permission, no account, no ads.

## Security

| Protection | What it stops |
|---|---|
| No INTERNET permission (forcibly removed) | The app itself can't send anything anywhere |
| Signed release build, signing key kept in GitHub Secrets | Fake updates |
| Signature self-check | Modified/re-signed copies lock themselves |
| R8 shrinking + obfuscation, logs stripped | Reverse engineering, leaks in logs |
| Android Keystore AES-256-GCM | Reading your shortcuts/last dictation from storage |
| Backups disabled | Data leaking through cloud backup or device transfer |
| FLAG_SECURE | Screenshots, screen recording, recent-apps previews |
| Tapjacking filter | Fake screens drawn over the app |
| Bubble hidden on lock screen, passwords, PIN/OTP/card fields, banking/UPI/password apps | Accidental dictation into sensitive places |
| Clipboard marked sensitive and wiped after 60s | Clipboard snooping |
| Prominent consent screen | Required by Google Play for Accessibility apps |

No app is impossible to hack; these make Voxora a very hard and unrewarding target.

## How it's built

Every push to `main` makes GitHub Actions build a fresh signed APK and publish it under Releases (tag `latest`).

| File | What it does |
|---|---|
| `MainActivity.kt` / `Ui.kt` | Setup screen, test box, settings (navy + lavender design) |
| `TypingService.kt` | Finds the focused text box, shows the bubble, types the words |
| `BubbleView.kt` | The animated bubble |
| `VoiceEngine.kt` | Press-and-hold speech recognition |
| `Prefs.kt` | Settings (personal data encrypted) |
| `Security.kt` | Encryption, signature check, blocked apps, safe clipboard |
| `Languages.kt` | 174 speech languages/accents |
| `TextCleaner.kt` | Filler removal, voice commands, shortcuts |

Privacy: the bubble hides on password, PIN, OTP, card, phone-number and URL fields. Nothing is sent anywhere except your voice to the phone's speech recognizer.
