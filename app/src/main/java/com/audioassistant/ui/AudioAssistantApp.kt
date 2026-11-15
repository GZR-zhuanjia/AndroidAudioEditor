package com.audioassistant.ui

import android.net.Uri
import android.app.Activity
import android.content.ClipData
import android.content.Intent
import android.provider.DocumentsContract
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
import com.audioassistant.ui.components.AppModalSheet
import com.audioassistant.ui.components.EditorOptions
import com.audioassistant.ui.components.ExportOptions
import com.audioassistant.ui.components.HomeAppBar
import com.audioassistant.ui.components.StateBanner
import com.audioassistant.ui.components.SuccessOptions
import com.audioassistant.ui.components.TextAction
import com.audioassistant.ui.components.VideoNameOptions
import com.audioassistant.ui.components.ResultPlayerDock
import com.audioassistant.ui.components.FileOperationDock
import com.audioassistant.ui.components.SaveCopyOptions
import com.audioassistant.ui.components.RenameResultDialog
import com.audioassistant.ui.components.DeleteResultDialog
import com.audioassistant.ui.navigation.AppBottomBar
import com.audioassistant.ui.navigation.Destination
import com.audioassistant.ui.navigation.EDITOR_ROUTE
import com.audioassistant.ui.screens.EditWorkspaceScreen
import com.audioassistant.ui.screens.EditorPresentation
import com.audioassistant.ui.screens.EditorScreen
import com.audioassistant.ui.screens.HomeScreen
import com.audioassistant.ui.screens.LibraryScreen
import com.audioassistant.ui.screens.VideoScreen
import com.audioassistant.ui.theme.AppSpacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioAssistantApp(workspace: WorkspaceViewModel = viewModel()) {
    // ViewModel 属于 Activity，绝不在每个 composable 路由内重新创建队列。
    val state by workspace.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val sheetActionScope = rememberCoroutineScope()
    LaunchedEffect(Unit) { withContext(Dispatchers.Default) { AppIcon.entries.forEach { it.vector } } }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { workspace.editor.pauseForBackground(); workspace.resultPreview.pause() }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (state.ready && !state.isWorking && (state.songs.isNotEmpty() || state.results.isNotEmpty())) workspace.refreshSources()
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
    val saveFolder = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) result.data?.data?.let { uri ->
            workspace.rememberSaveLocation(uri, result.data?.flags ?: 0)
        }
    }
    val share = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { /* choosing/cancelling is not a delivery receipt */ }
    LaunchedEffect(state.pendingShare?.token) {
        val request = state.pendingShare ?: return@LaunchedEffect
        workspace.consumeShare(request.token)
        try {
            val uri = Uri.parse(request.uri)
            val intent = Intent(Intent.ACTION_SEND).setType("audio/mpeg").putExtra(Intent.EXTRA_STREAM, uri)
                .putExtra(Intent.EXTRA_TITLE, request.name).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            intent.clipData = ClipData.newUri(context.contentResolver, request.name, uri)
            share.launch(Intent.createChooser(intent, "分享音频").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
        } catch (_: Exception) { workspace.reportError("无法打开系统分享面板；内部成果仍保留，请稍后重试。") }
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
        // 等待当前编辑自动保存完成，再返回同一个工作区。
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
                enterTransition = { EnterTransition.None }, exitTransition = { ExitTransition.None },
                popEnterTransition = { EnterTransition.None }, popExitTransition = { ExitTransition.None },
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
                        onEditor = { songId -> workspace.resultPreview.stop(); nav.navigate("editor?songId=${Uri.encode(songId.orEmpty())}") { launchSingleTop = true } },
                        onExport = { workspace.showSheet(AppSheet.EXPORT) },
                        onOrder = workspace::reorderSongs, onMove = workspace::moveSong,
                        onRemove = workspace::removeSong,
                        onRelink = { id -> relinkSongId = id; launchPicker { relinkFile.launch(arrayOf("audio/*")) } },
                        onRefresh = workspace::refreshSources, onDragState = workspace::setReordering,
                        preview = preview, onPreviewAll = { workspace.resultPreview.stop(); workspace.editor.playWorkspace() },
                        processing = state.processing, onCancel = workspace::cancelProcessing,
                    )
                }
                composable(Destination.LIBRARY.route) {
                    val playback by workspace.resultPreview.summary.collectAsStateWithLifecycle()
                    LibraryScreen(state.results, state.category, workspace::selectCategory, onMakeAudio = { navigateTo(Destination.HOME) },
                        isLoading = !state.ready && state.loadingMessage != null, enabled = state.ready && !state.isWorking,
                        playback = playback, onAction = workspace::onResultAction)
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
            if (!inEditor) {
                FileOperationDock(workspace.fileOperation, workspace::cancelFileOperation)
                ResultPlayerDock(workspace.resultPreview)
            }
        }
    }

    // 放在 NavHost 之后注册，让工具栏与系统返回走相同出口；弹窗/面板优先关闭自身。
    BackHandler(enabled = !inEditor && !state.isReordering && current != Destination.HOME && state.sheet == null && state.completedResult == null && state.saveTarget == null && state.resultDialog == null) {
        navigateTo(Destination.HOME)
    }
    BackHandler(enabled = inEditor && state.sheet == null && state.completedResult == null) { exitEditor() }

    BackHandler(enabled = state.isReordering) { workspace.requestCancelDrag() }

    val success = state.completedResult
    if (success != null) {
        val playback by workspace.resultPreview.summary.collectAsStateWithLifecycle()
        AppModalSheet(sheetActionScope, workspace::dismissSuccess,
            isOpen = { workspace.state.value.completedResult?.id == success.id }) { finish ->
            SuccessOptions(success, { action ->
                if (action == "另存" || action == "另存到 U 盘") finish {
                    workspace.onResultAction(success.id, action)
                } else workspace.onResultAction(success.id, action)
            }, onLibrary = { finish {
                workspace.dismissSuccess()
                workspace.selectCategory(ResultCategory.ALL)
                navigateTo(Destination.LIBRARY)
            } }, onClose = { finish { workspace.dismissSuccess() } }, playback = playback, enabled = state.ready && !state.isWorking)
        }
    } else if (state.saveTarget != null) {
        val result = state.results.firstOrNull { it.id == state.saveTarget }
        if (result != null) AppModalSheet(sheetActionScope, workspace::closeSaveOptions,
            isOpen = { workspace.state.value.saveTarget == result.id }) { finish ->
            SaveCopyOptions(result, state.saveLocation, state.ready && !state.isWorking, state.errorMessage,
                onSave = { finish { workspace.saveResultCopy(result.id) } }, onChoose = { launchPicker {
                    val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or Intent.FLAG_GRANT_PREFIX_URI_PERMISSION)
                    state.saveLocation?.uri?.let { intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, Uri.parse(it)) }
                    saveFolder.launch(intent)
                } }, onClose = { finish { workspace.closeSaveOptions() } })
        }
    } else if (state.sheet != null) {
        val activeSheet = state.sheet
        AppModalSheet(sheetActionScope, workspace::closeSheet,
            isOpen = { workspace.state.value.sheet == activeSheet }) { finish ->
            val close = { finish { workspace.closeSheet() } }
            when (state.sheet) {
                AppSheet.VIDEO_NAMES -> VideoNameOptions(state, workspace::setVideoName, close,
                    { finish { workspace.startConversions() } })
                AppSheet.EXPORT -> ExportOptions(state, workspace::setExportName, workspace::setNormalize, close,
                    { finish { workspace.startExport() } })
                AppSheet.MORE_EDIT -> {
                    val editor by workspace.editor.state.collectAsStateWithLifecycle()
                    EditorOptions(editor, close, workspace.editor::act, workspace.editor::inputBoundary,
                        workspace.editor::adjustBoundary, workspace.editor::boundaryAtPosition,
                        workspace.editor::restoreDeleted, workspace.editor::inputSeek)
                }
                null -> Unit
            }
        }
    }
    state.resultDialog?.let { dialog ->
        val result = state.results.firstOrNull { it.id == dialog.id }
        if (result != null) when (dialog.kind) {
            ResultDialogKind.RENAME -> RenameResultDialog(result, state.ready && !state.isWorking,
                workspace::closeResultDialog, state.errorMessage) { workspace.renameResult(result.id, it) }
            ResultDialogKind.DELETE -> DeleteResultDialog(result, state.ready && !state.isWorking,
                workspace::closeResultDialog) { workspace.deleteResult(result.id) }
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
