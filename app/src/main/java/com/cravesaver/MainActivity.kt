package com.cravesaver

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.cravesaver.data.AppDatabase
import com.cravesaver.data.SavingRepository
import com.cravesaver.settings.AiConfigStore
import com.cravesaver.settings.SettingsScreen
import com.cravesaver.settings.SettingsViewModel
import com.cravesaver.ui.add.AddRecordScreen
import com.cravesaver.ui.add.AddRecordViewModel
import com.cravesaver.ui.home.HomeScreen
import com.cravesaver.ui.home.HomeViewModel
import com.cravesaver.ui.theme.CraveSaverTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // 手动依赖注入：数据库 → 仓库 → ViewModel，保持简单，不引入 Hilt
        val repository = SavingRepository(AppDatabase.get(applicationContext).savingRecordDao())
        val aiConfigStore = AiConfigStore(applicationContext)
        setContent {
            CraveSaverTheme {
                AppNavHost(repository, aiConfigStore)
            }
        }
    }
}

@Composable
fun AppNavHost(repository: SavingRepository, aiConfigStore: AiConfigStore) {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = "home") {
        composable("home") {
            val viewModel: HomeViewModel = viewModel(factory = viewModelFactory {
                initializer { HomeViewModel(repository) }
            })
            HomeScreen(
                viewModel = viewModel,
                onAddClick = { navController.navigate("add") },
                onSettingsClick = { navController.navigate("settings") }
            )
        }
        composable("add") {
            val viewModel: AddRecordViewModel = viewModel(factory = viewModelFactory {
                initializer { AddRecordViewModel(repository, aiConfigStore) }
            })
            AddRecordScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }
        composable("settings") {
            val viewModel: SettingsViewModel = viewModel(factory = viewModelFactory {
                initializer { SettingsViewModel(aiConfigStore) }
            })
            SettingsScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
