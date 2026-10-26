package com.cloneLocal.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.BasicText
import kotlinx.coroutines.delay

// ---------------------------------------------------------------- icons

enum class Ic(val d: String) {
    Mic("M12 2a3 3 0 0 0-3 3v7a3 3 0 0 0 6 0V5a3 3 0 0 0-3-3z M19 10v2a7 7 0 0 1-14 0v-2 M12 19v3 M8 22h8"),
    Activity("M22 12h-4l-3 9L9 3l-3 9H2"),
    Download("M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4 M7 10l5 5 5-5 M12 15V3"),
    Home("M3 9l9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z M9 22V12h6v10"),
    Sliders("M4 21v-7 M4 10V3 M12 21v-9 M12 8V3 M20 21v-5 M20 12V3 M1 14h6 M9 8h6 M17 16h6"),
    Globe("M12 2a10 10 0 1 0 0 20 10 10 0 0 0 0-20z M2 12h20 M12 2a15.3 15.3 0 0 1 4 10 15.3 15.3 0 0 1-4 10 15.3 15.3 0 0 1-4-10 15.3 15.3 0 0 1 4-10z"),
    Bolt("M13 2L3 14h9l-1 8 10-12h-9l1-8z"),
    Layers("M12 2L2 7l10 5 10-5-10-5z M2 17l10 5 10-5 M2 12l10 5 10-5"),
    Check("M20 6L9 17l-5-5"),
    Close("M18 6L6 18 M6 6l12 12"),
    Back("M15 18l-6-6 6-6"),
    Archive("M21 8v13H3V8 M1 3h22v5H1z M10 12h4"),
    Alert("M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z M12 9v4 M12 17h.01"),
    Refresh("M23 4v6h-6 M1 20v-6h6 M3.51 9a9 9 0 0 1 14.85-3.36L23 10 M1 14l4.64 4.36A9 9 0 0 0 20.49 15"),
    Trash("M3 6h18 M19 6l-1 14a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2L5 6 M10 11v6 M14 11v6 M9 6V4a1 1 0 0 1 1-1h4a1 1 0 0 1 1 1v2"),
    Folder("M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2z"),
    Play("M6 4l14 8-14 8z"),
    Pause("M8 5v14 M16 5v14"),
    Shield("M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"),
    Clock("M12 2a10 10 0 1 0 0 20 10 10 0 0 0 0-20z M12 6v6l4 2"),
}

private val pathCache = HashMap<Ic, androidx.compose.ui.graphics.Path>()
private fun basePath(ic: Ic) = pathCache.getOrPut(ic) { PathParser().parsePathString(ic.d).toPath() }

/** Line icon on a 24px grid, 2px stroke, round caps — one weight everywhere. */
@Composable
fun Icon(ic: Ic, tint: Color = C.Text, size: Dp = 20.dp, mirrorInRtl: Boolean = false) {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Canvas(Modifier.size(size).graphicsLayer { if (mirrorInRtl && rtl) scaleX = -1f }) {
        val s = this.size.width / 24f
        val p = androidx.compose.ui.graphics.Path()
        p.addPath(basePath(ic))
        p.transform(Matrix().apply { scale(s, s) })
        drawPath(p, tint, style = Stroke(2f * s, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

// ---------------------------------------------------------------- atmosphere

@Composable
fun Atmosphere(modifier: Modifier = Modifier) {
    val inf = rememberInfiniteTransition(label = "atm")
    val pulse by inf.animateFloat(
        .75f, 1f,
        infiniteRepeatable(tween(5200, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "pulse",
    )
    val scan by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(9000, easing = LinearEasing)), label = "scan")
    Canvas(modifier.fillMaxSize()) {
        drawRect(C.Bg)
        drawRect(
            Brush.radialGradient(
                listOf(C.Cyan.copy(alpha = .30f * pulse * .55f), Color.Transparent),
                center = Offset(size.width * .15f, size.height * .06f), radius = size.width * .95f,
            ),
        )
        drawRect(
            Brush.radialGradient(
                listOf(C.Violet.copy(alpha = .16f), Color.Transparent),
                center = Offset(size.width * .95f, size.height * .95f), radius = size.width * .9f,
            ),
        )
        val step = 36.dp.toPx()
        val gc = Color.White.copy(alpha = .028f)
        var x = 0f
        while (x < size.width) { drawLine(gc, Offset(x, 0f), Offset(x, size.height), 1f); x += step }
        var y = 0f
        while (y < size.height) { drawLine(gc, Offset(0f, y), Offset(size.width, y), 1f); y += step }
        val sy = size.height * scan
        drawRect(
            Brush.verticalGradient(
                listOf(Color.Transparent, C.Cyan.copy(alpha = .05f), Color.Transparent),
                startY = sy - 60.dp.toPx(), endY = sy + 60.dp.toPx(),
            ),
            topLeft = Offset(0f, sy - 60.dp.toPx()), size = Size(size.width, 120.dp.toPx()),
        )
    }
}

// ---------------------------------------------------------------- panel

@Composable
fun Panel(
    modifier: Modifier = Modifier,
    strong: Boolean = false,
    accent: Color = C.Cyan,
    pad: Dp = 18.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(6.dp)
    Column(
        modifier
            .clip(shape)
            .background(Brush.verticalGradient(listOf(C.SurfaceHi.copy(alpha = .78f), C.Surface.copy(alpha = .86f))))
            .border(
                1.dp,
                Brush.verticalGradient(listOf(accent.copy(alpha = if (strong) .6f else .32f), C.Line.copy(alpha = .7f))),
                shape,
            )
            .drawBehind {
                val len = 12.dp.toPx()
                val sw = 2.dp.toPx()
                val c = accent.copy(alpha = if (strong) 1f else .85f)
                val w = size.width
                val h = size.height
                // four L-shaped corner brackets
                drawLine(c, Offset(0f, 0f), Offset(len, 0f), sw); drawLine(c, Offset(0f, 0f), Offset(0f, len), sw)
                drawLine(c, Offset(w, 0f), Offset(w - len, 0f), sw); drawLine(c, Offset(w, 0f), Offset(w, len), sw)
                drawLine(c, Offset(0f, h), Offset(len, h), sw); drawLine(c, Offset(0f, h), Offset(0f, h - len), sw)
                drawLine(c, Offset(w, h), Offset(w - len, h), sw); drawLine(c, Offset(w, h), Offset(w, h - len), sw)
            }
            .padding(pad),
        content = content,
    )
}

// ---------------------------------------------------------------- buttons

@Composable
fun PrimaryButton(
    text: String,
    modifier: Modifier = Modifier,
    icon: Ic? = null,
    loading: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(6.dp)
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) .965f else 1f, spring(dampingRatio = .5f, stiffness = 700f), label = "press",
    )
    val inf = rememberInfiniteTransition(label = "btn")
    val sweep by inf.animateFloat(-0.4f, 1.4f, infiniteRepeatable(tween(1300, easing = LinearEasing)), label = "sweep")
    val type = LocalType.current
    Box(
        modifier
            .fillMaxWidth()
            .height(58.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale; alpha = if (enabled) 1f else .4f }
            .shadow(if (enabled) 18.dp else 0.dp, shape, ambientColor = C.Cyan, spotColor = C.Cyan)
            .clip(shape)
            .background(Glow)
            .clickable(src, null, enabled = enabled && !loading, onClick = onClick)
            .drawBehind {
                drawLine(Color.White.copy(alpha = .35f), Offset(0f, 0f), Offset(size.width, 0f), 1.5.dp.toPx())
                if (loading) {
                    val cx = size.width * sweep
                    drawRect(
                        Brush.horizontalGradient(
                            listOf(Color.Transparent, Color.White.copy(alpha = .45f), Color.Transparent),
                            startX = cx - 70.dp.toPx(), endX = cx + 70.dp.toPx(),
                        ),
                    )
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (icon != null) Icon(icon, Color.White, 20.dp)
            BasicText(text, style = type.button(Color.White))
        }
    }
}

@Composable
fun GhostButton(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = C.Cyan,
    icon: Ic? = null,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(6.dp)
    val type = LocalType.current
    Box(
        modifier
            .fillMaxWidth()
            .height(50.dp)
            .clip(shape)
            .background(color.copy(alpha = .06f))
            .border(1.dp, color.copy(alpha = .5f), shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (icon != null) Icon(icon, color, 20.dp)
            BasicText(text, style = type.button(color).copy(fontSize = 15.sp))
        }
    }
}

@Composable
fun IconBtn(ic: Ic, tint: Color = C.Cyan, mirrorInRtl: Boolean = false, onClick: () -> Unit) {
    val shape = RoundedCornerShape(6.dp)
    Box(
        Modifier
            .size(44.dp)
            .clip(shape)
            .background(C.SurfaceHi.copy(alpha = .8f))
            .border(1.dp, C.Line, shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(ic, tint, 20.dp, mirrorInRtl) }
}

// ---------------------------------------------------------------- small pieces

@Composable
fun SystemTag(text: String, color: Color = C.Cyan, blink: Boolean = true) {
    val inf = rememberInfiniteTransition(label = "tag")
    val a by inf.animateFloat(1f, .25f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "blink")
    val type = LocalType.current
    Row(
        Modifier
            .clip(RoundedCornerShape(3.dp))
            .border(1.dp, color.copy(alpha = .5f), RoundedCornerShape(3.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(6.dp).alpha(if (blink) a else 1f).background(color))
        BasicText(text.upperIfEn(), style = type.label(color))
    }
}

@Composable
fun String.upperIfEn(): String = if (LocalArabic.current) this else uppercase()

@Composable
fun Label(text: String, modifier: Modifier = Modifier, color: Color = C.Muted) {
    BasicText(text.upperIfEn(), modifier, style = LocalType.current.label(color))
}

@Composable
fun Skeleton(modifier: Modifier = Modifier) {
    val inf = rememberInfiniteTransition(label = "sk")
    val t by inf.animateFloat(-0.3f, 1.3f, infiniteRepeatable(tween(1400, easing = LinearEasing)), label = "sk")
    Box(
        modifier
            .clip(RoundedCornerShape(4.dp))
            .background(C.SurfaceHi.copy(alpha = .7f))
            .drawBehind {
                val cx = size.width * t
                drawRect(
                    Brush.horizontalGradient(
                        listOf(Color.Transparent, C.Cyan.copy(alpha = .10f), Color.Transparent),
                        startX = cx - 80.dp.toPx(), endX = cx + 80.dp.toPx(),
                    ),
                )
            },
    )
}

@Composable
fun GlowBar(progress: Float, color: Brush = Glow, modifier: Modifier = Modifier) {
    val p by animateFloatAsState(progress.coerceIn(0f, 1f), tween(450), label = "bar")
    Box(modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(C.Line.copy(alpha = .6f))) {
        Box(Modifier.fillMaxWidth(p).fillMaxHeight().clip(RoundedCornerShape(3.dp)).background(color))
    }
}

@Composable
fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    val shape = RoundedCornerShape(6.dp)
    val type = LocalType.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    BoxWithConstraints(Modifier.fillMaxWidth().height(46.dp).clip(shape).border(1.dp, C.Line, shape)) {
        val w = maxWidth / options.size
        val x by animateDpAsState(w * selected, spring(dampingRatio = .75f), label = "thumb")
        Box(
            Modifier
                .offset(x = if (rtl) -x else x)
                .width(w).fillMaxHeight()
                .padding(3.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(C.Cyan.copy(alpha = .14f))
                .border(1.dp, C.Cyan.copy(alpha = .8f), RoundedCornerShape(4.dp)),
        )
        Row(Modifier.fillMaxSize()) {
            options.forEachIndexed { i, o ->
                val col by animateColorAsState(if (i == selected) C.Cyan else C.Muted, tween(200), label = "seg")
                Box(
                    Modifier.weight(1f).fillMaxHeight().clickable { onSelect(i) },
                    contentAlignment = Alignment.Center,
                ) { BasicText(o.upperIfEn(), style = type.label(col).copy(fontSize = 13.sp)) }
            }
        }
    }
}

@Composable
fun Field(
    value: String,
    onChange: (String) -> Unit,
    hint: String,
    modifier: Modifier = Modifier,
    minLines: Int = 1,
    ltr: Boolean = false,
    small: Boolean = false,
) {
    val type = LocalType.current
    val shape = RoundedCornerShape(6.dp)
    var focused by remember { mutableStateOf(false) }
    val border by animateColorAsState(if (focused) C.Cyan.copy(alpha = .8f) else C.Line, tween(200), label = "fb")
    val style = type.bodyRegular(C.Text, if (small) 12 else 15).copy(
        textDirection = if (ltr) TextDirection.Ltr else TextDirection.Content,
    )
    BasicTextField(
        value = value,
        onValueChange = onChange,
        textStyle = style,
        cursorBrush = SolidColor(C.Cyan),
        minLines = minLines,
        modifier = modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused },
        decorationBox = { inner ->
            Box(
                Modifier.fillMaxWidth().clip(shape).background(C.SurfaceHi).border(1.dp, border, shape)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                if (value.isEmpty()) BasicText(hint, style = type.body(C.Faint, if (small) 12 else 15))
                inner()
            }
        },
    )
}

/** Fade + 18dp rise, staggered 60ms per index. */
@Composable
fun Reveal(index: Int, content: @Composable () -> Unit) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(60L * index); shown = true }
    val p by animateFloatAsState(if (shown) 1f else 0f, tween(450, easing = FastOutSlowInEasing), label = "reveal")
    val rise = with(LocalDensity.current) { 18.dp.toPx() }
    Box(Modifier.graphicsLayer { alpha = p; translationY = (1f - p) * rise }) { content() }
}

// ---------------------------------------------------------------- logo

@Composable
fun LogoMark(size: Dp = 34.dp) {
    Canvas(Modifier.size(size)) {
        val c = Offset(this.size.width / 2, this.size.height / 2)
        val r = this.size.width / 2
        drawCircle(Brush.radialGradient(listOf(C.Cyan.copy(alpha = .45f), Color.Transparent), c, r), r, c)
        fun diamond(k: Float) = androidx.compose.ui.graphics.Path().apply {
            moveTo(c.x, c.y - r * k); lineTo(c.x + r * k, c.y); lineTo(c.x, c.y + r * k); lineTo(c.x - r * k, c.y); close()
        }
        drawPath(diamond(.62f), C.Cyan, style = Stroke(2.dp.toPx(), join = StrokeJoin.Round))
        drawPath(
            diamond(.34f),
            Brush.linearGradient(listOf(Color(0xFFE9F8FF), C.Cyan, C.Blue), Offset(c.x - r * .3f, c.y - r * .3f), Offset(c.x + r * .3f, c.y + r * .3f)),
        )
        val sw = 2.dp.toPx()
        drawLine(C.Cyan, Offset(c.x, 0f), Offset(c.x, r * .26f), sw, StrokeCap.Round)
        drawLine(C.Cyan, Offset(c.x, this.size.height), Offset(c.x, this.size.height - r * .26f), sw, StrokeCap.Round)
    }
}

@Composable
fun Logo() {
    val type = LocalType.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        LogoMark()
        Row {
            BasicText("VOX", style = TextStyle(fontFamily = EnFonts.display, fontWeight = FontWeight.Bold, fontSize = 21.sp, letterSpacing = 2.5.sp, color = C.Text))
            BasicText(
                "GATE",
                style = TextStyle(
                    fontFamily = EnFonts.display, fontWeight = FontWeight.Bold, fontSize = 21.sp, letterSpacing = 2.5.sp, color = C.Cyan,
                    shadow = androidx.compose.ui.graphics.Shadow(C.Cyan.copy(alpha = .7f), Offset.Zero, 18f),
                ),
            )
        }
    }
}

// ---------------------------------------------------------------- dock

@Composable
fun Dock(selected: Int, labels: List<String>, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val icons = listOf(Ic.Home, Ic.Archive, Ic.Sliders)
    val type = LocalType.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Panel(modifier.width(280.dp).height(64.dp), strong = true, pad = 0.dp) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val w = maxWidth / 3
            val x by animateDpAsState(w * selected, spring(dampingRatio = .75f), label = "dock")
            Row(Modifier.fillMaxSize()) {
                for (i in 0 until 3) {
                    val col by animateColorAsState(if (i == selected) C.Cyan else C.Muted, tween(200), label = "dc")
                    Column(
                        Modifier.weight(1f).fillMaxHeight().clickable { onSelect(i) },
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(icons[i], col, 22.dp)
                        BasicText(labels[i], style = type.label(col).copy(fontSize = 10.sp, letterSpacing = 0.sp))
                    }
                }
            }
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .offset(x = (if (rtl) -x else x) + w / 2 - 14.dp)
                    .width(28.dp).height(2.dp)
                    .shadow(6.dp, spotColor = C.Cyan, ambientColor = C.Cyan)
                    .background(C.Cyan),
            )
        }
    }
}
