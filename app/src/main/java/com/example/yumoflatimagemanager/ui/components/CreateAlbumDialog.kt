package com.example.yumoflatimagemanager.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.*

/**
 * 创建新相册对话框组件
 */
@Composable
fun CreateAlbumDialog(
    onCreate: (String) -> Unit,
    onCancel: () -> Unit
) {
    var albumName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("创建新文件夹") },
        text = {
            TextField(
                value = albumName,
                onValueChange = { albumName = it },
                placeholder = { Text("请输入文件夹名称") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = {
                    if (albumName.isNotBlank()) {
                        onCreate(albumName)
                    }
                })
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    if (albumName.isNotBlank()) {
                        onCreate(albumName)
                    }
                },
                enabled = albumName.isNotBlank()
            ) {
                Text("确定")
            }
        },
        dismissButton = {
            Button(
                onClick = onCancel,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.LightGray
                )
            ) {
                Text("取消")
            }
        }
    )
}