package com.cravesaver.ai

import com.cravesaver.settings.AiConfigStore.AiConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/** 菜品识别结果，price 是单价（元） */
@Serializable
data class AiOcrItem(
    val name: String? = null,
    val price: Double? = null
)

/** AI 识别结果，任何字段都可能为 null（留给用户手填） */
@Serializable
data class AiOcrResult(
    val store: String? = null,
    val items: List<AiOcrItem>? = null,
    val total: Double? = null
)

/** OpenAI 兼容接口的响应结构（只取用得上的字段） */
@Serializable
private data class ChatResponse(
    val choices: List<Choice> = emptyList()
) {
    @Serializable
    data class Choice(val message: Message? = null)

    @Serializable
    data class Message(val content: String? = null)
}

/** 通用 OpenAI 兼容多模态接口的截图识别客户端 */
class AiOcrClient(private val config: AiConfig) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * 把 JPEG 字节发给视觉模型识别。
     * 非 200、超时、解析异常等一律抛异常，由调用方降级到本地识别。
     */
    suspend fun recognize(jpegBytes: ByteArray): AiOcrResult = withContext(Dispatchers.IO) {
        val base64 = android.util.Base64.encodeToString(jpegBytes, android.util.Base64.NO_WRAP)
        val request = Request.Builder()
            .url(config.baseUrl.trimEnd('/') + "/chat/completions")
            .header("Authorization", "Bearer ${config.apiKey}")
            .post(buildRequestBody(base64).toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("AI 接口返回 HTTP ${response.code}")
            }
            val content = json.decodeFromString<ChatResponse>(
                response.body?.string() ?: throw IOException("AI 返回为空")
            )
                .choices.firstOrNull()?.message?.content
                ?: throw IOException("AI 返回格式异常")
            json.decodeFromString<AiOcrResult>(stripCodeFence(content))
        }
    }

    /** 拼 OpenAI 风格多模态请求体，只带 model/messages/max_tokens，不加其他参数 */
    private fun buildRequestBody(base64Image: String): String = buildJsonObject {
        put("model", config.model)
        put("max_tokens", 800)
        putJsonArray("messages") {
            add(buildJsonObject {
                put("role", "user")
                putJsonArray("content") {
                    add(buildJsonObject {
                        put("type", "image_url")
                        putJsonObject("image_url") {
                            put("url", "data:image/jpeg;base64,$base64Image")
                        }
                    })
                    add(buildJsonObject {
                        put("type", "text")
                        put("text", PROMPT)
                    })
                }
            })
        }
    }.toString()

    /** 模型可能用 ```json 代码围栏包裹 JSON，解析前先剥掉 */
    private fun stripCodeFence(content: String): String {
        var s = content.trim()
        if (s.startsWith("```")) {
            s = s.removePrefix("```json").removePrefix("```JSON").removePrefix("```").trim()
            s = s.removeSuffix("```").trim()
        }
        return s
    }

    companion object {
        private val PROMPT = """
这是一张外卖/电商的下单支付确认页截图。请提取信息，只返回 JSON，不要多余文字：
{"store": 店名字符串, "items": [{"name": 菜品名, "price": 单价数字}], "total": 应付总额数字}
规则：total 取页面上最终需支付的金额（合计/实付/立即支付按钮上的金额，注意有优惠时取优惠后的）；price 取每个菜品优惠后的实付单价，没有优惠价就取原价；提取不到的字段给 null。
        """.trimIndent()
    }
}
