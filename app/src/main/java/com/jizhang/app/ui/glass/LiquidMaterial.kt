/*
 * Liquid material recipe adapted from Kyant0/AndroidLiquidGlass (Apache-2.0).
 * https://github.com/Kyant0/AndroidLiquidGlass
 */
package com.jizhang.app.ui.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.jizhang.app.data.prefs.GlassStyle
import com.jizhang.app.data.prefs.SettingsStore
import com.jizhang.app.util.glassEffectsEnabled
import androidx.compose.ui.platform.LocalContext
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.RoundedRectangle
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Stable
internal class LiquidScene(
    val backdrop: LayerBackdrop,
    val contentBackdrop: LayerBackdrop,
    val paint: DrawScope.() -> Unit,
    val dark: Boolean,
    val enabled: Boolean,
    val style: GlassStyle,
) {
    var contentCoordinates: LayoutCoordinates? by mutableStateOf(null)
    var windowConsumers by mutableStateOf(0)
}

internal val LocalLiquidScene = staticCompositionLocalOf<LiquidScene?> { null }
internal val LocalLiquidBackdrop = staticCompositionLocalOf<Backdrop?> { null }

/** Background and complete page are independent sources; cards only receive the former. */
@Composable
internal fun ProvideLiquidMaterial(content: @Composable () -> Unit) {
    if (LocalLiquidScene.current != null) {
        content()
        return
    }
    val background = MiuixTheme.colorScheme.background
    val accent = MiuixTheme.colorScheme.primary
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val settings = remember(context) { SettingsStore(context) }
    val style = settings.glassStyle
    val enabled = context.glassEffectsEnabled()
    val image = rememberCustomBackground(context,
        settings.backgroundImageName.takeIf { style == GlassStyle.CLEAR })
    val paint = remember(background, accent, dark, image) { liquidScenePaint(background, accent, dark, image) }
    val recordPaint: androidx.compose.ui.graphics.drawscope.ContentDrawScope.() -> Unit =
        remember(paint) { { paint() } }
    val backdrop = rememberLayerBackdrop(onDraw = recordPaint)
    val contentBackdrop = rememberLayerBackdrop()
    val scene = remember(backdrop, contentBackdrop, paint, dark, enabled, style) {
        LiquidScene(backdrop, contentBackdrop, paint, dark, enabled, style)
    }
    CompositionLocalProvider(LocalLiquidScene provides scene, LocalLiquidBackdrop provides backdrop, content = content)
}

/** Shared by the visible background and its recording; rebuild only after a resize. */
private fun liquidScenePaint(background: Color, accent: Color, dark: Boolean, image: ImageBitmap?): DrawScope.() -> Unit {
    var cachedSize = Size.Unspecified
    var brushes = emptyList<Brush>()
    var imageOrigin = IntOffset.Zero
    var imageSize = IntSize.Zero
    return {
        if (size != cachedSize) {
            cachedSize = size
            if (image != null) {
                val scale = maxOf(size.width / image.width, size.height / image.height)
                imageSize = IntSize(kotlin.math.ceil(image.width * scale).toInt(), kotlin.math.ceil(image.height * scale).toInt())
                imageOrigin = IntOffset((size.width.toInt() - imageSize.width) / 2, (size.height.toInt() - imageSize.height) / 2)
            }
            val radius = size.maxDimension * .65f
            brushes = listOf(
                Brush.linearGradient(
                    colors = listOf(
                        lerp(background, accent, if (dark) .08f else .025f),
                        background,
                        lerp(background, Color(0xFF62CABC), if (dark) .05f else .02f),
                    ),
                    start = Offset.Zero,
                    end = Offset(size.width, size.height),
                ),
                Brush.radialGradient(
                    listOf(accent.copy(alpha = if (dark) .14f else .07f), Color.Transparent),
                    center = Offset(size.width * .95f, size.height * .12f), radius = radius,
                ),
                Brush.radialGradient(
                    listOf(lerp(accent, Color(0xFF62CABC), .55f).copy(alpha = if (dark) .08f else .045f), Color.Transparent),
                    center = Offset(0f, size.height * .78f), radius = radius * .8f,
                ),
            )
        }
        if (image != null) {
            drawImage(image, dstOffset = imageOrigin, dstSize = imageSize)
            // Keep foreground text readable even over very bright or very dark photos.
            drawRect(if (dark) Color.Black.copy(alpha = .54f) else Color.White.copy(alpha = .48f))
        } else brushes.forEach { drawRect(it) }
    }
}

/** Producer is drawn before its sibling consumers, including on the first frame. */
@Composable
internal fun LiquidPage(modifier: Modifier = Modifier, recordContent: Boolean = false, content: @Composable BoxScope.() -> Unit) {
    val scene = LocalLiquidScene.current
    if (scene?.enabled != true) {
        val page = if (scene?.style == GlassStyle.CLEAR) Modifier.drawBehind(scene.paint)
            else Modifier.background(MiuixTheme.colorScheme.background)
        Box(modifier.fillMaxSize().then(page), content = content)
        return
    }
    // Capture both siblings: transparent gaps in the content must include the page artwork.
    Box(modifier.fillMaxSize()
        .onGloballyPositioned { scene.contentCoordinates = it }
        .then(if (recordContent || scene.windowConsumers > 0) Modifier.layerBackdrop(scene.contentBackdrop) else Modifier)) {
        Box(Modifier.matchParentSize()
            .layerBackdrop(scene.backdrop)
            .drawBehind(scene.paint))
        // Record the complete underlay for separate Window consumers, never for child cards.
        Box(Modifier.fillMaxSize(), content = content)
    }
    DisposableEffect(scene) { onDispose { scene.contentCoordinates = null } }
}

internal enum class LiquidSurfaceRole { Panel, Control, FloatingControl, Field, Dialog, Sheet, Message, Navigation }

private data class MaterialRecipe(
    val blur: Float, val height: Float, val refraction: Float,
    val opacity: Float, val highlight: Float, val shadow: Float,
)

private fun recipe(role: LiquidSurfaceRole, dark: Boolean) = when (role) {
    LiquidSurfaceRole.Panel -> MaterialRecipe(9f, 8f, 16f, if (dark) .76f else .82f, .10f, if (dark) .12f else .025f)
    LiquidSurfaceRole.Control -> MaterialRecipe(4f, 9f, 18f, if (dark) .58f else .52f, .20f, 0f)
    LiquidSurfaceRole.FloatingControl -> MaterialRecipe(7f, 12f, 24f, if (dark) .46f else .40f, .30f, if (dark) .12f else .06f)
    LiquidSurfaceRole.Field -> MaterialRecipe(6f, 5f, 10f, .72f, .12f, 0f)
    LiquidSurfaceRole.Dialog -> MaterialRecipe(18f, 12f, 24f, if (dark) .72f else .62f, .24f, .14f)
    LiquidSurfaceRole.Sheet -> MaterialRecipe(12f, 12f, 24f, if (dark) .82f else .78f, .16f, .10f)
    LiquidSurfaceRole.Message -> MaterialRecipe(10f, 10f, 20f, .90f, .14f, .08f)
    LiquidSurfaceRole.Navigation -> MaterialRecipe(7f, 13f, 26f, if (dark) .32f else .28f, .48f, .12f)
}

private fun clearRecipe(role: LiquidSurfaceRole, dark: Boolean) = when (role) {
    LiquidSurfaceRole.Panel -> MaterialRecipe(3f, 10f, 20f, if (dark) .12f else .07f, .32f, .04f)
    LiquidSurfaceRole.Control -> MaterialRecipe(2f, 8f, 16f, if (dark) .15f else .09f, .28f, 0f)
    LiquidSurfaceRole.FloatingControl -> MaterialRecipe(3f, 12f, 24f, if (dark) .14f else .08f, .40f, .05f)
    LiquidSurfaceRole.Field -> MaterialRecipe(3f, 6f, 12f, if (dark) .16f else .10f, .24f, 0f)
    LiquidSurfaceRole.Dialog -> MaterialRecipe(6f, 12f, 24f, if (dark) .18f else .12f, .34f, .10f)
    LiquidSurfaceRole.Sheet -> MaterialRecipe(5f, 12f, 24f, if (dark) .18f else .12f, .30f, .08f)
    LiquidSurfaceRole.Message -> MaterialRecipe(6f, 10f, 20f, if (dark) .45f else .40f, .22f, .06f)
    LiquidSurfaceRole.Navigation -> MaterialRecipe(3f, 13f, 26f, if (dark) .14f else .08f, .48f, .08f)
}

/** Real backdrop blur, vibrancy and lens refraction; text stays outside the sampled layer. */
@Composable
internal fun Modifier.liquidSurface(
    shape: Shape = RoundedRectangle(24.dp),
    tint: Color = MiuixTheme.colorScheme.surfaceContainer,
    compact: Boolean = false,
    backdrop: Backdrop? = LocalLiquidBackdrop.current,
    role: LiquidSurfaceRole = if (compact) LiquidSurfaceRole.Control else LiquidSurfaceRole.Panel,
): Modifier {
    val scene = LocalLiquidScene.current
    if (scene?.enabled != true || backdrop == null) {
        val solid = if (tint.alpha < .01f) MiuixTheme.colorScheme.surfaceContainer else tint.copy(alpha = 1f)
        return background(solid, shape)
    }
    // Saturated actions keep the contrast expected by the native miuix foreground colors.
    val colored = maxOf(tint.red, tint.green, tint.blue) - minOf(tint.red, tint.green, tint.blue) > .12f
    val windowSurface = role == LiquidSurfaceRole.Dialog || role == LiquidSurfaceRole.Sheet
    val preserveAction = role == LiquidSurfaceRole.Control || role == LiquidSurfaceRole.FloatingControl || role == LiquidSurfaceRole.Message
    // CLEAR buttons keep their theme tint, but the tint is now a translucent base for the
    // backdrop blur. The previous .92f opacity made HyperOS primary buttons look solid.
    val frostedColoredAction = scene.style == GlassStyle.CLEAR && colored && preserveAction && !windowSurface
    val material = remember(role, scene.dark, scene.style, frostedColoredAction) {
        val base = if (scene.style == GlassStyle.CLEAR) clearRecipe(role, scene.dark) else recipe(role, scene.dark)
        if (frostedColoredAction) base.copy(
            blur = maxOf(base.blur, 6f),
            height = maxOf(base.height, 10f),
            refraction = maxOf(base.refraction, 20f),
            opacity = if (scene.dark) .58f else .52f,
            highlight = maxOf(base.highlight, .24f),
        ) else base
    }
    val opacity = if (colored && !frostedColoredAction &&
        (!windowSurface && (scene.style != GlassStyle.CLEAR || preserveAction))) {
        maxOf(.92f, material.opacity)
    } else material.opacity
    val frostedActionVeil = when {
        !frostedColoredAction -> Color.Transparent
        scene.dark -> Color.White.copy(alpha = .10f)
        else -> Color.White.copy(alpha = .12f)
    }
    // Window sampling replays the undimmed page. Match its surrounding scrim before
    // tinting, so a white page remains translucent instead of lighting up the panel.
    val backdropShade = if (windowSurface && scene.style == GlassStyle.CLEAR) Color.Black.copy(alpha = .18f) else Color.Transparent
    val surfaceBrush = remember(tint, opacity) {
        Brush.verticalGradient(listOf(
            tint.copy(alpha = tint.alpha * opacity * .88f),
            tint.copy(alpha = (tint.alpha * opacity * 1.08f).coerceAtMost(1f)),
        ))
    }
    // Caller animations/layout can change without rebuilding the GPU effect chain.
    val glass = remember(backdrop, shape, surfaceBrush, material, backdropShade) {
        val highlight = Highlight(width = .5.dp, alpha = material.highlight)
        val shadow = if (material.shadow == 0f) null else Shadow(radius = 10.dp, color = Color.Black.copy(alpha = material.shadow))
        Modifier.drawBackdrop(
            backdrop = backdrop,
            shape = { shape },
            effects = {
                vibrancy()
                blur(material.blur.dp.toPx())
                lens(material.height.dp.toPx(), material.refraction.dp.toPx())
            },
            highlight = { highlight },
            shadow = if (shadow == null) null else { { shadow } },
            onDrawSurface = {
                if (windowSurface) drawRect(backdropShade)
                drawRect(surfaceBrush)
                if (frostedActionVeil.alpha > 0f) drawRect(frostedActionVeil)
            },
        )
    }
    return this.then(glass)
}
