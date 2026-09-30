package com.example.mykeyboardadmin

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

class HourBarChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var hourlyCounts: FloatArray = FloatArray(24) { 0f }
    private val labels = listOf("12AM", "4AM", "8AM", "12PM", "4PM", "8PM")
    private val bucketHours = listOf(0, 4, 8, 12, 16, 20)

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#38BDF8")
        style = Paint.Style.FILL
    }

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1E293B")
        strokeWidth = 2f
        style = Paint.Style.STROKE
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#94A3B8")
        textSize = 24f
    }

    fun setData(counts: FloatArray) {
        if (counts.size == 24) {
            this.hourlyCounts = counts
            invalidate()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val widthF = width.toFloat()
        val heightF = height.toFloat()
        val leftPadding = 40f
        val rightPadding = 40f
        val topPadding = 30f
        val bottomPadding = 50f
        val chartWidth = widthF - leftPadding - rightPadding
        val chartHeight = heightF - topPadding - bottomPadding

        // Draw horizontal grid lines
        for (i in 0..3) {
            val y = topPadding + (chartHeight * i / 3f)
            canvas.drawLine(leftPadding, y, widthF - rightPadding, y, gridPaint)
        }

        val maxVal = (hourlyCounts.maxOrNull() ?: 10f).coerceAtLeast(5f)
        val barWidth = chartWidth / 24f * 0.75f
        val gap = (chartWidth / 24f) * 0.25f

        for (i in 0 until 24) {
            val valCount = hourlyCounts[i]
            val barH = (valCount / maxVal) * chartHeight
            val left = leftPadding + (i * (chartWidth / 24f)) + (gap / 2f)
            val top = topPadding + chartHeight - barH
            val right = left + barWidth
            val bottom = topPadding + chartHeight

            val rect = RectF(left, top, right, bottom)
            canvas.drawRoundRect(rect, 4f, 4f, barPaint)
        }

        // Draw time labels at 12AM, 4AM, 8AM, 12PM, 4PM, 8PM
        bucketHours.forEach { hour ->
            val label = labels[bucketHours.indexOf(hour)]
            val x = leftPadding + (hour * (chartWidth / 24f))
            canvas.drawText(label, x, heightF - 10f, textPaint)
        }
    }
}
