package com.example.yumoflatimagemanager.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit


class OneDriveSyncWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
	override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
		// TODO: 接入 Microsoft Graph API，处理相册与云端的双向同步与冲突
		Result.success()
	}

	companion object {
		fun schedule(context: Context) {
			val request = PeriodicWorkRequestBuilder<OneDriveSyncWorker>(
				6 * 60L, // 6小时 = 360分钟
				TimeUnit.MINUTES
			).setConstraints(
				Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
			).build()
			WorkManager.getInstance(context).enqueueUniquePeriodicWork(
				"one_drive_sync",
				ExistingPeriodicWorkPolicy.UPDATE,
				request
			)
		}
	}
}


