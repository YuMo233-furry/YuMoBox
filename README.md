# YuMoFlatImageManager

一个使用 Jetpack Compose 开发的 Android 图片管理应用，支持图片查看、选择、标签管理等功能。

## 功能特性

### 核心功能
- **图片浏览**: 网格布局展示图片，支持缩放和滑动
- **图片选择**: 支持单选、多选、拖拽选择等多种选择方式
- **标签管理**: 为图片添加标签，支持标签层级管理
- **隐私模式**: 设置图片为私密状态
- **多列布局**: 支持自定义网格列数（2-6列）

### 选择功能优化
- **矩形区域选择**: 拖拽选择时选中起始点到结束点形成的矩形区域内所有项目
- **滚动冲突解决**: 
  - 只在实际拖拽时阻止页面滚动，选择模式下允许正常滚动
  - 页面滚动时自动禁用选择功能，避免误操作
- **智能拖拽检测**:
  - 最小拖拽距离：15px，避免误触发
  - 初始延迟：150ms，避免与长按冲突
  - 拖拽超时保护：300ms
- **状态管理优化**: 拖拽状态重置延迟300ms，确保状态一致性

### 图片预览优化
- **立即响应**: 修复了相册详情页未滚动时无法打开图片大图预览的问题
- **事件处理**: 优化了ImageClickHandler和拖拽选择处理器的初始化时机，确保页面加载完成后立即可用
- **调试支持**: 添加了点击事件调试日志，便于问题排查

### 多选模式动画优化
- **过渡动画统一**: 为相册列表页和相册详情页的多选模式底部栏添加了统一的过渡动画
- **动画参数**: 进入动画使用`slideInVertically` + `fadeIn`（300ms/200ms），退出动画使用`slideOutVertically` + `fadeOut`（300ms/150ms）
- **视觉一致性**: 确保两个页面在多选模式切换时的用户体验一致

### 多选模式底部菜单区别化
- **页面类型区分**: 新增`SelectionPageType`枚举，支持`ALBUM_LIST`（主页面）和`ALBUM_DETAIL`（相册详情页）两种类型
- **按钮组合定制**:
  - **主页面**（相册列表）: 显示【删除】、【重命名】、【设为隐私】、【更多】按钮
  - **相册详情页**: 显示【添加到】、【删除】、【添加标签】、【更多】按钮
- **图标优化**: 
  - 重命名功能使用`Icons.Filled.Edit`图标
  - 添加到功能使用`Icons.Filled.DriveFileMove`图标
- **功能适配**: 根据页面类型隐藏不需要的功能按钮，提供更精准的用户操作选项

### 顶部栏字体动画统一
修复相册详情页与主页面顶部栏字体过渡动画不一致的问题：
- **问题分析**: 相册详情页顶部栏被`androidx.compose.runtime.key(viewModel.refreshKey)`包装，导致组件重新创建时打断动画
- **解决方案**: 移除`refreshKey`包装，让顶部栏组件保持常驻状态
- **额外修复**: 发现相册详情页布局中的渐变层也使用了`refreshKey`包装，一并移除以确保整个页面动画一致性
- **动画效果**: 字体过渡动画统一使用`AnimatedContent`的`fadeIn(tween(200)) with fadeOut(tween(150))`效果
- **一致性提升**: 两页面顶部栏字体淡出淡入动画完全一致，提升视觉体验

### 顶部栏动画统一
- **颜色动画统一**: 修复了相册详情页顶部栏颜色动画与主页面不一致的问题
- **参数统一**: 统一使用220ms的过渡时长，移除详情页首次加载无动画的特殊逻辑
- **代码简化**: 移除了不必要的首次加载标志位和finishedListener，简化动画逻辑

### 标签统计信息显示优化

- **修复标签图片数量显示异常**：解决标签按钮不显示图片数量的问题
  - **问题分析**：标签管理抽屉加载标签数据时未调用`updateTagStatistics`更新统计信息，以及TagItem组件使用`tag.imageCount`而非`tagStats.value?.totalImageCount`
  - **解决方案**：
    - 在`TagManagerDrawer`中添加`LaunchedEffect(tags)`代码块，遍历所有标签调用`viewModel.updateTagStatistics`更新统计信息
    - 修改`TagItem`组件的图片数量显示逻辑，从使用`tag.imageCount`改为`tagStats.value?.totalImageCount`
    - 为未分类标签添加统计信息显示，通过`viewModel.tagStatistics[-1L]`获取未分类图片数量
    - 修复未分类标签统计显示所有标签图片总数的问题，正确显示未打标签的图片数量
  - **效果提升**：标签现在正确显示关联的图片数量，格式统一为"(数量)"，未分类标签也显示统计信息
- **标签选择对话框优化**：更新`TagSelectionDialog`中的图片数量显示逻辑
  - 添加`viewModel`参数支持，使对话框能够访问标签统计信息
  - 修改`TagSelectionItem`组件使用`tagStats.value?.totalImageCount`替代`tag.imageCount`
  - 确保标签选择对话框中的数量显示与标签管理抽屉保持一致
- **删除标签对话框优化**：更新`DeleteTagDialog`中的图片数量显示
  - 添加`viewModel`参数支持，使用统计信息中的`totalImageCount`替代`tag.imageCount`
  - 确保删除确认对话框显示准确的标签关联图片数量

### 标签删除功能优化

- **修复标签删除不彻底问题**：在删除标签时，现在会同时清理该标签与所有媒体文件的关联关系、标签之间的引用关系，并正确处理子标签的父级引用，确保标签被彻底删除
- **修复标签过滤未更新问题**：删除标签后，系统会自动从激活的标签过滤列表中移除该标签ID，并持久化更新过滤状态，同时即时刷新过滤列表和更新相关标签的统计信息
- **提升用户体验**：标签删除后立即更新界面显示，确保数据一致性和良好的用户反馈

### 标签删除撤回提示位置优化

- **固定显示位置**：将删除标签后的撤回提示从标签列表底部移动到屏幕底部固定位置显示，使用系统Snackbar组件实现
- **统一视觉体验**：撤回提示现在显示在屏幕底部，符合用户习惯，不会被标签列表滚动影响
- **提升可用性**：确保用户在任何界面位置都能清晰看到撤回提示，避免遗漏重要操作反馈
- **修复显示层级问题**：将SnackbarHost移到最外层Box中，设置zIndex(20f)确保显示在所有组件上方，解决撤回弹窗被标签抽屉遮挡的问题

### 标签功能增强
#### 标签展开/折叠功能
- **状态管理**: 添加了`expandedTagIds`状态变量，支持标签树的展开/折叠控制
- **UI交互**: 标签项左侧显示展开/折叠图标（右箭头/下箭头），点击可切换状态
- **层级显示**: 支持多级标签嵌套显示，子标签自动缩进显示

#### 标签管理抽屉
- **左滑删除**: 支持左滑删除标签，带有300ms动画效果
- **对话框组件**: 实现创建、重命名、删除标签的专用对话框
- **图标优化**: 使用AutoMirrored图标确保正确的视觉方向
- **层级优化**: 抽屉组件移到Scaffold外部，确保显示在顶部菜单之上

#### 标签引用功能（多对多关系）
- **引用管理**: 支持标签之间的多对多引用关系，一个标签可以引用多个其他标签
- **引用添加**: 点击标签右侧的"+"按钮可添加对其他标签的引用
- **引用显示**: 展开标签时显示其引用的其他标签，使用特殊图标标识
- **循环引用检测**: 自动检测并防止循环引用，确保引用关系的正确性
- **父标签过滤**: 引用页面自动排除当前标签的所有父标签和祖宗标签，避免循环引用

#### 标签UI交互优化
- **展开按钮修复**: 修复点击标签展开/折叠按钮错误触发引用页面的问题，确保只有点击"+"按钮才能打开引用页面
- **事件隔离**: 优化按钮点击事件处理，防止事件冒泡导致的误操作

#### 标签统计信息
- **图片计数**: 每个标签显示关联的图片数量
- **统计计算**: 支持直接关联、引用关联和总计三种统计方式
- **实时更新**: 标签统计信息实时更新，反映最新的图片关联状态

#### 标签添加反馈功能
- **操作反馈**: 添加标签到图片后显示Toast提示，如"已添加X张图片到XX"
- **结果统计**: 显示成功添加和失败的图片数量统计
- **错误处理**: 异常情况显示具体的错误信息
- **用户体验**: 提供明确的操作结果反馈，提升用户操作信心

#### 标签排除模式功能
- **快速双击进入排除模式**: 快速双击标签（300ms内）可进入排除模式，显示所有**不包含**该标签的图片
- **排除状态标识**: 排除模式的标签显示红色×图标，而非绿色✓图标
- **模式切换**: 
  - 单击标签：正常过滤模式（显示包含该标签的图片）
  - 快速双击：排除模式（显示不包含该标签的图片）
  - 再次点击：取消选择（无论当前是过滤还是排除模式）
- **状态互斥**: 一个标签不能同时处于过滤和排除状态，自动处理状态切换
- **交集功能**: 排除模式与其他标签的交集过滤功能正常工作，支持复杂的组合过滤条件
- **选择模式适配**: 当图片处于选择模式时，双击功能暂时禁用，避免与选择操作冲突
- **单击取消功能**: 单击已处于排除或激活状态的标签可取消选择
- **单标签排除模式行为**: 当单个排除标签不包含任何图片时，显示全部图片是**正确的行为**，因为没有内容需要排除
- **逻辑一致性**: 排除模式独立于激活过滤工作，确保逻辑一致性和可预测性
- **全排除模式修复**: 修复当所有选中标签都是排除模式时显示所有图片的问题，现在正确地从所有图片中排除这些标签对应的图片

#### 未分类标签功能
- **未分类标签**: 新增"未分类"标签项，显示所有未打标签的图片
- **统计计算**: 自动统计未关联任何标签的图片数量
- **过滤支持**: 支持点击"未分类"标签过滤显示未分类图片
- **特殊标识**: 使用-1L作为未分类标签的特殊ID
- **添加限制**: "未分类"标签仅用于过滤显示，不可添加到图片上

#### 标签UI优化
- **层级样式**: 不同层级的标签使用不同的字体大小和颜色
- **文本溢出**: 支持长标签名称的文本溢出处理
- **响应式布局**: 标签项自适应不同屏幕尺寸和方向

### 标签引用功能增强
#### 标签引用管理
- **多对多关系**: 支持标签之间的多对多引用关系，一个标签可以引用多个其他标签
- **引用添加**: 点击标签右侧的"+"按钮可添加对其他标签的引用
- **引用显示**: 展开标签时显示其引用的其他标签，使用特殊图标标识
- **循环引用检测**: 自动检测并防止循环引用，确保引用关系的正确性

#### 标签统计信息
- **图片计数**: 每个标签显示关联的图片数量
- **统计计算**: 支持直接关联、引用关联和总计三种统计方式
- **实时更新**: 标签统计信息实时更新，反映最新的图片关联状态

#### 数据库架构升级
- **引用关系表**: 新增`tag_references`表存储标签引用关系
- **扩展字段**: 为`tags`表添加`isExpanded`和`imageCount`字段
- **索引优化**: 为引用关系添加索引，提升查询性能
- **迁移支持**: 提供完整的数据库迁移方案（版本1→2→3）

#### 数据访问层优化
- **Repository模式**: 使用TagRepository封装所有标签相关操作
- **DAO接口**: TagDao提供完整的CRUD操作和复杂查询
- **事务支持**: 使用Room的事务机制确保数据一致性
- **协程集成**: 所有数据库操作都支持协程，避免阻塞主线程

## 技术架构

### 技术栈
- **UI框架**: Jetpack Compose (BOM 2024.09.00)
- **依赖注入**: Hilt 2.52
- **媒体处理**: ExoPlayer 2.19.1
- **图片加载**: Coil 2.4.0
- **导航**: Navigation Compose 2.8.0
- **协程**: Kotlin Coroutines 1.7.3

### 项目结构
```
app/src/main/java/com/example/yumoflatimagemanager/
├── data/                    # 数据模型
│   ├── ImageItem.kt        # 图片数据模型
│   └── local/              # 本地数据相关
│       ├── AppDatabase.kt  # Room数据库配置
│       ├── TagEntities.kt  # 标签实体定义
│       ├── TagDao.kt       # 标签数据访问对象
│       └── migrations/     # 数据库迁移文件
├── data/repo/              # 数据仓库层
│   └── TagRepository.kt    # 标签仓库接口和实现
├── ui/                     # UI层
│   ├── components/         # 可复用组件
│   │   ├── ImageCard.kt   # 图片卡片组件
│   │   ├── ScrollProgressBar.kt # 滚动进度条
│   │   ├── SelectionBottomBar.kt # 选择操作栏
│   │   └── TagDialogs.kt  # 标签管理对话框组件
│   ├── screens/            # 屏幕页面
│   │   ├── AlbumDetailGrid.kt # 相册详情网格
│   │   └── AlbumDetailScreen.kt # 相册详情屏幕
│   ├── drawer/             # 抽屉组件
│   │   └── TagManagerDrawer.kt # 标签管理抽屉
│   ├── selection/          # 选择功能相关
│   │   ├── SelectionManagerFacade.kt # 选择管理器
│   │   ├── DragSelectionHandler.kt  # 拖拽选择处理器
│   │   ├── ImageClickHandler.kt     # 图片点击处理器
│   │   └── SelectionState.kt       # 选择状态管理
│   └── navigation/         # 导航相关
├── MainViewModel.kt        # 主视图模型
└── utils/                  # 工具类
```

## 核心功能详解

### 选择功能实现

#### 1. 矩形区域选择
在 `DragSelectionHandler.kt` 中实现了 `calculateRectangularSelection` 函数：

```kotlin
private fun calculateRectangularSelection(
    startIndex: Int,
    endIndex: Int,
    images: List<ImageItem>,
    spanCount: Int
): List<ImageItem> {
    val startRow = startIndex / spanCount
    val startCol = startIndex % spanCount
    val endRow = endIndex / spanCount
    val endCol = endIndex % spanCount
    
    val minRow = minOf(startRow, endRow)
    val maxRow = maxOf(startRow, endRow)
    val minCol = minOf(startCol, endCol)
    val maxCol = maxOf(startCol, endCol)
    
    val selectedImages = mutableListOf<ImageItem>()
    for (row in minRow..maxRow) {
        for (col in minCol..maxCol) {
            val index = row * spanCount + col
            if (index >= 0 && index < images.size) {
                selectedImages.add(images[index])
            }
        }
    }
    
    return selectedImages
}
```

#### 2. 滚动冲突解决
在 `AlbumDetailGrid.kt` 中通过 `LazyGridState.isScrollInProgress` 检测页面滚动状态：

```kotlin
// 监听滚动状态，页面滚动时禁止选择
val isScrolling = lazyGridState.isScrollInProgress

// 页面滚动时禁止选择功能
.pointerInput(selectionManager.isSelectionMode, images, selectionManager.isDragging, selectionManager.selectedImages.size, isScrolling) {
    // 页面滚动时禁止选择功能
    if (!isScrolling) {
        detectDragSelection(/* ... */)
    }
}
```

#### 3. 嵌套滚动连接
使用自定义的 `NestedScrollConnection` 处理滚动冲突：

```kotlin
val nestedScrollConnection = remember {
    object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            // 只在实际拖拽时阻止滚动，选择模式下允许滚动
            return if (selectionManager.isDragging) {
                available // 消费滚动，阻止页面滚动
            } else {
                Offset.Zero // 不消费，允许正常滚动
            }
        }
    }
}
```

### 拖拽检测优化

#### 检测参数
- **最小拖拽距离**: 15px，避免手指轻微移动误触发选择
- **初始延迟**: 150ms，确保不会与长按手势冲突
- **拖拽超时**: 300ms，防止长时间按压被误识别为拖拽

#### 状态管理
在 `SelectionManagerFacade.kt` 中实现了完整的状态管理：

```kotlin
// 重置拖拽状态
fun resetDragState() {
    isDragging = false
    dragStartIndex = -1
    dragEndIndex = -1
}

// 重置所有选择状态
fun resetAllSelectionState() {
    coroutineScope.launch {
        delay(300) // 延迟300ms重置，确保状态一致性
        resetDragState()
        // ... 其他状态重置
    }
}
```

## 安装和运行

### 环境要求
- Android Studio Arctic Fox 或更高版本
- Android SDK 21 (Android 5.0) 或更高版本
- Kotlin 1.9.0 或更高版本

### 构建项目
```bash
# 克隆项目
git clone [项目地址]

# 进入项目目录
cd YuMoFlatImageManager

# 构建调试版本
./gradlew assembleDebug

# 安装到设备
./gradlew installDebug
```

## 使用说明

### 图片选择
1. **单选**: 直接点击图片
2. **多选**: 
   - 长按图片进入选择模式
   - 点击图片进行选择/取消选择
3. **拖拽选择**: 
   - 在选择模式下，长按并拖拽形成矩形区域
   - 区域内的所有图片将被选中

### 标签管理
1. 点击标签管理按钮打开标签管理器
2. 可以创建、编辑、删除标签
3. 支持标签层级结构
4. 为图片分配标签进行分类管理

### 隐私模式
1. 选择图片后点击隐私按钮
2. 可以将图片设置为私密状态
3. 私密图片将在特定视图中隐藏


### 核心功能优化
- **选择功能增强**: 新增矩形区域选择、拖拽选择优化、滚动冲突解决
- **权限管理**: 适配Android 10+权限模型，支持管理所有文件权限
- **性能优化**: 异步加载媒体内容，提升应用响应速度

## 注意事项

1. **性能优化**: 应用使用懒加载和缓存机制处理大量图片
2. **内存管理**: 合理管理图片内存，避免OOM
3. **权限处理**: 需要存储权限来访问设备图片
4. **兼容性**: 适配不同屏幕尺寸和Android版本

## 更新日志

### 2024年12月
- 修复选择功能滚动冲突问题
- 实现矩形区域选择逻辑
- 优化拖拽检测参数
- 添加页面滚动时选择功能禁用

### 2024-11
- 实现基础图片浏览功能
- 添加标签管理系统
- 实现多列网格布局
- 添加隐私模式功能

### 标签管理优化 (2025-01)
- ✅ 标签拖拽排序功能
- ✅ 标签层级结构支持
- ✅ 标签引用关系管理
- ✅ 标签搜索和过滤
- ✅ 标签统计信息显示
- ✅ 标签展开/折叠状态持久化
- ✅ 标签重命名功能
- ✅ 标签删除确认对话框
- ✅ 标签删除撤回功能
- ✅ 标签引用添加功能
- ✅ **引用标签排序对话框** - 新增引用标签排序功能，可在排序模式下对引用标签进行拖拽排序