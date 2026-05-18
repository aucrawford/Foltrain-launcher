package com.soc.launcher.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.soc.launcher.data.model.Reminder
import com.soc.launcher.data.model.TaskImportance
import com.soc.launcher.ui.theme.*

@Composable
fun TaskEditor(
    reminders: List<Reminder>,
    onAddReminder: (String) -> Unit,
    onRemoveReminder: (Reminder) -> Unit,
    onUpdateReminder: (Reminder) -> Unit,
    onClearCompleted: () -> Unit,
    onDismiss: () -> Unit
) {
    var newTaskText by remember { mutableStateOf("") }

    // Use a stable order for the list while the modal is open
    val stableOrder = remember { mutableStateListOf<String>().apply { addAll(reminders.map { it.id }) } }

    // Sync the stable order when items are added or removed
    LaunchedEffect(reminders) {
        val currentIds = reminders.map { it.id }.toSet()
        val existingIds = stableOrder.toSet()

        // Add new IDs to the top
        val newIds = reminders.filter { it.id !in existingIds }.map { it.id }
        if (newIds.isNotEmpty()) {
            stableOrder.addAll(0, newIds)
        }

        // Remove IDs that no longer exist
        stableOrder.retainAll { it in currentIds }
    }

    val displayReminders = stableOrder.mapNotNull { id -> reminders.find { it.id == id } }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(1f)
                .padding(5.dp),
            shape = RoundedCornerShape(4.dp),
            color = Color(0xFF050A10).copy(alpha = 0.95f)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "TASKS",
                        fontFamily = Raleway,
                        color = FoltrainMain,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 1.sp
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (reminders.any { it.isCompleted }) {
                            Text(
                                text = "CLEAR COMPLETED",
                                fontFamily = Raleway,
                                color = FoltrainDangerColor.copy(alpha = 0.7f),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.clickable { onClearCompleted() }
                            )
                            Spacer(Modifier.width(16.dp))
                        }

                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = FoltrainWhite.copy(alpha = 0.5f)
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextField(
                        value = newTaskText,
                        onValueChange = { newTaskText = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("I Must...",
                            fontFamily = Raleway,
                            color = FoltrainWhite.copy(alpha = 0.2f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Normal
                        ) },
                        colors = TextFieldDefaults.colors(
                            unfocusedContainerColor = Color.Transparent,
                            focusedContainerColor = Color.Transparent,
                            unfocusedTextColor = FoltrainWhite,
                            focusedTextColor = FoltrainWhite,
                            cursorColor = FoltrainMain,
                            focusedIndicatorColor = FoltrainMain.copy(alpha = 0.5f),
                            unfocusedIndicatorColor = FoltrainWhite.copy(alpha = 0.1f)
                        ),
                        singleLine = true
                    )
                    Spacer(Modifier.width(8.dp))
                    IconButton(
                        onClick = {
                            if (newTaskText.isNotBlank()) {
                                onAddReminder(newTaskText)
                                newTaskText = ""
                            }
                        },
                        modifier = Modifier.background(FoltrainMain.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = "Add",
                            tint = FoltrainMain
                        )
                    }
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(displayReminders, key = { it.id }) { reminder ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Completion Toggle
                            Checkbox(
                                checked = reminder.isCompleted,
                                onCheckedChange = { onUpdateReminder(reminder.copy(isCompleted = it)) },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = FoltrainGoodColor,
                                    uncheckedColor = FoltrainWhite.copy(alpha = 0.3f),
                                    checkmarkColor = Color.Black
                                ),
                                modifier = Modifier.size(24.dp)
                            )
                            
                            Spacer(Modifier.width(12.dp))

                            // Importance Cycle
                            val (icon, color) = when (reminder.importance) {
                                TaskImportance.REGULAR -> Icons.Outlined.Flag to FoltrainWhite.copy(alpha = 0.2f)
                                TaskImportance.PRIORITY -> Icons.Default.PriorityHigh to FoltrainPriorityColor
                                TaskImportance.MAJOR -> Icons.Default.LocalFireDepartment to FoltrainDangerColor
                            }
                            val taskColor = when (reminder.importance) {
                                TaskImportance.REGULAR -> FoltrainWhite
                                TaskImportance.PRIORITY -> FoltrainPriorityColor
                                TaskImportance.MAJOR -> FoltrainDangerColor
                            }
                            Box(
                                modifier = Modifier.background(FoltrainMain.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = "Importance",
                                    tint = if (reminder.isCompleted) FoltrainWhite.copy(alpha = 0.1f) else color,
                                    modifier = Modifier
                                        .size(25.dp)
                                        .padding(4.dp, 2.dp)
                                        .clickable {
                                            val nextImportance = when (reminder.importance) {
                                                TaskImportance.REGULAR -> TaskImportance.PRIORITY
                                                TaskImportance.PRIORITY -> TaskImportance.MAJOR
                                                TaskImportance.MAJOR -> TaskImportance.REGULAR
                                            }
                                            onUpdateReminder(reminder.copy(importance = nextImportance))
                                        }
                                )
                            }

                            Spacer(Modifier.width(12.dp))

                            var isEditing by remember { mutableStateOf(false) }
                            var editedText by remember(reminder.text) { mutableStateOf(reminder.text) }

                            if (isEditing) {
                                TextField(
                                    value = editedText,
                                    onValueChange = { editedText = it },
                                    modifier = Modifier.weight(1f),
                                    colors = TextFieldDefaults.colors(
                                        unfocusedContainerColor = Color.Transparent,
                                        focusedContainerColor = Color.Transparent,
                                        unfocusedTextColor = FoltrainWhite,
                                        focusedTextColor = FoltrainWhite,
                                        cursorColor = FoltrainMain,
                                        focusedIndicatorColor = FoltrainMain.copy(alpha = 0.5f),
                                        unfocusedIndicatorColor = FoltrainWhite.copy(alpha = 0.1f)
                                    ),
                                    singleLine = true,
                                    textStyle = LocalTextStyle.current.copy(
                                        fontSize = 12.sp,
                                        fontFamily = Raleway,
                                        fontWeight = FontWeight.Normal
                                    )
                                )
                                IconButton(
                                    modifier = Modifier.background(FoltrainMain.copy(alpha = 0.2f), RoundedCornerShape(8.dp)),
                                    onClick = {
                                        if (editedText.isNotBlank()) {
                                            onUpdateReminder(reminder.copy(text = editedText))
                                        }
                                        isEditing = false
                                    }
                                ) {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = "Save",
                                        tint = FoltrainGoodColor
                                    )
                                }
                            } else {
                                Text(
                                    text = reminder.text.uppercase(),
                                    color = if (reminder.isCompleted) FoltrainGoodColor else taskColor,
                                    fontSize = 12.sp,
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { isEditing = true },
                                    fontFamily = Raleway,
                                    fontWeight = FontWeight.Normal,
                                    textDecoration = if (reminder.isCompleted) TextDecoration.LineThrough else null
                                )
                            }

                            IconButton(
                                onClick = { onRemoveReminder(reminder) }
                            ) {
                                Box(modifier = Modifier.background(FoltrainMain.copy(alpha = 0.2f), RoundedCornerShape(8.dp)))
                                {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "Delete",
                                        tint = FoltrainWhite,
                                        modifier = Modifier.size(20.dp)
                                            .padding(4.dp, 2.dp)
                                    )
                                }
                            }
                        }
                    }
                    if (reminders.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "ALL TASKS COMPLETED",
                                    color = FoltrainWhite.copy(alpha = 0.2f),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                )
                            }
                        }
                    }
                }

            }
        }
    }
}
