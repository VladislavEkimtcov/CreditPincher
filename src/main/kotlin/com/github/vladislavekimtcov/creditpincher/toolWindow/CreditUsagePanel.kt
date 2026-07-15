package com.github.vladislavekimtcov.creditpincher.toolWindow

import com.github.vladislavekimtcov.creditpincher.MyBundle
import com.github.vladislavekimtcov.creditpincher.model.CreditUsageEntry
import com.github.vladislavekimtcov.creditpincher.model.UsageStats
import com.github.vladislavekimtcov.creditpincher.services.CreditStatsCalculator
import com.github.vladislavekimtcov.creditpincher.services.CreditUsageStore
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBUI
import org.jdesktop.swingx.JXDatePicker
import java.awt.BorderLayout
import java.awt.Component
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.border.TitledBorder

class CreditUsagePanel(project: Project) : JPanel(BorderLayout()) {
    private val store = ApplicationManager.getApplication().getService(CreditUsageStore::class.java)
    private val zoneId = ZoneId.systemDefault()
    private val numberFormat = DecimalFormat("#,##0.00")
    private val dateTimeFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

    private val toggleUnitButton = JButton()
    private var showInDollars = false

    private val amountField = JBTextField()
    private val budgetField = JBTextField()
    private val startDatePicker = createDatePicker(LocalDate.now().withDayOfMonth(1))
    private val endDatePicker = createDatePicker(LocalDate.now())
    private val statusLabel = JBLabel(" ")
    private val totalCreditsValue = JBLabel()
    private val rangeValue = JBLabel()
    private val entryCountValue = JBLabel()
    private val activeDaysValue = JBLabel()
    private val averagePerDayValue = JBLabel()
    private val averagePerActiveDayValue = JBLabel()
    private val busiestDayValue = JBLabel()
    private val highestEntryValue = JBLabel()
    private val budgetValue = JBLabel()
    private val rangeBudgetValue = JBLabel()
    private val remainingBudgetValue = JBLabel()
    private val projectedMonthValue = JBLabel()
    private val runwayValue = JBLabel()
    private val lastEntryValue = JBLabel()
    private val recentEntriesArea = JBTextArea()

    init {
        border = JBUI.Borders.empty(8)

        toggleUnitButton.addActionListener {
            showInDollars = !showInDollars
            updateToggleButtonText()
            refreshStats()
        }
        updateToggleButtonText()

        recentEntriesArea.isEditable = false
        recentEntriesArea.lineWrap = false
        recentEntriesArea.rows = 8
        recentEntriesArea.emptyText.text = MyBundle["recentEntries.empty"]

        val content = JBPanel<JBPanel<*>>()
        content.layout = BoxLayout(content, BoxLayout.Y_AXIS)
        content.add(createUsageEntrySection())
        content.add(Box.createVerticalStrut(JBUI.scale(8)))
        content.add(createDateRangeSection())
        content.add(Box.createVerticalStrut(JBUI.scale(8)))
        content.add(createBudgetSection())
        content.add(Box.createVerticalStrut(JBUI.scale(8)))
        content.add(createStatsSection())
        content.add(Box.createVerticalStrut(JBUI.scale(8)))
        content.add(createStorageSection())
        content.add(Box.createVerticalStrut(JBUI.scale(8)))
        content.add(createRecentEntriesSection())
        content.add(Box.createVerticalStrut(JBUI.scale(8)))
        content.add(statusLabel)

        add(JBScrollPane(content).apply { border = JBUI.Borders.empty() }, BorderLayout.CENTER)

        loadBudgetField()
        refreshStats(MyBundle["status.ready"])
    }

    private fun createUsageEntrySection(): JComponent {
        val section = createSectionPanel(MyBundle["section.logUsage"])
        val submitButton = JButton(MyBundle["button.submitUsage"]).apply {
            addActionListener { submitUsage() }
        }

        amountField.emptyText.text = MyBundle["placeholder.amount"]
        amountField.addActionListener { submitUsage() }

        section.addRow(MyBundle["label.amount"], amountField)
        section.addFullWidth(submitButton)
        section.addFullWidth(JBLabel(MyBundle["hint.usageEntry"]))
        return section
    }

    private fun createDateRangeSection(): JComponent {
        val section = createSectionPanel(MyBundle["section.dateRange"])
        val currentMonthButton = JButton(MyBundle["button.currentMonth"]).apply {
            addActionListener {
                resetToCurrentMonth()
            }
        }

        section.addRow(MyBundle["label.startDate"], startDatePicker)
        section.addRow(MyBundle["label.endDate"], endDatePicker)
        section.addFullWidth(currentMonthButton)
        section.addFullWidth(JBLabel(MyBundle["hint.dateRange"]))
        return section
    }

    private fun createBudgetSection(): JComponent {
        val section = createSectionPanel(MyBundle["section.budget"])
        val saveButton = JButton(MyBundle["button.saveBudget"]).apply {
            addActionListener { saveBudget() }
        }
        val clearButton = JButton(MyBundle["button.clearBudget"]).apply {
            addActionListener {
                budgetField.text = ""
                saveBudget(clearValue = true)
            }
        }

        budgetField.emptyText.text = MyBundle["placeholder.budget"]
        budgetField.addActionListener { saveBudget() }

        section.addRow(MyBundle["label.monthlyBudget"], budgetField)
        section.addInlineButtons(saveButton, clearButton)
        section.addFullWidth(JBLabel(MyBundle["hint.budget"]))
        return section
    }

    private fun createStatsSection(): JComponent {
        val section = createSectionPanel(MyBundle["section.stats"])
        section.addHeaderControl(toggleUnitButton)
        section.addRow(MyBundle["stat.range"], rangeValue)
        section.addRow(MyBundle["stat.totalCredits"], totalCreditsValue)
        section.addRow(MyBundle["stat.entryCount"], entryCountValue)
        section.addRow(MyBundle["stat.activeDays"], activeDaysValue)
        section.addRow(MyBundle["stat.averagePerDay"], averagePerDayValue)
        section.addRow(MyBundle["stat.averagePerActiveDay"], averagePerActiveDayValue)
        section.addRow(MyBundle["stat.busiestDay"], busiestDayValue)
        section.addRow(MyBundle["stat.highestEntry"], highestEntryValue)
        section.addRow(MyBundle["stat.monthlyBudget"], budgetValue)
        section.addRow(MyBundle["stat.proratedBudget"], rangeBudgetValue)
        section.addRow(MyBundle["stat.remainingBudget"], remainingBudgetValue)
        section.addRow(MyBundle["stat.projectedMonthTotal"], projectedMonthValue)
        section.addRow(MyBundle["stat.runway"], runwayValue)
        section.addRow(MyBundle["stat.lastEntry"], lastEntryValue)
        return section
    }

    private fun createStorageSection(): JComponent {
        val section = createSectionPanel(MyBundle["section.storage"])
        val pathField = JBTextField(store.storageDirectory().toString()).apply {
            isEditable = false
            toolTipText = text
        }

        section.addRow(MyBundle["label.storageDirectory"], pathField)
        section.addFullWidth(JBLabel(MyBundle["hint.storage"]))
        return section
    }

    private fun createRecentEntriesSection(): JComponent {
        val section = createSectionPanel(MyBundle["section.recentEntries"])
        section.addFullWidth(JBScrollPane(recentEntriesArea).apply {
            preferredSize = JBUI.size(100, 150)
        })
        return section
    }

    private fun createDatePicker(initialDate: LocalDate): JXDatePicker =
        JXDatePicker(toDate(initialDate)).apply {
            setFormats(SimpleDateFormat("yyyy-MM-dd"))
            addActionListener { refreshStats() }
        }

    private fun submitUsage() {
        val amount = amountField.text.trim().toDoubleOrNull()
        if (amount == null || !amount.isFinite() || amount <= 0.0) {
            refreshStats(MyBundle["status.invalidAmount"])
            return
        }

        store.addUsage(amount)
        amountField.text = ""
        refreshStats(MyBundle["status.usageSaved", formatCredits(amount)])
    }

    private fun saveBudget(clearValue: Boolean = false) {
        val text = budgetField.text.trim()
        val amount = if (clearValue || text.isEmpty()) {
            null
        } else {
            text.toDoubleOrNull()
        }

        if (!clearValue && text.isNotEmpty() && (amount == null || !amount.isFinite() || amount <= 0.0)) {
            refreshStats(MyBundle["status.invalidBudget"])
            return
        }

        store.setMonthlyBudget(amount)
        budgetField.text = amount?.toString().orEmpty()
        refreshStats(if (amount == null) MyBundle["status.budgetCleared"] else MyBundle["status.budgetSaved", formatCredits(amount)])
    }

    private fun loadBudgetField() {
        budgetField.text = store.getMonthlyBudget()?.toString().orEmpty()
    }

    private fun refreshStats(statusMessage: String? = null) {
        val startDate = selectedDate(startDatePicker)
        val endDate = selectedDate(endDatePicker)
        val entries = store.getEntries()
        val stats = CreditStatsCalculator.calculate(
            entries = entries,
            startDate = startDate,
            endDate = endDate,
            monthlyBudget = store.getMonthlyBudget(),
        )

        totalCreditsValue.text = formatCredits(stats.totalCredits)
        rangeValue.text = MyBundle["value.range", stats.startDate, stats.endDate, stats.daysInRange]
        entryCountValue.text = stats.entryCount.toString()
        activeDaysValue.text = stats.activeDays.toString()
        averagePerDayValue.text = formatCredits(stats.averageCreditsPerDay)
        averagePerActiveDayValue.text = formatCredits(stats.averageCreditsPerActiveDay)
        busiestDayValue.text = stats.busiestDay?.let { day: LocalDate ->
            MyBundle["value.busiestDay", day, formatCredits(stats.busiestDayCredits)]
        } ?: MyBundle["value.notAvailable"]
        highestEntryValue.text = formatCredits(stats.highestSingleEntry)
        budgetValue.text = formatCredits(stats.monthlyBudget)
        rangeBudgetValue.text = if (stats.proratedBudgetForRange != null && stats.budgetUsedPercent != null) {
            MyBundle["value.budgetUsed", formatCredits(stats.proratedBudgetForRange), formatPercent(stats.budgetUsedPercent)]
        } else {
            MyBundle["value.notAvailable"]
        }
        remainingBudgetValue.text = formatCredits(stats.remainingBudgetForRange)
        projectedMonthValue.text = stats.projectedMonthTotal?.let { value -> formatCredits(value) }
            ?: MyBundle["value.singleMonthOnly"]
        runwayValue.text = formatRunway(stats)
        lastEntryValue.text = stats.latestEntryDate?.toString() ?: MyBundle["value.notAvailable"]
        updateRecentEntries(entries)
        statusLabel.text = statusMessage ?: MyBundle["status.summary", stats.entryCount, stats.daysInRange]
    }

    private fun updateRecentEntries(entries: List<CreditUsageEntry>) {
        recentEntriesArea.text = entries
            .sortedByDescending(CreditUsageEntry::timestamp)
            .take(10)
            .joinToString(separator = "\n") { entry ->
                val dateTime = dateTimeFormat.format(entry.timestamp.atZone(zoneId))
                "$dateTime  •  ${formatCredits(entry.amount)}"
            }
    }

    private fun formatRunway(stats: UsageStats): String {
        if (stats.monthlyBudget == null) {
            return MyBundle["value.budgetNotSet"]
        }

        val projectionMonth = stats.projectionMonth ?: return MyBundle["value.singleMonthOnly"]
        val runOutDay = stats.projectedBudgetRunOutDay
        if (runOutDay != null) {
            val runOutDate = projectionMonth.atDay(runOutDay)
            if (runOutDate.isBefore(LocalDate.now(zoneId))) {
                val overBudgetAmount = stats.projectedMonthRemaining?.let { kotlin.math.abs(it) }
                return MyBundle["value.overBudget", formatCredits(overBudgetAmount)]
            }
            return MyBundle["value.runOutDay", runOutDate]
        }

        return stats.projectedMonthRemaining
            ?.let { MyBundle["value.underBudget", formatCredits(it)] }
            ?: MyBundle["value.notAvailable"]
    }

    fun resetToCurrentMonth() {
        val today = LocalDate.now()
        startDatePicker.date = toDate(today.withDayOfMonth(1))
        endDatePicker.date = toDate(today)
        refreshStats(MyBundle["status.dateRangeReset"])
    }

    private fun selectedDate(datePicker: JXDatePicker): LocalDate =
        datePicker.date?.toInstant()?.atZone(zoneId)?.toLocalDate() ?: LocalDate.now()

    private fun toDate(localDate: LocalDate): Date =
        Date.from(localDate.atStartOfDay(zoneId).toInstant())

    private fun updateToggleButtonText() {
        toggleUnitButton.text = if (showInDollars) {
            MyBundle["button.showInCredits"]
        } else {
            MyBundle["button.showInDollars"]
        }
    }

    private fun formatCredits(value: Double?): String {
        if (value == null) return MyBundle["value.notAvailable"]
        return if (showInDollars) {
            val prefix = if (value < 0.0) "-" else ""
            val absVal = kotlin.math.abs(value)
            prefix + "$" + numberFormat.format(absVal / 100.0)
        } else {
            MyBundle["value.credits", numberFormat.format(value)]
        }
    }

    private fun formatPercent(value: Double?): String =
        value?.let { MyBundle["value.percent", numberFormat.format(it)] } ?: MyBundle["value.notAvailable"]

    private fun createSectionPanel(title: String): SectionPanel = SectionPanel(title)

    private class SectionPanel(title: String) : JPanel(GridBagLayout()) {
        private var row = 0

        init {
            alignmentX = LEFT_ALIGNMENT
            border = TitledBorder(title)
        }

        fun addRow(label: String, component: JComponent) {
            val constraints = baseConstraints()
            constraints.gridx = 0
            constraints.gridy = row
            constraints.weightx = 0.0
            add(JBLabel(label), constraints)

            val componentConstraints = baseConstraints()
            componentConstraints.gridx = 1
            componentConstraints.gridy = row
            componentConstraints.weightx = 1.0
            componentConstraints.fill = GridBagConstraints.HORIZONTAL
            add(component, componentConstraints)
            row += 1
        }

        fun addInlineButtons(vararg buttons: JButton) {
            val buttonPanel = JPanel().apply {
                layout = BoxLayout(this, BoxLayout.X_AXIS)
                buttons.forEachIndexed { index, button ->
                    if (index > 0) {
                        add(Box.createHorizontalStrut(JBUI.scale(8)))
                    }
                    add(button)
                }
            }

            addFullWidth(buttonPanel)
        }

        fun addHeaderControl(component: JComponent) {
            val headerPanel = JPanel(BorderLayout()).apply {
                isOpaque = false
                add(component, BorderLayout.EAST)
            }

            addFullWidth(headerPanel)
        }

        fun addFullWidth(component: JComponent) {
            val constraints = baseConstraints()
            constraints.gridx = 0
            constraints.gridy = row
            constraints.gridwidth = 2
            constraints.weightx = 1.0
            constraints.fill = GridBagConstraints.HORIZONTAL
            add(component, constraints)
            row += 1
        }

        private fun baseConstraints() = GridBagConstraints().apply {
            anchor = GridBagConstraints.NORTHWEST
            insets = Insets(JBUI.scale(4), JBUI.scale(4), JBUI.scale(4), JBUI.scale(4))
        }
    }
}
