package com.le0xff.plauncher.data

import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.CopyOnWriteArrayList

data class LogEntry(
    val timestamp: String,
    val level: String,
    val tag: String,
    val message: String
)

object AppLogBuffer {
    private val _entries = CopyOnWriteArrayList<LogEntry>()
    private const val MAX_ENTRIES = 500
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    private fun now(): String {
        return dateFormat.format(Date())
    }

    private fun add(level: String, tag: String, message: String) {
        synchronized(_entries) {
            if (_entries.size >= MAX_ENTRIES) {
                _entries.removeAt(0)
            }
            _entries.add(LogEntry(now(), level, tag, message))
        }
    }

    fun info(tag: String, message: String) = add("INFO", tag, message)
    fun warn(tag: String, message: String) = add("WARN", tag, message)
    fun error(tag: String, message: String) = add("ERROR", tag, message)
    fun debug(tag: String, message: String) = add("DEBUG", tag, message)

    fun getEntries(): List<LogEntry> {
        return synchronized(_entries) {
            _entries.toList()
        }
    }

    fun getLogsAsString(): String {
        return synchronized(_entries) {
            _entries.joinToString("\n") { entry ->
                "[${entry.timestamp}] ${entry.level}/${entry.tag}: ${entry.message}"
            }
        }
    }

    fun clear() {
        synchronized(_entries) {
            _entries.clear()
        }
    }
}
