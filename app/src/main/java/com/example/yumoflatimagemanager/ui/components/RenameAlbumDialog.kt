package com.example.yumoflatimagemanager.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.yumoflatimagemanager.data.Album

/**
 * 重命名相册对话框组件
 */
@Composable
fun RenameAlbumDialog(
    album: Album,
    onRename: (String) -> Unit,
    onCancel: () -> Unit
) {
    // 存储用户输入的新名称
    var newName by remember { mutableStateOf(album.name) }
    // 存储错误信息（如果有）
    var errorMessage by remember { mutableStateOf(" ") }
    
    Dialog(onDismissRequest = onCancel) {
        Card(
            modifier = Modifier
                .padding(16.dp)
                .widthIn(max = 320.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                // 对话框标题
                Text(
                    text = "重命名相册",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                // 相册名称输入框
                OutlinedTextField(
                    value = newName,
                    onValueChange = {
                        newName = it
                        // 清除错误信息
                        if (errorMessage.isNotEmpty()) {
                            errorMessage = ""
                        }
                    },
                    label = { Text("新名称") },
                    isError = errorMessage.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth()
                )
                
                // 错误信息显示
                if (errorMessage.isNotEmpty()) {
                    Text(
                        text = errorMessage,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                
                // 按钮区域
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    // 取消按钮
                    TextButton(onClick = onCancel) {
                        Text("取消")
                    }
                    
                    // 重命名按钮
                    Button(
                        onClick = {
                            // 验证名称
                            if (newName.trim().isEmpty()) {
                                errorMessage = "名称不能为空"
                            } else if (newName.trim() == album.name) {
                                errorMessage = "新名称与原名称相同"
                            } else {
                                // 调用重命名回调
                                onRename(newName.trim())
                            }
                        },
                        modifier = Modifier.padding(start = 8.dp)
                    ) {
                        Text("重命名")
                    }
                }
            }
        }
    }
}