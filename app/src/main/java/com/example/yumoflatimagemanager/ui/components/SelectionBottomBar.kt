package com.example.yumoflatimagemanager.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.yumoflatimagemanager.R
import com.example.yumoflatimagemanager.MainViewModel
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.activity.compose.BackHandler
import androidx.compose.ui.zIndex

/**
 * 页面类型枚举，用于区分不同的页面
 */
enum class SelectionPageType {
    ALBUM_LIST,      // 相册列表页面（主页面）
    ALBUM_DETAIL     // 相册详情页面
}

/**
 * 多选模式下的底部操作栏
 */
@Composable
fun SelectionBottomBar(
    onShare: () -> Unit,
    onAddTo: () -> Unit,
    onDelete: () -> Unit,
    onTag: () -> Unit,
    onMore: () -> Unit,
    onRename: () -> Unit = {},
    onTogglePrivacy: () -> Unit = {},
    canRename: Boolean = false,
    privacyButtonState: PrivacyButtonState = PrivacyButtonState.HIDDEN,
    pageType: SelectionPageType = SelectionPageType.ALBUM_LIST,
    modifier: Modifier = Modifier,
    viewModel: MainViewModel? = null
) {
    var showSecondaryMenu by remember { mutableStateOf(false) }
    
    // 处理系统返回键关闭二级菜单
    BackHandler(enabled = showSecondaryMenu) {
        showSecondaryMenu = false
    }
    
    Box(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // 二级菜单 - 显示在原本菜单上方
            if (viewModel != null) {
                SelectionSecondaryBottomBar(
                    viewModel = viewModel,
                    isVisible = showSecondaryMenu,
                    onDismiss = { showSecondaryMenu = false }
                )
            }
            
            // 底部功能按钮栏
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .background(color = MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
            when (pageType) {
                SelectionPageType.ALBUM_LIST -> {
                    // 主页面（相册列表）的按钮组合：删除、重命名、设为隐私、更多
                    
                    // 删除
                    SelectionActionButton(
                        icon = Icons.Filled.Delete,
                        label = "删除",
                        onClick = onDelete
                    )
                    
                    // 重命名
                    SelectionActionButton(
                        icon = Icons.Filled.Edit,
                        label = "重命名",
                        onClick = onRename,
                        enabled = canRename
                    )
                    
                    // 私密相册相关按钮
                    when (privacyButtonState) {
                        PrivacyButtonState.SET_TO_PRIVATE -> {
                            SelectionActionButton(
                                icon = Icons.Filled.Lock,
                                label = "设为私密",
                                onClick = onTogglePrivacy
                            )
                        }
                        PrivacyButtonState.REMOVE_PRIVATE -> {
                            SelectionActionButton(
                                icon = Icons.Filled.LockOpen,
                                label = "取消私密",
                                onClick = onTogglePrivacy
                            )
                        }
                        PrivacyButtonState.DISABLED -> {
                            SelectionActionButton(
                                icon = Icons.Filled.Lock,
                                label = "设为私密",
                                onClick = { /* 禁用状态下不执行任何操作 */ },
                                enabled = false
                            )
                        }
                        else -> { /* 不显示任何私密相关按钮 */ }
                    }
                    
                    // 更多按钮
                    SelectionActionButton(
                        icon = Icons.Filled.MoreVert,
                        label = "更多",
                        onClick = { 
                            if (viewModel != null) {
                                showSecondaryMenu = true
                            } else {
                                onMore()
                            }
                        }
                    )
                }
                
                SelectionPageType.ALBUM_DETAIL -> {
                    // 相册详情页的按钮组合：分享、添加到、删除、添加标签、更多
                    
                    // 分享按钮
                    SelectionActionButton(
                        icon = Icons.Filled.Share,
                        label = "分享",
                        onClick = onShare
                    )
                    
                    // 添加到（移动/复制）
                    SelectionActionButton(
                        icon = Icons.Filled.DriveFileMove,
                        label = "添加到",
                        onClick = onAddTo
                    )

                    // 删除
                    SelectionActionButton(
                        icon = Icons.Filled.Delete,
                        label = "删除",
                        onClick = onDelete
                    )

                    // 编辑标签
                    SelectionActionButton(
                        icon = Icons.Filled.Tag,
                        label = "编辑标签",
                        onClick = onTag
                    )
                    
                    // 更多按钮
                    SelectionActionButton(
                        icon = Icons.Filled.MoreVert,
                        label = "更多",
                        onClick = { 
                            if (viewModel != null) {
                                showSecondaryMenu = true
                            } else {
                                onMore()
                            }
                        }
                    )
                }
            }
        }
        }
    }
}

/**
 * 私密按钮状态枚举
 */
enum class PrivacyButtonState {
    /** 不显示按钮 */
    HIDDEN,
    /** 显示"设为私密"按钮 */
    SET_TO_PRIVATE,
    /** 显示"取消私密"按钮 */
    REMOVE_PRIVATE,
    /** 显示禁用状态的"设为私密"按钮 */
    DISABLED
}

/**
 * 底部菜单中的操作按钮组件
 */
@Composable
fun SelectionActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        IconButton(
            onClick = onClick,
            modifier = Modifier.size(40.dp),
            enabled = enabled
        ) {
            Icon(
                icon,
                contentDescription = label,
                tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
        }
        Text(
            text = label,
            fontSize = 12.sp,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * 文本按钮组件
 */
@Composable
private fun TextButton(onClick: () -> Unit, content: @Composable RowScope.() -> Unit) {
    androidx.compose.material3.TextButton(
        onClick = onClick,
        colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
            contentColor = Color.White
        ),
        content = content
    )
}