package com.cravesaver.ai

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.cravesaver.data.AppDatabase
import com.cravesaver.data.DishItem
import com.cravesaver.data.SavingRecord
import com.cravesaver.settings.AiConfigStore
import com.cravesaver.util.Notifications
import com.cravesaver.util.compressToJpeg
import com.cravesaver.util.formatCents
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.math.roundToLong

/**
 * 后台截图识别自动入账（免确认流程）：
 * 读临时图片 → AI 识别 → 直接写 Room → 发本地通知。
 * 任何失败（含图片读取 SecurityException、AI 异常）只发失败通知，不入库。
 */
class RecognizeScreenshotWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val imagePath = inputData.getString(KEY_IMAGE_PATH) ?: return Result.failure()
        val recordType = inputData.getInt(KEY_RECORD_TYPE, SavingRecord.TYPE_RESISTED)
        // 入队时的 AI 配置快照
        val config = AiConfigStore.AiConfig(
            baseUrl = inputData.getString(KEY_BASE_URL) ?: AiConfigStore.DEFAULT_BASE_URL,
            model = inputData.getString(KEY_MODEL) ?: AiConfigStore.DEFAULT_MODEL,
            apiKey = inputData.getString(KEY_API_KEY) ?: ""
        )
        val imageFile = File(imagePath)
        try {
            val jpeg = withContext(Dispatchers.IO) { compressToJpeg(imageFile) }
            val result = AiOcrClient(config).recognize(jpeg)
            val record = buildRecord(result, recordType)
            if (record == null) {
                notifyFailure()
                return Result.failure()
            }
            AppDatabase.get(applicationContext).savingRecordDao().insert(record)
            Notifications.notify(
                applicationContext,
                "已记下：${record.storeName} ${formatCents(record.totalCents)}",
                if (recordType == SavingRecord.TYPE_ATE) "吃一笔已入账" else "忍住一笔已入账"
            )
            return Result.success()
        } catch (e: Exception) {
            notifyFailure()
            return Result.failure()
        } finally {
            // 临时图片用完即删
            imageFile.delete()
        }
    }

    private fun notifyFailure() {
        Notifications.notify(applicationContext, "识别失败", "请重试或手动记一笔")
    }

    /** AI 结果 → 记录；店名/金额都识别不到（或金额为 0）视为失败返回 null */
    private fun buildRecord(result: AiOcrResult, type: Int): SavingRecord? {
        val items = result.items.orEmpty().mapNotNull { item ->
            val cents = item.price?.let { (it * 100).roundToLong() }
            if (item.name.isNullOrBlank() || cents == null) {
                null
            } else {
                DishItem(item.name.trim(), cents)
            }
        }
        val totalCents = result.total?.let { (it * 100).roundToLong() }
            ?: items.sumOf { it.priceCents }.takeIf { items.isNotEmpty() }
        if (totalCents == null || totalCents <= 0) return null
        return SavingRecord(
            storeName = result.store?.trim()?.takeIf { it.isNotBlank() } ?: "未识别店名",
            itemsJson = Json.encodeToString(items),
            totalCents = totalCents,
            type = type
        )
    }

    companion object {
        const val KEY_IMAGE_PATH = "image_path"
        const val KEY_RECORD_TYPE = "record_type"
        const val KEY_BASE_URL = "base_url"
        const val KEY_MODEL = "model"
        const val KEY_API_KEY = "api_key"

        /** 入队后台识别：临时图片路径 + 记录类型 + 当时的 AI 配置快照，尽快执行 */
        fun enqueue(context: Context, imageFile: File, recordType: Int) {
            val config = AiConfigStore(context).load()
            val request = OneTimeWorkRequestBuilder<RecognizeScreenshotWorker>()
                .setInputData(
                    workDataOf(
                        KEY_IMAGE_PATH to imageFile.absolutePath,
                        KEY_RECORD_TYPE to recordType,
                        KEY_BASE_URL to config.baseUrl,
                        KEY_MODEL to config.model,
                        KEY_API_KEY to config.apiKey
                    )
                )
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()
            WorkManager.getInstance(context).enqueue(request)
        }
    }
}
