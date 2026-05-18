package com.soc.launcher.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import android.provider.MediaStore
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import android.util.Size
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import com.soc.launcher.ui.theme.*

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RecentMedia(uri: Uri, onRemove: () -> Unit) {
    val context = LocalContext.current
    var showMenu by remember { mutableStateOf(false) }

    val thumbnail by produceState<android.graphics.Bitmap?>(initialValue = null, key1 = uri) {
        value = withContext(Dispatchers.IO) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    context.contentResolver.loadThumbnail(uri, Size(300, 300), null)
                } else {
                    MediaStore.Images.Thumbnails.getThumbnail(
                        context.contentResolver,
                        uri.lastPathSegment?.toLong() ?: 0L,
                        MediaStore.Images.Thumbnails.MINI_KIND,
                        null
                    )
                }
            } catch (e: Exception) {
                Log.e("PastScreen", "Failed to load thumbnail", e)
                null
            }
        }
    }

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(12.dp))
            .background(FoltrainWhite.copy(alpha = 0.05f))
            .combinedClickable(
                onClick = {
                    val intent = Intent(Intent.ACTION_VIEW)
                    intent.setDataAndType(uri, "image/*")
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    context.startActivity(intent)
                },
                onLongClick = { showMenu = true }
            )
    ) {
        if (thumbnail != null) {
            Image(
                bitmap = thumbnail!!.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }

        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false },
            modifier = Modifier.background(Color(0xFF1A1A1A))
        ) {
            DropdownMenuItem(
                text = {
                    Text(
                        "Share",
                        fontFamily = Raleway,
                        color = FoltrainWhite
                    )
                },
                leadingIcon = { Icon(Icons.Default.Share, contentDescription = null, tint = FoltrainWhite) },
                onClick = {
                    showMenu = false
                    val intent = Intent(Intent.ACTION_SEND)
                    intent.type = "image/*"
                    intent.putExtra(Intent.EXTRA_STREAM, uri)
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    context.startActivity(Intent.createChooser(intent, "Share Image"))
                }
            )
            DropdownMenuItem(
                text = {
                    Text(
                        "Remove",
                        fontFamily = Raleway,
                        color = FoltrainWhite
                    )
                },
                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = FoltrainWhite) },
                onClick = {
                    showMenu = false
                    onRemove()
                }
            )
        }
    }
}

fun getRecentMedia(context: Context, excludedUris: Set<String>): List<Uri> {
    val media = mutableListOf<Uri>()
    val projection = arrayOf(
        MediaStore.Files.FileColumns._ID,
        MediaStore.Files.FileColumns.MEDIA_TYPE
    )

    val columnPath = MediaStore.Files.FileColumns.RELATIVE_PATH
    val selection = "(${MediaStore.Files.FileColumns.MEDIA_TYPE}=${MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE} OR ${MediaStore.Files.FileColumns.MEDIA_TYPE}=${MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO}) AND (" +
        "$columnPath LIKE ? OR " +
        "$columnPath LIKE ? OR " +
        "$columnPath LIKE ? OR " +
        "$columnPath LIKE ?)"
    val selectionArgs = arrayOf(
        "DCIM/Camera/",
        "DCIM/Screenshots/",
        "Pictures/Screenshots/",
        "Download/"
    )
    val sortOrder = "${MediaStore.Files.FileColumns.DATE_ADDED} DESC"

    context.contentResolver.query(
        MediaStore.Files.getContentUri("external"),
        projection,
        selection,
        selectionArgs,
        sortOrder
    )?.use { cursor ->
        val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
        val typeColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE)
        while (cursor.moveToNext() && media.size < 15) {
            val id = cursor.getLong(idColumn)
            val type = cursor.getInt(typeColumn)
            val baseUri = if (type == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO) {
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            } else {
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            }
            val contentUri = Uri.withAppendedPath(baseUri, id.toString())
            if (!excludedUris.contains(contentUri.toString())) {
                media.add(contentUri)
            }
        }
    }

    return media
}
