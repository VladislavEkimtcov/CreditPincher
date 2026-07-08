package com.github.vladislavekimtcov.creditpincher.startup

import com.github.vladislavekimtcov.creditpincher.MyBundle
import com.github.vladislavekimtcov.creditpincher.services.CreditUsageStore
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

class MyProjectActivity : ProjectActivity {

    override suspend fun execute(project: Project) {
        val store = ApplicationManager.getApplication().getService(CreditUsageStore::class.java)
        store.initialize()
        thisLogger().info(MyBundle["startup.storageReady", store.storageDirectory()])
    }
}