package vegabobo.dsusideloader.ui.maxregner

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import vegabobo.dsusideloader.core.MaxRegnerCore
import vegabobo.dsusideloader.core.SystemImagePorter
import vegabobo.dsusideloader.util.PrivilegeManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MaxRegnerMainScreen(
    maxRegnerCore: MaxRegnerCore,
    systemImagePorter: SystemImagePorter,
    privilegeManager: PrivilegeManager,
    onNavigateToSettings: () -> Unit,
    onNavigateToPorting: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0A0A0A), // MaxRegnerBackground
                        Color(0xFF1E1E1E), // MaxRegnerSurface
                    ),
                ),
            ),
    ) {
        // MaxRegner Header
        MaxRegnerHeader(onSettingsClick = onNavigateToSettings)

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // System Information Card
            item {
                SystemInfoCard()
            }

            // Main Actions
            item {
                MainActionsCard(
                    onSystemPortingClick = onNavigateToPorting,
                    onCustomRomClick = { /* Navigate to custom ROM creation */ },
                    onBackupClick = { /* Navigate to backup */ },
                )
            }

            // Quick Actions
            item {
                QuickActionsCard(
                    onRebootDSUClick = { /* Reboot to DSU */ },
                    onRebootSystemClick = { /* Reboot to system */ },
                    onClearDSUClick = { /* Clear DSU */ },
                )
            }
        }
    }
}

@Composable
fun MaxRegnerHeader(
    onSettingsClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1E1E1E), // MaxRegnerSurface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    text = "MaxRegner",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1A73E8), // MaxRegnerPrimary
                )
                Text(
                    text = "System Image Porter & Custom ROM Builder",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFE0E0E0).copy(alpha = 0.7f),
                )
            }

            IconButton(
                onClick = onSettingsClick,
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = Color(0xFF1A73E8), // MaxRegnerPrimary
                    contentColor = Color.White,
                ),
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings",
                )
            }
        }
    }
}

@Composable
fun SystemInfoCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1E1E1E), // MaxRegnerSurface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = Color(0xFF1A73E8), // MaxRegnerPrimary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "System Information",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFFE0E0E0),
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            SystemInfoRow("Device", "Android Device")
            SystemInfoRow("Android", "Android 10+")
            SystemInfoRow("DSU Support", "✓ Supported")
            SystemInfoRow("Root Access", "✓ Available")
            SystemInfoRow("System.img Support", "✓ Available")
        }
    }
}

@Composable
fun SystemInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFFE0E0E0).copy(alpha = 0.7f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFFE0E0E0),
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
fun MainActionsCard(
    onSystemPortingClick: () -> Unit,
    onCustomRomClick: () -> Unit,
    onBackupClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1E1E1E), // MaxRegnerSurface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
        ) {
            Text(
                text = "Main Actions",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFFE0E0E0),
            )

            Spacer(modifier = Modifier.height(16.dp))

            // System Image Porting
            MaxRegnerActionButton(
                title = "Port System Image",
                description = "Merge system.img with current system",
                icon = Icons.Default.Build,
                onClick = onSystemPortingClick,
                color = Color(0xFF6200EA), // RomPortPrimary
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Custom ROM Creation
            MaxRegnerActionButton(
                title = "Create Custom ROM",
                description = "Build custom ROM from scratch",
                icon = Icons.Default.Create,
                onClick = onCustomRomClick,
                color = Color(0xFFFF6D00), // SystemMergeColor
            )

            Spacer(modifier = Modifier.height(12.dp))

            // System Backup
            MaxRegnerActionButton(
                title = "Backup System",
                description = "Create backup of current system",
                icon = Icons.Default.Backup,
                onClick = onBackupClick,
                color = Color(0xFF4CAF50), // SuccessColor
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MaxRegnerActionButton(
    title: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    color: Color,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = color.copy(alpha = 0.1f),
        ),
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(24.dp),
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = Color(0xFFE0E0E0),
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFE0E0E0).copy(alpha = 0.7f),
                )
            }

            Icon(
                imageVector = Icons.Default.ArrowForward,
                contentDescription = null,
                tint = color,
            )
        }
    }
}

@Composable
fun QuickActionsCard(
    onRebootDSUClick: () -> Unit,
    onRebootSystemClick: () -> Unit,
    onClearDSUClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1E1E1E), // MaxRegnerSurface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
        ) {
            Text(
                text = "Quick Actions",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFFE0E0E0),
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                QuickActionButton(
                    text = "Reboot DSU",
                    icon = Icons.Default.RestartAlt,
                    onClick = onRebootDSUClick,
                    modifier = Modifier.weight(1f),
                )

                QuickActionButton(
                    text = "Reboot System",
                    icon = Icons.Default.PowerSettingsNew,
                    onClick = onRebootSystemClick,
                    modifier = Modifier.weight(1f),
                )

                QuickActionButton(
                    text = "Clear DSU",
                    icon = Icons.Default.Clear,
                    onClick = onClearDSUClick,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
fun QuickActionButton(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFF1A73E8), // MaxRegnerPrimary
            contentColor = Color.White,
        ),
        shape = RoundedCornerShape(8.dp),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(vertical = 4.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}
