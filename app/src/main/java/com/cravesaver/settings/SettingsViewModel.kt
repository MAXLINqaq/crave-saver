package com.cravesaver.settings

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class SettingsUiState(
    val baseUrl: String = "",
    val model: String = "",
    val apiKey: String = "",
    /** 刚保存成功，用于界面提示 */
    val justSaved: Boolean = false
)

class SettingsViewModel(private val store: AiConfigStore) : ViewModel() {

    private val _uiState = MutableStateFlow(store.load().let {
        SettingsUiState(baseUrl = it.baseUrl, model = it.model, apiKey = it.apiKey)
    })
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    fun onBaseUrlChange(value: String) = _uiState.update { it.copy(baseUrl = value, justSaved = false) }

    fun onModelChange(value: String) = _uiState.update { it.copy(model = value, justSaved = false) }

    fun onApiKeyChange(value: String) = _uiState.update { it.copy(apiKey = value, justSaved = false) }

    /** 保存三元组；Base URL / 模型留空则回落到默认值，key 留空即清除 */
    fun save() {
        val s = _uiState.value
        store.save(
            AiConfigStore.AiConfig(
                baseUrl = s.baseUrl.ifBlank { AiConfigStore.DEFAULT_BASE_URL },
                model = s.model.ifBlank { AiConfigStore.DEFAULT_MODEL },
                apiKey = s.apiKey
            )
        )
        _uiState.update {
            it.copy(
                baseUrl = s.baseUrl.ifBlank { AiConfigStore.DEFAULT_BASE_URL },
                model = s.model.ifBlank { AiConfigStore.DEFAULT_MODEL },
                justSaved = true
            )
        }
    }
}
