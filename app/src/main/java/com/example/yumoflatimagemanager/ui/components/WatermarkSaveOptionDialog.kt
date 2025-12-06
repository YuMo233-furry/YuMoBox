package com.example.yumoflatimagemanager.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.yumoflatimagemanager.data.WatermarkSaveOption

@Composable
fun WatermarkSaveOptionDialog(
    imageCount: Int,
    onConfirm: (WatermarkSaveOption) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedOption by remember { mutableStateOf(WatermarkSaveOption.CREATE_NEW) }
    var showOverwriteConfirm by remember { mutableStateOf(false) }
    
    if (showOverwriteConfirm) {
        AlertDialog(
            onDismissRequest = { showOverwriteConfirm = false },
            title = { Text("确认覆盖") },
            text = { 
                Text("确定要覆盖原图吗？\n\n这将修改 $imageCount 张图片，此操作不可撤销！") 
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showOverwriteConfirm = false
                        onConfirm(WatermarkSaveOption.OVERWRITE)
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("确认覆盖")
                }
            },
            dismissButton = {
                TextButton(onClick = { showOverwriteConfirm = false }) {
                    Text("取消")
                }
            }
        )
    }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择保存方式") },
        text = {
            Column {
                Text("将为 $imageCount 张图片添加水印")
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // 创建新文件选项
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selectedOption = WatermarkSaveOption.CREATE_NEW }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = selectedOption == WatermarkSaveOption.CREATE_NEW,
                        onClick = { selectedOption = WatermarkSaveOption.CREATE_NEW }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text("创建新文件", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "保留原图，创建带水印的新文件",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                // 覆盖原图选项
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selectedOption = WatermarkSaveOption.OVERWRITE }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = selectedOption == WatermarkSaveOption.OVERWRITE,
                        onClick = { selectedOption = WatermarkSaveOption.OVERWRITE }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text("覆盖原图", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "直接修改原图（不可撤销）",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (selectedOption == WatermarkSaveOption.OVERWRITE) {
                        showOverwriteConfirm = true
                    } else {
                        onConfirm(selectedOption)
                    }
                }
            ) {
                Text("下一步")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
