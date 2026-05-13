package com.aiagent.client.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiagent.client.data.security.SecureStorage
import com.aiagent.client.domain.model.ConnectionStatus
import com.aiagent.client.domain.repository.SshRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ConfigUiState(
    val sshHost: String = "",
    val sshPort: Int = 22,
    val sshUser: String = "",
    val sshPassword: String = "",
    val aiBaseUrl: String = "",
    val aiApiKey: String = "",
    val aiModel: String = "",
    val isSaving: Boolean = false,
    val saveSuccess: Boolean = false,
    val connectionStatus: ConnectionStatus = ConnectionStatus.Disconnected
)

@HiltViewModel
class ConfigViewModel @Inject constructor(
    private val secureStorage: SecureStorage,
    private val sshRepository: SshRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ConfigUiState())
    val uiState: StateFlow<ConfigUiState> = _uiState.asStateFlow()

    init {
        loadConfig()
        observeConnectionStatus()
    }

    private fun loadConfig() {
        _uiState.value = _uiState.value.copy(
            sshHost = secureStorage.getSshHost() ?: "",
            sshPort = secureStorage.getSshPort(),
            sshUser = secureStorage.getSshUser() ?: "",
            sshPassword = secureStorage.getSshPassword() ?: "",
            aiBaseUrl = secureStorage.getAiBaseUrl() ?: "",
            aiApiKey = secureStorage.getAiApiKey() ?: "",
            aiModel = secureStorage.getAiModel() ?: ""
        )
    }

    private fun observeConnectionStatus() {
        viewModelScope.launch {
            sshRepository.connectionStatus.collect { status ->
                _uiState.value = _uiState.value.copy(connectionStatus = status)
            }
        }
    }

    fun updateSshHost(host: String) {
        _uiState.value = _uiState.value.copy(sshHost = host)
    }

    fun updateSshPort(port: String) {
        _uiState.value = _uiState.value.copy(sshPort = port.toIntOrNull() ?: 22)
    }

    fun updateSshUser(user: String) {
        _uiState.value = _uiState.value.copy(sshUser = user)
    }

    fun updateSshPassword(password: String) {
        _uiState.value = _uiState.value.copy(sshPassword = password)
    }

    fun updateAiBaseUrl(url: String) {
        _uiState.value = _uiState.value.copy(aiBaseUrl = url)
    }

    fun updateAiApiKey(key: String) {
        _uiState.value = _uiState.value.copy(aiApiKey = key)
    }

    fun updateAiModel(model: String) {
        _uiState.value = _uiState.value.copy(aiModel = model)
    }

    fun saveConfig() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true)
            
            try {
                secureStorage.saveSshConfig(
                    _uiState.value.sshHost,
                    _uiState.value.sshPort,
                    _uiState.value.sshUser,
                    _uiState.value.sshPassword
                )
                secureStorage.saveAiConfig(
                    _uiState.value.aiBaseUrl,
                    _uiState.value.aiApiKey,
                    _uiState.value.aiModel
                )
                _uiState.value = _uiState.value.copy(isSaving = false, saveSuccess = true)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isSaving = false, saveSuccess = false)
            }
        }
    }

    fun connect() {
        viewModelScope.launch {
            sshRepository.connect(
                _uiState.value.sshHost,
                _uiState.value.sshPort,
                _uiState.value.sshUser,
                _uiState.value.sshPassword
            )
        }
    }

    fun disconnect() {
        viewModelScope.launch {
            sshRepository.disconnect()
        }
    }

    fun resetSaveSuccess() {
        _uiState.value = _uiState.value.copy(saveSuccess = false)
    }
}
