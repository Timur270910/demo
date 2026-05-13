package com.aiagent.client.data.repository

import com.aiagent.client.domain.model.ConnectionStatus
import com.aiagent.client.domain.model.SshCommandResult
import com.aiagent.client.domain.repository.SshRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import org.apache.sshd.client.SshClient
import org.apache.sshd.client.channel.ClientChannel
import org.apache.sshd.client.channel.ClientShellChannel
import org.apache.sshd.client.session.ClientSession
import org.apache.sshd.common.future.OpenFuture
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

@Singleton
class SshRepositoryImpl @Inject constructor() : SshRepository {

    private val _connectionStatus = MutableStateFlow<ConnectionStatus>(ConnectionStatus.Disconnected)
    override val connectionStatus: Flow<ConnectionStatus> = _connectionStatus

    private var sshClient: SshClient? = null
    private var clientSession: ClientSession? = null

    override suspend fun connect(host: String, port: Int, username: String, password: String) {
        withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                _connectionStatus.value = ConnectionStatus.Connecting
                
                sshClient = SshClient.setUpDefaultClient().apply {
                    start()
                }

                clientSession = suspendCoroutine { continuation ->
                    val future = sshClient!!.connect(username, host, port)
                    future.addListener { openFuture: OpenFuture ->
                        try {
                            openFuture.verify()
                            val session = openFuture.session as ClientSession
                            session.addPasswordIdentity(password)
                            session.auth().addListener { authFuture ->
                                authFuture.verify()
                                continuation.resume(session)
                            }
                        } catch (e: Exception) {
                            _connectionStatus.value = ConnectionStatus.Error(e.message ?: "Connection failed")
                        }
                    }
                }

                _connectionStatus.value = ConnectionStatus.Connected
            } catch (e: Exception) {
                _connectionStatus.value = ConnectionStatus.Error(e.message ?: "Connection failed")
                disconnect()
            }
        }
    }

    override suspend fun disconnect() {
        withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                clientSession?.close(true)
                clientSession = null
                sshClient?.stop()
                sshClient = null
            } catch (e: Exception) {
                // Ignore errors during disconnect
            } finally {
                _connectionStatus.value = ConnectionStatus.Disconnected
            }
        }
    }

    override suspend fun executeCommand(command: String): SshCommandResult {
        return withContext(kotlinx.coroutines.Dispatchers.IO) {
            val session = clientSession ?: throw IllegalStateException("Not connected")
            
            val stdoutBuffer = ByteArrayOutputStream()
            val stderrBuffer = ByteArrayOutputStream()
            
            val channel = session.createExecChannel(command)
            channel.out = stdoutBuffer
            channel.err = stderrBuffer
            
            channel.open().verify()
            channel.waitFor(ClientChannel.CLOSED or ClientChannel.EXIT_STATUS, 0)
            
            val exitStatus = channel.exitStatus ?: 0
            
            SshCommandResult(
                exitCode = exitStatus,
                stdout = stdoutBuffer.toString(Charsets.UTF_8),
                stderr = stderrBuffer.toString(Charsets.UTF_8)
            )
        }
    }

    override fun streamOutput(command: String): Flow<String> = callbackFlow {
        val session = clientSession ?: throw IllegalStateException("Not connected")
        
        val channel = session.createShellChannel()
        val outputStream = ByteArrayOutputStream()
        
        channel.out = object : java.io.OutputStream() {
            override fun write(b: Int) {
                outputStream.write(b)
                trySend(outputStream.toString(Charsets.UTF_8))
                outputStream.reset()
            }
            
            override fun write(b: ByteArray, off: Int, len: Int) {
                outputStream.write(b, off, len)
                trySend(outputStream.toString(Charsets.UTF_8))
                outputStream.reset()
            }
        }
        
        channel.open().verify()
        
        // Send command
        channel.invertedOut?.write((command + "\n").toByteArray())
        channel.invertedOut?.flush()
        
        awaitClose {
            try {
                channel.close(true)
            } catch (e: Exception) {
                // Ignore close errors
            }
        }
    }

    override suspend fun listFiles(path: String): List<String> {
        return withContext(kotlinx.coroutines.Dispatchers.IO) {
            val result = executeCommand("ls -la \"$path\"")
            if (result.exitCode == 0) {
                result.stdout.split("\n").filter { it.isNotBlank() }
            } else {
                emptyList()
            }
        }
    }

    override suspend fun downloadFile(remotePath: String, localPath: String) {
        withContext(kotlinx.coroutines.Dispatchers.IO) {
            val result = executeCommand("cat \"$remotePath\"")
            if (result.exitCode == 0) {
                java.io.File(localPath).writeText(result.stdout)
            } else {
                throw Exception("Failed to download file: ${result.stderr}")
            }
        }
    }

    override suspend fun uploadFile(localPath: String, remotePath: String) {
        withContext(kotlinx.coroutines.Dispatchers.IO) {
            val content = java.io.File(localPath).readText()
            val encoded = java.util.Base64.getEncoder().encodeToString(content.toByteArray())
            val result = executeCommand("echo \"$encoded\" | base64 -d > \"$remotePath\"")
            if (result.exitCode != 0) {
                throw Exception("Failed to upload file: ${result.stderr}")
            }
        }
    }
}
