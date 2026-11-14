package com.audioassistant.editing

import com.audioassistant.model.AudioSegment

/** 成品时间线均为整数微秒，半开区间；不读写任何媒体文件。 */
object SegmentTimeline {
    fun duration(segments: List<AudioSegment>): Long = segments.fold(0L) { sum, part -> Math.addExact(sum, part.durationUs) }

    fun slice(segments: List<AudioSegment>, startUs: Long, endUs: Long): List<AudioSegment> {
        require(startUs >= 0 && endUs >= startUs && endUs <= duration(segments)) { "选区超出歌曲范围" }
        var cursor = 0L
        return buildList {
            segments.forEach { part ->
                val end = Math.addExact(cursor, part.durationUs)
                val from = maxOf(cursor, startUs)
                val to = minOf(end, endUs)
                if (from < to) add(part.copy(sourceStartUs = part.sourceStartUs + (from - cursor), sourceEndUs = part.sourceStartUs + (to - cursor)))
                cursor = end
            }
        }
    }

    fun delete(segments: List<AudioSegment>, startUs: Long, endUs: Long): List<AudioSegment> {
        require(startUs < endUs) { "请先标记有效选区" }
        return slice(segments, 0, startUs) + slice(segments, endUs, duration(segments))
    }

    fun split(segments: List<AudioSegment>, positionUs: Long): List<AudioSegment> {
        val total = duration(segments)
        require(positionUs > 0 && positionUs < total) { "分割点应在歌曲内部" }
        return slice(segments, 0, positionUs) + slice(segments, positionUs, total)
    }

    /** 接缝归右侧片段；成品末尾归最后源片段的结束点。 */
    fun sourcePosition(segments: List<AudioSegment>, positionUs: Long): Long? {
        if (segments.isEmpty()) return null
        val position = positionUs.coerceIn(0, duration(segments))
        var cursor = 0L
        segments.forEach { part ->
            if (position < cursor + part.durationUs) return part.sourceStartUs + (position - cursor)
            cursor += part.durationUs
        }
        return segments.last().sourceEndUs
    }

    /** 原曲处于已删除范围时定位到该范围的成品接缝。 */
    fun editedPosition(segments: List<AudioSegment>, sourceUs: Long): Long {
        var cursor = 0L
        segments.forEach { part ->
            if (sourceUs < part.sourceStartUs) return cursor
            if (sourceUs < part.sourceEndUs) return cursor + (sourceUs - part.sourceStartUs)
            cursor += part.durationUs
        }
        return cursor
    }

    /** 按源位置补回真实删除内容，重叠部分只保留一份，其余删除仍保留。 */
    fun restore(segments: List<AudioSegment>, removed: List<AudioSegment>): List<AudioSegment> {
        val sorted = (segments + removed).sortedBy { it.sourceStartUs }
        require(sorted.map { it.sourceId }.distinct().size <= 1)
        val result = mutableListOf<AudioSegment>()
        sorted.forEach { part ->
            val previous = result.lastOrNull()
            if (previous != null && part.sourceStartUs < previous.sourceEndUs) {
                result[result.lastIndex] = previous.copy(sourceEndUs = maxOf(previous.sourceEndUs, part.sourceEndUs))
            } else result += part
        }
        return result
    }
}
