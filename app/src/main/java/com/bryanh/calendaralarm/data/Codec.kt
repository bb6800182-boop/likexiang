package com.bryanh.calendaralarm.data

/** DataStore 用的簡單字串編碼（key=value;key=value），不引入 JSON 套件。 */
object Codec {
    fun encodeLongInt(map: Map<Long, Int>): String = map.entries.joinToString(";") { "${it.key}=${it.value}" }

    fun decodeLongInt(s: String?): Map<Long, Int> = pairs(s).mapNotNull { (k, v) ->
        val key = k.toLongOrNull() ?: return@mapNotNull null
        val value = v.toIntOrNull() ?: return@mapNotNull null
        key to value
    }.toMap()

    fun encodeLongBool(map: Map<Long, Boolean>): String =
        map.entries.joinToString(";") { "${it.key}=${if (it.value) 1 else 0}" }

    fun decodeLongBool(s: String?): Map<Long, Boolean> = pairs(s).mapNotNull { (k, v) ->
        val key = k.toLongOrNull() ?: return@mapNotNull null
        when (v) {
            "1" -> key to true
            "0" -> key to false
            else -> null
        }
    }.toMap()

    /** 稍後提醒用：key（eventId@begin）→ 時間。key 不含 '=' 與 ';'。 */
    fun encodeStringLong(map: Map<String, Long>): String =
        map.entries.filter { isSafeKey(it.key) }.joinToString(";") { "${it.key}=${it.value}" }

    fun decodeStringLong(s: String?): Map<String, Long> = pairs(s).mapNotNull { (k, v) ->
        val value = v.toLongOrNull() ?: return@mapNotNull null
        if (isSafeKey(k)) k to value else null
    }.toMap()

    fun isSafeKey(key: String): Boolean = key.isNotEmpty() && '=' !in key && ';' !in key

    /** 從 key 取出行程開始時間；格式不對回傳 null。 */
    fun beginOf(key: String): Long? = key.substringAfter('@', "").toLongOrNull()

    private fun pairs(s: String?): List<Pair<String, String>> =
        s.orEmpty().split(';').mapNotNull {
            val i = it.indexOf('=')
            if (i > 0) it.substring(0, i) to it.substring(i + 1) else null
        }
}
