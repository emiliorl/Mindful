package com.mindshield.app.accessibility

import android.animation.AnimatorSet
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.*
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

private const val TAG = "FrictionOverlay"

/**
 * Fullscreen pause overlay drawn via WindowManager TYPE_ACCESSIBILITY_OVERLAY.
 *
 * Shows a two-phase breathing animation (inhale → exhale) lasting [durationSeconds].
 * "Open anyway" is fully hidden until the cycle completes, then fades in.
 * "Go back" is always available.
 */
class FrictionOverlay(
    private val context: Context,
    private val appName: String,
    private val durationSeconds: Int,
    private val onOpenAnyway: () -> Unit,
    private val onGoBack: () -> Unit,
    private val isSleepBlock: Boolean = false
) {
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var rootView: View? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    private val params = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT
    ).apply { gravity = Gravity.TOP or Gravity.START }

    fun show() {
        mainHandler.post {
            if (rootView != null) return@post
            val view = buildView()
            try {
                windowManager.addView(view, params)
                rootView = view
                Log.d(TAG, "Overlay shown for $appName (${durationSeconds}s)")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to show overlay for $appName", e)
            }
        }
    }

    fun dismiss() {
        mainHandler.post {
            rootView?.let {
                runCatching { windowManager.removeView(it) }
                rootView = null
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // View construction
    // ─────────────────────────────────────────────────────────────────────────

    private fun buildView(): View = if (isSleepBlock) buildSleepView() else buildBreathView()

    private fun buildBreathView(): View {
        val d = context.resources.displayMetrics.density

        // ── Colors ────────────────────────────────────────────────────────────
        val bgColor      = 0xFF_0F_1A_2E.toInt()   // deep navy — calming
        val primaryColor = 0xFF_5B_B8_F5.toInt()   // soft sky blue
        val textPrimary  = 0xFF_E8_F4_FD.toInt()   // near-white
        val textSecond   = 0xFF_8A_B4_D4.toInt()   // muted blue-grey
        val btnColor     = 0xFF_5B_B8_F5.toInt()   // sky blue for Open anyway
        val btnTextColor = 0xFF_0F_1A_2E.toInt()   // navy text on button

        val root = FrameLayout(context).apply {
            setBackgroundColor(bgColor)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding((32 * d).toInt(), (56 * d).toInt(), (32 * d).toInt(), (48 * d).toInt())
        }

        // ── Header ────────────────────────────────────────────────────────────
        val openingLabel = TextView(context).apply {
            text = "You're opening"
            textSize = 15f
            gravity = Gravity.CENTER
            setTextColor(textSecond)
        }

        val appLabel = TextView(context).apply {
            text = appName
            textSize = 26f
            gravity = Gravity.CENTER
            setTextColor(textPrimary)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(0, (4 * d).toInt(), 0, 0)
        }

        val spacerTop = View(context).apply { layoutParams = LinearLayout.LayoutParams(1, 0, 1f) }

        // ── Breath circle + phase label ───────────────────────────────────────
        val circleSize = (200 * d).toInt()
        val breathView = BreathCircleView(context, primaryColor, textPrimary).apply {
            layoutParams = LinearLayout.LayoutParams(circleSize, circleSize)
        }

        val phaseLabel = TextView(context).apply {
            text = "Breathe in..."
            textSize = 18f
            gravity = Gravity.CENTER
            setTextColor(primaryColor)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(0, (16 * d).toInt(), 0, 0)
        }

        val spacerBottom = View(context).apply { layoutParams = LinearLayout.LayoutParams(1, 0, 1f) }

        // ── Buttons ───────────────────────────────────────────────────────────
        val btnWidth = ViewGroup.LayoutParams.MATCH_PARENT

        val btnOpen = Button(context).apply {
            text = "Open anyway"
            visibility = View.GONE
            alpha = 0f
            setBackgroundColor(btnColor)
            setTextColor(btnTextColor)
            textSize = 16f
            layoutParams = LinearLayout.LayoutParams(btnWidth, (48 * d).toInt()).apply {
                bottomMargin = (12 * d).toInt()
            }
            setOnClickListener { dismiss(); onOpenAnyway() }
        }

        val btnBack = Button(context).apply {
            text = "Go back"
            setBackgroundColor(0x33_FF_FF_FF.toInt())
            setTextColor(textPrimary)
            textSize = 16f
            layoutParams = LinearLayout.LayoutParams(btnWidth, (48 * d).toInt())
            setOnClickListener { dismiss(); onGoBack() }
        }

        container.addView(openingLabel)
        container.addView(appLabel)
        container.addView(spacerTop)
        container.addView(breathView)
        container.addView(phaseLabel)
        container.addView(spacerBottom)
        container.addView(btnOpen)
        container.addView(btnBack)
        root.addView(container)

        val halfMs = durationSeconds * 500L

        val inhale = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = halfMs
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { anim ->
                breathView.setProgress(anim.animatedValue as Float, Phase.INHALE)
                phaseLabel.text = "Breathe in..."
            }
        }

        val exhale = ValueAnimator.ofFloat(1f, 0f).apply {
            duration = halfMs
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { anim ->
                breathView.setProgress(anim.animatedValue as Float, Phase.EXHALE)
                phaseLabel.text = "Breathe out..."
            }
        }

        AnimatorSet().apply { playSequentially(inhale, exhale); start() }

        exhale.addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: android.animation.Animator) {
                mainHandler.post {
                    phaseLabel.text = "Take your time."
                    btnOpen.visibility = View.VISIBLE
                    btnOpen.animate().alpha(1f).setDuration(400).start()
                }
            }
        })

        return root
    }

    // ── Sleep block: no animation, requires 3 deliberate taps ─────────────────
    private fun buildSleepView(): View {
        val d = context.resources.displayMetrics.density
        val bgColor     = 0xFF_07_0D_18.toInt()   // darker navy for sleep
        val primaryColor = 0xFF_5B_B8_F5.toInt()
        val textPrimary  = 0xFF_E8_F4_FD.toInt()
        val textSecond   = 0xFF_8A_B4_D4.toInt()

        var tapCount = 0

        val root = FrameLayout(context).apply {
            setBackgroundColor(bgColor)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL or Gravity.CENTER_VERTICAL
            setPadding((32 * d).toInt(), (56 * d).toInt(), (32 * d).toInt(), (48 * d).toInt())
        }

        val moonEmoji = TextView(context).apply {
            text = "🌙"
            textSize = 64f
            gravity = Gravity.CENTER
        }

        val titleLabel = TextView(context).apply {
            text = "It's sleep time"
            textSize = 26f
            gravity = Gravity.CENTER
            setTextColor(textPrimary)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(0, (12 * d).toInt(), 0, 0)
        }

        val subtitleLabel = TextView(context).apply {
            text = "$appName is blocked right now"
            textSize = 15f
            gravity = Gravity.CENTER
            setTextColor(textSecond)
            setPadding(0, (8 * d).toInt(), 0, 0)
        }

        val spacer = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(1, (40 * d).toInt())
        }

        val tapHintLabel = TextView(context).apply {
            text = "Tap 3 times to unlock anyway"
            textSize = 13f
            gravity = Gravity.CENTER
            setTextColor(0x88_FF_FF_FF.toInt())
            setPadding(0, 0, 0, (8 * d).toInt())
        }

        val btnWidth = ViewGroup.LayoutParams.MATCH_PARENT

        val btnUnlock = Button(context).apply {
            text = "Unlock anyway (1/3)"
            setBackgroundColor(0x22_FF_FF_FF.toInt())
            setTextColor(textPrimary)
            textSize = 15f
            layoutParams = LinearLayout.LayoutParams(btnWidth, (52 * d).toInt()).apply {
                bottomMargin = (12 * d).toInt()
            }
            setOnClickListener {
                tapCount++
                if (tapCount >= 3) {
                    dismiss(); onOpenAnyway()
                } else {
                    text = "Unlock anyway (${tapCount + 1}/3)"
                }
            }
        }

        val btnBack = Button(context).apply {
            text = "Go to sleep"
            setBackgroundColor(primaryColor)
            setTextColor(0xFF_0F_1A_2E.toInt())
            textSize = 16f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(btnWidth, (52 * d).toInt())
            setOnClickListener { dismiss(); onGoBack() }
        }

        container.addView(moonEmoji)
        container.addView(titleLabel)
        container.addView(subtitleLabel)
        container.addView(spacer)
        container.addView(tapHintLabel)
        container.addView(btnUnlock)
        container.addView(btnBack)
        root.addView(container)

        return root
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Breath phase
// ─────────────────────────────────────────────────────────────────────────────

private enum class Phase { INHALE, EXHALE }

// ─────────────────────────────────────────────────────────────────────────────
// Custom breath view
// ─────────────────────────────────────────────────────────────────────────────

private class BreathCircleView(
    context: Context,
    private val circleColor: Int,
    private val textColor: Int
) : View(context) {

    private var scale = 0f
    private var phase = Phase.INHALE

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.5f * resources.displayMetrics.density
    }
    // Outer glow ring (larger, very transparent)
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 12f * resources.displayMetrics.density
    }

    fun setProgress(value: Float, p: Phase) {
        scale = value
        phase = p
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val maxR = minOf(width, height) / 2f

        // Circle scales from 25% to 100%
        val r = maxR * (0.25f + scale * 0.75f)

        // Alpha: brighter when fully inhaled
        val fillAlpha = (0.10f + scale * 0.12f)
        val ringAlpha = (0.50f + scale * 0.45f)
        val glowAlpha = scale * 0.18f

        fillPaint.color = withAlpha(circleColor, fillAlpha)
        ringPaint.color = withAlpha(circleColor, ringAlpha)
        glowPaint.color = withAlpha(circleColor, glowAlpha)

        // Glow (outer, only visible when expanded)
        if (glowAlpha > 0.01f) {
            canvas.drawCircle(cx, cy, r + 20f * resources.displayMetrics.density, glowPaint)
        }
        canvas.drawCircle(cx, cy, r, fillPaint)
        canvas.drawCircle(cx, cy, r, ringPaint)
    }

    private fun withAlpha(color: Int, alpha: Float): Int {
        val a = (alpha.coerceIn(0f, 1f) * 255).toInt()
        return (color and 0x00FFFFFF) or (a shl 24)
    }
}
