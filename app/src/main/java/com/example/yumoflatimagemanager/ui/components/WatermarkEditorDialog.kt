package com.example.yumoflatimagemanager.ui.components

import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.yumoflatimagemanager.data.WatermarkAnchor
import com.example.yumoflatimagemanager.data.WatermarkPreset
import com.example.yumoflatimagemanager.data.WatermarkType

@Composable
fun WatermarkEditorDialog(
    preset: WatermarkPreset?,
    onSave: (WatermarkPreset) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(preset?.name ?: "") }
    var type by remember { mutableStateOf(preset?.type ?: WatermarkType.TEXT) }
    var text by remember { mutableStateOf(preset?.text ?: "© 2025") }
    var textSize by remember { mutableStateOf(preset?.textSize ?: 48f) }
    var imageUri by remember { mutableStateOf(preset?.imageUri) }
    var imageScale by remember { mutableStateOf(preset?.imageScale ?: 0.2f) }
    var alpha by remember { mutableStateOf(preset?.alpha ?: 200) }
    var anchor by remember { mutableStateOf(preset?.anchor ?: WatermarkAnchor.BOTTOM_RIGHT) }
    var offsetX by remember { mutableStateOf(preset?.offsetX ?: 20) }
    var offsetY by remember { mutableStateOf(preset?.offsetY ?: 20) }
    
    // 使用便捷的图片选择器工具
    val imagePicker = com.example.yumoflatimagemanager.utils.ImagePickerHelper.rememberImagePicker { uri ->
        imageUri = uri.toString()
    }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (preset == null) "创建水印预设" else "编辑水印预设") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                // 预设名称
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("预设名称") },
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // 水印类型
                Text("水印类型", style = MaterialTheme.typography.titleSmall)
                Row(modifier = Modifier.fillMaxWidth()) {
                    FilterChip(
                        selected = type == WatermarkType.TEXT,
                        onClick = { type = WatermarkType.TEXT },
                        label = { Text("文字") }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    FilterChip(
                        selected = type == WatermarkType.IMAGE,
                        onClick = { type = WatermarkType.IMAGE },
                        label = { Text("图片") }
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // 文字水印参数
                if (type == WatermarkType.TEXT) {
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        label = { Text("水印文字") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text("文字大小: ${textSize.toInt()}")
                    Slider(
                        value = textSize,
                        onValueChange = { textSize = it },
                        valueRange = 12f..120f
                    )
                } else {
                    // 图片水印参数
                    if (imageUri != null) {
                        // 显示已选图片预览
                        AsyncImage(
                            model = imageUri,
                            contentDescription = "水印图片",
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp)
                                .clip(RoundedCornerShape(8.dp))
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row {
                            Button(onClick = { imagePicker.launch() }) {
                                Text("更换图片")
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            TextButton(onClick = { imageUri = null }) {
                                Text("移除图片")
                            }
                        }
                    } else {
                        // 未选择图片
                        Button(
                            onClick = { imagePicker.launch() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("选择水印图片")
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text("图片缩放: ${(imageScale * 100).toInt()}%")
                    Slider(
                        value = imageScale,
                        onValueChange = { imageScale = it },
                        valueRange = 0.05f..1.0f
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // 锚点位置选择
                Text("初始位置（锚点）", style = MaterialTheme.typography.titleSmall)
                WatermarkAnchorSelector(
                    selected = anchor,
                    onSelect = { anchor = it }
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // 偏移量
                Text("偏移量 X: ${offsetX}dp")
                Slider(
                    value = offsetX.toFloat(),
                    onValueChange = { offsetX = it.toInt() },
                    valueRange = 0f..100f
                )
                
                Text("偏移量 Y: ${offsetY}dp")
                Slider(
                    value = offsetY.toFloat(),
                    onValueChange = { offsetY = it.toInt() },
                    valueRange = 0f..100f
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // 透明度
                Text("默认透明度: ${alpha}")
                Slider(
                    value = alpha.toFloat(),
                    onValueChange = { alpha = it.toInt() },
                    valueRange = 0f..255f
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val newPreset = WatermarkPreset(
                        id = preset?.id ?: 0,
                        name = name,
                        type = type,
                        text = text,
                        textSize = textSize,
                        imageUri = imageUri,
                        imageScale = imageScale,
                        alpha = alpha,
                        anchor = anchor,
                        offsetX = offsetX,
                        offsetY = offsetY
                    )
                    onSave(newPreset)
                },
                enabled = name.isNotBlank() && 
                    ((type == WatermarkType.TEXT && text.isNotBlank()) || 
                     (type == WatermarkType.IMAGE && imageUri != null))
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

@Composable
fun WatermarkAnchorSelector(
    selected: WatermarkAnchor,
    onSelect: (WatermarkAnchor) -> Unit
) {
    Column {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            AnchorButton(WatermarkAnchor.TOP_LEFT, selected, onSelect, "左上")
            AnchorButton(WatermarkAnchor.TOP_CENTER, selected, onSelect, "上中")
            AnchorButton(WatermarkAnchor.TOP_RIGHT, selected, onSelect, "右上")
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            AnchorButton(WatermarkAnchor.CENTER_LEFT, selected, onSelect, "左中")
            AnchorButton(WatermarkAnchor.CENTER, selected, onSelect, "中心")
            AnchorButton(WatermarkAnchor.CENTER_RIGHT, selected, onSelect, "右中")
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            AnchorButton(WatermarkAnchor.BOTTOM_LEFT, selected, onSelect, "左下")
            AnchorButton(WatermarkAnchor.BOTTOM_CENTER, selected, onSelect, "下中")
            AnchorButton(WatermarkAnchor.BOTTOM_RIGHT, selected, onSelect, "右下")
        }
    }
}

@Composable
fun AnchorButton(
    anchor: WatermarkAnchor,
    selected: WatermarkAnchor,
    onSelect: (WatermarkAnchor) -> Unit,
    label: String
) {
    FilterChip(
        selected = anchor == selected,
        onClick = { onSelect(anchor) },
        label = { Text(label, fontSize = 12.sp) }
    )
}
