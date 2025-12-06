package com.example.yumoflatimagemanager.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.example.yumoflatimagemanager.R
import com.example.yumoflatimagemanager.data.Album
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check

/**
 * 移动图片对话框组件
 */
@Composable
fun MoveDialog(
    albums: List<Album>,
    onMove: (String) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    var selectedAlbum by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("移动到") },
        text = {
            Column {
                albums.forEach { album ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(60.dp)
                            .clickable {
                                selectedAlbum = album.id
                            }
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 相册封面图片
                        if (album.coverUri != null) {
                            Image(
                                painter = rememberAsyncImagePainter(
                                    ImageRequest.Builder(context)
                                        .data(album.coverUri)
                                        .build()
                                ),
                                contentDescription = album.name,
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(4.dp))
                            )
                        } else {
                            // 使用占位符图片
                            Image(
                                painter = painterResource(id = R.drawable.ic_launcher_background),
                                contentDescription = album.name,
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(4.dp))
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(text = album.name, fontSize = 14.sp)
                            Text(text = "${album.count} 个项目", fontSize = 12.sp, color = Color.Gray)
                        }
                        if (selectedAlbum == album.id) {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = "已选择",
                                modifier = Modifier.weight(1f).align(Alignment.CenterVertically)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    selectedAlbum?.let { onMove(it) }
                },
                enabled = selectedAlbum != null
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