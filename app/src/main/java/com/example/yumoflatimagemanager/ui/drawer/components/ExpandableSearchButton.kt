package com.example.yumoflatimagemanager.ui.drawer.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp

/**
 * 可展开的搜索按钮组件
 * - 位置：右上角，切换排序按钮左侧
 * - 展开方向：向左展开，覆盖标题文字
 * - 状态管理：有内容时保持展开，为空时收缩
 * - 标题文字动画：淡入淡出效果
 */
@OptIn(ExperimentalAnimationApi::class)
@Composable
fun ExpandableSearchButton(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    title: String = "标签管理",
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val textFieldValue = remember { mutableStateOf(TextFieldValue(searchQuery)) }
    
    // 标题文字透明度动画
    val titleAlpha by animateFloatAsState(
        targetValue = if (isExpanded && searchQuery.isNotEmpty()) 0f else 1f,
        animationSpec = tween(300)
    )
    
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 标题文字，带透明度动画
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.alpha(titleAlpha)
        )
        
        // 搜索框 - 向左展开
        AnimatedVisibility(
            visible = isExpanded,
            enter = fadeIn() + slideInHorizontally(initialOffsetX = { it }),
            exit = fadeOut() + slideOutHorizontally(targetOffsetX = { it })
        ) {
            OutlinedTextField(
                value = textFieldValue.value,
                onValueChange = { newValue ->
                    textFieldValue.value = newValue
                    onSearchQueryChange(newValue.text)
                    
                    // 内容为空时自动收缩
                    if (newValue.text.isEmpty()) {
                        isExpanded = false
                    }
                },
                modifier = Modifier
                    .focusRequester(focusRequester)
                    .focusable()
                    .width(200.dp)
                    .onKeyEvent {
                        if (it.key == Key.Back && isExpanded) {
                            isExpanded = false
                            true
                        } else {
                            false
                        }
                    },
                    placeholder = { Text("搜索标签...") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "搜索") },
                shape = RoundedCornerShape(20.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                )
            )
        }
        
        // 搜索按钮
        IconButton(
            onClick = {
                isExpanded = !isExpanded
                if (isExpanded) {
                    // 展开时自动聚焦并唤起输入法
                    focusRequester.requestFocus()
                    keyboardController?.show()
                }
            }
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = if (isExpanded) "关闭搜索" else "搜索标签"
            )
        }
    }
}
