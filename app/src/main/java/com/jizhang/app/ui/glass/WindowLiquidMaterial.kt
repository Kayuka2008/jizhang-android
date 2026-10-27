package com.jizhang.app.ui.glass

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.unit.Density
import com.kyant.backdrop.Backdrop

/**
 * Separate Windows sample the recorded page in screen coordinates. The page producer
 * never records these windows, and consumers never replay Activity View.draw().
 */
internal val LocalLiquidWindowBackdrop = staticCompositionLocalOf<Backdrop?> { null }

private class RecordedWindowBackdrop(
    private val scene: LiquidScene,
    private val completePage: Boolean,
) : Backdrop {
    override val isCoordinatesDependent = true
    private val sourceToConsumer = Matrix()

    override fun DrawScope.drawBackdrop(
        density: Density,
        coordinates: LayoutCoordinates?,
        layerBlock: (GraphicsLayerScope.() -> Unit)?,
    ) {
        val source = scene.contentCoordinates?.takeIf { it.isAttached } ?: return
        val consumer = coordinates?.takeIf { it.isAttached } ?: return
        // Adapted from SleepDown's TransformAwareLayerBackdrop (Apache-2.0).
        // Screen-space basis mapping also compensates the dialog's animated scale.
        val origin = consumer.screenToLocal(source.localToScreen(Offset.Zero))
        // A longer basis avoids subtracting nearly identical screen coordinates.
        val span = 64f
        val x = consumer.screenToLocal(source.localToScreen(Offset(span, 0f)))
        val y = consumer.screenToLocal(source.localToScreen(Offset(0f, span)))
        if (!origin.isSpecified || !x.isSpecified || !y.isSpecified ||
            !origin.x.isFinite() || !origin.y.isFinite() ||
            !x.x.isFinite() || !x.y.isFinite() || !y.x.isFinite() || !y.y.isFinite()) return
        sourceToConsumer.reset()
        sourceToConsumer[0, 0] = (x.x - origin.x) / span
        sourceToConsumer[0, 1] = (x.y - origin.y) / span
        sourceToConsumer[1, 0] = (y.x - origin.x) / span
        sourceToConsumer[1, 1] = (y.y - origin.y) / span
        sourceToConsumer[3, 0] = origin.x
        sourceToConsumer[3, 1] = origin.y
        // Coordinates include ancestor graphics layers; do not invert layerBlock again.
        withTransform({ transform(sourceToConsumer) }) {
            drawLayer(if (completePage) scene.contentBackdrop.graphicsLayer else scene.backdrop.graphicsLayer)
        }
    }
}

/** Invoke in the Activity composition, outside the dialog or bottom sheet window. */
@Composable
internal fun ProvideWindowLiquidMaterial(
    active: Boolean = true,
    blurVisible: Boolean = active,
    content: @Composable () -> Unit,
) {
    ModalPageBlurEffect(active && blurVisible)
    val scene = LocalLiquidScene.current
    val backdrop = remember(scene) { scene?.let { RecordedWindowBackdrop(it, completePage = true) } }
    val controlsBackdrop = remember(scene) { scene?.let { RecordedWindowBackdrop(it, completePage = false) } }
    DisposableEffect(scene, active) {
        if (active && scene?.enabled == true) scene.windowConsumers++
        onDispose { if (active && scene?.enabled == true) scene.windowConsumers-- }
    }
    CompositionLocalProvider(
        LocalLiquidWindowBackdrop provides backdrop,
        LocalLiquidBackdrop provides controlsBackdrop,
        content = content,
    )
}
