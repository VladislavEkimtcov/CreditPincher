package com.github.vladislavekimtcov.creditpincher.toolWindow

import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.MouseMotionAdapter
import java.awt.geom.Rectangle2D
import java.text.DecimalFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.swing.JComponent
import kotlin.math.ceil

class UsageBarChart : JComponent() {
    data class DailyUsage(val date: LocalDate, val credits: Double)

    private var data: List<DailyUsage> = emptyList()
    private var showInDollars: Boolean = false
    private var hoveredIndex: Int = -1

    private val leftMargin: Int
        get() = JBUI.scale(60)
    private val rightMargin: Int
        get() = JBUI.scale(15)
    private val topMargin: Int
        get() = JBUI.scale(15)
    private val bottomMargin: Int
        get() = JBUI.scale(25)

    private val numberFormat = DecimalFormat("#,##0.00")

    init {
        toolTipText = "" // Enable tooltips
        addMouseMotionListener(object : MouseMotionAdapter() {
            override fun mouseMoved(e: MouseEvent) {
                val index = getBarIndexAt(e.x, e.y)
                if (index != hoveredIndex) {
                    hoveredIndex = index
                    repaint()
                }
            }
        })
        addMouseListener(object : MouseAdapter() {
            override fun mouseExited(e: MouseEvent) {
                if (hoveredIndex != -1) {
                    hoveredIndex = -1
                    repaint()
                }
            }
        })
    }

    fun updateData(newData: List<DailyUsage>, showDollars: Boolean) {
        this.data = newData
        this.showInDollars = showDollars
        this.hoveredIndex = -1
        repaint()
    }

    private fun getBarIndexAt(x: Int, y: Int): Int {
        if (data.isEmpty()) return -1
        val chartWidth = width - leftMargin - rightMargin
        val chartHeight = height - topMargin - bottomMargin
        if (chartWidth <= 0 || chartHeight <= 0) return -1

        if (x < leftMargin || x > width - rightMargin || y < topMargin || y > height - bottomMargin) {
            return -1
        }
        val N = data.size
        val step = chartWidth.toDouble() / N
        val index = ((x - leftMargin) / step).toInt().coerceIn(0, N - 1)

        // Only register hover if the Y coordinate is within the height of the bar
        val usage = data[index]
        val maxUsage = data.maxOfOrNull { it.credits } ?: 0.0
        val maxVal = if (maxUsage > 0.0) maxUsage else 1.0
        val barHeight = (usage.credits / maxVal) * chartHeight
        val barTop = height - bottomMargin - barHeight
        val barBottom = height - bottomMargin

        // Allow some padding or min height for hover recognition of zero-bars
        val minHoverTop = if (barHeight < 5.0) height - bottomMargin - 5.0 else barTop
        if (y >= minHoverTop && y <= barBottom + 2.0) {
            return index
        }
        return -1
    }

    override fun getToolTipText(event: MouseEvent): String? {
        val index = getBarIndexAt(event.x, event.y)
        if (index == -1 || index >= data.size) return null
        val usage = data[index]
        val dateStr = usage.date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        val creditsStr = numberFormat.format(usage.credits) + " credits"
        val dollarsStr = "$" + numberFormat.format(usage.credits / 100.0)
        return "<html><b>$dateStr</b><br/>Usage: $creditsStr<br/>Cost: $dollarsStr</html>"
    }

    override fun getPreferredSize(): Dimension = Dimension(JBUI.scale(150), JBUI.scale(180))
    override fun getMinimumSize(): Dimension = Dimension(JBUI.scale(100), JBUI.scale(120))

    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        val g2 = g.create() as Graphics2D
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

            val chartWidth = width - leftMargin - rightMargin
            val chartHeight = height - topMargin - bottomMargin
            if (chartWidth <= 0 || chartHeight <= 0) return

            // Calculate max usage
            val maxUsage = data.maxOfOrNull { it.credits } ?: 0.0
            val maxVal = if (maxUsage > 0.0) maxUsage else 10.0

            // Draw Y grid lines and labels
            val tickCount = 4
            g2.font = JBUI.Fonts.smallFont()
            val fmY = g2.fontMetrics
            for (i in 0 until tickCount) {
                val yVal = height - bottomMargin - (i * chartHeight) / (tickCount - 1)
                val currentVal = (i * maxVal) / (tickCount - 1)

                // Grid line
                g2.color = JBColor(Color(230, 230, 230), Color(60, 60, 60))
                g2.drawLine(leftMargin, yVal, width - rightMargin, yVal)

                // Label
                val label = if (showInDollars) {
                    "$" + numberFormat.format(currentVal / 100.0)
                } else {
                    numberFormat.format(currentVal)
                }
                g2.color = JBColor.foreground()
                val labelWidth = fmY.stringWidth(label)
                g2.drawString(label, leftMargin - JBUI.scale(5) - labelWidth, yVal + fmY.ascent / 2 - JBUI.scale(1))
            }

            // Draw X axis
            g2.color = JBColor.border()
            g2.drawLine(leftMargin, height - bottomMargin, width - rightMargin, height - bottomMargin)

            if (data.isEmpty()) {
                g2.color = JBColor.foreground()
                val emptyText = "No data available in selected range"
                val textWidth = fmY.stringWidth(emptyText)
                g2.drawString(emptyText, leftMargin + (chartWidth - textWidth) / 2, topMargin + chartHeight / 2 + fmY.ascent / 2)
                return
            }

            // Draw Bars
            val N = data.size
            val step = chartWidth.toDouble() / N
            val gap = if (step > 4) 2.0 else 0.0
            val barWidth = maxOf(1.0, step - gap)

            for (i in 0 until N) {
                val usage = data[i]
                val x = leftMargin + i * step
                val barHeight = (usage.credits / maxVal) * chartHeight
                val y = height - bottomMargin - barHeight

                // Draw bar rectangle
                if (i == hoveredIndex) {
                    // Modern highlighted blue color
                    g2.color = JBColor(Color(0x56a6e2), Color(0x4b8cb8))
                } else {
                    // Modern primary theme blue color
                    g2.color = JBColor(Color(0x3574A8), Color(0x2d618c))
                }
                g2.fill(Rectangle2D.Double(x, y, barWidth, barHeight))
            }

            // Draw X ticks and labels
            val labelWidth = fmY.stringWidth("00-00")
            val minSpacing = JBUI.scale(15)
            val labelSlotWidth = labelWidth + minSpacing
            val labelInterval = maxOf(1, ceil(labelSlotWidth / step).toInt())

            for (i in 0 until N step labelInterval) {
                val usage = data[i]
                val xCenter = leftMargin + i * step + barWidth / 2
                val dateStr = usage.date.format(DateTimeFormatter.ofPattern("MM-dd"))
                val strWidth = fmY.stringWidth(dateStr)

                // Tick
                g2.color = JBColor.border()
                g2.drawLine(xCenter.toInt(), height - bottomMargin, xCenter.toInt(), height - bottomMargin + JBUI.scale(4))

                // Text
                g2.color = JBColor.foreground()
                g2.drawString(dateStr, (xCenter - strWidth / 2).toInt(), height - bottomMargin + JBUI.scale(16))
            }
        } finally {
            g2.dispose()
        }
    }
}
