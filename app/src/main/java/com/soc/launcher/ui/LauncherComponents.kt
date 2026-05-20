package com.soc.launcher.ui

import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.Icons
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.soc.launcher.data.model.AppInfo
import com.soc.launcher.ui.theme.*

@Composable
fun AppIcon(app: AppInfo, modifier: Modifier = Modifier, showLabel: Boolean = true) {
    val context = LocalContext.current
    val icon = remember(app.packageName) {
        try {
            context.packageManager.getApplicationIcon(app.packageName).toBitmap().asImageBitmap()
        } catch (e: Exception) {
            Log.e("FoltrainLauncher", "Error loading icon for ${app.packageName}", e)
            context.packageManager.defaultActivityIcon.toBitmap().asImageBitmap()
        }
    }

    Column(
        modifier = modifier.clickable {
            val intent = context.packageManager.getLaunchIntentForPackage(app.packageName)
            if (intent != null) {
                try {
                    context.startActivity(intent)
                } catch (e: Exception) {}
            }
        },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        val boxModifier = if (showLabel) {
            Modifier.size(48.dp).background(Color.White.copy(alpha = 0.03f), RoundedCornerShape(12.dp))
        } else {
            Modifier.fillMaxSize()
        }
        Box(
            modifier = boxModifier,
            contentAlignment = Alignment.Center
        ) {
            Image(
                bitmap = icon,
                contentDescription = app.name,
                modifier = if (showLabel) Modifier.fillMaxSize(0.6f) else Modifier.fillMaxSize()
            )
        }
        if (showLabel) {
            Text(
                text = app.name,
                color = Color.White,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Composable
fun ToDoList(
    title: String,
    items: List<String>,
    onItemClick: (Int) -> Unit,
    onPermissionClick: (() -> Unit)? = null,
    onClearClick: (() -> Unit)? = null,
    maxItems: Int = 3
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                title.uppercase(),
                fontFamily = Raleway,
                color = FoltrainMain,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 1.sp
            )
            if (onClearClick != null && items.isNotEmpty()) {
                IconButton(
                    onClick = onClearClick,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        Icons.Default.Clear,
                        contentDescription = "Clear",
                        tint = FoltrainMain.copy(alpha = 0.6f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        if (onPermissionClick != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onPermissionClick() }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    "Enable permissions to see $title".uppercase(),
                    fontFamily = Raleway,
                    color = FoltrainWhite.copy(alpha = 0.4f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        } else if (items.isEmpty()) {
            Text(
                "No new $title".uppercase(),
                fontFamily = Raleway,
                color = FoltrainWhite.copy(alpha = 0.2f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        } else {
            items.take(maxItems).forEachIndexed { index, item ->
                Text(
                    text = item,
                    fontFamily = Raleway,
                    color = FoltrainWhite,
                    fontSize = 15.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onItemClick(index) }
                        .padding(vertical = 8.dp)
                )
            }
        }
    }
}
