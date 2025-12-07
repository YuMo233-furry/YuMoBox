## 问题
当引用标签添加图片后，引用它的父标签统计信息不会及时刷新，重启应用后才会更新。

## 根本原因
MainViewModel 中有自己的一套标签统计管理逻辑，包括 `tagStatisticsCache` 缓存和 `clearTagStatisticsCacheForTag` 方法，而不是使用我们之前修改过的 `TagStatisticsManager`。MainViewModel 中的 `clearTagStatisticsCacheForTag` 方法只更新当前标签的统计信息，没有递归更新所有父标签（包括引用父标签）的统计信息。

## 解决方案
修改 MainViewModel 中的 `clearTagStatisticsCacheForTag` 方法，添加递归更新所有父标签统计信息的逻辑，确保当引用标签的图片数量变化时，所有引用它的标签都能及时刷新统计信息。

## 实现步骤
1. 更新 `MainViewModel.kt` 中的 `clearTagStatisticsCacheForTag` 方法：
   - 保留原有的缓存清理和当前标签更新逻辑
   - 添加异步任务，获取所有父标签ID（包括引用父标签）
   - 递归更新所有父标签的统计信息
   - 确保异常处理，避免崩溃

2. 验证修复：
   - 确保添加/移除标签后，所有相关标签的统计信息都能及时刷新
   - 确保引用标签的父标签统计信息也能正确更新
   - 确保未分类标签统计信息正常更新

## 要修改的文件
- `d:\app\YuMoBox\app\src\main\java\com\example\yumoflatimagemanager\MainViewModel.kt`

## 修复后效果
当用户添加图片到引用标签时，该标签的所有父标签（包括直接父标签和引用父标签）都会立即刷新统计信息，无需重启应用，提供更响应式的用户体验。