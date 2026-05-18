package com.soc.launcher.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.soc.launcher.*
import android.provider.Settings
import com.soc.launcher.data.model.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.widget.Toast
import com.soc.launcher.ui.components.AppUsageGraph
import com.soc.launcher.ui.components.AppUsageList
import com.soc.launcher.ui.components.RecentMedia
import com.soc.launcher.ui.components.getRecentMedia
import com.soc.launcher.ui.theme.*

/**
 * PastScreen displays historical usage data and recent media.
 *
 * Visual Consistency:
 * - Root background alpha: 0.85
 * - Section containers: FoltrainWhite.copy(alpha = 0.03f)
 *
 * Usage Logic:
 * - Weekly Graph: Displays true Screen-On Time using UsageEvents (SCREEN_INTERACTIVE).
 * - App Activity List: Displays total running time (foreground + background).
 * - Density: High-density app list with 28dp icons and 6dp vertical padding.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PastScreen(
    apps: List<AppInfo>
) {
    val context = LocalContext.current

    val imagesPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_IMAGES
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

    var hasImagesPermission by remember { mutableStateOf(context.checkSelfPermission(imagesPermission) == PackageManager.PERMISSION_GRANTED) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasImagesPermission = permissions[imagesPermission] ?: hasImagesPermission
    }

    val sharedPrefs = remember { context.getSharedPreferences("launcher_prefs", android.content.Context.MODE_PRIVATE) }

    val lifecycleOwner = LocalLifecycleOwner.current
    
    // MediaStore Images Logic
    var recentImages by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var excludedImages by remember { 
        mutableStateOf(sharedPrefs.getStringSet("excluded_images", emptySet()) ?: emptySet()) 
    }

    LaunchedEffect(hasImagesPermission, excludedImages, lifecycleOwner) {
        if (hasImagesPermission) {
            val images = withContext(Dispatchers.IO) {
                getRecentMedia(context, excludedImages)
            }
            recentImages = images
        }
    }

    val coroutineScope = rememberCoroutineScope()

    val currentExcludedImages by rememberUpdatedState(excludedImages)

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                if (hasImagesPermission) {
                    coroutineScope.launch(Dispatchers.IO) {
                         val images = getRecentMedia(context, currentExcludedImages)
                         withContext(Dispatchers.Main) { recentImages = images }
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    var appUsage by remember { mutableStateOf<List<AppUsageInfo>>(emptyList()) }
    var weeklyUsage by remember { mutableStateOf<List<Long>>(emptyList()) }
    var hasUsagePermission by remember { mutableStateOf(hasUsageStatsPermission(context)) }

    LaunchedEffect(hasUsagePermission, apps) {
        if (hasUsagePermission) {
            appUsage = getDailyAppUsage(context, apps).take(20)
            weeklyUsage = getWeeklyUsageData(context)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF050A10).copy(alpha = 0.85f))
    ) {
        // App Usage
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 0.dp)
        ) {
            AppUsageHeader(
                hasPermission = hasUsagePermission,
                onRequestPermission = {
                    val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    context.startActivity(intent)
                }
            )

            if (!hasUsagePermission) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable {
                            val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            context.startActivity(intent)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Grant Usage Stats Permission to see your phone usage.".uppercase(),
                        fontFamily = Raleway,
                        color = FoltrainWhite.copy(alpha = 0.4f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            } else if (appUsage.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "No usage data available yet.".uppercase(),
                        fontFamily = Raleway,
                        color = FoltrainWhite.copy(alpha = 0.4f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            } else {
                Spacer(Modifier.height(16.dp))
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    items(appUsage) { usage ->
                        AppUsageList(usage)
                    }
                }
            }
        }

        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(FoltrainWhite.copy(alpha = 0.05f)))

        // Screen Time
        if (hasUsagePermission && weeklyUsage.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(FoltrainWhite.copy(alpha = 0.05f))
                    .padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 12.dp)
            ) {
                Text(
                    "Screen Time".uppercase(),
                    fontFamily = Raleway,
                    color = FoltrainMain,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                AppUsageGraph(weeklyUsage)
            }
        }

        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(FoltrainWhite.copy(alpha = 0.05f)))

        // Recent Photos
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 24.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
                    .clickable {
                        val intent = Intent(Intent.ACTION_VIEW)
                        intent.type = "image/*"
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        try {
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Log.e("PastScreen", "Failed to open gallery", e)
                        }
                    }
            ) {
                Text(
                    "Recent Media (${recentImages.size})".uppercase(),
                    color = FoltrainMain,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp
                )
            }

            if (!hasImagesPermission) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp)
                        .clickable { launcher.launch(arrayOf(imagesPermission)) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Grant Permission to see recent photos".uppercase(),
                        color = FoltrainWhite.copy(alpha = 0.4f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            } else if (recentImages.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "No photos found".uppercase(),
                        color = FoltrainWhite.copy(alpha = 0.2f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3), // 3 columns
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp), // Your fixed height
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    items(recentImages) { uri ->
                        RecentMedia(
                            uri = uri,
                            onRemove = {
                                val newExcluded = excludedImages.toMutableSet()
                                newExcluded.add(uri.toString())
                                excludedImages = newExcluded
                                sharedPrefs.edit()
                                    .putStringSet("excluded_images", newExcluded)
                                    .apply()
                            }
                        )
                    }
                }
            }
        }

    }
}

@Composable
fun AppUsageHeader(
    hasPermission: Boolean,
    onRequestPermission: () -> Unit
) {
    val context = LocalContext.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(intent)
            }
    ) {
        Text(
            "App Usage".uppercase(),
            fontFamily = Raleway,
            color = FoltrainMain,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 1.sp
        )
        Spacer(Modifier.width(8.dp))
        Icon(
            Icons.Default.Info,
            contentDescription = "Info",
            tint = FoltrainMain.copy(alpha = 0.6f),
            modifier = Modifier
                .size(14.dp)
                .clickable {
                    Toast
                        .makeText(
                            context,
                            "App Activity times are individually based on total running time, not just screen time.",
                            Toast.LENGTH_LONG
                        )
                        .show()
                }
        )
    }
}
