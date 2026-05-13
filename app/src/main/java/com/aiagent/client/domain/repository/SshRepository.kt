package com.aiagent.client.domain.repository

import com.aiagent.client.domain.model.ConnectionStatus
import com.aiagent.client.domain.model.SshCommandResult
import kotlinx.coroutines.flow.Flow

interface SshRepository {
    val connectionStatus: Flow<ConnectionStatus>
    
    suspend fun connect(host: String, port: Int, username: String, password: String)
    suspend fun disconnect()
    suspend fun executeCommand(command: String): SshCommandResult
    fun streamOutput(command: String): Flow<String>
    suspend fun listFiles(path: String): List<String>
    suspend fun downloadFile(remotePath: String, localPath: String)
    suspend fun uploadFile(localPath: String, remotePath: String)
}
