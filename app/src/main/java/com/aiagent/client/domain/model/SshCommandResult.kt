package com.aiagent.client.domain.model

data class SshCommandResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String
)
