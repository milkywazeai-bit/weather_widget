package com.milkywaze.weatherwidget

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

class RefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val prefs = Prefs(applicationContext)
        val models = prefs.selectedModels
        if (models.isEmpty()) {
            prefs.clearCache()
            CompareWidgetProvider.updateAll(applicationContext)
            return Result.success()
        }
        prefs.isRefreshing = true
        CompareWidgetProvider.updateAll(applicationContext)
        return try {
            val json = OpenMeteoClient.fetchForecastJson(prefs.location, models)
            val now = System.currentTimeMillis()
            OpenMeteoParser.parse(json, models, now) // validate before caching
            prefs.saveCache(json, models, now)
            Result.success()
        } catch (e: Exception) {
            prefs.lastError = e.message ?: e.javaClass.simpleName
            if (runAttemptCount < 2) Result.retry() else Result.failure()
        } finally {
            prefs.isRefreshing = false
            CompareWidgetProvider.updateAll(applicationContext)
        }
    }

    companion object {
        private const val PERIODIC = "weather_compare_periodic"
        private const val ONE_TIME = "weather_compare_now"

        private val networkConstraint = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        fun schedulePeriodic(context: Context) {
            val request = PeriodicWorkRequestBuilder<RefreshWorker>(1, TimeUnit.HOURS)
                .setConstraints(networkConstraint)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        fun refreshNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<RefreshWorker>()
                .setConstraints(networkConstraint)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(ONE_TIME, ExistingWorkPolicy.REPLACE, request)
        }

        fun cancelAll(context: Context) {
            WorkManager.getInstance(context).run {
                cancelUniqueWork(PERIODIC)
                cancelUniqueWork(ONE_TIME)
            }
        }
    }
}
