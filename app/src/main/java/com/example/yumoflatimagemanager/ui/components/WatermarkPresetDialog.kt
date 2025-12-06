package com.example.yumoflatimagemanager.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.yumoflatimagemanager.data.WatermarkPreset
import com.example.yumoflatimagemanager.data.WatermarkType

@Composable
fun WatermarkPresetDialog(
    presets: List<WatermarkPreset>,
    onSelectPreset: (WatermarkPreset) -> Unit,
    onCreatePreset: () -> Unit,
    onEditPreset: (WatermarkPreset) -> Unit,
    onDeletePreset: (WatermarkPreset) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择水印预设") },
        text = {
            LazyColumn(modifier = Modifier.height(400.dp)) {
                items(presets) { preset ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable { onSelectPreset(preset) }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(preset.name, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    when (preset.type) {
                                        WatermarkType.TEXT -> "文字: ${preset.text}"
                                        WatermarkType.IMAGE -> "图片水印"
                                    },
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            Row {
                                IconButton(onClick = { onEditPreset(preset) }) {
                                    Icon(Icons.Default.Edit, "编辑")
                                }
                                if (!preset.isDefault) {
                                    IconButton(onClick = { onDeletePreset(preset) }) {
                                        Icon(Icons.Default.Delete, "删除")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onCreatePreset) {
                Text("创建新预设")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
