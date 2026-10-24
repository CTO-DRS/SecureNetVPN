package com.securenet.vpn.ui.stats

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.securenet.vpn.R

/**
 * v1.5 — Dependency-free stacked bar chart for the monthly usage dashboard.
 *
 * One bar per calendar day; the upload (tx) segment sits on the baseline in
 * brand teal and the download (rx) segment stacks above it in light blue.
 * Tapping a bar selects it (white halo) and reports the day to the host.
 *
 * Always drawn left-to-right (day 1 leftmost) — charts read the same in both
 * app languages, so the view pins its layout direction to LTR.
 */
class StatsBarChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    data class Bar(val rxMb: Float, val txMb: Float) {
        val totalMb: Float get() = rxMb + txMb
    }

    interface OnBarSelectedListener {
        fun onBarSelected(dayIndex: Int, bar: Bar)
    }

    private var bars: List<Bar> = emptyList()
    private var todayIndex = -1
    private var selectedIndex = -1
    private var listener: OnBarSelectedListener? = null

    private val rxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.chart_rx)
    }
    private val txPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.chart_tx)
    }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.chart_grid)
        strokeWidth = resources.displayMetrics.density
    }
    private val baselinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.chart_baseline)
        strokeWidth = resources.displayMetrics.density * 1.2f
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.on_glass_secondary)
        textSize = resources.displayMetrics.density * 10
    }
    private val todayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.chart_today)
    }
    private val selectPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.chart_selected)
        style = Paint.Style.STROKE
        strokeWidth = resources.displayMetrics.density * 1.5f
    }

    private val barRect = RectF()

    fun setOnBarSelectedListener(l: OnBarSelectedListener?) {
        listener = l
    }

    /**
     * @param bars one entry per calendar day (zero-filled by the engine)
     * @param todayIndex index of today inside [bars] when viewing the live
     *        month, or -1 for past months
     */
    fun setData(bars: List<Bar>, todayIndex: Int) {
        this.bars = bars
        this.todayIndex = todayIndex
        this.selectedIndex = -1
        invalidate()
    }

    fun clearSelection() {
        selectedIndex = -1
        invalidate()
    }

    override fun onTouchEvent(event: android.view.MotionEvent): Boolean {
        if (event.action != android.view.MotionEvent.ACTION_DOWN &&
            event.action != android.view.MotionEvent.ACTION_UP
        ) {
            return super.onTouchEvent(event)
        }
        val index = barIndexAt(event.x) ?: return super.onTouchEvent(event)
        selectedIndex = index
        bars.getOrNull(index)?.let { listener?.onBarSelected(index, it) }
        invalidate()
        performClick()
        return true
    }

    /** Accessibility-friendly click passthrough. */
    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun barIndexAt(x: Float): Int? {
        if (bars.isEmpty()) return null
        val plot = plotArea()
        if (x < plot.left || x > plot.right) return null
        val slot = plot.width() / bars.size
        val index = ((x - plot.left) / slot).toInt().coerceIn(0, bars.lastIndex)
        return index
    }

    private fun plotArea(): RectF {
        val density = resources.displayMetrics.density
        val bottom = height - density * 18 // room for day labels
        return RectF(density * 4, density * 8, width - density * 4, bottom)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val plot = plotArea()
        if (bars.isEmpty()) return

        // Baseline + faint quarter gridlines.
        canvas.drawLine(plot.left, plot.bottom, plot.right, plot.bottom, baselinePaint)
        for (i in 1..3) {
            val y = plot.bottom - (plot.height() * i / 4f)
            canvas.drawLine(plot.left, y, plot.right, y, gridPaint)
        }

        val maxTotal = bars.maxOf { it.totalMb }.coerceAtLeast(0.001f)
        val slot = plot.width() / bars.size
        val density = resources.displayMetrics.density
        val barWidth = (slot * 0.58f).coerceAtMost(density * 18)
        val minVisible = density * 2 // hairline so zero-days stay visible

        bars.forEachIndexed { index, bar ->
            val cx = plot.left + slot * index + slot / 2f
            val left = cx - barWidth / 2f
            val right = cx + barWidth / 2f

            val totalH = (bar.totalMb / maxTotal) * plot.height()
            if (bar.totalMb > 0) {
                val txH = if (bar.totalMb <= 0) 0f
                else (bar.txMb / maxTotal) * plot.height()
                val rxH = totalH - txH

                val txBottom = plot.bottom
                val txTop = plot.bottom - txH.coerceAtLeast(if (bar.txMb > 0) minVisible else 0f)
                if (bar.txMb > 0) {
                    barRect.set(left, txTop, right, txBottom)
                    canvas.drawRoundRect(barRect, density * 2, density * 2, txPaint)
                }

                val rxTop = txTop - rxH.coerceAtLeast(if (bar.rxMb > 0) minVisible else 0f)
                if (bar.rxMb > 0) {
                    barRect.set(left, rxTop.coerceAtLeast(plot.top), right, txTop)
                    canvas.drawRoundRect(barRect, density * 2, density * 2, rxPaint)
                }
            }

            // Today marker: amber dot under the baseline.
            if (index == todayIndex) {
                canvas.drawCircle(cx, plot.bottom + density * 6, density * 2.5f, todayPaint)
            }

            // Selection halo.
            if (index == selectedIndex) {
                barRect.set(
                    left - density * 3, plot.top, right + density * 3, plot.bottom
                )
                canvas.drawRoundRect(barRect, density * 4, density * 4, selectPaint)
            }

            // Sparse day labels: 1, 5, 10, 15, 20, 25, 30(31).
            val dayNumber = index + 1
            if (dayNumber == 1 || dayNumber % 5 == 0 || index == bars.lastIndex) {
                val label = dayNumber.toString()
                val labelWidth = labelPaint.measureText(label)
                canvas.drawText(
                    label,
                    cx - labelWidth / 2f,
                    plot.bottom + density * 14,
                    labelPaint
                )
            }
        }
    }
}
