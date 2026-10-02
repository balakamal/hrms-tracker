package com.balakamal.hrmstracker

import android.content.Context
import android.graphics.Matrix
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import androidx.appcompat.widget.AppCompatImageView

/**
 * High-performance hardware-accelerated ImageView supporting:
 * - Multi-touch pinch-to-zoom (1.0x to 5.0x scale)
 * - Double-tap to toggle zoom (1.0x <-> 2.5x)
 * - Smooth 2D panning with bounds clamping
 * - 60fps Matrix transformations without external library bloat
 */
class ZoomableImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatImageView(context, attrs, defStyleAttr) {

    private val matrixValues = FloatArray(9)
    private val currentMatrix = Matrix()
    private val savedMatrix = Matrix()

    private var minScale = 1.0f
    private var maxScale = 5.0f

    private val lastTouch = PointF()
    private val startTouch = PointF()

    private var mode = MODE_NONE
    private var isFitCenterDone = false

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
        gestureDetector = GestureDetector(context, GestureListener())
    }

    override fun setImageDrawable(drawable: Drawable?) {
        super.setImageDrawable(drawable)
        isFitCenterDone = false
        post { fitCenterImage() }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        fitCenterImage()
    }

    fun fitCenterImage() {
        val d = drawable ?: return
        val viewWidth = width.toFloat()
        val viewHeight = height.toFloat()
        if (viewWidth <= 0 || viewHeight <= 0) return

        val drawableWidth = d.intrinsicWidth.toFloat()
        val drawableHeight = d.intrinsicHeight.toFloat()
        if (drawableWidth <= 0 || drawableHeight <= 0) return

        val scaleX = viewWidth / drawableWidth
        val scaleY = viewHeight / drawableHeight
        val scale = Math.min(scaleX, scaleY)

        val dx = (viewWidth - drawableWidth * scale) / 2f
        val dy = (viewHeight - drawableHeight * scale) / 2f

        currentMatrix.reset()
        currentMatrix.postScale(scale, scale)
        currentMatrix.postTranslate(dx, dy)
        imageMatrix = currentMatrix
        isFitCenterDone = true
    }

    fun resetZoom() {
        fitCenterImage()
    }

    val currentScaleFactor: Float
        get() {
            currentMatrix.getValues(matrixValues)
            return matrixValues[Matrix.MSCALE_X]
        }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)

        val curr = PointF(event.x, event.y)

        when (event.action and MotionEvent.ACTION_MASK) {
            MotionEvent.ACTION_DOWN -> {
                savedMatrix.set(currentMatrix)
                startTouch.set(curr)
                lastTouch.set(curr)
                mode = MODE_DRAG
                parent?.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                savedMatrix.set(currentMatrix)
                mode = MODE_ZOOM
            }
            MotionEvent.ACTION_MOVE -> {
                if (mode == MODE_DRAG) {
                    val deltaX = curr.x - lastTouch.x
                    val deltaY = curr.y - lastTouch.y
                    currentMatrix.postTranslate(deltaX, deltaY)
                    clampTranslation()
                    imageMatrix = currentMatrix
                    lastTouch.set(curr.x, curr.y)
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                mode = MODE_NONE
                parent?.requestDisallowInterceptTouchEvent(false)
            }
        }
        return true
    }

    private fun clampTranslation() {
        val d = drawable ?: return
        val rect = RectF(0f, 0f, d.intrinsicWidth.toFloat(), d.intrinsicHeight.toFloat())
        currentMatrix.mapRect(rect)

        val viewW = width.toFloat()
        val viewH = height.toFloat()

        var deltaX = 0f
        var deltaY = 0f

        if (rect.width() <= viewW) {
            deltaX = (viewW - rect.width()) / 2f - rect.left
        } else {
            if (rect.left > 0) {
                deltaX = -rect.left
            } else if (rect.right < viewW) {
                deltaX = viewW - rect.right
            }
        }

        if (rect.height() <= viewH) {
            deltaY = (viewH - rect.height()) / 2f - rect.top
        } else {
            if (rect.top > 0) {
                deltaY = -rect.top
            } else if (rect.bottom < viewH) {
                deltaY = viewH - rect.bottom
            }
        }

        currentMatrix.postTranslate(deltaX, deltaY)
    }

    private inner class ScaleListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            var scaleFactor = detector.scaleFactor
            currentMatrix.getValues(matrixValues)
            val currentScale = matrixValues[Matrix.MSCALE_X]

            if (currentScale * scaleFactor < minScale) {
                scaleFactor = minScale / currentScale
            } else if (currentScale * scaleFactor > maxScale) {
                scaleFactor = maxScale / currentScale
            }

            currentMatrix.postScale(scaleFactor, scaleFactor, detector.focusX, detector.focusY)
            clampTranslation()
            imageMatrix = currentMatrix
            return true
        }
    }

    private inner class GestureListener : GestureDetector.SimpleOnGestureListener() {
        override fun onDoubleTap(e: MotionEvent): Boolean {
            currentMatrix.getValues(matrixValues)
            val currentScale = matrixValues[Matrix.MSCALE_X]

            if (currentScale > minScale * 1.5f) {
                // Zoom out to fit
                fitCenterImage()
            } else {
                // Zoom in to 2.5x around tap point
                val targetZoom = 2.5f
                currentMatrix.postScale(targetZoom, targetZoom, e.x, e.y)
                clampTranslation()
                imageMatrix = currentMatrix
            }
            return true
        }
    }
}
