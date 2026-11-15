package com.audioassistant.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.audioassistant.ui.components.AppGlyph
import com.audioassistant.ui.components.AppIcon
import com.audioassistant.ui.components.EditorOptions
import com.audioassistant.ui.components.ExportOptions
import com.audioassistant.ui.components.HomeAppBar
import com.audioassistant.ui.components.StateBanner
import com.audioassistant.ui.components.SuccessOptions
import com.audioassistant.ui.components.TextAction
import com.audioassistant.ui.components.VideoNameOptions
import com.audioassistant.ui.navigation.AppBottomBar
import com.audioassistant.ui.navigation.AppMotion
import com.audioassistant.ui.navigation.Destination
import com.audioassistant.ui.navigation.EDITOR_ROUTE
import com.audioassistant.ui.screens.EditWorkspaceScreen
import com.audioassistant.ui.screens.EditorPresentation
import com.audioassistant.ui.screens.EditorScreen
import com.audioassistant.ui.screens.HomeScreen
import com.audioassistant.ui.screens.LibraryScreen
import com.audioassistant.ui.screens.VideoScreen
import com.audioassistant.ui.theme.AppSheetShape
import com.audioassistant.ui.theme.AppSpacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioAssistantApp(workspace: WorkspaceViewModel = viewModel()) {
    // ViewModel 属于 Activity，绝不在每个 composable 路由内重新创建队列。
    val state by workspace.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { withContext(Dispatchers.Default) { AppIcon.entries.forEach { it.vector } } }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { workspace.editor.pauseForBackground() }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (state.ready && !state.isWorking && state.songs.isNotEmpty()) workspace.refreshSources()
    }
    var relinkSongId by rememberSaveable { mutableStateOf<String?>(null) }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { workspace.importVideos(it) }
    val videoFiles = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { workspace.importVideos(it) }
    val audioFiles = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { workspace.importSongs(it) }
    val relinkFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val songId = relinkSongId
        relinkSongId = null
        if (uri != null && songId != null) workspace.replaceSongSource(songId, uri)
    }
    fun launchPicker(action: () -> Unit) {
        try { action() } catch (_: Exception) { workspace.reportError("无法打开系统选择器，请确认设备上有可用的文件选择应用后重试。") }
    }
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route ?: Destination.HOME.route
    val inEditor = route == EDITOR_ROUTE
    val editorSong = if (inEditor) state.songs.firstOrNull { it.id == entry?.arguments?.getString("songId") } else null
    val current = Destination.entries.firstOrNull { it.route == route } ?: Destination.HOME

    fun navigateTo(destination: Destination) {
        if (route == destination.route) return
        nav.navigate(destination.route) {
            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }
    fun exitEditor() {
        // 项目列表/设置已逐次自动保存；阶段 4 接入剪辑后须复用同一写入队列。
        workspace.editor.close { nav.popBackStack() }
    }


    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            if (!inEditor && current == Destination.HOME) HomeAppBar { workspace.unavailable("设置") }
            else TopAppBar(
                title = {
                    Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.small), verticalAlignment = Alignment.CenterVertically) {
                        if (!inEditor) AppGlyph(current.icon, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                        Text(if (inEditor) "单曲裁剪" else current.title, style = MaterialTheme.typography.headlineSmall)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                navigationIcon = {
                    if (inEditor) IconButton(onClick = { exitEditor() }) { AppGlyph(AppIcon.BACK, "返回歌曲列表") }
                },
                actions = {
                    if (inEditor && editorSong != null && editorSong.error == null) TextAction("更多编辑", AppIcon.TUNE, { workspace.showSheet(AppSheet.MORE_EDIT) })
                },
            )
        },
        bottomBar = {
            if (!inEditor) AppBottomBar(current, !state.isReordering) { navigateTo(it) }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
            StateBanner(state.loadingMessage, state.errorMessage, workspace::dismissError)
            NavHost(navController = nav, startDestination = Destination.HOME.route, modifier = Modifier.weight(1f),
                enterTransition = { AppMotion.enter() }, exitTransition = { AppMotion.exit() },
                popEnterTransition = { AppMotion.enter() }, popExitTransition = { AppMotion.exit() },
            ) {
                composable(Destination.HOME.route) {
                    HomeScreen(state.songs.size, { navigateTo(Destination.VIDEO) }, { navigateTo(Destination.EDIT) })
                }
                composable(Destination.VIDEO.route) {
                    VideoScreen(
                        videos = state.videos, enabled = state.ready && !state.isWorking,
                        onPhoto = { launchPicker { photoPicker.launch(PickVisualMediaRequest(mediaType = ActivityResultContracts.PickVisualMedia.VideoOnly)) } },
                        onFiles = { launchPicker { videoFiles.launch(arrayOf("video/*")) } },
                        onSettings = { workspace.showSheet(AppSheet.VIDEO_NAMES) },
                        onRemove = workspace::removeVideo, onRetry = workspace::retryVideo,
                        processing = state.processing, onCancel = workspace::cancelProcessing,
                    )
                }
                composable(Destination.EDIT.route) {
                    val preview by workspace.editor.workspaceState.collectAsStateWithLifecycle()
                    EditWorkspaceScreen(
                        songs = state.songs, enabled = state.ready && !state.isWorking && !preview.busy, cancelDragSignal = state.cancelDragSignal,
                        onImport = { launchPicker { audioFiles.launch(arrayOf("audio/*")) } },
                        onLibrary = { navigateTo(Destination.LIBRARY) },
                        onEditor = { songId -> nav.navigate("editor?songId=${Uri.encode(songId.orEmpty())}") { launchSingleTop = true } },
                        onExport = { workspace.showSheet(AppSheet.EXPORT) },
                        onOrder = workspace::reorderSongs, onMove = workspace::moveSong,
                        onRemove = workspace::removeSong,
                        onRelink = { id -> relinkSongId = id; launchPicker { relinkFile.launch(arrayOf("audio/*")) } },
                        onRefresh = workspace::refreshSources, onDragState = workspace::setReordering,
                        preview = preview, onPreviewAll = workspace.editor::playWorkspace,
                        processing = state.processing, onCancel = workspace::cancelProcessing,
                    )
                }
                composable(Destination.LIBRARY.route) {
                    LibraryScreen(state.results, state.category, workspace::selectCategory, onMakeAudio = { navigateTo(Destination.HOME) },
                        isLoading = !state.ready && state.loadingMessage != null) { resultId, action ->
                        if (action == "添加到拼接") {
                            if (state.ready && !state.isWorking) workspace.addResultToProject(resultId)
                            else workspace.reportError("正在读取或保存项目，请稍后再试。")
                        } else workspace.unavailable(action)
                    }
                }
                composable(EDITOR_ROUTE, arguments = listOf(navArgument("songId") { type = NavType.StringType; defaultValue = "" })) { editorEntry ->
                    val songId = editorEntry.arguments?.getString("songId")
                    val editor by workspace.editor.state.collectAsStateWithLifecycle()
                    val waveform by workspace.editor.waveform.state.collectAsStateWithLifecycle()
                    LaunchedEffect(songId, state.ready) { if (state.ready && !songId.isNullOrBlank()) workspace.editor.open(songId) }
                    EditorScreen(
                        song = state.songs.firstOrNull { it.id == songId }, onBack = { exitEditor() },
                        onAdjustTime = { workspace.showSheet(AppSheet.MORE_EDIT) },
                        onAction = workspace.editor::act,
                        presentation = if (editor.songId == songId) editor else EditorPresentation(busy = true),
                        isLoading = !state.ready && state.loadingMessage != null,
                        waveform = if (waveform.songId == songId) waveform else com.audioassistant.waveform.WaveformState(),
                        onWaveformRetry = workspace.editor.waveform::retry,
                        onWaveformCancel = workspace.editor.waveform::cancel,
                        onWaveformSeek = workspace.editor::seek,
                    )
                }
            }
        }
    }

    // 放在 NavHost 之后注册，让工具栏与系统返回走相同出口；弹窗/面板优先关闭自身。
    BackHandler(enabled = !inEditor && !state.isReordering && current != Destination.HOME && state.sheet == null && state.completedResult == null) {
        navigateTo(Destination.HOME)
    }
    BackHandler(enabled = inEditor && state.sheet == null && state.completedResult == null) { exitEditor() }

    BackHandler(enabled = state.isReordering) { workspace.requestCancelDrag() }

    val success = state.completedResult
    if (success != null) {
        ModalBottomSheet(onDismissRequest = workspace::dismissSuccess, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.background, shape = AppSheetShape, tonalElevation = 0.dp) {
            SuccessOptions(success, workspace::unavailable, onLibrary = {
                workspace.dismissSuccess()
                navigateTo(Destination.LIBRARY)
            }, onClose = workspace::dismissSuccess)
        }
    } else if (state.sheet != null) {
        ModalBottomSheet(onDismissRequest = workspace::closeSheet, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.background, shape = AppSheetShape, tonalElevation = 0.dp) {
            when (state.sheet) {
                AppSheet.VIDEO_NAMES -> VideoNameOptions(state, workspace::setVideoName, workspace::closeSheet, { workspace.startConversions() })
                AppSheet.EXPORT -> ExportOptions(state, workspace::setExportName, workspace::setNormalize, workspace::closeSheet, workspace::startExport)
                AppSheet.MORE_EDIT -> {
                    val editor by workspace.editor.state.collectAsStateWithLifecycle()
                    EditorOptions(editor, workspace::closeSheet, workspace.editor::act, workspace.editor::inputBoundary,
                        workspace.editor::adjustBoundary, workspace.editor::boundaryAtPosition,
                        workspace.editor::restoreDeleted, workspace.editor::inputSeek)
                }
                null -> Unit
            }
        }
    }
    state.notice?.let { notice ->
        AlertDialog(
            onDismissRequest = workspace::dismissNotice,
            icon = { AppGlyph(AppIcon.INFO, tint = MaterialTheme.colorScheme.primary) },
            title = { Text(notice.title) },
            text = { Text(notice.message) },
            confirmButton = { TextButton(onClick = workspace::dismissNotice) { Text("知道了") } },
        )
    }
}
