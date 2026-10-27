/*
 * Feature Module: UI Components
 * File: ProgressiveBlur.kt
 * Description: AGSL progressive blur (android-common-main "Progressive Blur Implementation"),
 * used to softly blur content scrolling beneath the floating navigation bar.
 */

package com.ssethhyy.journey.ui.components

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import android.os.PowerManager
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import org.intellij.lang.annotations.Language

enum class BlurDirection { TOP, BOTTOM }

@Language("AGSL")
private val PROGRESSIVE_BLUR_SKSL = """
    uniform shader content;
    uniform float blurRadius;
    uniform float height;
    uniform float contentHeight;
    uniform int isTop;

    half4 main(float2 fragCoord) {
        float progress;
        if (isTop == 1) {
            progress = 1.0 - clamp(fragCoord.y / height, 0.0, 1.0);
        } else {
            progress = 1.0 - clamp((contentHeight - fragCoord.y) / height, 0.0, 1.0);
        }

        // Easing curve for smoother transition (power curve)
        progress = pow(progress, 1.5);

        float radius = progress * blurRadius;

        if (radius <= 0.0) {
            return content.eval(fragCoord);
        }

        half4 accum = half4(0.0);
        float weightSum = 0.0;

        // Random value for dithering based on pixel coordinates
        float dither = fract(sin(dot(fragCoord, float2(12.9898, 78.233))) * 43758.5453);
        float2 jitter = float2(dither - 0.5, fract(dither * 1.618) - 0.5);

        const int SAMPLES = 4;
        float offsetScale = radius / float(SAMPLES);

        for (int x = -SAMPLES; x <= SAMPLES; x++) {
            for (int y = -SAMPLES; y <= SAMPLES; y++) {
                float2 offset = (float2(float(x), float(y)) + jitter) * offsetScale;
                float distSq = dot(offset, offset);
                float radiusSq = radius * radius;
                if (distSq <= radiusSq) {
                    float weight = exp(-3.0 * distSq / radiusSq);
                    accum += content.eval(fragCoord + offset) * weight;
                    weightSum += weight;
                }
            }
        }

        return accum / weightSum;
    }
""".trimIndent()

/**
 * Progressively blurs this content towards one edge. [heightPx] is how far the blur reaches from
 * that edge. No-op below Android 13 or when [blurRadiusPx] is 0.
 */
fun Modifier.progressiveBlur(
    blurRadiusPx: Float,
    heightPx: Float,
    direction: BlurDirection = BlurDirection.BOTTOM,
): Modifier = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || blurRadiusPx <= 0f || heightPx <= 0f) {
    this
} else {
    composed {
        val shader = remember { RuntimeShader(PROGRESSIVE_BLUR_SKSL) }
        graphicsLayer {
            applyBlur(shader, blurRadiusPx, heightPx, size.height, direction)
        }
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun androidx.compose.ui.graphics.GraphicsLayerScope.applyBlur(
    shader: RuntimeShader,
    radius: Float,
    height: Float,
    contentHeight: Float,
    direction: BlurDirection,
) {
    shader.setFloatUniform("blurRadius", radius)
    shader.setFloatUniform("height", height)
    shader.setFloatUniform("contentHeight", contentHeight)
    shader.setIntUniform("isTop", if (direction == BlurDirection.TOP) 1 else 0)
    renderEffect = RenderEffect.createRuntimeShaderEffect(shader, "content").asComposeRenderEffect()
}

object BlurSupport {
    /** Some Samsung builds on Android 15 and below render a grey overlay instead of the blur. */
    fun isProblematicDevice(): Boolean =
        Build.MANUFACTURER.equals("samsung", ignoreCase = true) && Build.VERSION.SDK_INT <= 35

    fun isPowerSaveMode(context: Context): Boolean =
        (context.getSystemService(Context.POWER_SERVICE) as? PowerManager)?.isPowerSaveMode == true
}

/** True when blur should run: Android 13+, not a problematic device, and not in battery saver. */
@Composable
fun rememberBlurAvailable(): Boolean {
    val context = LocalContext.current
    var powerSave by remember { mutableStateOf(BlurSupport.isPowerSaveMode(context)) }
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                powerSave = BlurSupport.isPowerSaveMode(c)
            }
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }
    return Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !BlurSupport.isProblematicDevice() && !powerSave
}
