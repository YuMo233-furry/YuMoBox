package com.example.yumoflatimagemanager.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch

/**
 * 网格列数设置对话框
 * 使用LazyColumn和Snap滚动行为实现更流畅的数字选择体验
 * @param currentColumns 当前的列数
 * @param onConfirm 确认按钮回调
 * @param onCancel 取消按钮回调
 */
@Composable
fun GridColumnSizeDialog(
    currentColumns: Int,
    onConfirm: (Int) -> Unit,
    onCancel: () -> Unit
) {
    // 对话框显示的列数范围
    val minColumns = 2
    val maxColumns = 8
    val columnsRange = minColumns..maxColumns
    
    // 当前选择的列数状态
    var selectedColumns by remember { mutableStateOf(currentColumns.coerceIn(minColumns, maxColumns)) }
    
    // 计算初始选中的索引
    val initialIndex = selectedColumns - minColumns
    
    // 创建LazyListState，用于控制滚动行为
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)
    val coroutineScope = rememberCoroutineScope()
    
    // 数字项的高度
    val itemHeight = 60.dp
    
    // 在Composable上下文中获取Density
    val density = androidx.compose.ui.platform.LocalDensity.current
    val itemHeightPx = remember(density, itemHeight) {
        with(density) { itemHeight.toPx() }
    }
    
    // 监听滚动位置变化，更新选中的列数
    LaunchedEffect(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset) {
        val currentIndex = listState.firstVisibleItemIndex
        val offset = listState.firstVisibleItemScrollOffset
        
        // 计算当前应该选中的项（当滚动超过一半时切换到下一项）
        val adjustedIndex = if (offset > itemHeightPx / 2) currentIndex + 1 else currentIndex
        
        // 确保索引在有效范围内
        if (adjustedIndex in 0 until columnsRange.count()) {
            val newColumns = minColumns + adjustedIndex
            if (newColumns != selectedColumns) {
                selectedColumns = newColumns
            }
        }
    }
    
    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnClickOutside = true
        )
    ) {
        Surface(
            modifier = Modifier
                .width(300.dp)
                .padding(16.dp),
            shape = MaterialTheme.shapes.medium,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                // 标题
                Text(
                    text = "调整网格列数",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                // 显示当前选中的列数
                Text(
                    text = "当前选择: $selectedColumns 列",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(bottom = 16.dp)
                )
                
                // 数字选择器区域
                Box(
                    modifier = Modifier
                        .height(160.dp) // 增加高度以显示更多项目
                        .width(80.dp)
                        .align(Alignment.CenterHorizontally)
                        .background(Color.LightGray.copy(alpha = 0.3f))
                ) {
                    // 使用LazyColumn实现滚动选择器
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxHeight()
                            .align(Alignment.Center),
                        flingBehavior = androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior(lazyListState = listState),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        contentPadding = PaddingValues(vertical = 50.dp) // 添加内边距使初始项居中
                    ) {
                        items(columnsRange.count()) { index ->
                            val columnValue = minColumns + index
                            val isSelected = columnValue == selectedColumns
                            
                            Box(
                                 modifier = Modifier
                                     .height(itemHeight)
                                     .width(80.dp)
                                     .background(Color.Transparent)
                             ) {
                                Text(
                                    text = columnValue.toString(),
                                    style = if (isSelected) {
                                        MaterialTheme.typography.headlineMedium.copy(color = MaterialTheme.colorScheme.primary)
                                    } else {
                                        MaterialTheme.typography.bodyLarge.copy(color = Color.Gray)
                                    },
                                    modifier = Modifier.align(Alignment.Center)
                                )
                            }
                        }
                    }
                    
                    // 中心线 - 指示当前选中的位置
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(Color.Gray)
                    )
                    
                    // 顶部和底部指示器线
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .offset(y = -30.dp)
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(Color.Gray.copy(alpha = 0.5f))
                    )
                    
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .offset(y = 30.dp)
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(Color.Gray.copy(alpha = 0.5f))
                    )
                }
                
                // 底部按钮
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onCancel) {
                        Text("取消")
                    }
                    Button(
                        onClick = { onConfirm(selectedColumns) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = Color.White
                        )
                    ) {
                        Text("确定")
                    }
                }
            }
        }
    }
}