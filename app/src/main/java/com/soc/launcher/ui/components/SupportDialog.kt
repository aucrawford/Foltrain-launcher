package com.soc.launcher.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.soc.launcher.ui.theme.*

@Composable
fun SupportDialog(
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding( horizontal = 8 .dp),
            shape = RoundedCornerShape(4.dp),
            color = Color(0xFF050A10).copy(alpha = 0.95f)
        ) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 20.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "THE FOLTRAIN ROBOTICS COMPANY",
                        fontFamily = Raleway,
                        fontWeight = FontWeight.Normal,
                        color = FoltrainMain,
                        fontSize = 14.sp
                    )
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

                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Imagine a future where robots are as ubiquitous as smart phones, and they don’t replace people, they make life easier and safer for everyone. The Foltrain Robotics Company is dedicated to the education and application of security and ethics in the use of AI and Robotics.",
                        fontFamily = Raleway,
                        color = FoltrainWhite,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Normal
                    )

                    Spacer(Modifier.width(8.dp))

                    Text(
                        "This free launcher is designed to help you monitor your phone use patterns and build better habits. If you find it useful and want to help contribute to future developments and our cause then consider purchasing the Pro Version.",
                        fontFamily = Raleway,
                        color = FoltrainWhite,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Normal
                    )
                }

                Box() {
                    TextButton(
                        modifier = Modifier.align(Alignment.Center)
                            .fillMaxWidth(1f)
                            .padding(5.dp),
                        shape = RoundedCornerShape(4.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF050A10).copy(alpha = 0.95f),
                            contentColor = FoltrainPriorityColor
                        ),
                        onClick = { /* TODO: Launch Billing Flow */ }
                    ) {
                        Text(
                            "CHECK OUT PRO VERSION",
                            fontFamily = Raleway,
                            color = FoltrainPriorityColor,
                            fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}