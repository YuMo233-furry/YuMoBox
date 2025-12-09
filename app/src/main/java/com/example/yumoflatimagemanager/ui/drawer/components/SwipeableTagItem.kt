package com.example.yumoflatimagemanager.ui.drawer.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.yumoflatimagemanager.MainViewModel
import com.example.yumoflatimagemanager.data.local.TagEntity
import com.example.yumoflatimagemanager.data.local.TagWithChildren
import sh.calvin.reorderable.ReorderableCollectionItemScope
import kotlin.math.roundToInt

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SwipeToDeleteTagItem(
    tagWithChildren: TagWithChildren,
    viewModel: MainViewModel,
    onDelete: (TagEntity) -> Unit,
    useReferencedTagExpansion: Boolean = false,
    isDragging: Boolean = false,
    reorderableScope: ReorderableCollectionItemScope? = null
) {
    var offsetX by remember { mutableStateOf(0f) }
    var isDeleteRevealed by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    val deleteButtonWidth = with(density) { 80.dp.toPx() }
    
    // 重置状态
    fun resetSwipeState() {
        offsetX = 0f
        isDeleteRevealed = false
    }
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                // 点击空白区域时重置删除状态
                if (isDeleteRevealed) {
                    resetSwipeState()
                }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        when {
                            offsetX < -deleteButtonWidth / 2 -> {
                                // 显示删除按钮
                                offsetX = -deleteButtonWidth
                                isDeleteRevealed = true
                            }
                            else -> {
                                // 重置
                                resetSwipeState()
                            }
                        }
                    },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        val newOffset = offsetX + dragAmount
                        offsetX = newOffset.coerceIn(-deleteButtonWidth, 0f)
                    }
                )
            }
    ) {
        // 删除按钮背景
        if (isDeleteRevealed || offsetX < -10) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFFFF5252))
                    .padding(end = 16.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                IconButton(
                    onClick = {
                        onDelete(tagWithChildren.tag)
                        resetSwipeState()
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "删除",
                        tint = Color.White
                    )
                }
            }
        }
        
        // 标签内容
        Box(
            modifier = Modifier
                .offset { IntOffset(offsetX.roundToInt(), 0) }
                .fillMaxWidth()
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surface)
        ) {
            TagItem(
                tagWithChildren = tagWithChildren,
                viewModel = viewModel,
                onClickOutside = {
                    if (isDeleteRevealed) {
                        resetSwipeState()
                    }
                },
                useReferencedTagExpansion = useReferencedTagExpansion,
                reorderableScope = reorderableScope
            )
        }
    }
}

