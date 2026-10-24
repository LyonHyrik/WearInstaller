package com.lyon.wearinstaller.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import com.lyon.wearinstaller.AppContainer
import com.lyon.wearinstaller.R
import com.lyon.wearinstaller.data.AppSettings
import com.lyon.wearinstaller.data.ConnectionStatus
import com.lyon.wearinstaller.data.JobState
import com.lyon.wearinstaller.data.PendingInstall
import com.lyon.wearinstaller.data.UiMessage
import com.lyon.wearinstaller.ui.components.SectionCard
import com.lyon.wearinstaller.ui.screens.ConfirmScreen
import com.lyon.wearinstaller.ui.screens.DevOptionsScreen
import com.lyon.wearinstaller.ui.screens.AboutScreen
import com.lyon.wearinstaller.ui.screens.HomeScreen
import com.lyon.wearinstaller.ui.screens.JobScreen
import com.lyon.wearinstaller.ui.screens.PAGE_DOTS_HEIGHT
import com.lyon.wearinstaller.ui.screens.SettingsScreen
import kotlinx.coroutines.delay

private object Routes {
    const val HOME = "home"
    const val CONFIRM = "confirm"
    const val JOB = "job"
    const val DEV_OPTIONS = "devOptions"
}

private const val PAGE_COUNT = 3
private const val PAGE_HOME = 0
private const val PAGE_SETTINGS = 1
private const val PAGE_ABOUT = 2

@Composable
fun WearInstallerRoot(
    container: AppContainer,
    incomingIntent: Intent?,
    versionName: String,
) {
    val coordinator = container.coordinator
    val settingsRepository = container.settings
    val state by coordinator.state.collectAsState()
    val settings by settingsRepository.settings.collectAsState()
    val navController = rememberSwipeDismissableNavController()
    val context = LocalContext.current
    val viaIntent = state.externalLaunch

    remember { container.keyStore.ensure() }

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri != null) coordinator.prepareFromUri(uri)
    }

    LaunchedEffect(incomingIntent) {
        if (incomingIntent != null) coordinator.handleIntent(incomingIntent)
    }

    LaunchedEffect(Unit) {
        while (true) {
            coordinator.testConnection()
            val connected = coordinator.state.value.connection is com.lyon.wearinstaller.data.ConnectionStatus.Online
            kotlinx.coroutines.delay(if (connected) 1000 else 5000)
        }
    }

    LaunchedEffect(state.pending) {
        if (state.pending != null) navController.navigateOnce(Routes.CONFIRM)
    }

    // 安装进度/结果直接复用确认页，不再跳转到独立的 Job 页面

    LaunchedEffect(state.needsDevOptions) {
        if (!state.needsDevOptions) return@LaunchedEffect
        if (settings.autoOpenDevOptions) {
            openDeveloperOptions(context)
            coordinator.consumeDevOptionsRequest()
        }
    }

    AppScaffold(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize()) {
            androidx.wear.compose.foundation.HierarchicalFocusCoordinator(requiresFocus = { true }) {
                SwipeDismissableNavHost(
                    navController = navController,
                    startDestination = Routes.HOME,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    composable(Routes.HOME) {
                        TopLevelPager(
                            connection = state.connection,
                            pending = state.pending,
                            debugAddress = settings.debugAddress,
                            settings = settings,
                            versionName = versionName,
                            onPickFile = {
                                runCatching {
                                    picker.launch(arrayOf("application/vnd.android.package-archive", "*/*"))
                                }.onFailure { coordinator.showMessage(UiMessage.NoFilePicker) }
                            },
                            onUpdateSettings = settingsRepository::update,
                                )
                    }

                    composable(Routes.CONFIRM) {
                        val pending = state.pending
                        if (pending == null && state.job is JobState.Idle) {
                            LaunchedEffect(Unit) { navController.popBackStack() }
                        } else {
                            ConfirmScreen(
                                pending = pending,
                                job = state.job,
                                onInstall = { coordinator.startInstall() },
                                onCancel = {
                                    coordinator.cancelInstall()
                                },
                                onCancelIdle = {
                                    coordinator.resetJob()
                                    if (viaIntent) context.findActivity()?.finish() else navController.popBackStack()
                                },
                                onRetry = {
                                    coordinator.resetJob()
                                    coordinator.startInstall()
                                },
                                onBackHome = {
                                    coordinator.resetJob()
                                    navController.popBackStack()
                                },
                                onExitApp = {
                                    coordinator.resetJob()
                                    if (viaIntent) context.findActivity()?.finish() else navController.popBackStack()
                                },
                            )
                        }
                    }

                    composable(Routes.JOB) {
                        JobScreen(
                            job = state.job,
                            onCancel = {
                                coordinator.cancelInstall()
                            },
                            onRetry = {
                                coordinator.resetJob()
                                coordinator.startInstall()
                            },
                            onOpenDevOptions = { openDeveloperOptions(context) },
                            onBackHome = {
                                coordinator.resetJob()
                                navController.popBackStack()
                            },
                            onExitApp = {
                                coordinator.resetJob()
                                if (viaIntent) context.findActivity()?.finish() else navController.popBackStack()
                            },
                        )
                    }

                    composable(Routes.DEV_OPTIONS) {
                        DevOptionsScreen(
                            onOpen = { openDeveloperOptions(context) },
                            onDismiss = {
                                coordinator.consumeDevOptionsRequest()
                                navController.popBackStack()
                            },
                        )
                    }
                }
                }

            MessageBanner(
                message = state.message,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(12.dp),
                onDismissed = coordinator::consumeMessage,
            )
        }
    }
}

private fun NavHostController.navigateOnce(route: String) {
    if (currentDestination?.route == route) return
    navigate(route) { launchSingleTop = true }
}

@Composable
private fun TopLevelPager(
    connection: ConnectionStatus,
    pending: PendingInstall?,
    debugAddress: String,
    settings: AppSettings,
    versionName: String,
    onPickFile: () -> Unit,
    onUpdateSettings: ((AppSettings) -> AppSettings) -> Unit,
) {
    val pagerState = rememberPagerState(pageCount = { PAGE_COUNT })
    val bottomPadding = androidx.compose.foundation.layout.PaddingValues(bottom = PAGE_DOTS_HEIGHT)

    Box(modifier = Modifier.fillMaxSize()) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            when (page) {
                PAGE_HOME -> HomeScreen(
                    connection = connection,
                    pending = pending,
                    debugAddress = debugAddress,
                    onPickFile = onPickFile,
                    bottomPadding = bottomPadding,
                )
                PAGE_SETTINGS -> SettingsScreen(
                    settings = settings,
                    versionName = versionName,
                    onUpdate = onUpdateSettings,
                    bottomPadding = bottomPadding,
                )
                else -> AboutScreen(
                    versionName = versionName,
                )
            }
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            repeat(PAGE_COUNT) { index ->
                Box(
                    modifier = Modifier
                        .size(if (index == pagerState.currentPage) 6.dp else 4.dp)
                        .background(
                            if (index == pagerState.currentPage) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                            CircleShape,
                        ),
                )
            }
        }
    }
}

@Composable
private fun MessageBanner(
    message: UiMessage?,
    modifier: Modifier = Modifier,
    onDismissed: () -> Unit,
) {
    val text = when (message) {
        null -> return
        is UiMessage.Text -> message.value
        UiMessage.InvalidApk -> stringResource(R.string.error_apk_invalid)
        UiMessage.NoFilePicker -> stringResource(R.string.error_picker)
        UiMessage.PairCodeMissing -> stringResource(R.string.error_pair_code)
        UiMessage.PairFailed -> stringResource(R.string.error_pair)
        UiMessage.AddressInvalid -> stringResource(R.string.error_address)
    }

    LaunchedEffect(message) {
        delay(3200)
        onDismissed()
    }

    SectionCard(modifier = modifier) {
        Text(text = text)
    }
}

private fun openDeveloperOptions(context: Context) {
    val candidates = listOf(
        Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS),
        Intent().setClassName("com.huawei.watch.settings", "com.huawei.watch.settings.common.activity.WatchPreferenceActivity"),
        Intent().setClassName("com.huawei.watch.settings", "com.huawei.watch.settings.main.SettingsMainActivity"),
        Intent(Settings.ACTION_SETTINGS),
    )
    for (intent in candidates) {
        val i = intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (runCatching { context.startActivity(i) }.isSuccess) return
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
