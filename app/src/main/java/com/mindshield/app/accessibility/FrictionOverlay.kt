package com.mindshield.app.accessibility

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.*
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

private const val BREATH_DURATION_MS = 5_000L

/**
 * Draws a fullscreen overlay via WindowManager using TYPE_ACCESSIBILITY_OVERLAY.
 * Uses pure Android Views to avoid the ComposeView lifecycle-owner requirement
 * in a non-Activity context.
 *
 * Shows a 5-second breath circle; "Open anyway" is locked until it completes.
 */
class FrictionOverlay(
    private val context: Context,
    private val appName: String,
    private val onOpenAnyway: () -> Unit,
    private val onGoBack: () -> Unit
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
    ).apply {
        gravity = Gravity.TOP or Gravity.START
    }

    fun show() {
        mainHandler.post {
            if (rootView != null) return@post

            val view = buildView()
            rootView = view
            windowManager.addView(view, params)
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

    private fun buildView(): View {
        val colorBackground = 0xFF_F8_F5_F2.toInt()   // warm off-white
        val colorPrimary    = 0xFF_4C_AF_50.toInt()   // calm green
        val colorOnPrimary  = 0xFF_FF_FF_FF.toInt()   // white

        val root = FrameLayout(context).apply {
            setBackgroundColor(colorBackground)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        val density = context.resources.displayMetrics.density

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(
                (32 * density).toInt(), (48 * density).toInt(),
                (32 * density).toInt(), (48 * density).toInt()
            )
        }

        // ── Title ──────────────────────────────────────────────────────────────
        val opening = TextView(context).apply {
            text = "You're opening"
            textSize = 16f
            gravity = Gravity.CENTER
            setTextColor(0xFF_88_88_88.toInt())
        }

        val appNameView = TextView(context).apply {
            text = appName
            textSize = 24f
            gravity = Gravity.CENTER
            setTextColor(0xFF_1A_1A_1A.toInt())
            setPadding(0, (4 * density).toInt(), 0, 0)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }

        val subtitle = TextView(context).apply {
            text = "Take a breath first."
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(0xFF_88_88_88.toInt())
            setPadding(0, (4 * density).toInt(), 0, (32 * density).toInt())
        }

        // ── Breath circle ──────────────────────────────────────────────────────
        val circleSize = (220 * density).toInt()
        val breathView = BreathCircleView(context, colorPrimary).apply {
            layoutParams = LinearLayout.LayoutParams(circleSize, circleSize)
        }

        val spacer = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (32 * density).toInt()
            )
        }

        // ── Buttons ────────────────────────────────────────────────────────────
        val btnOpen = Button(context).apply {
            text = "Open anyway"
            isEnabled = false
            setBackgroundColor(colorPrimary)
            setTextColor(colorOnPrimary)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = (12 * density).toInt() }
            setOnClickListener {
                dismiss()
                onOpenAnyway()
            }
        }

        val btnBack = Button(context).apply {
            text = "Go back"
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setOnClickListener {
                dismiss()
                onGoBack()
            }
        }

        container.addView(opening)
        container.addView(appNameView)
        container.addView(subtitle)
        container.addView(breathView)
        container.addView(spacer)
        container.addView(btnOpen)
        container.addView(btnBack)
        root.addView(container)

        // Start animation — unlock button when done
        breathView.startBreath(BREATH_DURATION_MS) {
            mainHandler.post { btnOpen.isEnabled = true }
        }

        return root
    }

}

// ─────────────────────────────────────────────────────────────────────────────
// Custom view: animating breath circle
// ─────────────────────────────────────────────────────────────────────────────

private class BreathCircleView(context: Context, private val color: Int) : View(context) {

    private var progress = 0f   // 0..1
    private var onDone: (() -> Unit)? = null

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f * resources.displayMetrics.density
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 16f * resources.displayMetrics.density
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val maxR = minOf(width, height) / 2f

        val scale = 0.6f + progress * 0.4f
        val r = maxR * scale

        val alpha = (0xFF * 0.15f).toInt()
        fillPaint.color = (color and 0x00FFFFFF) or (alpha shl 24)
        canvas.drawCircle(cx, cy, r, fillPaint)

        strokePaint.color = (color and 0x00FFFFFF) or (0x99 shl 24)
        canvas.drawCircle(cx, cy, r, strokePaint)

        val label = if (progress >= 1f) "✓" else "breathe"
        textPaint.color = color
        textPaint.textSize = if (progress >= 1f) 32f * resources.displayMetrics.density
                             else 16f * resources.displayMetrics.density
        canvas.drawText(label, cx, cy - (textPaint.ascent() + textPaint.descent()) / 2f, textPaint)
    }

    fun startBreath(durationMs: Long, onComplete: () -> Unit) {
        onDone = onComplete
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = durationMs
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { anim ->
                progress = anim.animatedValue as Float
                invalidate()
                if (progress >= 1f) onDone?.also { onDone = null }?.invoke()
            }
            start()
        }
    }
}
