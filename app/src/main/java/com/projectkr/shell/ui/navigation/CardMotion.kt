// Copyright 2026, kr-scripts-miuix contributors
// SPDX-License-Identifier: GPL-3.0

package com.projectkr.shell.ui.navigation

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastRoundToInt
import top.yukonga.miuix.kmp.nav.transition.NavGesture
import top.yukonga.miuix.kmp.nav.transition.NavMotion
import top.yukonga.miuix.kmp.nav.transition.NavRole
import top.yukonga.miuix.kmp.nav.transition.NavSettle
import top.yukonga.miuix.kmp.nav.transition.NavSettlePhase
import top.yukonga.miuix.kmp.nav.transition.NavSettleSpec
import top.yukonga.miuix.kmp.nav.transition.NavSwipeEdge
import top.yukonga.miuix.kmp.nav.transition.NavTransition
import top.yukonga.miuix.kmp.nav.transition.navDirectionalTransition
import top.yukonga.miuix.kmp.nav.transition.navGraphicsTransition
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * HyperOS-style card transition, ported from the Miuix example app's
 * `CrossActivityTransition` (AOSP WM Shell reference), trimmed to what this
 * app uses:
 *
 *  - Programmatic push/pop: classic window pair — both layers slide 96dp on a
 *    fixed 450ms eased timeline, the moving page fading on its own 83ms
 *    wall-clock alpha track, no dim and no scale.
 *  - Predictive back / edge swipe: gesture-scaled card — the covered page
 *    shrinks centered toward 0.9 with rounded corners and a held scrim, both
 *    layers ride vertically with the finger, and the commit pairs a 450ms
 *    eased track with a velocity-seeded underdamped bounce overlay.
 *
 * Pair with NavDisplayEffects(cornerClipMode = All, cornerClipRadius =
 * rememberNavSystemCornerRadius(), backdropColor = theme background).
 */
object CardMotion {

    val crossActivity: NavTransition = navDirectionalTransition(
        push = ClassicActivityOpen,
        pop = ClassicActivityClose,
        predictivePop = PredictiveCard,
    )
}

/** Exact port of `fast_out_extra_slow_in`: a two-segment cubic path. */
private val FastOutExtraSlowIn: Easing = run {
    val knotX = 0.166666f
    val knotY = 0.4f
    val first = CubicBezierEasing(0.05f / knotX, 0f, 0.133333f / knotX, 0.06f / knotY)
    val second = CubicBezierEasing(
        (0.208333f - knotX) / (1f - knotX),
        (0.82f - knotY) / (1f - knotY),
        (0.25f - knotX) / (1f - knotX),
        (1f - knotY) / (1f - knotY),
    )
    Easing { fraction ->
        if (fraction < knotX) {
            knotY * first.transform(fraction / knotX)
        } else {
            knotY + (1f - knotY) * second.transform((fraction - knotX) / (1f - knotX))
        }
    }
}

/** Reference pre-commit gesture interpolator (decelerate, slight accel start). */
private val BackGestureEasing: CubicBezierEasing = CubicBezierEasing(0.1f, 0.1f, 0f, 1f)

private const val BOUNCE_STIFFNESS = 200f
private const val BOUNCE_DAMPING = 0.75f
private const val BOUNCE_MAX_KICK = 1000f
private const val BOUNCE_MIN_KICK = 120f

private const val CROSS_ACTIVITY_MIN_SCALE = 0.9f
private const val MIN_RELEASE_SPAN = 0.001f
private val CrossActivityDrift = 96.dp
private val CrossActivityEdgeMargin = 8.dp

private const val CLASSIC_FADE_DURATION = 83f
private const val OPEN_FADE_OFFSET = 50f
private const val OPEN_FADE_START = 0.12f
private const val OPEN_FADE_SPAN = 0.71f
private const val CLOSE_FADE_OFFSET = 35f
private const val CLOSE_FADE_START = 0.21f
private const val CLOSE_FADE_SPAN = 0.74f

private val ClassicMotion = NavMotion(
    programmatic = NavSettleSpec.Tween(durationMillis = 450, easing = FastOutExtraSlowIn),
)

/** Velocity-scaled underdamped bounce overlay applied to both layers' scale. */
private fun bounceScale(settle: NavSettle?, gesture: NavGesture?): Float {
    if (settle == null || settle.phase != NavSettlePhase.Commit || gesture == null) return 1f
    val factor = if (gesture.swipeEdge != NavSwipeEdge.None) 2f else 1f
    val floorKick = if (gesture.progress < 0.1f) BOUNCE_MIN_KICK else 0f
    val kick = (abs(settle.releaseVelocity) * 100f * (1f - CROSS_ACTIVITY_MIN_SCALE) * factor)
        .coerceIn(floorKick, BOUNCE_MAX_KICK)
    if (kick <= 0f) return 1f
    val omega = sqrt(BOUNCE_STIFFNESS)
    val omegaD = omega * sqrt(1f - BOUNCE_DAMPING * BOUNCE_DAMPING)
    val t = settle.elapsedMillis / 1000f
    val overlay = -(kick / omegaD) * exp(-BOUNCE_DAMPING * omega * t) * sin(omegaD * t)
    return ((100f + overlay) / 100f).coerceAtMost(1f)
}

/** Eases delivered gesture progress so the card front-loads its travel. */
private fun shapedTopProgress(p: Float, gesture: NavGesture?): Float =
    if (gesture == null) p else 1f - BackGestureEasing.transform((1f - p).coerceIn(0f, 1f))

private fun topProgress(d: Float): Float = (1f + d).coerceIn(0f, 1f)

private fun coverProgress(d: Float): Float = d.coerceIn(0f, 1f)

private fun snapScaleToPixelWidth(scale: Float, width: Float): Float =
    if (width <= 0f) scale else (scale * width).fastRoundToInt() / width

private fun snapEdgeTranslation(translation: Float, scale: Float, extent: Float): Float {
    val inset = extent * (1f - scale) / 2f
    return (translation + inset).fastRoundToInt() - inset
}

private fun crossActivityYShift(
    gesture: NavGesture?,
    height: Float,
    scale: Float,
    density: Density,
): Float {
    if (gesture == null || height <= 0f) return 0f
    val rawDelta = gesture.touchY - gesture.initialTouchY
    val half = height / 2f
    val ratio = min(half, abs(rawDelta)) / half
    val damped = 1f - (1f - ratio) * (1f - ratio)
    val marginPx = with(density) { CrossActivityEdgeMargin.toPx() }
    val maxShift = ((height - height * scale) / 2f - marginPx).coerceAtLeast(0f)
    return maxShift * damped * (if (rawDelta < 0f) -1f else 1f)
}

/** Classic open: both layers slide 96dp, entering page fades on an 83ms ramp. */
private val ClassicActivityOpen: NavTransition = navGraphicsTransition(
    motion = ClassicMotion,
    scrim = { 0f },
) { scope ->
    val d = scope.relativeDepth
    val driftPx = with(scope.density) { CrossActivityDrift.toPx() }
    if (d <= 0f) {
        val p = topProgress(d)
        translationX = ((1f - p) * driftPx).fastRoundToInt().toFloat()
        alpha = if (scope.role == NavRole.Incoming) {
            val settle = scope.settle
            if (settle != null) {
                ((settle.elapsedMillis - OPEN_FADE_OFFSET) / CLASSIC_FADE_DURATION).coerceIn(0f, 1f)
            } else {
                ((p - OPEN_FADE_START) / OPEN_FADE_SPAN).coerceIn(0f, 1f)
            }
        } else {
            1f
        }
    } else {
        translationX = (-coverProgress(d) * driftPx).fastRoundToInt().toFloat()
    }
}

/** Classic close: leaving page slides out right, revealed page slides from left. */
private val ClassicActivityClose: NavTransition = navGraphicsTransition(
    motion = ClassicMotion,
    scrim = { 0f },
) { scope ->
    val d = scope.relativeDepth
    val driftPx = with(scope.density) { CrossActivityDrift.toPx() }
    if (d <= 0f) {
        val p = topProgress(d)
        translationX = ((1f - p) * driftPx).fastRoundToInt().toFloat()
        alpha = if (scope.role == NavRole.Outgoing) {
            val settle = scope.settle
            if (settle != null) {
                (1f - (settle.elapsedMillis - CLOSE_FADE_OFFSET) / CLASSIC_FADE_DURATION).coerceIn(0f, 1f)
            } else {
                ((p - CLOSE_FADE_START) / CLOSE_FADE_SPAN).coerceIn(0f, 1f)
            }
        } else {
            1f
        }
    } else {
        translationX = (-coverProgress(d) * driftPx).fastRoundToInt().toFloat()
    }
}

/** Gesture-driven card: shrink + corner hug + vertical follow + bounce commit. */
private val PredictiveCard: NavTransition = navGraphicsTransition(
    opaqueDepth = 1f,
    motion = NavMotion(
        commit = NavSettleSpec.Tween(durationMillis = 450, easing = FastOutExtraSlowIn),
        cancel = NavSettleSpec.Spring(stiffness = 1500f),
    ),
    scrim = { scope ->
        val s = scope.settle
        val g = scope.gesture
        when {
            s?.phase == NavSettlePhase.Commit -> (1f - s.elapsedMillis / 450f).coerceIn(0f, 1f)
            g != null -> (scope.relativeDepth.coerceIn(0f, 1f) / (1f - g.progress).coerceAtLeast(MIN_RELEASE_SPAN)).coerceIn(0f, 1f)
            else -> scope.relativeDepth.coerceIn(0f, 1f)
        }
    },
) { scope ->
    val d = scope.relativeDepth
    val gesture = scope.gesture
    val settle = scope.settle
    val committing = settle?.phase == NavSettlePhase.Commit
    val widthPx = scope.layoutSize.width.toFloat()
    val heightPx = scope.layoutSize.height.toFloat()
    val driftPx = with(scope.density) { CrossActivityDrift.toPx() }
    val bounce = bounceScale(settle, gesture)
    val hugMax = (
        widthPx * (1f - CROSS_ACTIVITY_MIN_SCALE) / 2f -
            with(scope.density) { CrossActivityEdgeMargin.toPx() }
        ).coerceAtLeast(0f)
    val hugs = gesture?.swipeEdge != NavSwipeEdge.Right

    if (d <= 0f) {
        val p = topProgress(d)
        if (scope.role == NavRole.Outgoing && committing && gesture != null) {
            // Post-commit closing card: grows back while flying out, fades on wall clock.
            val releaseP = (1f - gesture.progress).coerceAtLeast(MIN_RELEASE_SPAN)
            val post = (1f - p / releaseP).coerceIn(0f, 1f)
            val releasePE = shapedTopProgress(releaseP, gesture)
            val committedScale = CROSS_ACTIVITY_MIN_SCALE + (1f - CROSS_ACTIVITY_MIN_SCALE) * releasePE
            val grown = committedScale + (1f - committedScale) * post
            scaleX = snapScaleToPixelWidth(grown * bounce, widthPx)
            scaleY = scaleX
            var tx = if (hugs) (1f - releasePE) * hugMax else 0f
            tx += post * driftPx
            alpha = (1f - 5f * (settle.elapsedMillis / 450f)).coerceAtLeast(0f)
            translationX = snapEdgeTranslation(tx, scaleX, widthPx)
            translationY = snapEdgeTranslation(crossActivityYShift(gesture, heightPx, scaleX, scope.density), scaleX, heightPx)
        } else {
            // Finger driving (or cancel settling): eased travel axis, fully opaque.
            val pE = shapedTopProgress(p, gesture)
            scaleX = snapScaleToPixelWidth((CROSS_ACTIVITY_MIN_SCALE + (1f - CROSS_ACTIVITY_MIN_SCALE) * pE) * bounce, widthPx)
            scaleY = scaleX
            translationX = snapEdgeTranslation(if (hugs) (1f - pE) * hugMax else 0f, scaleX, widthPx)
            alpha = when {
                scope.role == NavRole.Outgoing && gesture != null -> {
                    val releaseP = (1f - gesture.progress).coerceAtLeast(MIN_RELEASE_SPAN)
                    (1f - (1f - p / releaseP).coerceIn(0f, 1f) * 3.5f).coerceAtLeast(0f)
                }
                gesture != null -> 1f
                else -> (p / 0.2f).coerceIn(0f, 1f)
            }
            translationY = snapEdgeTranslation(crossActivityYShift(gesture, heightPx, scaleX, scope.density), scaleX, heightPx)
        }
    } else {
        val dc = coverProgress(d)
        val post = if (gesture != null) {
            val releaseProgress = gesture.progress
            if (releaseProgress >= 1f) 1f else (((1f - dc) - releaseProgress) / (1f - releaseProgress)).coerceIn(0f, 1f)
        } else {
            1f - dc
        }
        if (gesture != null) {
            val travel = if (committing) gesture.progress else (1f - dc)
            val eased = BackGestureEasing.transform(travel.coerceIn(0f, 1f))
            val liveScale = CROSS_ACTIVITY_MIN_SCALE + (1f - CROSS_ACTIVITY_MIN_SCALE) * (1f - eased)
            scaleX = snapScaleToPixelWidth((liveScale + (1f - liveScale) * post) * bounce, widthPx)
            scaleY = scaleX
        }
        translationX = snapEdgeTranslation(-(1f - post) * driftPx, scaleX, widthPx)
        translationY = snapEdgeTranslation(crossActivityYShift(gesture, heightPx, scaleX, scope.density), scaleX, heightPx)
    }
}
