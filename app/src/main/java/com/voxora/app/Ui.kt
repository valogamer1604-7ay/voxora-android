package com.voxora.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.sin
import kotlin.random.Random

// ---- palette taken from your purple space image ----
val Night = Color(0xFF0A0620)
val Deep = Color(0xFF140C3A)
val Indigo = Color(0xFF2A1C6E)
val Violet = Color(0xFF7C5CFF)
val Lavender = Color(0xFFB9A7FF)
val Mist = Color(0xFFE6DEFF)
val Pink = Color(0xFFFF6FB5)
val Mint = Color(0xFF7BE0B5)
val Dim = Color(0xFF9A90C4)

@Composable
fun VoxoraApp(
    tick: Int,
    hasMic: () -> Boolean,
    hasAccess: () -> Boolean,
    askMic: () -> Unit,
    openAccess: () -> Unit,
    onChanged: () -> Unit,
) {
    val ctx = LocalContext.current
    val mic = remember(tick) { hasMic() }
    val access = remember(tick) { hasAccess() }
    val last = remember(tick) { Prefs.lastText(ctx) }
    val words = remember(tick) { Prefs.wordsTotal(ctx) }
    var lang by remember { mutableStateOf(Prefs.language(ctx)) }
    var fillers by remember { mutableStateOf(Prefs.removeFillers(ctx)) }
    var bubbleOn by remember { mutableStateOf(Prefs.bubbleOn(ctx)) }
    val ready = mic && access

    MaterialTheme(colorScheme = darkColorScheme(primary = Violet, background = Night, surface = Deep)) {
        Box(Modifier.fillMaxSize().background(Night)) {
            Cosmos()
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                    .statusBarsPadding().navigationBarsPadding().imePadding()
                    .padding(horizontal = 22.dp, vertical = 18.dp)
            ) {
                // header
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("voxora", color = Mist, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)
                    Spacer(Modifier.weight(1f))
                    StatusPill(ready, (if (mic) 0 else 1) + (if (access) 0 else 1))
                }

                Spacer(Modifier.height(26.dp))
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { Orb(active = ready) }
                Spacer(Modifier.height(22.dp))

                Text("Speak anywhere.", color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold, lineHeight = 38.sp)
                Text("Write instantly.", color = Lavender, fontSize = 34.sp, fontWeight = FontWeight.Bold, lineHeight = 38.sp)
                Spacer(Modifier.height(10.dp))
                Text(
                    "Tap any text box — WhatsApp, Instagram, Gmail, ChatGPT — then hold the bubble, talk, and let go. Your words appear right there.",
                    color = Dim, fontSize = 15.sp, lineHeight = 22.sp
                )

                Spacer(Modifier.height(28.dp))
                SectionLabel(if (ready) "ALL SET" else "TWO QUICK STEPS")
                Spacer(Modifier.height(10.dp))
                StepCard(1, "Microphone", "So Voxora can hear you while you hold the bubble.", mic, "Allow", askMic)
                Spacer(Modifier.height(10.dp))
                StepCard(
                    2, "Voice bubble",
                    "Settings → Accessibility → Installed apps → Voxora → turn it on. This lets the bubble type into the box you tapped.",
                    access, "Open settings", openAccess
                )

                Spacer(Modifier.height(28.dp))
                SectionLabel("TRY IT HERE")
                Spacer(Modifier.height(10.dp))
                TryField(ready)

                if (last.isNotBlank()) {
                    Spacer(Modifier.height(28.dp))
                    SectionLabel("LAST DICTATION")
                    Spacer(Modifier.height(10.dp))
                    Glass {
                        Text(last, color = Mist, fontSize = 15.sp, lineHeight = 22.sp)
                        Spacer(Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("$words words dictated so far", color = Dim, fontSize = 12.sp)
                            Spacer(Modifier.weight(1f))
                            SmallButton("Copy") {
                                val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cm.setPrimaryClip(ClipData.newPlainText("Voxora", last))
                                Toast.makeText(ctx, "Copied", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }

                Spacer(Modifier.height(28.dp))
                SectionLabel("SETTINGS")
                Spacer(Modifier.height(10.dp))
                Glass {
                    Text("Language", color = Mist, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(10.dp))
                    val langs = listOf("en-IN" to "English (India)", "en-US" to "English (US)", "en-GB" to "English (UK)", "hi-IN" to "Hindi")
                    Row(Modifier.horizontalScroll(rememberScrollState())) {
                        langs.forEach { (code, name) ->
                            VChip(name, lang == code) { lang = code; Prefs.setLanguage(ctx, code) }
                            Spacer(Modifier.width(8.dp))
                        }
                    }
                    Separator()
                    ToggleRow("Remove “um”, “uh”, “erm”", "Cleaner text automatically", fillers) {
                        fillers = it; Prefs.setRemoveFillers(ctx, it)
                    }
                    Separator()
                    ToggleRow("Show bubble", "Turn off to hide it everywhere", bubbleOn) {
                        bubbleOn = it; Prefs.setBubbleOn(ctx, it); onChanged()
                    }
                }

                Spacer(Modifier.height(20.dp))
                Text(
                    "Voxora never types into passwords, PINs, OTPs or card numbers, and never presses Send for you.",
                    color = Dim.copy(alpha = 0.8f), fontSize = 12.sp, lineHeight = 17.sp
                )
                Spacer(Modifier.height(30.dp))
            }
        }
    }
}

// ---------- background: stars + violet nebula ----------
@Composable
fun Cosmos() {
    val stars = remember {
        val r = Random(7)
        List(90) { Triple(r.nextFloat(), r.nextFloat(), r.nextFloat()) }
    }
    val t = rememberInfiniteTransition(label = "cosmos")
    val drift by t.animateFloat(0f, 1f, infiniteRepeatable(tween(14000, easing = LinearEasing), RepeatMode.Reverse), label = "d")
    Canvas(Modifier.fillMaxSize()) {
        drawRect(Brush.verticalGradient(listOf(Night, Deep, Night)))
        drawCircle(
            Brush.radialGradient(listOf(Indigo.copy(alpha = 0.75f), Color.Transparent),
                center = Offset(size.width * (0.85f - drift * 0.1f), size.height * 0.18f), radius = size.width * 0.9f),
            radius = size.width * 0.9f, center = Offset(size.width * (0.85f - drift * 0.1f), size.height * 0.18f)
        )
        drawCircle(
            Brush.radialGradient(listOf(Violet.copy(alpha = 0.28f), Color.Transparent),
                center = Offset(size.width * (0.1f + drift * 0.15f), size.height * 0.62f), radius = size.width * 0.8f),
            radius = size.width * 0.8f, center = Offset(size.width * (0.1f + drift * 0.15f), size.height * 0.62f)
        )
        stars.forEach { (x, y, s) ->
            val a = 0.25f + 0.6f * ((sin((drift * 6f + s * 20f)) + 1f) / 2f)
            drawCircle(Color.White.copy(alpha = a * s), radius = 0.6f + s * 1.6f, center = Offset(x * size.width, y * size.height))
        }
    }
}

// ---------- animated hero orb ----------
@Composable
fun Orb(active: Boolean) {
    val t = rememberInfiniteTransition(label = "orb")
    val p by t.animateFloat(0f, (Math.PI * 2).toFloat(), infiniteRepeatable(tween(2600, easing = LinearEasing)), label = "p")
    val breathe by t.animateFloat(0.92f, 1.08f, infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "b")
    Canvas(Modifier.size(170.dp)) {
        val c = center
        val r = size.minDimension / 2f
        drawCircle(Brush.radialGradient(listOf(Violet.copy(alpha = 0.55f), Color.Transparent), c, r * breathe), r * breathe, c)
        drawCircle(Lavender.copy(alpha = 0.25f), r * 0.68f, c, style = Stroke(1.5f))
        drawCircle(Lavender.copy(alpha = 0.12f), r * 0.86f * breathe, c, style = Stroke(1.2f))
        drawCircle(Brush.linearGradient(listOf(Color(0xFFC9B8FF), Violet, Color(0xFF4B2FD6)),
            Offset(c.x - r, c.y - r), Offset(c.x + r, c.y + r)), r * 0.52f, c)
        val hs = floatArrayOf(0.35f, 0.62f, 1f, 0.62f, 0.35f)
        val gap = r * 0.15f
        for (i in 0 until 5) {
            val wave = if (active) 0.7f + 0.3f * sin(p * 2 + i) else 0.75f
            val h = r * 0.48f * hs[i] * wave
            val x = c.x + (i - 2) * gap
            drawLine(Color.White, Offset(x, c.y - h / 2), Offset(x, c.y + h / 2), strokeWidth = r * 0.055f, cap = StrokeCap.Round)
        }
    }
}

@Composable
fun StatusPill(ready: Boolean, left: Int) {
    val col = if (ready) Mint else Pink
    Row(
        Modifier.clip(CircleShape).background(col.copy(alpha = 0.12f)).border(1.dp, col.copy(alpha = 0.35f), CircleShape)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(col))
        Spacer(Modifier.width(7.dp))
        Text(if (ready) "Ready everywhere" else "$left step${if (left == 1) "" else "s"} left", color = col, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun SectionLabel(s: String) = Text(s, color = Lavender.copy(alpha = 0.7f), fontSize = 11.sp, letterSpacing = 2.sp, fontWeight = FontWeight.SemiBold)

@Composable
fun Glass(content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(22.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape).background(Color.White.copy(alpha = 0.05f))
            .border(1.dp, Lavender.copy(alpha = 0.14f), shape).padding(18.dp),
        content = content
    )
}

@Composable
fun StepCard(n: Int, title: String, body: String, done: Boolean, action: String, onClick: () -> Unit) {
    Glass {
        Row(verticalAlignment = Alignment.Top) {
            Box(
                Modifier.size(34.dp).clip(CircleShape).background(if (done) Mint.copy(alpha = 0.18f) else Violet.copy(alpha = 0.22f)),
                contentAlignment = Alignment.Center
            ) { Text(if (done) "✓" else "$n", color = if (done) Mint else Lavender, fontWeight = FontWeight.Bold) }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Text(body, color = Dim, fontSize = 13.sp, lineHeight = 19.sp)
                if (!done) {
                    Spacer(Modifier.height(12.dp))
                    PrimaryButton(action, onClick)
                }
            }
        }
    }
}

@Composable
fun PrimaryButton(label: String, onClick: () -> Unit) {
    Box(
        Modifier.clip(CircleShape)
            .background(Brush.horizontalGradient(listOf(Violet, Color(0xFF9B7BFF))))
            .clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 10.dp)
    ) { Text(label, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
}

@Composable
fun SmallButton(label: String, onClick: () -> Unit) {
    Box(
        Modifier.clip(CircleShape).border(1.dp, Lavender.copy(alpha = 0.4f), CircleShape)
            .clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 7.dp)
    ) { Text(label, color = Lavender, fontSize = 13.sp, fontWeight = FontWeight.Medium) }
}

@Composable
fun VChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.clip(CircleShape)
            .background(if (selected) Violet.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.04f))
            .border(1.dp, if (selected) Lavender else Lavender.copy(alpha = 0.2f), CircleShape)
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp)
    ) { Text(label, color = if (selected) Color.White else Dim, fontSize = 13.sp) }
}

@Composable
fun Separator() {
    Spacer(Modifier.height(14.dp))
    Box(Modifier.fillMaxWidth().height(1.dp).background(Lavender.copy(alpha = 0.1f)))
    Spacer(Modifier.height(14.dp))
}

@Composable
fun ToggleRow(title: String, sub: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Mist, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(sub, color = Dim, fontSize = 12.sp)
        }
        Switch(
            checked = value, onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White, checkedTrackColor = Violet,
                uncheckedThumbColor = Dim, uncheckedTrackColor = Deep, uncheckedBorderColor = Lavender.copy(alpha = 0.3f)
            )
        )
    }
}

@Composable
fun TryField(ready: Boolean) {
    var text by remember { mutableStateOf("") }
    val shape = RoundedCornerShape(22.dp)
    Box(
        Modifier.fillMaxWidth().heightIn(min = 120.dp).clip(shape)
            .background(Color.White.copy(alpha = 0.05f)).border(1.dp, Violet.copy(alpha = 0.45f), shape).padding(18.dp)
    ) {
        BasicTextField(
            value = text, onValueChange = { text = it },
            textStyle = TextStyle(color = Color.White, fontSize = 16.sp, lineHeight = 23.sp),
            cursorBrush = SolidColor(Lavender),
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { inner ->
                if (text.isEmpty()) Text(
                    if (ready) "Tap here, then hold the bubble on the right and speak…" else "Finish the two steps above, then test here.",
                    color = Dim, fontSize = 16.sp
                )
                inner()
            }
        )
    }
}
