package com.audioassistant.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
import com.audioassistant.ui.components.EditorOptions
import com.audioassistant.ui.components.ExportOptions
import com.audioassistant.ui.components.StateBanner
import com.audioassistant.ui.components.SuccessOptions
import com.audioassistant.ui.components.VideoNameOptions
import com.audioassistant.ui.navigation.Destination
import com.audioassistant.ui.navigation.EDITOR_ROUTE
import com.audioassistant.ui.screens.EditWorkspaceScreen
import com.audioassistant.ui.screens.EditorScreen
import com.audioassistant.ui.screens.HomeScreen
import com.audioassistant.ui.screens.LibraryScreen
import com.audioassistant.ui.screens.VideoScreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioAssistantApp(workspace: WorkspaceViewModel = viewModel()) {
    // ViewModel 属于 Activity，绝不在每个 composable 路由内重新创建队列。
    val state by workspace.state.collectAsStateWithLifecycle()
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
    val current = Destination.entries.firstOrNull { it.route == route } ?: Destination.HOME

    fun navigateTo(destination: Destination) {
        nav.navigate(destination.route) {
            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }
    fun exitEditor() {
        // 项目列表/设置已逐次自动保存；阶段 4 接入剪辑后须复用同一写入队列。
        nav.popBackStack()
    }


    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (inEditor) "单曲裁剪" else if (current == Destination.HOME) "音频助手" else current.title) },
                navigationIcon = {
                    if (inEditor) IconButton(onClick = { exitEditor() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回歌曲列表")
                    }
                },
                actions = {
                    if (inEditor) TextButton(onClick = { workspace.showSheet(AppSheet.MORE_EDIT) }) { Text("更多编辑") }
                },
            )
        },
        bottomBar = {
            if (!inEditor) NavigationBar {
                Destination.entries.forEach { destination ->
                    NavigationBarItem(
                        selected = current == destination,
                        enabled = !state.isReordering,
                        onClick = { navigateTo(destination) },
                        icon = { Icon(destination.icon, contentDescription = null) },
                        label = { Text(destination.title) },
                    )
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
            StateBanner(state.loadingMessage, state.errorMessage, workspace::dismissError)
            NavHost(navController = nav, startDestination = Destination.HOME.route, modifier = Modifier.weight(1f)) {
                composable(Destination.HOME.route) {
                    HomeScreen(state.songs.isNotEmpty(), { navigateTo(Destination.VIDEO) }, { navigateTo(Destination.EDIT) })
                }
                composable(Destination.VIDEO.route) {
                    VideoScreen(
                        videos = state.videos, enabled = state.ready && !state.isWorking,
                        onPhoto = { launchPicker { photoPicker.launch(PickVisualMediaRequest(mediaType = ActivityResultContracts.PickVisualMedia.VideoOnly)) } },
                        onFiles = { launchPicker { videoFiles.launch(arrayOf("video/*")) } },
                        onSettings = { workspace.showSheet(AppSheet.VIDEO_NAMES) },
                        onRemove = workspace::removeVideo, onUnavailable = workspace::unavailable,
                    )
                }
                composable(Destination.EDIT.route) {
                    EditWorkspaceScreen(
                        songs = state.songs, enabled = state.ready && !state.isWorking, cancelDragSignal = state.cancelDragSignal,
                        onImport = { launchPicker { audioFiles.launch(arrayOf("audio/*")) } },
                        onLibrary = { navigateTo(Destination.LIBRARY) },
                        onEditor = { songId -> nav.navigate("editor?songId=${Uri.encode(songId.orEmpty())}") { launchSingleTop = true } },
                        onExport = { workspace.showSheet(AppSheet.EXPORT) },
                        onOrder = workspace::reorderSongs, onMove = workspace::moveSong,
                        onRemove = workspace::removeSong,
                        onRelink = { id -> relinkSongId = id; launchPicker { relinkFile.launch(arrayOf("audio/*")) } },
                        onRefresh = workspace::refreshSources, onDragState = workspace::setReordering,
                    )
                }
                composable(Destination.LIBRARY.route) {
                    LibraryScreen(state.results, state.category, workspace::selectCategory) { resultId, action ->
                        if (action == "添加到拼接") {
                            if (state.ready && !state.isWorking) workspace.addResultToProject(resultId)
                            else workspace.reportError("正在读取或保存项目，请稍后再试。")
                        } else workspace.unavailable(action)
                    }
                }
                composable(EDITOR_ROUTE, arguments = listOf(navArgument("songId") { type = NavType.StringType; defaultValue = "" })) { editorEntry ->
                    val songId = editorEntry.arguments?.getString("songId")
                    EditorScreen(state.songs.firstOrNull { it.id == songId })
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
        ModalBottomSheet(onDismissRequest = workspace::dismissSuccess, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            SuccessOptions(success, workspace::unavailable) {
                workspace.dismissSuccess()
                navigateTo(Destination.LIBRARY)
            }
        }
    } else if (state.sheet != null) {
        ModalBottomSheet(onDismissRequest = workspace::closeSheet, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            when (state.sheet) {
                AppSheet.VIDEO_NAMES -> VideoNameOptions(state, workspace::setVideoName)
                AppSheet.EXPORT -> ExportOptions(state, workspace::setExportName, workspace::setNormalize)
                AppSheet.MORE_EDIT -> EditorOptions()
                null -> Unit
            }
        }
    }
    state.notice?.let { notice ->
        AlertDialog(
            onDismissRequest = workspace::dismissNotice,
            title = { Text(notice.title) },
            text = { Text(notice.message) },
            confirmButton = { TextButton(onClick = workspace::dismissNotice) { Text("知道了") } },
        )
    }
}
