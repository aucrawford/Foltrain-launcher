package com.soc.launcher.data.model

data class AppInfo(val name: String, val packageName: String, val category: String)

data class MissedCallInfo(val id: String, val name: String, val number: String, val count: Int)

data class MessageInfo(val id: String, val text: String, val address: String, val senderName: String, val photoUri: String?, val isSpam: Boolean = false)

data class ContactInfo(val id: String, val name: String, val photoUri: String?)

enum class TaskImportance { REGULAR, PRIORITY, MAJOR }

data class Reminder(
    val id: String,
    val text: String,
    val timestamp: Long,
    val importance: TaskImportance = TaskImportance.REGULAR,
    val isCompleted: Boolean = false
)

data class AppUsageInfo(
    val packageName: String,
    val name: String,
    val totalTimeInForeground: Long
)

data class MediaInfo(
    val title: String = "",
    val artist: String = "",
    val isPlaying: Boolean = false,
    val packageName: String = ""
)
