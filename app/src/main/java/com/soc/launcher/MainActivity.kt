package com.soc.launcher

import com.soc.launcher.data.model.*
import com.soc.launcher.util.AppCategoryHelper
import com.soc.launcher.ui.*
import android.Manifest
import android.app.ActivityManager
import android.app.AppOpsManager
import android.app.WallpaperManager
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Process
import android.os.StatFs
import android.provider.CallLog
import android.provider.ContactsContract
import android.util.Log
import android.app.usage.UsageEvents
import android.os.*
import android.provider.Settings
import android.net.Uri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.core.app.NotificationManagerCompat
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.drawable.toBitmap
import androidx.core.view.WindowCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.*

class MainActivity : ComponentActivity() {

    @OptIn(ExperimentalFoundationApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            val launcher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions()
            ) { _ -> }

            LaunchedEffect(Unit) {
                val permissions = mutableListOf(
                    Manifest.permission.READ_CONTACTS,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.READ_CALENDAR
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    permissions.add(Manifest.permission.READ_MEDIA_IMAGES)
                } else {
                    permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
                }
                launcher.launch(permissions.toTypedArray())
            }

            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color.Black
                ) {
                    FoltrainLauncher()
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FoltrainLauncher() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val sharedPrefs = remember { context.getSharedPreferences("launcher_prefs", Context.MODE_PRIVATE) }

    val wallpaperManager = remember { WallpaperManager.getInstance(context) }

    var refreshAppsCounter by remember { mutableStateOf(0) }

    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                Log.d("FoltrainLauncher", "Package broadcast received: ${intent?.action}")
                refreshAppsCounter++
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }
        context.registerReceiver(receiver, filter)
        onDispose {
            context.unregisterReceiver(receiver)
        }
    }

    val wallpaperPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_IMAGES
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

    var hasWallpaperPermission by remember {
        mutableStateOf(context.checkSelfPermission(wallpaperPermission) == PackageManager.PERMISSION_GRANTED)
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasWallpaperPermission = context.checkSelfPermission(wallpaperPermission) == PackageManager.PERMISSION_GRANTED
                refreshAppsCounter++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val defaultWallpaper by produceState<Bitmap?>(initialValue = null, hasWallpaperPermission) {
        if (hasWallpaperPermission) {
            withContext(Dispatchers.IO) {
                try {
                    val drawable = try {
                        wallpaperManager.peekDrawable()
                    } catch (e: SecurityException) {
                        null
                    }
                    if (drawable != null) {
                        val metrics = context.resources.displayMetrics
                        val screenWidth = metrics.widthPixels
                        val screenHeight = metrics.heightPixels
                        
                        val intrinsicWidth = drawable.intrinsicWidth
                        val intrinsicHeight = drawable.intrinsicHeight

                        val bitmap = if (intrinsicWidth > 0 && intrinsicHeight > 0) {
                            // Calculate scale to fill the screen while preserving aspect ratio
                            val scale = Math.max(
                                screenWidth.toFloat() / intrinsicWidth,
                                screenHeight.toFloat() / intrinsicHeight
                            )
                            val targetWidth = (intrinsicWidth * scale).toInt()
                            val targetHeight = (intrinsicHeight * scale).toInt()
                            
                            // Use RGB_565 to save 50% RAM compared to ARGB_8888.
                            // Wallpapers generally don't need alpha transparency.
                            drawable.toBitmap(targetWidth, targetHeight, Bitmap.Config.RGB_565)
                        } else {
                            // Fallback if intrinsic dimensions are not available
                            drawable.toBitmap(screenWidth, screenHeight, Bitmap.Config.RGB_565)
                        }
                        value = bitmap
                    } else {
                        value = null
                    }
                } catch (e: SecurityException) {
                    Log.e("FoltrainLauncher", "SecurityException reading wallpaper", e)
                } catch (e: Exception) {
                    Log.e("FoltrainLauncher", "Error reading wallpaper", e)
                }
            }
        }
    }
    
    val apps by produceState(initialValue = emptyList<AppInfo>(), refreshAppsCounter) {
        withContext(Dispatchers.IO) {
            val result = getInstalledApps(context)
            Log.d("FoltrainLauncher", "Loaded ${result.size} apps (refresh: $refreshAppsCounter)")
            value = result
        }
    }

    var aiAppPackage by remember { mutableStateOf(sharedPrefs.getString("ai_pkg", "com.google.android.apps.gemini") ?: "com.google.android.apps.gemini") }
    var mapAppPackage by remember { mutableStateOf(sharedPrefs.getString("map_pkg", "com.google.android.apps.maps") ?: "com.google.android.apps.maps") }
    val pagerState = rememberPagerState(initialPage = 1, pageCount = { 3 })

    Box(modifier = Modifier.fillMaxSize()) {
        if (defaultWallpaper != null) {
            Image(
                bitmap = defaultWallpaper!!.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            // Default background if no wallpaper is found
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color(0xFF0F2027), Color(0xFF203A43), Color(0xFF2C5364))
                        )
                    )
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onLongPress = {
                            val intent = Intent(Intent.ACTION_SET_WALLPAPER)
                            context.startActivity(Intent.createChooser(intent, "Select Wallpaper"))
                        }
                    )
                }
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                when (page) {
                    0 -> PastScreen(
                        apps = apps
                    )
                    1 -> PresentScreen(
                        aiAppPackage = aiAppPackage,
                        mapAppPackage = mapAppPackage,
                        apps = apps,
                        onAiAppSelected = { pkg ->
                            aiAppPackage = pkg
                            sharedPrefs.edit().putString("ai_pkg", pkg).apply()
                        },
                        onMapAppSelected = { pkg ->
                            mapAppPackage = pkg
                            sharedPrefs.edit().putString("map_pkg", pkg).apply()
                        }
                    )
                    2 -> FutureScreen(apps)
                }
            }
        }
    }
}

fun getInstalledApps(context: Context): List<AppInfo> {
    val pm = context.packageManager
    val appList = mutableListOf<AppInfo>()
    val packageSet = mutableSetOf<String>()
    
    try {
        // 1. Standard Launcher query
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolved = pm.queryIntentActivities(intent, 0)

        resolved.forEach { resolveInfo ->
            val pkg = resolveInfo.activityInfo.packageName
            if (packageSet.add(pkg)) {
                val label = resolveInfo.loadLabel(pm).toString()
                val category = AppCategoryHelper.determineCategory(pkg, label)
                appList.add(AppInfo(label, pkg, category))
            }
        }
    } catch (e: Exception) {
        Log.e("FoltrainLauncher", "Error in main app query", e)
    }

    // 2. Comprehensive fallback scan
    try {
        pm.getInstalledPackages(0).forEach { pkgInfo ->
            val pkg = pkgInfo.packageName
            if (!packageSet.contains(pkg)) {
                val launchIntent = pm.getLaunchIntentForPackage(pkg)
                if (launchIntent != null) {
                    val label = pm.getApplicationLabel(pkgInfo.applicationInfo).toString()
                    val category = AppCategoryHelper.determineCategory(pkg, label)
                    appList.add(AppInfo(label, pkg, category))
                    packageSet.add(pkg)
                }
            }
        }
    } catch (e: Exception) {
        Log.e("FoltrainLauncher", "Error in fallback app query", e)
    }
    
    return appList.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
}

fun getStorageInfo(): Int {
    val path = Environment.getDataDirectory()
    val stat = StatFs(path.path)
    val blockSize = stat.blockSizeLong
    val totalBlocks = stat.blockCountLong
    val availableBlocks = stat.availableBlocksLong
    val totalSpace = totalBlocks * blockSize
    val availableSpace = availableBlocks * blockSize
    return (100 - (availableSpace.toDouble() / totalSpace.toDouble() * 100)).toInt()
}

fun getMemoryInfo(context: Context): Int {
    val mi = ActivityManager.MemoryInfo()
    val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    activityManager.getMemoryInfo(mi)
    val percentUsed = 100 - (mi.availMem.toDouble() / mi.totalMem.toDouble() * 100).toInt()
    return percentUsed
}

fun getBatteryTemperature(context: Context): Float {
    val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    val temp = intent?.getIntExtra(android.os.BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
    return temp / 10f
}

fun getMostUsedApps(context: Context, allApps: List<AppInfo>): List<AppInfo> {
    if (!hasUsageStatsPermission(context)) {
        return allApps.take(10)
    }
    val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
    val endTime = System.currentTimeMillis()
    val startTime = endTime - 1000 * 60 * 60 * 24 * 7
    val stats = usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_WEEKLY, startTime, endTime)

    val sortedStats = stats.sortedByDescending { it.totalTimeInForeground }
    
    // Filter out apps that are home launchers
    val packageManager = context.packageManager
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
    val launcherPackages = packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
        .map { it.activityInfo.packageName }
        .toSet()

    val mostUsedPackages = sortedStats
        .map { it.packageName }
        .filter { it !in launcherPackages }
        .distinct()
        .take(10)
    
    val result = mutableListOf<AppInfo>()
    mostUsedPackages.forEach { pkg ->
        allApps.find { it.packageName == pkg }?.let { result.add(it) }
    }
    
    if (result.size < 10) {
        val remaining = allApps
            .filter { it !in result && it.packageName !in launcherPackages }
            .take(10 - result.size)
        result.addAll(remaining)
    }
    
    return result
}

fun hasUsageStatsPermission(context: Context): Boolean {
    val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
    val mode = appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
    return mode == AppOpsManager.MODE_ALLOWED
}

fun getFavoriteContacts(context: Context): List<ContactInfo> {
    val contacts = mutableListOf<ContactInfo>()
    if (context.checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
        return emptyList()
    }
    try {
        val cursor = context.contentResolver.query(
            ContactsContract.Contacts.CONTENT_URI,
            arrayOf(
                ContactsContract.Contacts._ID,
                ContactsContract.Contacts.DISPLAY_NAME,
                ContactsContract.Contacts.PHOTO_THUMBNAIL_URI
            ),
            "${ContactsContract.Contacts.STARRED} = 1",
            null,
            "${ContactsContract.Contacts.DISPLAY_NAME} ASC"
        )
        cursor?.use {
            val idIndex = it.getColumnIndex(ContactsContract.Contacts._ID)
            val nameIndex = it.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME)
            val photoIndex = it.getColumnIndex(ContactsContract.Contacts.PHOTO_THUMBNAIL_URI)
            while (it.moveToNext()) {
                val id = it.getString(idIndex)
                val name = it.getString(nameIndex)
                val photoUri = it.getString(photoIndex)
                contacts.add(ContactInfo(id, name, photoUri))
            }
        }
    } catch (e: Exception) {
        Log.e("FoltrainLauncher", "Error querying contacts", e)
    }
    return contacts
}

fun searchContacts(context: Context, query: String): List<ContactInfo> {val contacts = mutableMapOf<String, ContactInfo>()
    if (context.checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
        return emptyList()
    }

    // Using Contacts.CONTENT_URI ensures we find ALL contacts, even those without phone numbers
    val uri = ContactsContract.Contacts.CONTENT_URI
    val projection = arrayOf(
        ContactsContract.Contacts._ID,
        ContactsContract.Contacts.DISPLAY_NAME,
        ContactsContract.Contacts.PHOTO_THUMBNAIL_URI
    )

    // Search by name across all contacts
    val selection = "${ContactsContract.Contacts.DISPLAY_NAME} LIKE ?"
    val selectionArgs = arrayOf("$query%")
    val sortOrder = "${ContactsContract.Contacts.DISPLAY_NAME} ASC"

    try {
        context.contentResolver.query(uri, projection, selection, selectionArgs, sortOrder)?.use { cursor ->
            val idIndex = cursor.getColumnIndex(ContactsContract.Contacts._ID)
            val nameIndex = cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME)
            val photoIndex = cursor.getColumnIndex(ContactsContract.Contacts.PHOTO_THUMBNAIL_URI)

            while (cursor.moveToNext() && contacts.size < 5) {
                val id = cursor.getString(idIndex)
                if (!contacts.containsKey(id)) {
                    val name = cursor.getString(nameIndex) ?: "Unknown"
                    val photoUri = cursor.getString(photoIndex)
                    contacts[id] = ContactInfo(id, name, photoUri)
                }
            }
        }
    } catch (e: Exception) {
        Log.e("FoltrainLauncher", "Error searching contacts", e)
    }
    return contacts.values.toList()
}

fun isNotificationServiceEnabled(context: Context): Boolean {
    return NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
}

fun isIgnoringBatteryOptimizations(context: Context): Boolean {
    val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    return powerManager.isIgnoringBatteryOptimizations(context.packageName)
}

fun requestIgnoreBatteryOptimizations(context: Context) {
    try {
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${context.packageName}")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        Log.e("FoltrainLauncher", "Could not request ignore battery optimizations", e)
    }
}

fun getDailyAppUsage(context: Context, allApps: List<AppInfo>, filterApps: Boolean = true): List<AppUsageInfo> {
    if (!hasUsageStatsPermission(context)) return emptyList()
    
    val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
    val now = System.currentTimeMillis()
    val calendar = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    val startOfDay = calendar.timeInMillis
    
    // queryUsageStats with manual aggregation is more reliable for "today's" usage
    val statsList = usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startOfDay, now)
    val aggregatedMap = mutableMapOf<String, Long>()

    if (statsList.isNullOrEmpty()) {
        usageStatsManager.queryAndAggregateUsageStats(startOfDay, now).forEach { (pkg, stat) ->
            aggregatedMap[pkg] = stat.totalTimeInForeground
        }
    } else {
        for (stat in statsList) {
            if (stat.totalTimeInForeground > 0) {
                aggregatedMap[stat.packageName] = (aggregatedMap[stat.packageName] ?: 0L) + stat.totalTimeInForeground
            }
        }
    }

    if (!filterApps) {
        return aggregatedMap.map { (pkg, totalTime) ->
            AppUsageInfo(pkg, pkg, totalTime)
        }
    }

    val pm = context.packageManager
    val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
    val launcherPackages = pm.queryIntentActivities(homeIntent, PackageManager.MATCH_DEFAULT_ONLY)
        .map { it.activityInfo.packageName }
        .toMutableSet()
    launcherPackages.add(context.packageName)

    val appMap = allApps.associateBy { it.packageName }

    return aggregatedMap.entries
        .filter { it.value >= 60000 && it.key !in launcherPackages }
        .mapNotNull { (pkg, totalTime) ->
            val appInfo = appMap[pkg]
            if (appInfo != null) {
                AppUsageInfo(pkg, appInfo.name, totalTime)
            } else {
                try {
                    val ai = pm.getApplicationInfo(pkg, 0)
                    if (pm.getLaunchIntentForPackage(pkg) != null) {
                        AppUsageInfo(pkg, pm.getApplicationLabel(ai).toString(), totalTime)
                    } else null
                } catch (e: Exception) { null }
            }
        }
        .sortedByDescending { it.totalTimeInForeground }
}

fun getWeeklyUsageData(context: Context): List<Long> {
    if (!hasUsageStatsPermission(context)) return emptyList()
    val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
    
    val calendar = Calendar.getInstance()
    calendar.set(Calendar.HOUR_OF_DAY, 0)
    calendar.set(Calendar.MINUTE, 0)
    calendar.set(Calendar.SECOND, 0)
    calendar.set(Calendar.MILLISECOND, 0)
    
    val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
    val daysSinceSunday = dayOfWeek - 1
    
    val weeklyData = mutableListOf<Long>()
    
    // Move to the beginning of the week (Sunday)
    calendar.add(Calendar.DAY_OF_YEAR, -daysSinceSunday)
    
    for (i in 0..6) {
        if (i > daysSinceSunday) {
            weeklyData.add(0L)
            continue
        }
        
        val startOfDay = calendar.timeInMillis
        val endOfDay = if (i == daysSinceSunday) System.currentTimeMillis() else startOfDay + (24 * 60 * 60 * 1000) - 1
        
        // Calculate screen-on time for each day using UsageEvents
        val events = usageStatsManager.queryEvents(startOfDay, endOfDay)
        val event = android.app.usage.UsageEvents.Event()
        var dailyTotal = 0L
        var screenOnTimestamp = -1L

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == android.app.usage.UsageEvents.Event.SCREEN_INTERACTIVE) {
                screenOnTimestamp = event.timeStamp
            } else if (event.eventType == android.app.usage.UsageEvents.Event.SCREEN_NON_INTERACTIVE) {
                if (screenOnTimestamp != -1L) {
                    dailyTotal += event.timeStamp - screenOnTimestamp
                    screenOnTimestamp = -1L
                }
            }
        }

        if (screenOnTimestamp != -1L && i == daysSinceSunday) {
            dailyTotal += System.currentTimeMillis() - screenOnTimestamp
        }
        
        // Fallback to max foreground app if no events found
        if (dailyTotal == 0L) {
            val stats = usageStatsManager.queryAndAggregateUsageStats(startOfDay, endOfDay)
            dailyTotal = stats.values.maxOfOrNull { it.totalTimeInForeground } ?: 0L
        }

        weeklyData.add(dailyTotal)
        calendar.add(Calendar.DAY_OF_YEAR, 1)
    }
    
    return weeklyData
}

fun getScreenOnTime(context: Context): Long {
    if (!hasUsageStatsPermission(context)) return 0L

    val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
    val now = System.currentTimeMillis()
    val calendar = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    val startOfDay = calendar.timeInMillis

    val events = usageStatsManager.queryEvents(startOfDay, now)
    val event = UsageEvents.Event()
    var totalTime = 0L
    var screenOnTimestamp = -1L

    // We track screen interactive events to get true screen-on time
    while (events.hasNextEvent()) {
        events.getNextEvent(event)
        if (event.eventType == UsageEvents.Event.SCREEN_INTERACTIVE) {
            screenOnTimestamp = event.timeStamp
        } else if (event.eventType == UsageEvents.Event.SCREEN_NON_INTERACTIVE) {
            if (screenOnTimestamp != -1L) {
                totalTime += event.timeStamp - screenOnTimestamp
                screenOnTimestamp = -1L
            }
        }
    }

    // If the screen is currently on, add the time from last screenOn to now
    if (screenOnTimestamp != -1L) {
        totalTime += now - screenOnTimestamp
    }

    // Fallback: If no screen events were found (some devices/versions don't report them well),
    // we use the aggregate of the most used app's intervals as a heuristic, 
    // but typically SCREEN_INTERACTIVE is reliable on modern Android.
    if (totalTime == 0L) {
        val stats = usageStatsManager.queryAndAggregateUsageStats(startOfDay, now)
        // This is still an approximation but avoids double counting overlapping apps
        // by taking the maximum foreground time of any single app as a baseline, 
        // though it's not perfect. 
        totalTime = stats.values.maxOfOrNull { it.totalTimeInForeground } ?: 0L
    }

    return totalTime
}
