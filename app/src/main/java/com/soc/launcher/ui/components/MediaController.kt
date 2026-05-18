package com.soc.launcher.ui.components

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.soc.launcher.isNotificationServiceEnabled
import com.soc.launcher.MediaNotificationListener
import com.soc.launcher.ui.theme.*

fun resumeOrOpenApp(context: Context, packageName: String, isPlaying: Boolean) {
    if (isPlaying) {
        MediaNotificationListener.sendCommand("pause")
    } else {
        // Try sending the play command to the session listener
        MediaNotificationListener.sendCommand("play")
    }
}

fun openMediaApp(context: Context, packageName: String) {
    if (packageName.isNotEmpty()) {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
        launchIntent?.let {
            it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(it)
        }
    }
}

@Composable
fun MediaController(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val sharedPrefs = remember { context.getSharedPreferences("launcher_prefs", Context.MODE_PRIVATE) }
    val mediaInfo by MediaNotificationListener.mediaInfo.collectAsState()

    var lastTitle by remember { mutableStateOf(sharedPrefs.getString("last_title", "") ?: "") }
    var lastArtist by remember { mutableStateOf(sharedPrefs.getString("last_artist", "") ?: "") }

    LaunchedEffect(mediaInfo.title, mediaInfo.artist) {
        if (mediaInfo.title.isNotEmpty()) {
            lastTitle = mediaInfo.title
            lastArtist = mediaInfo.artist

            // Save it to storage so it survives app closures/reboots
            sharedPrefs.edit()
                .putString("last_title", lastTitle)
                .putString("last_artist", lastArtist)
                .putString("last_package", mediaInfo.packageName)
                .apply()
        }
    }

    val displayTitle = if (mediaInfo.title.isNotEmpty()) mediaInfo.title else lastTitle
    val displayArtist = if (mediaInfo.artist.isNotEmpty()) mediaInfo.artist else lastArtist
    val isLive = mediaInfo.title.isNotEmpty()
    val contentAlpha = if (isLive) 1.0f else 0.8f

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF050A10).copy(alpha = 0.7f))
            .padding(16.dp)
    ) {

        val lastPackage = remember { sharedPrefs.getString("last_package", "") ?: "" }
        val currentPackage = if (mediaInfo.packageName.isNotEmpty()) mediaInfo.packageName else lastPackage

        if (displayTitle.isNotEmpty()) {
            Text(
                text = displayTitle,
                fontFamily = Raleway,
                color = FoltrainMain.copy(alpha = contentAlpha),
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.clickable {
                    openMediaApp(context, currentPackage)
                }
            )
            Text(
                text = displayArtist,
                fontFamily = Raleway,
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 15.sp,
                fontWeight = FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        } else {
            Text(
                text = "No Media",
                fontFamily = Raleway,
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                )
            Text(
                text = "Select a Media App",
                fontFamily = Raleway,
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 15.sp,
                fontWeight = FontWeight.Normal,
            )
        }

        Spacer(Modifier.height(8.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(28.dp)
        ) {
            IconButton(
                onClick = { MediaNotificationListener.sendCommand("previous") },
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    Icons.Default.SkipPrevious,
                    contentDescription = "Previous",
                    tint = Color.White,
                    modifier = Modifier.size(40.dp)
                )
            }

            IconButton(
                onClick = {
                   resumeOrOpenApp(context, currentPackage, mediaInfo.isPlaying)
                },
                modifier = Modifier.size(48.dp)
            ) {
                if (mediaInfo.isPlaying) {
                    Icon(
                        Icons.Default.Pause,
                        contentDescription = "Pause",
                        tint = Color.White,
                        modifier = Modifier.size(40.dp)
                    )
                } else {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = "Play",
                        tint = Color.White,
                        modifier = Modifier.size(40.dp)
                    )
                }
            }
            IconButton(
                onClick = { MediaNotificationListener.sendCommand("next") },
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    Icons.Default.SkipNext,
                    contentDescription = "Next",
                    tint = Color.White,
                    modifier = Modifier.size(40.dp)
                )
            }
        }
    }

    if (mediaInfo.title.isEmpty()) {
        val context = LocalContext.current
        var notificationPermissionGranted by remember { mutableStateOf(isNotificationServiceEnabled(context)) }
        val lifecycleOwner = LocalLifecycleOwner.current

        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    notificationPermissionGranted = isNotificationServiceEnabled(context)
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }

        if (!notificationPermissionGranted) {
            Button(
                onClick = {
                    context.startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
                },
                modifier = Modifier.padding(vertical = 16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.1f)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Enable Media Controls", color = Color.White, fontSize = 12.sp)
            }
        }
    }
}