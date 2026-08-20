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
import com.cravesaver.settings.CycleConfigStore
import com.cravesaver.settings.SettingsScreen
import com.cravesaver.settings.SettingsViewModel
import com.cravesaver.ui.add.AddRecordScreen
import com.cravesaver.ui.add.AddRecordViewModel
import com.cravesaver.ui.cycle.CycleSettingsScreen
import com.cravesaver.ui.cycle.CycleSettingsViewModel
import com.cravesaver.ui.history.CycleHistoryScreen
import com.cravesaver.ui.history.CycleHistoryViewModel
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
        val cycleConfigStore = CycleConfigStore(applicationContext)
        setContent {
            CraveSaverTheme {
                AppNavHost(repository, aiConfigStore, cycleConfigStore)
            }
        }
    }
}

@Composable
fun AppNavHost(
    repository: SavingRepository,
    aiConfigStore: AiConfigStore,
    cycleConfigStore: CycleConfigStore
) {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = "home") {
        composable("home") {
            val viewModel: HomeViewModel = viewModel(factory = viewModelFactory {
                initializer { HomeViewModel(repository, cycleConfigStore) }
            })
            HomeScreen(
                viewModel = viewModel,
                onAddClick = { navController.navigate("add") },
                onHistoryClick = { navController.navigate("cycle_history") },
                onCycleSettingsClick = { navController.navigate("cycle_settings") },
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
        composable("cycle_settings") {
            val viewModel: CycleSettingsViewModel = viewModel(factory = viewModelFactory {
                initializer { CycleSettingsViewModel(cycleConfigStore) }
            })
            CycleSettingsScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }
        composable("cycle_history") {
            val viewModel: CycleHistoryViewModel = viewModel(factory = viewModelFactory {
                initializer { CycleHistoryViewModel(repository, cycleConfigStore) }
            })
            CycleHistoryScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
