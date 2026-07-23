package com.github.vladislavekimtcov.creditpincher.toolWindow

import com.github.vladislavekimtcov.creditpincher.MyBundle
import com.github.vladislavekimtcov.creditpincher.model.CreditUsageEntry
import com.github.vladislavekimtcov.creditpincher.model.UsageStats
import com.github.vladislavekimtcov.creditpincher.services.CreditStatsCalculator
import com.github.vladislavekimtcov.creditpincher.services.CreditUsageStore
import com.github.vladislavekimtcov.creditpincher.services.GitBackupService
import com.github.vladislavekimtcov.creditpincher.services.SwingGitConflictResolver
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
import java.awt.Dimension
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import java.awt.Rectangle
import javax.swing.Scrollable
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
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
    private val statusLabel = WrappingLabel(" ")
    private val totalCreditsValue = WrappingLabel()
    private val rangeValue = WrappingLabel()
    private val entryCountValue = WrappingLabel()
    private val activeDaysValue = WrappingLabel()
    private val averagePerDayValue = WrappingLabel()
    private val averagePerActiveDayValue = WrappingLabel()
    private val busiestDayValue = WrappingLabel()
    private val highestEntryValue = WrappingLabel()
    private val budgetValue = WrappingLabel()
    private val rangeBudgetValue = WrappingLabel()
    private val remainingBudgetValue = WrappingLabel()
    private val projectedMonthValue = WrappingLabel()
    private val runwayValue = WrappingLabel()
    private val lastEntryValue = WrappingLabel()
    private val recentEntriesArea = JBTextArea()
    private val usageBarChart = UsageBarChart()

    private val gitService by lazy { GitBackupService(store.storageDirectory(), SwingGitConflictResolver(project)) }
    private val remoteUrlField = JBTextField()
    private val connectGitButton = JButton()
    private val commitPushButton = JButton()
    private val gitStatusLabel = WrappingLabel(" ")
    private val gitConnectPanel = JPanel()
    private val gitBackupPanel = JPanel()

    init {
        border = JBUI.Borders.empty(8)

        toggleUnitButton.addActionListener {
            showInDollars = !showInDollars
            updateToggleButtonText()
            refreshStats()
        }
        updateToggleButtonText()

        recentEntriesArea.isEditable = false
        recentEntriesArea.lineWrap = true
        recentEntriesArea.wrapStyleWord = true
        recentEntriesArea.rows = 8
        recentEntriesArea.emptyText.text = MyBundle["recentEntries.empty"]

        val content = ScrollablePanel()
        content.add(createUsageEntrySection())
        content.add(Box.createVerticalStrut(JBUI.scale(8)))
        content.add(createDateRangeSection())
        content.add(Box.createVerticalStrut(JBUI.scale(8)))
        content.add(createBudgetSection())
        content.add(Box.createVerticalStrut(JBUI.scale(8)))
        content.add(createStatsSection())
        content.add(Box.createVerticalStrut(JBUI.scale(8)))
        content.add(createBarChartSection())
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
        section.addFullWidth(WrappingLabel(MyBundle["hint.usageEntry"]))
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
        section.addFullWidth(WrappingLabel(MyBundle["hint.dateRange"]))
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
        section.addFullWidth(WrappingLabel(MyBundle["hint.budget"]))
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
            minimumSize = Dimension(JBUI.scale(50), minimumSize.height)
        }

        section.addRow(MyBundle["label.storageDirectory"], pathField)
        section.addFullWidth(WrappingLabel(MyBundle["hint.storage"]))
        section.addFullWidth(createGitPanel())
        return section
    }

    private fun createGitPanel(): JComponent {
        remoteUrlField.emptyText.text = MyBundle["placeholder.gitRemoteUrl"]

        connectGitButton.text = MyBundle["button.connectGit"]
        connectGitButton.addActionListener { connectGitRepository() }

        commitPushButton.text = MyBundle["button.commitPushGit"]
        commitPushButton.addActionListener { commitAndPushGit() }

        val urlRow = JPanel(BorderLayout(JBUI.scale(8), 0)).apply {
            isOpaque = false
            alignmentX = Component.LEFT_ALIGNMENT
            add(JBLabel(MyBundle["label.gitRemoteUrl"]), BorderLayout.WEST)
            add(remoteUrlField, BorderLayout.CENTER)
        }

        gitConnectPanel.layout = BoxLayout(gitConnectPanel, BoxLayout.Y_AXIS)
        gitConnectPanel.isOpaque = false
        gitConnectPanel.alignmentX = Component.LEFT_ALIGNMENT
        gitConnectPanel.add(urlRow)
        gitConnectPanel.add(Box.createVerticalStrut(JBUI.scale(4)))
        gitConnectPanel.add(connectGitButton.apply { alignmentX = Component.LEFT_ALIGNMENT })
        gitConnectPanel.add(Box.createVerticalStrut(JBUI.scale(4)))
        gitConnectPanel.add(WrappingLabel(MyBundle["hint.gitConnect"]).apply { alignmentX = Component.LEFT_ALIGNMENT })

        gitBackupPanel.layout = BoxLayout(gitBackupPanel, BoxLayout.Y_AXIS)
        gitBackupPanel.isOpaque = false
        gitBackupPanel.alignmentX = Component.LEFT_ALIGNMENT
        gitBackupPanel.add(commitPushButton.apply { alignmentX = Component.LEFT_ALIGNMENT })
        gitBackupPanel.add(Box.createVerticalStrut(JBUI.scale(4)))
        gitBackupPanel.add(WrappingLabel(MyBundle["hint.gitBackup"]).apply { alignmentX = Component.LEFT_ALIGNMENT })

        val container = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            isOpaque = false
            alignmentX = Component.LEFT_ALIGNMENT
            add(TitledBorder(MyBundle["section.gitBackup"]).let { titled ->
                JPanel(BorderLayout()).apply {
                    isOpaque = false
                    border = titled
                    alignmentX = Component.LEFT_ALIGNMENT
                    add(JPanel().apply {
                        layout = BoxLayout(this, BoxLayout.Y_AXIS)
                        isOpaque = false
                        add(gitStatusLabel.apply { alignmentX = Component.LEFT_ALIGNMENT })
                        add(Box.createVerticalStrut(JBUI.scale(4)))
                        add(gitConnectPanel)
                        add(gitBackupPanel)
                    }, BorderLayout.CENTER)
                }
            })
        }

        refreshGitState()
        return container
    }

    private fun refreshGitState() {
        connectGitButton.isEnabled = false
        commitPushButton.isEnabled = false
        gitStatusLabel.text = MyBundle["status.gitChecking"]

        ApplicationManager.getApplication().executeOnPooledThread {
            val isRepo = gitService.isGitRepository()
            ApplicationManager.getApplication().invokeLater {
                gitConnectPanel.isVisible = !isRepo
                gitBackupPanel.isVisible = isRepo
                gitStatusLabel.text = if (isRepo) MyBundle["status.gitConnected"] else MyBundle["status.gitNotConnected"]
                connectGitButton.isEnabled = true
                commitPushButton.isEnabled = true
                revalidate()
                repaint()
            }
        }
    }

    private fun connectGitRepository() {
        val url = remoteUrlField.text.trim()
        if (url.isEmpty()) {
            gitStatusLabel.text = MyBundle["status.gitInvalidUrl"]
            return
        }

        connectGitButton.isEnabled = false
        gitStatusLabel.text = MyBundle["status.gitConnecting"]

        ApplicationManager.getApplication().executeOnPooledThread {
            val result = gitService.connectToRemote(url)
            ApplicationManager.getApplication().invokeLater {
                gitStatusLabel.text = if (result.success) {
                    MyBundle["status.gitConnectSuccess"]
                } else {
                    MyBundle["status.gitConnectFailure", result.output]
                }
                connectGitButton.isEnabled = true
                refreshGitState()
            }
        }
    }

    private fun commitAndPushGit() {
        commitPushButton.isEnabled = false
        gitStatusLabel.text = MyBundle["status.gitPushing"]

        ApplicationManager.getApplication().executeOnPooledThread {
            val result = gitService.commitAndPush(
                onStatusUpdate = { message ->
                    ApplicationManager.getApplication().invokeLater {
                        gitStatusLabel.text = message
                    }
                },
            )
            ApplicationManager.getApplication().invokeLater {
                gitStatusLabel.text = when {
                    result.success -> MyBundle["status.gitPushSuccess"]
                    result.conflict -> MyBundle["status.gitPushConflict", result.output]
                    else -> MyBundle["status.gitPushFailure", result.output]
                }
                commitPushButton.isEnabled = true
            }
        }
    }

    private fun createBarChartSection(): JComponent {
        val section = createSectionPanel(MyBundle["section.barChart"])
        section.addFullWidth(usageBarChart)
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

        // Aggregate daily usages for the bar chart
        val normalizedStart = minOf(startDate, endDate)
        val normalizedEnd = maxOf(startDate, endDate)
        val daysCount = ChronoUnit.DAYS.between(normalizedStart, normalizedEnd) + 1
        val filteredEntries = entries.filter { entry ->
            val entryDate = entry.localDate(zoneId)
            !entryDate.isBefore(normalizedStart) && !entryDate.isAfter(normalizedEnd)
        }
        val creditsByDay = filteredEntries
            .groupBy { it.localDate(zoneId) }
            .mapValues { (_, dayEntries) -> dayEntries.sumOf(CreditUsageEntry::amount) }
        val dailyUsages = (0 until daysCount).map { i ->
            val date = normalizedStart.plusDays(i)
            UsageBarChart.DailyUsage(date, creditsByDay[date] ?: 0.0)
        }
        usageBarChart.updateData(dailyUsages, showInDollars)

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
        recentEntriesArea.caretPosition = 0
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

    private class ScrollablePanel : JPanel(), Scrollable {
        init {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
        }
        override fun getPreferredScrollableViewportSize(): Dimension = preferredSize
        override fun getScrollableUnitIncrement(visibleRect: Rectangle?, orientation: Int, direction: Int): Int = 16
        override fun getScrollableBlockIncrement(visibleRect: Rectangle?, orientation: Int, direction: Int): Int = 32
        override fun getScrollableTracksViewportWidth(): Boolean = true
        override fun getScrollableTracksViewportHeight(): Boolean = false
    }

    private class WrappingLabel(text: String = "") : JBTextArea(text) {
        init {
            lineWrap = true
            wrapStyleWord = true
            isEditable = false
            isFocusable = false
            isOpaque = false
            border = null
            font = JBUI.Fonts.label()
        }
        override fun getPreferredSize(): Dimension {
            val parentWidth = parent?.width ?: 100
            val insets = parent?.insets
            val padding = (insets?.left ?: 0) + (insets?.right ?: 0) + 10
            val width = maxOf(50, parentWidth - padding)
            
            val oldSize = size
            setSize(width, 10000)
            val prefHeight = getUI().getPreferredSize(this).height
            size = oldSize
            return Dimension(10, prefHeight)
        }
        override fun getMinimumSize(): Dimension = Dimension(10, preferredSize.height)
    }
}
