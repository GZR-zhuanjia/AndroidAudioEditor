package com.audioassistant.editing

import java.math.BigDecimal

object TimeInput {
    /** 秒、分:秒或时:分:秒，最多六位小数；不经过二进制浮点。 */
    fun parseUs(text: String): Long {
        val fields = text.trim().replace('：', ':').split(':')
        require(fields.size in 1..3 && fields.all { it.matches(Regex("[0-9]+(\\.[0-9]{1,6})?")) }) { "请输入秒或分:秒，例如 01:23.200" }
        var seconds = BigDecimal.ZERO
        fields.forEachIndexed { index, value ->
            val number = value.toBigDecimal()
            if (index < fields.lastIndex) require(!value.contains('.')) { "只有秒数可包含小数" }
            if (fields.size > 1 && index > 0) require(number < BigDecimal(60)) { "分和秒应小于60" }
            seconds = seconds.multiply(BigDecimal(60)).add(number)
        }
        return try { seconds.movePointRight(6).longValueExact() } catch (_: ArithmeticException) { error("时间数值过大或精度无效") }
    }
}
