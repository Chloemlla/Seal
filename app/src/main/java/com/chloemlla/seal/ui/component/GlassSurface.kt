package com.chloemlla.seal.ui.component

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.chloemlla.seal.ui.common.LocalDarkTheme

/**
 * Shared tokens for local "glass" surfaces: translucent fill + soft gradient veil + hairline
 * outline. Default is **fake glass** (no backdrop sampling) so sticky headers and sheets stay cheap
 * while scrolling.
 *
 * True blur is intentionally off. Compose [androidx.compose.ui.graphics.BlurEffect] only blurs a
 * layer's own drawn content, not the backdrop behind it — applying it to a solid fill either does
 * nothing useful or softens text if mis-layered. Ship translucent + gradient + outline only.
 */
object GlassTokens {
    const val LightFillAlpha = 0.78f
    const val DarkFillAlpha = 0.76f
    const val HighContrastFillAlpha = 1f
    const val OutlineAlpha = 0.35f
    const val ScrimAlpha = 0.32f
    const val GradientTopAlpha = 0.18f
    val OutlineWidth = 1.dp
    /** Documented target radius if a future decorative (non-solid) blur layer is added (API 31+). */
    val BlurRadius = 12.dp
}

@Composable
@ReadOnlyComposable
fun glassContainerColor(): Color {
    val darkTheme = LocalDarkTheme.current
    val scheme = MaterialTheme.colorScheme
    if (darkTheme.isHighContrastModeEnabled) {
        return scheme.surfaceContainerHigh
    }
    val alpha =
        if (darkTheme.isDarkTheme()) GlassTokens.DarkFillAlpha else GlassTokens.LightFillAlpha
    return scheme.surfaceContainerHigh.copy(alpha = alpha)
}

@Composable
@ReadOnlyComposable
fun glassOutlineColor(): Color {
    val highContrast = LocalDarkTheme.current.isHighContrastModeEnabled
    return MaterialTheme.colorScheme.outlineVariant.copy(
        alpha = if (highContrast) 1f else GlassTokens.OutlineAlpha
    )
}

@Composable
@ReadOnlyComposable
fun glassScrimColor(): Color {
    return MaterialTheme.colorScheme.scrim.copy(alpha = GlassTokens.ScrimAlpha)
}

/**
 * Whether optional true blur would be legal (API 31+, not high contrast, opt-in). Drawing paths do
 * not apply blur today; this gate is the single place to consult if a decorative layer is added.
 */
@Composable
@ReadOnlyComposable
fun isGlassBlurEnabled(enableBlur: Boolean): Boolean {
    return enableBlur &&
        !LocalDarkTheme.current.isHighContrastModeEnabled &&
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
}

/**
 * Local glass treatment: translucent fill + optional top gradient veil + hairline border.
 *
 * High contrast forces opaque solid fill with no gradient. [enableBlur] is reserved for call-site
 * API symmetry; solid fills always stay fake-glass (see [GlassTokens]).
 *
 * Never use on LazyGrid/list items or dense scrolling cards.
 */
@Composable
fun Modifier.glassBackground(
    shape: Shape = RectangleShape,
    enableBlur: Boolean = true,
): Modifier {
    // True blur not applied to solid fills; evaluate gate so enableBlur stays a live parameter.
    @Suppress("UNUSED_VARIABLE")
    val blurEligible = isGlassBlurEnabled(enableBlur)

    val darkTheme = LocalDarkTheme.current
    val highContrast = darkTheme.isHighContrastModeEnabled
    val isDark = darkTheme.isDarkTheme()
    val scheme = MaterialTheme.colorScheme

    val fillAlpha =
        when {
            highContrast -> GlassTokens.HighContrastFillAlpha
            isDark -> GlassTokens.DarkFillAlpha
            else -> GlassTokens.LightFillAlpha
        }
    val fillColor =
        remember(scheme.surfaceContainerHigh, fillAlpha) {
            scheme.surfaceContainerHigh.copy(alpha = fillAlpha)
        }
    val outlineColor =
        remember(scheme.outlineVariant, highContrast) {
            scheme.outlineVariant.copy(
                alpha = if (highContrast) 1f else GlassTokens.OutlineAlpha
            )
        }

    val gradientBrush =
        remember(scheme.surface, highContrast) {
            if (highContrast) {
                null
            } else {
                Brush.verticalGradient(
                    colors =
                        listOf(
                            scheme.surface.copy(alpha = GlassTokens.GradientTopAlpha),
                            Color.Transparent,
                        )
                )
            }
        }

    return this.clip(shape)
        .background(color = fillColor, shape = shape)
        .then(
            if (gradientBrush != null) {
                Modifier.background(brush = gradientBrush, shape = shape)
            } else {
                Modifier
            }
        )
        .border(width = GlassTokens.OutlineWidth, color = outlineColor, shape = shape)
}

/**
 * Panel with glass fill under [content] so text/icons stay sharp.
 *
 * [enableBlur] mirrors [glassBackground]; true blur is not applied to solid decorative fills.
 */
@Composable
fun GlassPanel(
    modifier: Modifier = Modifier,
    shape: Shape = RectangleShape,
    enableBlur: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier = modifier.glassBackground(shape = shape, enableBlur = enableBlur)) {
        content()
    }
}
