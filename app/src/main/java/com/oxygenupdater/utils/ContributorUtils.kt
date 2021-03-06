package com.oxygenupdater.utils

import android.content.Context
import androidx.core.os.bundleOf
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequest.MIN_PERIODIC_INTERVAL_MILLIS
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.google.firebase.analytics.FirebaseAnalytics
import com.oxygenupdater.internal.settings.SettingsManager
import com.oxygenupdater.internal.settings.SettingsManager.Companion.PROPERTY_CONTRIBUTE
import com.oxygenupdater.workers.CheckSystemUpdateFilesWorker
import com.oxygenupdater.workers.WORK_UNIQUE_CHECK_SYSTEM_UPDATE_FILES
import org.koin.java.KoinJavaComponent.inject
import java.util.concurrent.TimeUnit

/**
 * @author [Adhiraj Singh Chauhan](https://github.com/adhirajsinghchauhan)
 * @author [Arjan Vlek](https://github.com/arjanvlek)
 */
class ContributorUtils(private val context: Context) {

    private val workManager by inject(WorkManager::class.java)
    private val settingsManager by inject(SettingsManager::class.java)

    fun flushSettings(isContributing: Boolean) {
        val isFirstTime = !settingsManager.containsPreference(PROPERTY_CONTRIBUTE)
        val wasContributing = settingsManager.getPreference(PROPERTY_CONTRIBUTE, false)

        if (isFirstTime || wasContributing != isContributing) {
            settingsManager.savePreference(PROPERTY_CONTRIBUTE, isContributing)

            val analytics = FirebaseAnalytics.getInstance(context)

            val analyticsEventData = bundleOf(
                "CONTRIBUTOR_DEVICE" to settingsManager.getPreference(SettingsManager.PROPERTY_DEVICE, "<<UNKNOWN>>"),
                "CONTRIBUTOR_UPDATEMETHOD" to settingsManager.getPreference(SettingsManager.PROPERTY_UPDATE_METHOD, "<<UNKNOWN>>")
            )

            if (isContributing) {
                analytics.logEvent("CONTRIBUTOR_SIGNUP", analyticsEventData)
                startFileCheckingProcess()
            } else {
                analytics.logEvent("CONTRIBUTOR_SIGNOFF", analyticsEventData)
                stopFileCheckingProcess()
            }
        }
    }

    private fun startFileCheckingProcess() {
        val periodicWorkRequest = PeriodicWorkRequestBuilder<CheckSystemUpdateFilesWorker>(
            MIN_PERIODIC_INTERVAL_MILLIS,
            TimeUnit.MILLISECONDS
        ).setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()

        workManager.enqueueUniquePeriodicWork(
            WORK_UNIQUE_CHECK_SYSTEM_UPDATE_FILES,
            ExistingPeriodicWorkPolicy.REPLACE,
            periodicWorkRequest
        )
    }

    private fun stopFileCheckingProcess() = workManager.cancelUniqueWork(
        WORK_UNIQUE_CHECK_SYSTEM_UPDATE_FILES
    )
}
