package com.lixoo.editor

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.lixoo.editor.ui.editor.EditorScreen
import com.lixoo.editor.ui.home.HomeScreen
import com.lixoo.editor.ui.theme.LixooEditorTheme
import androidx.compose.material3.ExperimentalMaterial3Api

@ExperimentalMaterial3Api
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LixooEditorTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    NavHost(navController = navController, startDestination = "home") {
                        composable("home") {
                            HomeScreen(onNavigateToEditor = { projectId ->
                                navController.navigate("editor/$projectId")
                            })
                        }
                        composable(
                            "editor/{projectId}",
                            arguments = listOf(navArgument("projectId") { type = NavType.LongType })
                        ) { backStackEntry ->
                            val projectId = backStackEntry.arguments?.getLong("projectId") ?: -1L
                            EditorScreen(
                                projectId = projectId,
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }
        }
    }
}
