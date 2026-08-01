package com.centollu.comicreader

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.centollu.comicreader.ui.history.HistoryScreen
import com.centollu.comicreader.ui.history.HistoryViewModel
import com.centollu.comicreader.ui.library.LibraryScreen
import com.centollu.comicreader.ui.library.LibraryViewModel
import com.centollu.comicreader.ui.reader.ReaderScreen
import com.centollu.comicreader.ui.reader.ReaderViewModel
import com.centollu.comicreader.ui.settings.SettingsScreen

class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate()

        checkAndRequestPermissions()

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainAppContent()
                }
            }
        }
    }

    private fun checkAndRequestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                        data = android.net.Uri.parse("package:$packageName")
                    }
                    startActivity(intent)
                } catch (e: Exception) {
                    val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                    startActivity(intent)
                }
            }
        } else {
            requestPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                )
            )
        }
    }
}

sealed class Screen(val route: String, val title: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    object Library : Screen("library", "Biblioteca", Icons.Default.LibraryBooks)
    object History : Screen("history", "Historial", Icons.Default.History)
    object Settings : Screen("settings", "Ajustes", Icons.Default.Settings)
}

@Composable
fun MainAppContent() {
    var currentTab by remember { mutableStateOf<Screen>(Screen.Library) }
    var activeReadingComicId by remember { mutableStateOf<String?>(null) }
    var activeReadingInitialPage by remember { mutableStateOf(0) }

    val libraryViewModel: LibraryViewModel = viewModel()
    val historyViewModel: HistoryViewModel = viewModel()
    val readerViewModel: ReaderViewModel = viewModel()

    val screens = listOf(Screen.Library, Screen.History, Screen.Settings)

    if (activeReadingComicId != null) {
        ReaderScreen(
            viewModel = readerViewModel,
            comicId = activeReadingComicId!!,
            initialPageIndex = activeReadingInitialPage,
            onBackClick = { activeReadingComicId = null }
        )
    } else {
        Scaffold(
            bottomBar = {
                NavigationBar {
                    screens.forEach { screen ->
                        NavigationBarItem(
                            icon = { Icon(screen.icon, contentDescription = screen.title) },
                            label = { Text(screen.title) },
                            selected = currentTab == screen,
                            onClick = { currentTab = screen }
                        )
                    }
                }
            }
        ) { paddingValues ->
            Box(modifier = Modifier.padding(paddingValues)) {
                when (currentTab) {
                    Screen.Library -> LibraryScreen(
                        viewModel = libraryViewModel,
                        onComicSelected = { comic ->
                            activeReadingInitialPage = 0
                            activeReadingComicId = comic._id
                        }
                    )
                    Screen.History -> HistoryScreen(
                        viewModel = historyViewModel,
                        onResumeReading = { comicId, pageIndex ->
                            activeReadingInitialPage = pageIndex
                            activeReadingComicId = comicId
                        }
                    )
                    Screen.Settings -> SettingsScreen()
                }
            }
        }
    }
}
