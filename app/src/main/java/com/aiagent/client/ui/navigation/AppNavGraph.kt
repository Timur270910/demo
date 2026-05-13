package com.aiagent.client.ui.navigation

sealed class Screen(val route: String) {
    object Config : Screen("config")
    object Chat : Screen("chat")
    object FileManager : Screen("files")
    object Console : Screen("console")
}
