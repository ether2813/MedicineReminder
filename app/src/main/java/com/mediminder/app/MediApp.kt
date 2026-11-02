package com.mediminder.app

import android.app.Application
import android.content.Context
import com.mediminder.app.data.BackupManager
import com.mediminder.app.data.MedicineRepository
import com.mediminder.app.data.SettingsRepository
import com.mediminder.app.data.db.AppDatabase
import com.mediminder.app.reminder.NotificationHelper
import com.mediminder.app.reminder.ReminderScheduler
import com.mediminder.app.util.LocaleHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class MediApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.settings.applyThemeMode()
        LocaleHelper.applyOnStart(this)
        NotificationHelper.createChannels(this)
        container.appScope.launch { ReminderScheduler.rescheduleAll(this@MediApp) }
    }
}

class AppContainer(context: Context) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val database: AppDatabase = AppDatabase.build(context)
    val settings = SettingsRepository(context)
    val repository = MedicineRepository(database)
    val backupManager = BackupManager(context, repository, settings)
}

val Context.container: AppContainer get() = (applicationContext as MediApp).container
