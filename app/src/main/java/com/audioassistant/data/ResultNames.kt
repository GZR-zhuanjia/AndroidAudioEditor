package com.audioassistant.data

/** User-facing names only; internal UUID paths never change on rename. */
object ResultNames {
    fun base(value: String): String {
        val clean = value.trim().replace(Regex("(?i)\\.mp3$"), "")
            .replace(Regex("[\\p{Cntrl}/\\\\:*?\"<>|]"), "_").trim()
        require(clean.isNotBlank() && clean != "." && clean != "..") { "请输入有效文件名" }
        var end = 0; var bytes = 0; var points = 0
        while (end < clean.length && points < 100) {
            val code = clean.codePointAt(end)
            val size = if (code <= 0x7f) 1 else if (code <= 0x7ff) 2 else if (code <= 0xffff) 3 else 4
            if (bytes + size > 220) break // reserve room for .mp3 and duplicate suffixes on local filesystems
            bytes += size; end += Character.charCount(code); points++
        }
        return clean.substring(0, end).trim()
    }
    fun unique(value: String, existing: Collection<String>): String {
        val base = base(value)
        val used = existing.map { it.lowercase() }.toSet()
        var name = "$base.mp3"; var suffix = 2
        while (name.lowercase() in used) name = "$base (${suffix++}).mp3"
        return name
    }
}
