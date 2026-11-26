package com.balakamal.hrmstracker

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF

/**
 * Renders razor-sharp, anti-aliased graphical gauges and Flighty-style shift timeline strips
 * into Bitmaps for Android Home Screen RemoteViews while keeping all typography as native TextViews.
 */
object WidgetGraphicsRenderer {

    private fun safeScale(context: Context): Float {
        return Math.min(Math.max(context.resources.displayMetrics.density, 1.5f), 2.5f)
    }

    /**
     * Compact 360-degree micro ring gauge for 4x1 Chrono Capsule.
     */
    fun renderMicroRingBitmap(
        context: Context,
        progressPercent: Int,
        activeColor: Int,
        trackColor: Int,
        sizeDp: Float = 44f
    ): Bitmap {
        val scale = safeScale(context)
        val sizePx = Math.max(1, (sizeDp * scale).toInt())
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val strokeWidth = 3.8f * scale
        val pad = strokeWidth / 2f + 1.5f * scale
        val rect = RectF(pad, pad, sizePx - pad, sizePx - pad)

        val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            this.strokeWidth = strokeWidth
            color = trackColor
            strokeCap = Paint.Cap.ROUND
        }
        canvas.drawOval(rect, trackPaint)

        val clamped = Math.min(100, Math.max(0, progressPercent))
        if (clamped > 0) {
            val sweep = 360f * (clamped / 100f)
            val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                this.strokeWidth = strokeWidth * 1.9f
                color = (activeColor and 0x00FFFFFF) or 0x33000000
                strokeCap = Paint.Cap.ROUND
            }
            canvas.drawArc(rect, -90f, sweep, false, glowPaint)

            val fgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                this.strokeWidth = strokeWidth
                color = activeColor
                strokeCap = Paint.Cap.ROUND
            }
            canvas.drawArc(rect, -90f, sweep, false, fgPaint)
        }
        return bitmap
    }

    /**
     * Apple Watch-inspired 250-degree open-bottom dial arc for 2x2 Dial Focus and 4x2 Studio Split.
     */
    fun renderOpenDialBitmap(
        context: Context,
        progressPercent: Int,
        activeColor: Int,
        trackColor: Int,
        widthDp: Float = 124f,
        heightDp: Float = 108f
    ): Bitmap {
        val scale = safeScale(context)
        val wPx = Math.max(1, (widthDp * scale).toInt())
        val hPx = Math.max(1, (heightDp * scale).toInt())
        val bitmap = Bitmap.createBitmap(wPx, hPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val strokeWidth = 8.0f * scale
        val radius = Math.min(wPx, hPx) / 2f - strokeWidth
        val cx = wPx / 2f
        val cy = hPx * 0.54f
        val rect = RectF(cx - radius, cy - radius, cx + radius, cy + radius)

        val startAngle = 145f
        val totalSweep = 250f

        val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            this.strokeWidth = strokeWidth
            color = trackColor
            strokeCap = Paint.Cap.ROUND
        }
        canvas.drawArc(rect, startAngle, totalSweep, false, trackPaint)

        val clamped = Math.min(100, Math.max(0, progressPercent))
        if (clamped > 0) {
            val activeSweep = totalSweep * (clamped / 100f)

            val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                this.strokeWidth = strokeWidth * 1.75f
                color = (activeColor and 0x00FFFFFF) or 0x2E000000
                strokeCap = Paint.Cap.ROUND
            }
            canvas.drawArc(rect, startAngle, activeSweep, false, glowPaint)

            val fgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                this.strokeWidth = strokeWidth
                color = activeColor
                strokeCap = Paint.Cap.ROUND
            }
            canvas.drawArc(rect, startAngle, activeSweep, false, fgPaint)

            // Precision specular tip highlight
            val tipRad = Math.toRadians((startAngle + activeSweep).toDouble())
            val tipX = (cx + radius * Math.cos(tipRad)).toFloat()
            val tipY = (cy + radius * Math.sin(tipRad)).toFloat()
            val tipPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                color = 0xCCFFFFFF.toInt()
            }
            canvas.drawCircle(tipX, tipY, strokeWidth * 0.24f, tipPaint)
        }
        return bitmap
    }

    /**
     * Concentric Dual Ring Gauge (Outer = Work Shift Progress, Inner = Break Usage vs Average)
     * tailored for 4x4 / Full-Screen Biometric Station.
     */
    fun renderConcentricDualRingBitmap(
        context: Context,
        workPercent: Int,
        breakPercent: Int,
        workColor: Int,
        breakColor: Int,
        trackColor: Int,
        sizeDp: Float = 132f
    ): Bitmap {
        val scale = safeScale(context)
        val sizePx = Math.max(1, (sizeDp * scale).toInt())
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val outerStroke = 10.0f * scale
        val innerStroke = 7.5f * scale
        val gap = 5.5f * scale

        val outerPad = outerStroke / 2f + 3f * scale
        val outerRect = RectF(outerPad, outerPad, sizePx - outerPad, sizePx - outerPad)
        val innerPad = outerPad + (outerStroke / 2f) + gap + (innerStroke / 2f)
        val innerRect = RectF(innerPad, innerPad, sizePx - innerPad, sizePx - innerPad)

        // Tracks
        val outerTrackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = outerStroke
            color = trackColor
        }
        canvas.drawOval(outerRect, outerTrackPaint)

        val innerTrackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = innerStroke
            color = trackColor
        }
        canvas.drawOval(innerRect, innerTrackPaint)

        // Outer Active Work Ring
        val clampedWork = Math.min(100, Math.max(0, workPercent))
        if (clampedWork > 0) {
            val sweep = 360f * (clampedWork / 100f)
            val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = outerStroke * 1.55f
                color = (workColor and 0x00FFFFFF) or 0x2E000000
                strokeCap = Paint.Cap.ROUND
            }
            canvas.drawArc(outerRect, -90f, sweep, false, glowPaint)

            val workPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = outerStroke
                color = workColor
                strokeCap = Paint.Cap.ROUND
            }
            canvas.drawArc(outerRect, -90f, sweep, false, workPaint)
        }

        // Inner Active Break Ring
        val clampedBreak = Math.min(100, Math.max(0, breakPercent))
        if (clampedBreak > 0) {
            val sweep = 360f * (clampedBreak / 100f)
            val breakPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = innerStroke
                color = breakColor
                strokeCap = Paint.Cap.ROUND
            }
            canvas.drawArc(innerRect, -90f, sweep, false, breakPaint)
        }
        return bitmap
    }

    /**
     * Flighty-inspired horizontal segmented timeline strip displaying work sessions (phase color)
     * and break gaps (amber) across the full shift duration.
     *
     * @param segmentsSpec Comma-separated pairs e.g. "W:0.42,B:0.06,W:0.39"
     */
    fun renderShiftTimelineBitmap(
        context: Context,
        segmentsSpec: String?,
        fallbackWorkPercent: Int,
        workColor: Int,
        breakColor: Int,
        trackColor: Int,
        widthDp: Float = 260f,
        heightDp: Float = 9f
    ): Bitmap {
        val scale = safeScale(context)
        val wPx = Math.max(1, (widthDp * scale).toInt())
        val hPx = Math.max(1, (heightDp * scale).toInt())
        val bitmap = Bitmap.createBitmap(wPx, hPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val radius = hPx / 2f
        val fullTrackRect = RectF(0f, 0f, wPx.toFloat(), hPx.toFloat())
        val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = trackColor
        }
        canvas.drawRoundRect(fullTrackRect, radius, radius, trackPaint)

        val segments = mutableListOf<Pair<Boolean, Float>>()
        if (!segmentsSpec.isNullOrBlank()) {
            for (part in segmentsSpec.split(",")) {
                val pieces = part.split(":")
                if (pieces.size == 2) {
                    val isBreak = pieces[0].trim().equals("B", ignoreCase = true)
                    val frac = pieces[1].toFloatOrNull() ?: 0f
                    if (frac > 0f) segments.add(isBreak to frac)
                }
            }
        }

        if (segments.isEmpty()) {
            val pct = Math.min(100, Math.max(0, fallbackWorkPercent)) / 100f
            if (pct > 0f) {
                val segW = Math.max(hPx.toFloat(), wPx * pct)
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.FILL
                    color = workColor
                }
                canvas.drawRoundRect(RectF(0f, 0f, segW, hPx.toFloat()), radius, radius, paint)
            }
            return bitmap
        }

        val totalSpan = Math.max(1.0f, segments.sumOf { it.second.toDouble() }.toFloat())
        val gapPx = 1.5f * scale
        var cursorX = 0f

        for (i in segments.indices) {
            val (isBreak, rawFrac) = segments[i]
            val segW = (rawFrac / totalSpan) * wPx
            if (segW < 1f) continue

            val rightX = Math.min(wPx.toFloat(), cursorX + segW - (if (i < segments.lastIndex) gapPx else 0f))
            if (rightX > cursorX) {
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.FILL
                    color = if (isBreak) breakColor else workColor
                }
                canvas.drawRoundRect(RectF(cursorX, 0f, rightX, hPx.toFloat()), radius, radius, paint)
            }
            cursorX += segW
        }
        return bitmap
    }
}
