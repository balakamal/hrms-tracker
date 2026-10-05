package com.balakamal.hrmstracker

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.appcompat.widget.AppCompatImageView
import kotlin.math.max
import kotlin.math.min

/**
 * High-performance hardware-accelerated ImageView supporting:
 * - Fluid multi-touch pinch-to-zoom calculated dynamically from image dimensions
 * - Seamless pointer transfer on finger release (zero jumps or coordinate snapping)
 * - Jitter-free strict boundary clamping
 * - Smooth 250ms animated double-tap zoom toggle (fit <-> 2.5x)
 * - Antialiased, dithered bitmap filtering
 */
class ZoomableImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatImageView(context, attrs, defStyleAttr) {

    private val matrixValues = FloatArray(9)
    private val currentMatrix = Matrix()

    // Dynamic scale limits based on image & viewport
    var baseFitScale = 1.0f
        private set
    private var minScale = 0.8f
    private var midScale = 2.5f
    private var maxScale = 6.0f

    // Touch tracking
    private var activePointerId = MotionEvent.INVALID_POINTER_ID
    private var lastTouchX = 0f
    private var lastTouchY = 0f

    private var mode = MODE_NONE
    private var isFitDone = false

    private var zoomAnimator: ValueAnimator? = null

    private val scaleDetector: ScaleGestureDetector
    private val gestureDetector: GestureDetector

    companion object {
        private const val MODE_NONE = 0
        private const val MODE_DRAG = 1
        private const val MODE_ZOOM = 2
    }

    init {
        scaleType = ScaleType.MATRIX
        scaleDetector = ScaleGestureDetector(context, ScaleListener())
        // Disable quick scale to prevent double-tap swipe conflict with our double-tap zoom
        scaleDetector.isQuickScaleEnabled = false

        gestureDetector = GestureDetector(context, GestureListener())
    }

    override fun setImageDrawable(drawable: Drawable?) {
        super.setImageDrawable(drawable)
        // Enable high-quality bilinear filtering and dithering on bitmap drawables
        if (drawable is BitmapDrawable) {
            drawable.paint.isFilterBitmap = true
            drawable.paint.isDither = true
            drawable.paint.flags = drawable.paint.flags or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG
        }
        isFitDone = false
        post { fitCenterImage() }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0) {
            fitCenterImage()
        }
    }

    /**
     * Calculates the matrix to fit the image perfectly within the view bounds,
     * maintaining aspect ratio and centering it.
     */
    fun fitCenterImage() {
        val d = drawable ?: return
        val viewW = width.toFloat()
        val viewH = height.toFloat()
        if (viewW <= 0f || viewH <= 0f) return

        val imgW = d.intrinsicWidth.toFloat()
        val imgH = d.intrinsicHeight.toFloat()
        if (imgW <= 0f || imgH <= 0f) return

        // Compute aspect-fit scale
        val scaleX = viewW / imgW
        val scaleY = viewH / imgH
        baseFitScale = min(scaleX, scaleY)

        // Dynamic thresholds relative to base fit scale
        minScale = baseFitScale * 0.75f   // Allow subtle pinch out bounce
        midScale = max(baseFitScale * 2.5f, 2.0f)
        maxScale = max(baseFitScale * 6.0f, 5.0f)

        val dx = (viewW - imgW * baseFitScale) / 2f
        val dy = (viewH - imgH * baseFitScale) / 2f

        currentMatrix.reset()
        currentMatrix.postScale(baseFitScale, baseFitScale)
        currentMatrix.postTranslate(dx, dy)
        imageMatrix = currentMatrix
        isFitDone = true
    }

    fun resetZoom() {
        cancelZoomAnimation()
        fitCenterImage()
    }

    fun getCurrentScale(): Float {
        currentMatrix.getValues(matrixValues)
        return matrixValues[Matrix.MSCALE_X]
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        // Let scale and gesture detectors inspect events first
        val scaleHandled = scaleDetector.onTouchEvent(event)
        val gestureHandled = gestureDetector.onTouchEvent(event)

        val action = event.actionMasked

        when (action) {
            MotionEvent.ACTION_DOWN -> {
                cancelZoomAnimation()
                activePointerId = event.getPointerId(0)
                lastTouchX = event.x
                lastTouchY = event.y
                mode = MODE_DRAG
                parent?.requestDisallowInterceptTouchEvent(true)
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                mode = MODE_ZOOM
            }

            MotionEvent.ACTION_MOVE -> {
                if (mode == MODE_DRAG && !scaleDetector.isInProgress) {
                    val pointerIndex = event.findPointerIndex(activePointerId)
                    if (pointerIndex != -1) {
                        val x = event.getX(pointerIndex)
                        val y = event.getY(pointerIndex)
                        val deltaX = x - lastTouchX
                        val deltaY = y - lastTouchY

                        if (deltaX != 0f || deltaY != 0f) {
                            currentMatrix.postTranslate(deltaX, deltaY)
                            clampTranslation()
                            imageMatrix = currentMatrix
                        }

                        lastTouchX = x
                        lastTouchY = y
                    }
                }
            }

            MotionEvent.ACTION_POINTER_UP -> {
                // When a pointer lifts during pinch, seamlessly transfer tracking
                // to the remaining pointer so no sudden coordinate delta happens.
                val pointerIndex = event.actionIndex
                val pointerId = event.getPointerId(pointerIndex)

                if (pointerId == activePointerId) {
                    // Pick the other pointer
                    val newPointerIndex = if (pointerIndex == 0) 1 else 0
                    lastTouchX = event.getX(newPointerIndex)
                    lastTouchY = event.getY(newPointerIndex)
                    activePointerId = event.getPointerId(newPointerIndex)
                }

                if (event.pointerCount <= 2) {
                    mode = MODE_DRAG
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                activePointerId = MotionEvent.INVALID_POINTER_ID
                mode = MODE_NONE
                parent?.requestDisallowInterceptTouchEvent(false)

                // If user pinched smaller than baseFitScale, smoothly spring back to fit
                val currentScale = getCurrentScale()
                if (currentScale < baseFitScale * 0.98f) {
                    animateToFitCenter()
                }
            }
        }

        return true
    }

    /**
     * Rock-solid boundary clamping with zero flicker or oscillation:
     * - If image fits inside view dimension, center it along that axis.
     * - If image exceeds view dimension, prevent boundaries from pulling inwards.
     */
    private fun clampTranslation() {
        val d = drawable ?: return
        val imgW = d.intrinsicWidth.toFloat()
        val imgH = d.intrinsicHeight.toFloat()
        if (imgW <= 0f || imgH <= 0f) return

        val rect = RectF(0f, 0f, imgW, imgH)
        currentMatrix.mapRect(rect)

        val viewW = width.toFloat()
        val viewH = height.toFloat()
        if (viewW <= 0f || viewH <= 0f) return

        var deltaX = 0f
        var deltaY = 0f

        // Horizontal Clamping
        if (rect.width() <= viewW) {
            deltaX = (viewW - rect.width()) / 2f - rect.left
        } else {
            if (rect.left > 0f) {
                deltaX = -rect.left
            } else if (rect.right < viewW) {
                deltaX = viewW - rect.right
            }
        }

        // Vertical Clamping
        if (rect.height() <= viewH) {
            deltaY = (viewH - rect.height()) / 2f - rect.top
        } else {
            if (rect.top > 0f) {
                deltaY = -rect.top
            } else if (rect.bottom < viewH) {
                deltaY = viewH - rect.bottom
            }
        }

        if (deltaX != 0f || deltaY != 0f) {
            currentMatrix.postTranslate(deltaX, deltaY)
        }
    }

    /**
     * Scale Gesture Listener handling smooth pinch-to-zoom
     */
    private inner class ScaleListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            var factor = detector.scaleFactor
            if (factor.isNaN() || factor.isInfinite() || factor == 1.0f) return false

            val currentScale = getCurrentScale()
            val targetScale = currentScale * factor

            // Clamp factor to stay within allowed dynamic scale range
            if (targetScale < minScale) {
                factor = minScale / currentScale
            } else if (targetScale > maxScale) {
                factor = maxScale / currentScale
            }

            if (factor == 1.0f) return false

            currentMatrix.postScale(factor, factor, detector.focusX, detector.focusY)
            clampTranslation()
            imageMatrix = currentMatrix

            // Keep drag coordinates synchronized with the scale focus
            lastTouchX = detector.focusX
            lastTouchY = detector.focusY
            return true
        }

        override fun onScaleEnd(detector: ScaleGestureDetector) {
            val currentScale = getCurrentScale()
            if (currentScale < baseFitScale * 0.98f) {
                animateToFitCenter()
            }
        }
    }

    /**
     * Double tap listener with smooth 250ms animation
     */
    private inner class GestureListener : GestureDetector.SimpleOnGestureListener() {
        override fun onDoubleTap(e: MotionEvent): Boolean {
            cancelZoomAnimation()
            val currentScale = getCurrentScale()

            // If already zoomed in beyond 1.3x base fit scale, double-tap zooms back to fit
            if (currentScale > baseFitScale * 1.3f) {
                animateToFitCenter()
            } else {
                // Zoom in to midScale centered at the tap point
                animateZoomTo(midScale, e.x, e.y)
            }
            return true
        }
    }

    private fun cancelZoomAnimation() {
        zoomAnimator?.cancel()
        zoomAnimator = null
    }

    /**
     * Smoothly animates the image matrix back to center-fit view
     */
    private fun animateToFitCenter() {
        val d = drawable ?: return
        val viewW = width.toFloat()
        val viewH = height.toFloat()
        if (viewW <= 0f || viewH <= 0f) return

        val imgW = d.intrinsicWidth.toFloat()
        val imgH = d.intrinsicHeight.toFloat()
        if (imgW <= 0f || imgH <= 0f) return

        val targetScale = min(viewW / imgW, viewH / imgH)
        val targetDx = (viewW - imgW * targetScale) / 2f
        val targetDy = (viewH - imgH * targetScale) / 2f

        val targetMatrix = Matrix().apply {
            postScale(targetScale, targetScale)
            postTranslate(targetDx, targetDy)
        }

        animateMatrixTransition(targetMatrix)
    }

    /**
     * Smoothly animates zoom to target scale focusing on (focusX, focusY)
     */
    private fun animateZoomTo(targetScale: Float, focusX: Float, focusY: Float) {
        val currentScale = getCurrentScale()
        if (currentScale <= 0f) return

        val scaleRatio = targetScale / currentScale
        val targetMatrix = Matrix(currentMatrix).apply {
            postScale(scaleRatio, scaleRatio, focusX, focusY)
        }

        // Apply clamping on target matrix to ensure final resting position is valid
        val d = drawable ?: return
        val rect = RectF(0f, 0f, d.intrinsicWidth.toFloat(), d.intrinsicHeight.toFloat())
        targetMatrix.mapRect(rect)

        val viewW = width.toFloat()
        val viewH = height.toFloat()
        var deltaX = 0f
        var deltaY = 0f

        if (rect.width() <= viewW) {
            deltaX = (viewW - rect.width()) / 2f - rect.left
        } else {
            if (rect.left > 0f) deltaX = -rect.left
            else if (rect.right < viewW) deltaX = viewW - rect.right
        }

        if (rect.height() <= viewH) {
            deltaY = (viewH - rect.height()) / 2f - rect.top
        } else {
            if (rect.top > 0f) deltaY = -rect.top
            else if (rect.bottom < viewH) deltaY = viewH - rect.bottom
        }

        targetMatrix.postTranslate(deltaX, deltaY)
        animateMatrixTransition(targetMatrix)
    }

    /**
     * Interpolates smoothly from currentMatrix to targetMatrix over 250ms
     */
    private fun animateMatrixTransition(targetMatrix: Matrix) {
        cancelZoomAnimation()

        val startValues = FloatArray(9)
        val endValues = FloatArray(9)
        currentMatrix.getValues(startValues)
        targetMatrix.getValues(endValues)

        zoomAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 250L
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { animator ->
                val fraction = animator.animatedFraction
                val interpolatedValues = FloatArray(9)
                for (i in 0 until 9) {
                    interpolatedValues[i] = startValues[i] + (endValues[i] - startValues[i]) * fraction
                }
                currentMatrix.setValues(interpolatedValues)
                imageMatrix = currentMatrix
            }
            start()
        }
    }
}
