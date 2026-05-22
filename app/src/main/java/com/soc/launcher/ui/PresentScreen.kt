package com.soc.launcher.ui

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.provider.Settings
import android.os.Build
import android.provider.AlarmClock
import android.text.format.DateFormat
import android.provider.CalendarContract
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.outlined.Flag
import com.soc.launcher.data.model.Reminder
import com.soc.launcher.data.model.TaskImportance
import com.soc.launcher.ui.components.TaskEditor
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.soc.launcher.ui.components.MediaController
import com.soc.launcher.ui.components.PresentSearchBar
import com.soc.launcher.*
import com.soc.launcher.data.model.AppInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import com.soc.launcher.ui.theme.*

fun isVpnActive(context: Context): Boolean {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val activeNetwork = cm.activeNetwork
    val capabilities = cm.getNetworkCapabilities(activeNetwork)
    return capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) ?: false
}

fun safeStartSettings(context: android.content.Context, action: String) {
    try {
        val intent = android.content.Intent(action)
        context.startActivity(intent)
    } catch (e: Exception) {
        // If the specific settings page doesn't exist, fall back to the main settings
        context.startActivity(android.content.Intent(android.provider.Settings.ACTION_SETTINGS))
    }
}

@Composable
fun PresentScreen(
    aiAppPackage: String,
    mapAppPackage: String,
    apps: List<AppInfo>,
    onAiAppSelected: (String) -> Unit,
    onMapAppSelected: (String) -> Unit
) {
    val aiAppsOnly = remember(apps) {
        apps.filter { it.category == "AI" }
    }
    val mapAppsOnly = remember(apps) {
        apps.filter { it.packageName.contains("map", ignoreCase = true) || it.name.contains("map", ignoreCase = true) }
            .distinctBy { it.packageName }
    }

    var showStats by remember { mutableStateOf(false) }
    var showMedia by remember { mutableStateOf(false) }
    var showTaskEditor by remember { mutableStateOf(false) }
    
    val context = LocalContext.current
    val sharedPrefs = remember { context.getSharedPreferences("launcher_prefs", Context.MODE_PRIVATE) }

    var allTasks by remember {
        mutableStateOf(
            sharedPrefs.getStringSet("tasks", emptySet())?.mapNotNull {
                try {
                    val parts = it.split("|")
                    if (parts.size < 3) return@mapNotNull null

                    val isCompleted = if (parts.size >= 5) parts.last().toBoolean() else false
                    val importance = if (parts.size >= 4) {
                        try { TaskImportance.valueOf(parts[parts.size - 2]) } catch(e: Exception) { TaskImportance.REGULAR }
                    } else TaskImportance.REGULAR
                    val timestamp = try {
                        parts[if (parts.size >= 4) parts.size - 3 else 2].toLong()
                    } catch(e: Exception) { System.currentTimeMillis() }

                    val id = parts.first()
                    val text = if (parts.size >= 4) {
                        parts.subList(1, parts.size - 3).joinToString("|")
                    } else {
                        parts[1]
                    }

                    Reminder(id, text, timestamp, importance, isCompleted)
                } catch (e: Exception) { null }
            }?.sortedWith(compareByDescending<Reminder> { it.importance }.thenBy { it.timestamp }) ?: emptyList()
        )
    }

    fun saveTasks(tasks: List<Reminder>) {
        val taskStrings = tasks.map { "${it.id}|${it.text}|${it.timestamp}|${it.importance.name}|${it.isCompleted}" }.toSet()
        sharedPrefs.edit().putStringSet("tasks", taskStrings).apply()
        allTasks = tasks.sortedWith(compareByDescending<Reminder> { it.importance }.thenBy { it.timestamp })
    }

    val lifecycleOwner = LocalLifecycleOwner.current

    val use24HourFormat = DateFormat.is24HourFormat(context)

    var hasUsagePermission by remember { mutableStateOf(hasUsageStatsPermission(context)) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasUsagePermission = hasUsageStatsPermission(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val mostUsedApps by produceState(initialValue = emptyList<AppInfo>(), apps, hasUsagePermission) {
        if (apps.isNotEmpty()) {
            value = withContext(Dispatchers.IO) { getMostUsedApps(context, apps) }
        }
    }

    val totalUsageTime by produceState(initialValue = "0m", hasUsagePermission) {
        while (true) {
            if (hasUsagePermission) {
                val totalMs = withContext(Dispatchers.IO) { getScreenOnTime(context) }
                val hours = totalMs / (1000 * 60 * 60)
                val minutes = (totalMs / (1000 * 60)) % 60
                value = when {
                    hours > 0 -> "${hours}h ${minutes}m"
                    else -> "${minutes}m"
                }
            }
            delay(60000) // Update every minute
        }
    }

    var currentDate by remember { mutableStateOf(SimpleDateFormat("EEEE, MMMM d", Locale.getDefault()).format(Date())) }

    val allAlarms by produceState(initialValue = emptyList<String>(), use24HourFormat) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        while (true) {
            val next = alarmManager.nextAlarmClock
            val alarms = mutableListOf<String>()
            next?.let {
                val alarmTime = it.triggerTime
                val now = Calendar.getInstance()
                val alarmCal = Calendar.getInstance().apply { timeInMillis = alarmTime }

                // Only show if it's today
                val isToday = now.get(Calendar.YEAR) == alarmCal.get(Calendar.YEAR) &&
                        now.get(Calendar.DAY_OF_YEAR) == alarmCal.get(Calendar.DAY_OF_YEAR)

                if (isToday) {
                    val pattern = if (use24HourFormat) "HH:mm" else "h:mm a"
                    alarms.add(SimpleDateFormat(pattern, Locale.getDefault()).format(Date(alarmTime)))
                }
            }
            value = alarms
            delay(10000)
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            currentDate = SimpleDateFormat("EEEE, MMMM d", Locale.getDefault()).format(Date())
            delay(1000)
        }
    }

    val storageUsage by produceState(initialValue = 0) {
        while (true) {
            value = withContext(Dispatchers.IO) { getStorageInfo() }
            delay(10000)
        }
    }

    val batteryTemp by produceState(initialValue = 0f) {
        while (true) {
            value = getBatteryTemperature(context)
            delay(10000)
        }
    }

    val calendarEvents by produceState(initialValue = emptyList<String>(), use24HourFormat) {
        while (true) {
            if (context.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED) {
                val events = mutableListOf<String>()
                val now = Calendar.getInstance()

                val startOfDay = (now.clone() as Calendar).apply {
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }

                val endOfDay = (now.clone() as Calendar).apply {
                    set(Calendar.HOUR_OF_DAY, 23)
                    set(Calendar.MINUTE, 59)
                    set(Calendar.SECOND, 59)
                    set(Calendar.MILLISECOND, 999)
                }

                // Use Instances to correctly handle recurring events
                val builder = CalendarContract.Instances.CONTENT_URI.buildUpon()
                android.content.ContentUris.appendId(builder, startOfDay.timeInMillis)
                android.content.ContentUris.appendId(builder, endOfDay.timeInMillis)

                val projection = arrayOf(
                    CalendarContract.Instances.TITLE,
                    CalendarContract.Instances.BEGIN,
                    CalendarContract.Instances.END,
                    CalendarContract.Instances.ALL_DAY
                )
                
                context.contentResolver.query(
                    builder.build(),
                    projection,
                    null,
                    null,
                    "${CalendarContract.Instances.BEGIN} ASC"
                )?.use { cursor ->
                    val titleIdx = cursor.getColumnIndex(CalendarContract.Instances.TITLE)
                    val startIdx = cursor.getColumnIndex(CalendarContract.Instances.BEGIN)
                    val endIdx = cursor.getColumnIndex(CalendarContract.Instances.END)
                    val allDayIdx = cursor.getColumnIndex(CalendarContract.Instances.ALL_DAY)
                    val todayVal = now.get(Calendar.YEAR) * 1000 + now.get(Calendar.DAY_OF_YEAR)

                    while (cursor.moveToNext()) {
                        val title = cursor.getString(titleIdx)
                        val startTime = cursor.getLong(startIdx)
                        val endTime = cursor.getLong(endIdx)
                        val allDay = cursor.getInt(allDayIdx) == 1

                        val isActuallyToday = if (allDay) {
                            val startUtc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = startTime }
                            val endUtc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = endTime }

                            val startVal = startUtc.get(Calendar.YEAR) * 1000 + startUtc.get(Calendar.DAY_OF_YEAR)
                            val endVal = endUtc.get(Calendar.YEAR) * 1000 + endUtc.get(Calendar.DAY_OF_YEAR)

                            todayVal >= startVal && todayVal < endVal
                        } else {
                            startTime <= endOfDay.timeInMillis
                        }

                        if (isActuallyToday) {
                            val pattern = if (use24HourFormat) "HH:mm" else "h:mm a"
                            val timeStr = if (allDay) "All Day" else SimpleDateFormat(pattern, Locale.getDefault()).format(Date(startTime))
                            events.add("$timeStr - $title")
                        }
                    }
                }
                value = events.distinct() // Remove duplicates if any
            }
            delay(300000) // Update every 5 minutes
        }
    }

    val walletPkg = "com.google.android.apps.walletnfcrel"
    val gmailPkg = "com.google.android.gm"
    val walletOrGmail = if (apps.any { it.packageName == walletPkg }) walletPkg else gmailPkg

    val googleAppsPackages = listOf(
        "com.google.android.dialer",     // Phone
        "com.google.android.apps.messaging", // Messages
        "com.google.android.apps.photos",    // Photos
        walletOrGmail,
        "com.google.android.apps.chromecast.app", // Home
        "com.google.android.GoogleCamera" // Camera
    )

    val bottomRowApps = (googleAppsPackages.mapNotNull { pkg ->
        apps.find { it.packageName == pkg } ?: apps.find { it.packageName.contains("camera", ignoreCase = true) && pkg == "com.google.android.GoogleCamera" }
    } + apps.filter { it.packageName !in googleAppsPackages }).distinctBy { it.packageName }.take(6)

    val hiddenPackages = remember { sharedPrefs.getStringSet("hidden_apps", emptySet()) ?: emptySet() }

    val playStorePackage = "com.android.vending"

    val bottomRowPackages = bottomRowApps.map { it.packageName }.toSet()

    val topRowApps = (mostUsedApps.filter { app ->
        val pkg = app.packageName
            pkg !in bottomRowPackages &&
            pkg !in hiddenPackages &&
            pkg !in aiAppPackage &&
            pkg !in mapAppPackage &&
            pkg != playStorePackage
        })
        .take(6)


    Box(modifier = Modifier
        .fillMaxSize()
    ) {
        // Top Content
        Column(modifier = Modifier
            .fillMaxWidth()
            .align(Alignment.TopCenter)
            .pointerInput(Unit) {
                detectVerticalDragGestures { change, dragAmount ->
                    if (dragAmount > 20) {
                        showStats = true
                    }
                }
            }
        ) {
            var searchQuery by remember { mutableStateOf("") }
            // Search Bar
            PresentSearchBar(
                aiPkg = aiAppPackage,
                aiApps = aiAppsOnly,
                mapApps = mapAppsOnly,
                currentMapsPkg = mapAppPackage,
                query = searchQuery,
                onQueryChange = { searchQuery = it },
                onAiAppSelected = onAiAppSelected,
                onMapAppSelected = onMapAppSelected
            )

            // Phone Status Section
            AnimatedVisibility(
                visible = showStats,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF050A10).copy(alpha = 0.7f))
                        .padding(16.dp)
                        // Add the swipe-down gesture here
                        .pointerInput(Unit) {
                            detectVerticalDragGestures { change, dragAmount ->
                                change.consume()
                                if (dragAmount < -20) {
                                    showStats = false
                                }
                            }
                        }
                ) {
                    // Stats Section
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalAlignment = Alignment.Start
                    ) {
                        // VPN Status
                        val vpnActive = remember(showStats) {
                            if (showStats) isVpnActive(context) else false
                        }
                        val vpnText = if (vpnActive) "ACTIVE" else "INACTIVE"
                        val vpnColor = if (vpnActive) FoltrainGoodColor else FoltrainHoldColor
                        StatItem("VPN: $vpnText", vpnColor, 16.sp) {
                            val intent = Intent("android.net.vpn.SETTINGS")
                            try {
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                // Fallback to general Network settings if the specific VPN page fails
                                safeStartSettings(context, Settings.ACTION_WIFI_SETTINGS)
                            }
                        }

                        // Android Version
                        StatItem("OS: ANDROID ${Build.VERSION.RELEASE}", FoltrainWhite, 16.sp) {
                            val intent = Intent(Settings.ACTION_DEVICE_INFO_SETTINGS)
                            try {
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                context.startActivity(Intent(Settings.ACTION_SETTINGS))
                            }
                        }

                        // Storage space
                        StatItem("STORAGE: $storageUsage%", FoltrainWhite, 16.sp) {
                            val intent = Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS)
                            try {
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                context.startActivity(Intent(Settings.ACTION_SETTINGS))
                            }
                        }

                        // Temperature
                        val rawTemp = batteryTemp.toInt() // Assuming you're pulling this from a BroadcastReceiver
                        val celsius = rawTemp / 10
                        val isHot = celsius >= 40 // Set your threshold (40°C is a good warning point)
                        val tempColor = if (isHot) FoltrainDangerColor else FoltrainWhite
                        StatItem("TEMP: ${batteryTemp}°C", tempColor, 16.sp) {
                            val intent = Intent(Intent.ACTION_POWER_USAGE_SUMMARY)
                            try {
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                context.startActivity(Intent(Settings.ACTION_SETTINGS))
                            }
                        }
                        if (hasUsagePermission) {
                            Text(
                                text = "Screen Time: $totalUsageTime".uppercase(),
                                fontFamily = Raleway,
                                color = FoltrainPriorityColor,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                }
            }
        }


        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(FoltrainWhite.copy(alpha = 0.05f)))

        // Bottom content
        Column(modifier = Modifier
            .fillMaxWidth()
            .align(Alignment.BottomCenter)
            .pointerInput(Unit) {
                detectVerticalDragGestures { _, dragAmount ->
                    // Negative dragAmount means your finger is moving UP
                    if (dragAmount < -20) {
                        showMedia = true
                    }
                }
            }
        ) {
            // Today
            Column(
                modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF050A10).copy(alpha = 0.6f))
                        .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.Start
            ) {
                // Calendar section
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Start,
                    modifier = Modifier.clickable {
                        val uri = Uri.parse("content://com.android.calendar/time/")
                        val intent = Intent(Intent.ACTION_VIEW).setData(uri)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        try {
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            val calendarPackages = listOf(
                                "com.google.android.calendar",
                                "com.android.calendar",
                                "com.samsung.android.calendar"
                            )
                            for (pkg in calendarPackages) {
                                val launchIntent =
                                    context.packageManager.getLaunchIntentForPackage(pkg)
                                if (launchIntent != null) {
                                    context.startActivity(launchIntent)
                                    return@clickable
                                }
                            }
                        }
                    }
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DateRange,
                            contentDescription = null,
                            tint = FoltrainWhite,
                            modifier = Modifier.matchParentSize()
                        )
                    }

                    Spacer(Modifier.width(8.dp))

                    Text(
                        text = currentDate.uppercase(),
                        fontSize = 16.sp,
                        fontFamily = Raleway,
                        color = FoltrainMain,
                        fontWeight = FontWeight.Medium,
                        style = TextStyle(
                            shadow = Shadow(
                                color = Color.Black.copy(alpha = 1f),
                                offset = Offset(0f, 0f),
                                blurRadius = 3f
                            )
                        )
                    )
                }

                if (calendarEvents.isNotEmpty()) {
                    Column(
                        modifier = Modifier.padding(start = 32.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        calendarEvents.forEach { event ->
                            Text(
                                text = "$event ",
                                fontFamily = Raleway,
                                color = FoltrainWhite,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Normal,
                                style = TextStyle(
                                    shadow = Shadow(
                                        color = Color.Black.copy(alpha = 1f),
                                        offset = Offset(0f, 0f),
                                        blurRadius = 3f
                                    ),
//                                    background = FoltrainWhite.copy(alpha = 0.3f),
                                )
                            )
                        }
                    }
                }

                // Alarm Section
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Start,
                    modifier = Modifier.clickable {
                        val intent = Intent(AlarmClock.ACTION_SHOW_ALARMS)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        try {
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            val calendarPackages = listOf(
                                "com.google.android.clockwork.alarmclock",
                                "com.android.alarmclock",
                                "com.samsung.android.alarmclock",
                                "com.google.android.deskclock",
                                "com.android.deskclock",
                                "com.sec.android.app.clockpackage"
                            )
                            for (pkg in calendarPackages) {
                                val launchIntent =
                                    context.packageManager.getLaunchIntentForPackage(pkg)
                                if (launchIntent != null) {
                                    context.startActivity(launchIntent)
                                    return@clickable
                                }
                            }
                        }
                    }
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Alarm,
                            contentDescription = null,
                            tint = FoltrainWhite,
                            modifier = Modifier.matchParentSize()
                        )
                    }

                    Spacer(Modifier.width(6.dp))

                    Column(
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        if (allAlarms.isNotEmpty()) {
                            allAlarms.forEach { alarm ->
                                Text(
                                    text = "NEXT ALARM: $alarm",
                                    fontFamily = Raleway,
                                    color = FoltrainMain,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium,
                                    style = TextStyle(
                                        shadow = Shadow(
                                            color = Color.Black.copy(alpha = 1f),
                                            offset = Offset(0f, 0f),
                                            blurRadius = 3f
                                        )
                                    )
                                )
                            }
                        } else {
                            Text(
                                text = "NO MORE ALARMS TODAY",
                                fontFamily = Raleway,
                                color = FoltrainMain,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium,
                                style = TextStyle(
                                    shadow = Shadow(
                                        color = Color.Black.copy(alpha = 1f),
                                        offset = Offset(0f, 0f),
                                        blurRadius = 3f
                                    )
                                )
                            )
                        }
                    }
                }

                // Tasks Section
                val incompleteTasks = allTasks.filter { !it.isCompleted }
                if (incompleteTasks.isEmpty()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Start,
                        modifier = Modifier.clickable {
                            showTaskEditor = true
                        }
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = FoltrainGoodColor,
                                modifier = Modifier.matchParentSize()
                            )
                        }

                        Spacer(Modifier.width(8.dp))

                        Text(
                            text = "NO TASKS TODAY",
                            color = FoltrainWhite,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Normal,
                            style = TextStyle(
                                shadow = Shadow(
                                    color = Color.Black.copy(alpha = 1f),
                                    offset = Offset(0f, 0f),
                                    blurRadius = 3f
                                )
                            )
                        )
                    }
                } else {
                    incompleteTasks.take(3).forEach { task ->
                        val (icon, importanceColor) = when (task.importance) {
                            TaskImportance.REGULAR -> Icons.Outlined.Flag to FoltrainWhite
                            TaskImportance.PRIORITY -> Icons.Default.PriorityHigh to FoltrainPriorityColor
                            TaskImportance.MAJOR -> Icons.Default.LocalFireDepartment to FoltrainDangerColor
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Start,
                            modifier = Modifier.clickable {
                                showTaskEditor = true
                            }
                        ) {

                            Spacer(Modifier.width(28.dp))

                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.size(16.dp)
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    tint = importanceColor,
                                    modifier = Modifier.matchParentSize()
                                )
                            }

                            Spacer(Modifier.width(2.dp))

                            Column(
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Text(
                                    text = task.text.uppercase(),
                                    color = importanceColor,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Normal,
                                    style = TextStyle(
                                        shadow = Shadow(
                                            color = Color.Black.copy(alpha = 1f),
                                            offset = Offset(0f, 0f),
                                            blurRadius = 3f
                                        )
                                    )
                                )
                            }
                        }
                    }
                }
            }

            // Media Coltroller
            AnimatedVisibility(
                visible = showMedia,
                enter = expandVertically(),
                exit = shrinkVertically(),
                modifier = Modifier.pointerInput(Unit) {
                    // LISTENER TO CLOSE (Swipe DOWN)
                    detectVerticalDragGestures { _, dragAmount ->
                        if (dragAmount > 20) showMedia = false
                    }
                }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF050A10).copy(alpha = 0.85f))
                ) {
                    MediaController(
                    )
                }
            }

            // App Drawer
            Column(modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF050A10).copy(alpha = 0.8f))
                .padding(vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)

            ) {
                // Top Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    topRowApps.forEach { app ->
                        AppIcon(app, Modifier.size(43.dp), showLabel = false)
                    }
                }

                // Bottom Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    bottomRowApps.forEach { app ->
                        AppIcon(app, Modifier.size(43.dp), showLabel = false)
                    }
                }
            }
        }
    }

    if (showTaskEditor) {
        TaskEditor(
            reminders = allTasks,
            onAddReminder = { text ->
                val newTasks = allTasks + Reminder(UUID.randomUUID().toString(), text, System.currentTimeMillis())
                saveTasks(newTasks)
            },
            onRemoveReminder = { reminder ->
                val newTasks = allTasks.filter { it.id != reminder.id }
                saveTasks(newTasks)
            },
            onUpdateReminder = { updated ->
                val newTasks = allTasks.map { if (it.id == updated.id) updated else it }
                saveTasks(newTasks)
            },
            onClearCompleted = {
                val newTasks = allTasks.filter { !it.isCompleted }
                saveTasks(newTasks)
            },
            onDismiss = { showTaskEditor = false }
        )
    }
}

@Composable
fun StatItem(text: String, color: Color, fontSize: TextUnit, onClick: () -> Unit) {
    Text(
        text = text,
        color = color,
        fontSize = fontSize,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.clickable(onClick = onClick),
        style = TextStyle(
            shadow = Shadow(
                color = Color.Black.copy(alpha = 1f),
                offset = Offset(0f, 0f),
                blurRadius = 6f
            )
        )
    )
}
