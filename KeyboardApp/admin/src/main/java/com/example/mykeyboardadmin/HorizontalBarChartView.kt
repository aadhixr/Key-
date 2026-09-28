package com.example.mykeyboardadmin

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

class HorizontalBarChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var barData: List<Pair<String, Int>> = emptyList()

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#0277BD")
        style = Paint.Style.FILL
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1A1A1A")
        textSize = 30f
    }

    private val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#666666")
        textSize = 28f
    }

    fun setData(data: List<Pair<String, Int>>) {
        this.barData = data
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (barData.isEmpty()) {
            canvas.drawText("No application logging volume data yet.", 40f, 100f, textPaint)
            return
        }

        val widthF = width.toFloat()
        val heightF = height.toFloat()
        val leftMargin = 200f
        val rightMargin = 100f
        val topMargin = 40f
        val bottomMargin = 40f
        val chartWidth = widthF - leftMargin - rightMargin
        val chartHeight = heightF - topMargin - bottomMargin

        val barHeight = chartHeight / (barData.size * 1.5f).coerceAtLeast(1f)
        val gap = barHeight * 0.5f

        val maxVal = (barData.maxOfOrNull { it.second } ?: 10).coerceAtLeast(5)

        barData.forEachIndexed { index, (appName, count) ->
            val y = topMargin + index * (barHeight + gap)
            val barW = (count.toFloat() / maxVal) * chartWidth

            // Draw app name label on the left
            canvas.drawText(appName, 20f, y + barHeight * 0.7f, textPaint)

            // Draw horizontal bar
            val rect = RectF(leftMargin, y, leftMargin + barW, y + barHeight)
            canvas.drawRoundRect(rect, 8f, 8f, barPaint)

            // Draw count label on the right
            canvas.drawText(count.toString(), leftMargin + barW + 16f, y + barHeight * 0.7f, valuePaint)
        }
    }
}
