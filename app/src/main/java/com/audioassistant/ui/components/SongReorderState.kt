package com.audioassistant.ui.components

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.audioassistant.ui.SongItemUi

/** 拖动期间仅改变本地顺序，结束后单次提交；取消不会写项目。 */
class SongReorderState(private val list: LazyListState) {
    var order by mutableStateOf<List<SongItemUi>>(emptyList())
        private set
    var draggedId by mutableStateOf<String?>(null)
        private set
    var centerY by mutableFloatStateOf(0f)
        private set
    var pointerY by mutableFloatStateOf(0f)
        private set
    private var original = emptyList<SongItemUi>()

    fun sync(songs: List<SongItemUi>) { if (draggedId == null) order = songs }
    fun begin(id: String, pointerInCardY: Float): Boolean {
        val item = list.layoutInfo.visibleItemsInfo.firstOrNull { it.key == "song:$id" } ?: return false
        original = order
        draggedId = id
        centerY = item.offset + item.size / 2f
        pointerY = item.offset + pointerInCardY
        return true
    }
    fun drag(delta: Float) { centerY += delta; pointerY += delta; moveToTarget() }
    fun moveToTarget() {
        val active = draggedId ?: return
        val target = list.layoutInfo.visibleItemsInfo.firstOrNull { info ->
            order.any { "song:${it.id}" == info.key } && centerY >= info.offset && centerY < info.offset + info.size
        }?.key?.toString()?.removePrefix("song:") ?: return
        if (target == active) return
        val from = order.indexOfFirst { it.id == active }
        val to = order.indexOfFirst { it.id == target }
        if (from >= 0 && to >= 0) order = order.toMutableList().apply { add(to, removeAt(from)) }
    }
    fun translation(id: String): Float {
        if (draggedId != id) return 0f
        val item = list.layoutInfo.visibleItemsInfo.firstOrNull { it.key == "song:$id" } ?: return 0f
        return centerY - (item.offset + item.size / 2f)
    }
    fun finish(): List<String> { draggedId = null; return order.map { it.id } }
    fun cancel() { if (draggedId != null) order = original; draggedId = null }
}
