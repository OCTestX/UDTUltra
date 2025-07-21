package io.github.octest.udtultra.utils

class StringMerger(
    private val merger: suspend (Map<String, String>) -> Unit
) {
    private val mergerMap = mutableMapOf<String, String>()
    suspend fun applyString(id: String, content: String) {
        mergerMap[id] = content
        merger(mergerMap)
    }
}