package com.cravesaver.settings

import android.content.Context

/**
 * AI 识别配置（OpenAI 兼容接口三元组），存 SharedPreferences，只保存在本机。
 * 默认指向硅基流动 SiliconFlow，用户可在设置页改成任何 OpenAI 兼容服务。
 */
class AiConfigStore(context: Context) {

    private val prefs = context.getSharedPreferences("ai_config", Context.MODE_PRIVATE)

    data class AiConfig(
        val baseUrl: String = DEFAULT_BASE_URL,
        val model: String = DEFAULT_MODEL,
        val apiKey: String = ""
    ) {
        /** 填了 key 才算配置完成，否则识别走本地 ML Kit */
        val isConfigured: Boolean get() = apiKey.isNotBlank()
    }

    fun load(): AiConfig = AiConfig(
        baseUrl = prefs.getString(KEY_BASE_URL, DEFAULT_BASE_URL) ?: DEFAULT_BASE_URL,
        model = prefs.getString(KEY_MODEL, DEFAULT_MODEL) ?: DEFAULT_MODEL,
        apiKey = prefs.getString(KEY_API_KEY, "") ?: ""
    )

    fun save(config: AiConfig) {
        prefs.edit()
            .putString(KEY_BASE_URL, config.baseUrl.trim())
            .putString(KEY_MODEL, config.model.trim())
            .putString(KEY_API_KEY, config.apiKey.trim())
            .apply()
    }

    companion object {
        // 默认服务：硅基流动 SiliconFlow；改默认模型只动这一行
        const val DEFAULT_BASE_URL = "https://api.siliconflow.cn/v1"
        const val DEFAULT_MODEL = "Qwen/Qwen3-VL-8B-Instruct"

        private const val KEY_BASE_URL = "base_url"
        private const val KEY_MODEL = "model"
        private const val KEY_API_KEY = "api_key"
    }
}
