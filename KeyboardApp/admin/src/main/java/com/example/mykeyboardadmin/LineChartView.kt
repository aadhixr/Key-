package com.example.mykeyboardadmin

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View

class LineChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var dataPoints: List<Float> = emptyList()
    private var labels: List<String> = emptyList()

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#0277BD")
        strokeWidth = 6f
        style = Paint.Style.STROKE
    }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#200277BD")
        style = Paint.Style.FILL
    }

    private val pointPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#0277BD")
        style = Paint.Style.FILL
    }

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E0E0E0")
        strokeWidth = 2f
        style = Paint.Style.STROKE
    }

    fun setData(points: List<Float>, timeLabels: List<String>) {
        this.dataPoints = points
        this.labels = timeLabels
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (dataPoints.isEmpty()) return

        val widthF = width.toFloat()
        val heightF = height.toFloat()
        val padding = 40f
        val chartWidth = widthF - (padding * 2)
        val chartHeight = heightF - (padding * 2)

        // Draw horizontal grid lines
        for (i in 0..4) {
            val y = padding + (chartHeight * i / 4f)
            canvas.drawLine(padding, y, widthF - padding, y, gridPaint)
        }

        val maxVal = (dataPoints.maxOrNull() ?: 10f).coerceAtLeast(5f)
        val path = Path()
        val fillPath = Path()

        val stepX = if (dataPoints.size > 1) chartWidth / (dataPoints.size - 1) else chartWidth

        dataPoints.forEachIndexed { index, value ->
            val x = padding + (index * stepX)
            val y = padding + chartHeight - (chartHeight * (value / maxVal))

            if (index == 0) {
                path.moveTo(x, y)
                fillPath.moveTo(x, padding + chartHeight)
                fillPath.lineTo(x, y)
            } else {
                path.lineTo(x, y)
                fillPath.lineTo(x, y)
            }
        }

        // Close fill path
        if (dataPoints.isNotEmpty()) {
            val lastX = padding + ((dataPoints.size - 1) * stepX)
            fillPath.lineTo(lastX, padding + chartHeight)
            fillPath.close()
            canvas.drawPath(fillPath, fillPaint)
        }

        // Draw line and points
        canvas.drawPath(path, linePaint)
        dataPoints.forEachIndexed { index, value ->
            val x = padding + (index * stepX)
            val y = padding + chartHeight - (chartHeight * (value / maxVal))
            canvas.drawCircle(x, y, 8f, pointPaint)
        }
    }
}
