package com.soc.launcher.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.soc.launcher.getFavoriteContacts
import com.soc.launcher.searchContacts
import com.soc.launcher.data.model.AppInfo
import com.soc.launcher.data.model.ContactInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.soc.launcher.ui.theme.*

@Composable
fun FutureScreen(apps: List<AppInfo>) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    
    val sharedPrefs = remember { context.getSharedPreferences("launcher_prefs", Context.MODE_PRIVATE) }
    var pinnedPackages by remember { mutableStateOf(sharedPrefs.getStringSet("pinned_apps", emptySet()) ?: emptySet()) }
    var hiddenPackages by remember { mutableStateOf(sharedPrefs.getStringSet("hidden_apps", emptySet()) ?: emptySet()) }
    var hiddenExpanded by remember { mutableStateOf(false) }

    val pinnedApps = remember(apps, pinnedPackages) {
        apps.filter { it.packageName in pinnedPackages && it.packageName != "com.android.vending" }
    }

    val visibleApps = remember(apps, pinnedPackages, hiddenPackages) {
        apps.filter { it.packageName !in pinnedPackages && it.packageName !in hiddenPackages && it.packageName != "com.android.vending" }
    }

    val hiddenApps = remember(apps, hiddenPackages) {
        apps.filter { it.packageName in hiddenPackages && it.packageName != "com.android.vending" }
    }

    val filteredVisibleApps = remember(visibleApps, searchQuery) {
        if (searchQuery.isBlank()) visibleApps
        else visibleApps.filter { it.name.startsWith(searchQuery, ignoreCase = true) }
    }

    val filteredPinnedApps = remember(pinnedApps, searchQuery) {
        if (searchQuery.isBlank()) pinnedApps
        else pinnedApps.filter { it.name.startsWith(searchQuery, ignoreCase = true) }
    }

    val filteredHiddenApps = remember(hiddenApps, searchQuery) {
        if (searchQuery.isBlank()) hiddenApps
        else hiddenApps.filter { it.name.startsWith(searchQuery, ignoreCase = true) }
    }

    val alphabet = "#ABCDEFGHIJKLMNOPQRSTUVWXYZ".toList()
    val sectionIndices = remember(filteredVisibleApps, filteredPinnedApps, searchQuery) {
        if (searchQuery.isNotBlank()) emptyMap()
        else {
            val pinnedCount = if (filteredPinnedApps.isNotEmpty()) filteredPinnedApps.size + 1 else 0
            alphabet.map { char ->
                val indexInVisible = filteredVisibleApps.indexOfFirst {
                    if (char == '#') !it.name.first().isLetter()
                    else it.name.startsWith(char, ignoreCase = true)
                }
                char to (if (indexInVisible != -1) indexInVisible + pinnedCount else -1)
            }.filter { it.second != -1 }.toMap()
        }
    }

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    val contactsPermission = Manifest.permission.READ_CONTACTS
    var hasContactsPermission by remember { mutableStateOf(context.checkSelfPermission(contactsPermission) == PackageManager.PERMISSION_GRANTED) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasContactsPermission = isGranted
    }

    val favoriteContacts by produceState<List<ContactInfo>>(initialValue = emptyList(), hasContactsPermission) {
        if (hasContactsPermission) {
            value = withContext(Dispatchers.IO) { getFavoriteContacts(context) }
        }
    }

    var searchedContacts by remember { mutableStateOf<List<ContactInfo>>(emptyList()) }
    LaunchedEffect(searchQuery, hasContactsPermission) {
        if (searchQuery.isNotBlank() && hasContactsPermission) {
            delay(300)
            searchedContacts = withContext(Dispatchers.IO) { searchContacts(context, searchQuery) }
        } else {
            searchedContacts = emptyList()
        }
    }

    val density = LocalDensity.current
    val scrollOffset = with(density) { -10.dp.roundToPx() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF050A10).copy(alpha = 0.85f))
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            FavoritesSidebar(favoriteContacts, hasContactsPermission) { permissionLauncher.launch(contactsPermission) }

            Box(modifier = Modifier.fillMaxHeight()
                .width(1.dp)
                .background(FoltrainWhite.copy(alpha = 0.05f))
            )

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(end = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        SearchBarUI(
                            query = searchQuery,
                            onQueryChange = { searchQuery = it },
                            onClear = { searchQuery = "" }
                        )
                    }

                    val playStoreApp = remember(apps) { apps.find { it.packageName == "com.android.vending" } }
                    if (playStoreApp != null) {
                        val icon = remember(context) {
                            try {
                                context.packageManager.getApplicationIcon("com.android.vending").toBitmap().asImageBitmap()
                            } catch (e: Exception) {
                                null
                            }
                        }
                        if (icon != null) {
                            Image(
                                bitmap = icon,
                                contentDescription = "Play Store",
                                modifier = Modifier
                                    .size(44.dp)
                                    .clickable {
                                        val intent = context.packageManager.getLaunchIntentForPackage("com.android.vending")
                                        if (intent != null) {
                                            try { context.startActivity(intent) } catch (e: Exception) {}
                                        }
                                    }
                            )
                        }
                    }
                }

                if (apps.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = FoltrainWhite.copy(alpha = 0.2f))
                    }
                } else {
                    Box(
                        modifier = Modifier.weight(1f)
                    ) {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize().padding(start = 16.dp, end = if (searchQuery.isBlank()) 44.dp else 16.dp),
                            contentPadding = PaddingValues(bottom = 64.dp)
                        ) {
                            if (searchQuery.isNotBlank()) {
                                if (searchedContacts.isNotEmpty()) {
                                    item(key = "contacts_header") {
                                        Text(
                                            "CONTACTS",
                                            color = FoltrainMain,
                                            fontFamily = Raleway,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium,
                                            letterSpacing = 1.sp,
                                            modifier = Modifier.padding(top = 24.dp, bottom = 8.dp)
                                        )
                                    }
                                    items(
                                        items = searchedContacts,
                                        key = { "contact_${it.id}" },
                                        contentType = { "contact" }
                                    ) { contact ->
                                        ContactRow(contact)
                                    }
                                }

                                if (filteredVisibleApps.isNotEmpty() || filteredPinnedApps.isNotEmpty() || filteredHiddenApps.isNotEmpty()) {
                                    item(key = "apps_header") {
                                        if (searchedContacts.isNotEmpty()) {
                                            Spacer(Modifier.height(24.dp))
                                            Box(Modifier.fillMaxWidth().height(1.dp).background(FoltrainWhite.copy(alpha = 0.05f)))
                                        }
                                        Text(
                                            "APPS",
                                            color = FoltrainMain,
                                            fontFamily = Raleway,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium,
                                            letterSpacing = 1.sp,
                                            modifier = Modifier.padding(top = 24.dp, bottom = 8.dp)
                                        )
                                    }
                                }
                            }

                            // PINNED APPS
                            if (filteredPinnedApps.isNotEmpty()) {
                                if (searchQuery.isBlank()) {
                                    item(key = "pinned_label") {
                                        Text(
                                            "PINNED",
                                            color = FoltrainMain,
                                            fontFamily = Raleway,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium,
                                            letterSpacing = 1.sp,
                                            modifier = Modifier.padding(top = 24.dp, bottom = 8.dp)
                                        )
                                    }
                                }
                                items(
                                    items = filteredPinnedApps,
                                    key = { "pinned_${it.packageName}" },
                                    contentType = { "app" }
                                ) { app ->
                                    AppRow(
                                        app = app,
                                        isPinned = true,
                                        isHidden = false,
                                        onPin = {
                                            pinnedPackages = pinnedPackages - it.packageName
                                            sharedPrefs.edit().putStringSet("pinned_apps", pinnedPackages).apply()
                                        },
                                        onHide = {
                                            pinnedPackages = pinnedPackages - it.packageName
                                            hiddenPackages = hiddenPackages + it.packageName
                                            sharedPrefs.edit().putStringSet("pinned_apps", pinnedPackages).putStringSet("hidden_apps", hiddenPackages).apply()
                                        }
                                    )
                                }
                            }

                            // VISIBLE APPS
                            items(
                                count = filteredVisibleApps.size,
                                key = { index -> "app_${filteredVisibleApps[index].packageName}" },
                                contentType = { "app" }
                            ) { index ->
                                val app = filteredVisibleApps[index]
                                val firstChar = app.name.first().uppercaseChar()
                                val isFirstInCategory = (index == 0 || filteredVisibleApps[index - 1].name.first().uppercaseChar() != firstChar) && searchQuery.isBlank()

                                if (isFirstInCategory) {
                                    Text(
                                        text = if (firstChar.isLetter()) firstChar.toString() else "#",
                                        color = FoltrainMain,
                                        fontFamily = Raleway,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.padding(top = 24.dp, bottom = 8.dp)
                                    )
                                }

                                AppRow(
                                    app = app,
                                    isPinned = false,
                                    isHidden = false,
                                    onPin = {
                                        pinnedPackages = pinnedPackages + it.packageName
                                        sharedPrefs.edit().putStringSet("pinned_apps", pinnedPackages).apply()
                                    },
                                    onHide = {
                                        hiddenPackages = hiddenPackages + it.packageName
                                        sharedPrefs.edit().putStringSet("hidden_apps", hiddenPackages).apply()
                                    }
                                )
                            }

                            // HIDDEN APPS
                            if (filteredHiddenApps.isNotEmpty()) {
                                if (searchQuery.isBlank()) {
                                    item(key = "hidden_header") {
                                        Spacer(Modifier.height(32.dp))
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { hiddenExpanded = !hiddenExpanded }
                                                .padding(vertical = 12.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                "HIDDEN (${filteredHiddenApps.size})",
                                                color = FoltrainWhite.copy(alpha = 0.3f),
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Black,
                                                letterSpacing = 1.sp
                                            )
                                            Text(
                                                text = if (hiddenExpanded) "▲" else "▼",
                                                color = FoltrainWhite.copy(alpha = 0.2f),
                                                fontSize = 12.sp
                                            )
                                        }
                                    }
                                    if (hiddenExpanded) {
                                        items(
                                            items = filteredHiddenApps,
                                            key = { "hidden_${it.packageName}" },
                                            contentType = { "app" }
                                        ) { app ->
                                            AppRow(
                                                app = app,
                                                isPinned = false,
                                                isHidden = true,
                                                onPin = {
                                                    hiddenPackages = hiddenPackages - it.packageName
                                                    pinnedPackages = pinnedPackages + it.packageName
                                                    sharedPrefs.edit().putStringSet("hidden_apps", hiddenPackages).putStringSet("pinned_apps", pinnedPackages).apply()
                                                },
                                                onHide = {
                                                    hiddenPackages = hiddenPackages - it.packageName
                                                    sharedPrefs.edit().putStringSet("hidden_apps", hiddenPackages).apply()
                                                }
                                            )
                                        }
                                    }
                                } else {
                                    // In search, show hidden apps if they match, but maybe with a subtle indicator
                                    items(
                                        items = filteredHiddenApps,
                                        key = { "hidden_search_${it.packageName}" },
                                        contentType = { "app" }
                                    ) { app ->
                                        AppRow(
                                            app = app,
                                            isPinned = false,
                                            isHidden = true,
                                            onPin = {
                                                hiddenPackages = hiddenPackages - it.packageName
                                                pinnedPackages = pinnedPackages + it.packageName
                                                sharedPrefs.edit().putStringSet("hidden_apps", hiddenPackages).putStringSet("pinned_apps", pinnedPackages).apply()
                                            },
                                            onHide = {
                                                hiddenPackages = hiddenPackages - it.packageName
                                                sharedPrefs.edit().putStringSet("hidden_apps", hiddenPackages).apply()
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        if (searchQuery.isBlank()) {
                            AlphabetScroller(alphabet, sectionIndices) { char ->
                                sectionIndices[char]?.let { index ->
                                    scope.launch {
                                        listState.scrollToItem(index, scrollOffset)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SearchBarUI(query: String, onQueryChange: (String) -> Unit, onClear: () -> Unit) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        placeholder = {
            Text("Apps & Contacts",
                fontFamily = Raleway,
                color = FoltrainWhite.copy(alpha = 0.3f),
                fontSize = 14.sp,
                fontWeight = FontWeight.Normal,
            )
        },
        leadingIcon = {
            Icon(
                Icons.Default.Search,
                contentDescription = null,
                tint = FoltrainWhite.copy(alpha = 0.3f),
                modifier = Modifier.size(20.dp)
            )
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = onClear) {
                    Icon(
                        Icons.Default.Clear,
                        contentDescription = "Clear",
                        tint = FoltrainWhite.copy(alpha = 0.3f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        },
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = FoltrainMain.copy(alpha = 0.4f),
            unfocusedBorderColor = FoltrainWhite.copy(alpha = 0.1f),
            focusedTextColor = FoltrainWhite,
            unfocusedTextColor = FoltrainWhite,
            cursorColor = FoltrainMain,
            focusedContainerColor = FoltrainWhite.copy(alpha = 0.03f),
            unfocusedContainerColor = FoltrainWhite.copy(alpha = 0.03f)
        ),
        shape = RoundedCornerShape(8.dp),
        singleLine = true,
        textStyle = TextStyle(fontSize = 15.sp)
    )
}

@Composable
fun FavoritesSidebar(favoriteContacts: List<ContactInfo>, hasPermission: Boolean, onPermissionRequest: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(72.dp)
            .background(FoltrainWhite.copy(alpha = 0.03f))
            .padding(top = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "FAV",
            color = FoltrainMain,
            fontFamily = Raleway,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 1.sp
        )

        Spacer(Modifier.height(24.dp))

        if (!hasPermission) {
            IconButton(
                onClick = onPermissionRequest,
                modifier = Modifier.background(FoltrainWhite.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
            ) {
                Icon(Icons.Default.Settings, contentDescription = "Contacts Permission", tint = FoltrainWhite.copy(alpha = 0.6f))
            }
        } else if (favoriteContacts.isEmpty()) {
            Text(
                "★",
                color = FoltrainWhite.copy(alpha = 0.1f),
                fontSize = 24.sp
            )
        } else {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                favoriteContacts.forEach { contact ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        ContactAvatar(contact)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = contact.name.split(" ").firstOrNull() ?: "",
                            fontFamily = Raleway,
                            color = FoltrainWhite.copy(alpha = 0.7f),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.width(64.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ContactAvatar(contact: ContactInfo, onClick: (() -> Unit)? = null) {
    val context = LocalContext.current
    val photoBitmap by produceState<android.graphics.Bitmap?>(initialValue = null, contact.photoUri) {
        if (contact.photoUri != null) {
            withContext(Dispatchers.IO) {
                try {
                    val inputStream = context.contentResolver.openInputStream(Uri.parse(contact.photoUri))
                    value = android.graphics.BitmapFactory.decodeStream(inputStream)
                } catch (e: Exception) {
                    Log.e("FoltrainLauncher", "Error loading contact photo", e)
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .size(44.dp)
            .clickable(enabled = onClick != null || contact.id.isNotEmpty()) {
                if (onClick != null) {
                    onClick()
                } else {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_URI, contact.id))
                    try { context.startActivity(intent) } catch (e: Exception) {}
                }
            }
    ) {
        if (photoBitmap != null) {
            Image(
                bitmap = photoBitmap!!.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(14.dp))
                    .border(1.dp, FoltrainWhite.copy(alpha = 0.1f), RoundedCornerShape(14.dp)),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(FoltrainWhite.copy(alpha = 0.03f), RoundedCornerShape(14.dp))
                    .border(1.dp, FoltrainWhite.copy(alpha = 0.1f), RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = contact.name.take(1).uppercase(),
                    fontFamily = Raleway,
                    color = FoltrainWhite.copy(alpha = 0.6f),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun ContactRow(contact: ContactInfo) {
    val context = LocalContext.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                val intent = Intent(Intent.ACTION_VIEW, Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_URI, contact.id))
                try { context.startActivity(intent) } catch (e: Exception) {}
            }
            .padding(vertical = 10.dp)
    ) {
        ContactAvatar(contact, onClick = { /* Already handled by Row */ })
        Spacer(Modifier.width(16.dp))
        Text(
            contact.name,
            fontFamily = Raleway,
            color = FoltrainWhite,
            fontSize = 15.sp,
            fontWeight = FontWeight.Light
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppRow(
    app: AppInfo,
    isPinned: Boolean,
    isHidden: Boolean,
    onPin: (AppInfo) -> Unit,
    onHide: (AppInfo) -> Unit
) {
    val context = LocalContext.current
    var showMenu by remember { mutableStateOf(false) }

    Box {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = {
                        val intent = context.packageManager.getLaunchIntentForPackage(app.packageName)
                        if (intent != null) {
                            try {
                                context.startActivity(intent)
                            } catch (e: Exception) {
                            }
                        }
                    },
                    onLongClick = { showMenu = true }
                )
                .padding(vertical = 10.dp)
        ) {
            val icon = remember(app.packageName) {
                try {
                    context.packageManager.getApplicationIcon(app.packageName).toBitmap().asImageBitmap()
                } catch (e: Exception) {
                    null
                }
            }
            if (icon != null) {
                Image(
                    bitmap = icon,
                    contentDescription = null,
                    modifier = Modifier
                        .size(36.dp)
                        .then(if (isHidden) Modifier.background(Color.Black.copy(alpha = 0.5f)) else Modifier)
                )
                Spacer(Modifier.width(16.dp))
            }
            Text(
                text = app.name,
                fontFamily = Raleway,
                color = if (isHidden) FoltrainWhite.copy(alpha = 0.3f) else FoltrainWhite,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium
            )
        }

        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false },
            modifier = Modifier.background(Color(0xFF2A2A2A))
        ) {
            DropdownMenuItem(
                text = {
                    Text(
                        if (isPinned) "Unpin app" else "Pin app",
                        fontFamily = Raleway,
                        color = FoltrainWhite,
                    ) },
                onClick = {
                    onPin(app)
                    showMenu = false
                }
            )
            DropdownMenuItem(
                text = {
                    Text(
                        if (isHidden) "Unhide app" else "Hide app",
                        fontFamily = Raleway,
                        color = FoltrainWhite
                    ) },
                onClick = {
                    onHide(app)
                    showMenu = false
                }
            )
            DropdownMenuItem(
                text = {
                    Text(
                        "App info",
                        fontFamily = Raleway,
                        color = FoltrainWhite
                    ) },
                onClick = {
                    showMenu = false
                    try {
                        val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", app.packageName, null)
                        }
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        Log.e("FoltrainLauncher", "Failed to open app info", e)
                    }
                }
            )
            DropdownMenuItem(
                text = {
                    Text(
                        "Uninstall app",
                        fontFamily = Raleway,
                        color = FoltrainDangerColor
                    ) },
                onClick = {
                    showMenu = false
                    try {
                        Log.d("FoltrainLauncher", "Triggering uninstall for: ${app.packageName}")
                        val intent = Intent(Intent.ACTION_DELETE).apply {
                            data = Uri.fromParts("package", app.packageName, null)
                        }
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        Log.e("FoltrainLauncher", "Failed to start uninstall intent", e)
                    }
                }
            )
        }
    }
}

@Composable
fun BoxScope.AlphabetScroller(
    alphabet: List<Char>,
    sectionIndices: Map<Char, Int>,
    onScrollTo: (Char) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(36.dp)
            .align(Alignment.CenterEnd)
            .background(
                Brush.horizontalGradient(
                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.2f))
                )
            )
            .padding(vertical = 48.dp)
            .pointerInput(alphabet, sectionIndices) {
                detectVerticalDragGestures { change, _ ->
                    val charHeight = size.height.toFloat() / alphabet.size
                    val index = (change.position.y / charHeight).toInt().coerceIn(0, alphabet.size - 1)
                    val char = alphabet[index]
                    if (sectionIndices.containsKey(char)) {
                        onScrollTo(char)
                    }
                }
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceEvenly
    ) {
        alphabet.forEach { char ->
            val hasApps = sectionIndices.containsKey(char)
            Text(
                text = char.toString(),
                fontFamily = Raleway,
                color = if (hasApps) FoltrainWhite.copy(alpha = 0.9f) else FoltrainWhite.copy(alpha = 0.2f),
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .clickable(enabled = hasApps) { onScrollTo(char) }
                    .padding(vertical = 1.dp)
            )
        }
    }
}
