package com.example.engine

import com.example.data.model.DiagnosticLogEntry
import com.example.data.model.LogCategory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicLong

class DiagnosticLogger {
    private val idGenerator = AtomicLong(1L)
    private val lock = Any()
    private val _logs = MutableStateFlow<List<DiagnosticLogEntry>>(emptyList())
    val logs: StateFlow<List<DiagnosticLogEntry>> = _logs.asStateFlow()

    fun log(
        category: LogCategory,
        message: String,
        detail: String = "",
        isError: Boolean = false
    ) {
        val entry = DiagnosticLogEntry(
            id = idGenerator.getAndIncrement(),
            timestampMs = System.currentTimeMillis(),
            category = category,
            message = message,
            detail = detail,
            isError = isError
        )
        synchronized(lock) {
            val current = _logs.value
            val updated = (listOf(entry) + current).take(MAX_LOG_ENTRIES)
            _logs.value = updated
        }
    }

    fun clear() {
        synchronized(lock) {
            _logs.value = emptyList()
        }
    }

    companion object {
        private const val MAX_LOG_ENTRIES = 400
    }
}
