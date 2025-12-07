## 问题
当引用标签的图片数量发生变化时，引用它的父标签统计信息不会立即更新，只有在重启应用后才会刷新。

## 根本原因
`TagStatisticsManager` 中的 `clearTagStatisticsCacheForTag` 方法只更新了发生变化的特定标签的统计信息，而没有更新引用该标签的父标签的统计信息。

## 解决方案
1. **修改 `TagStatisticsManager.kt` 中的 `clearTagStatisticsCacheForTag` 方法**：
   - 清除并更新发生变化的标签的统计信息
   - 使用 `getParentTagIds` 获取所有父标签（包括引用父标签）
   - 递归更新所有父标签的统计信息

2. **确保正确获取父标签**：
   - 使用 `TagRepository` 中已有的 `getParentTagIds` 方法，该方法已经返回包括引用父标签在内的所有父标签
   - 此方法同时处理直接父关系和引用关系

3. **更新所有父标签**：
   - 对于找到的每个父标签，清除其缓存并更新其统计信息
   - 确保引用该变化标签的所有标签都能立即刷新统计信息

## 实现步骤
1. 更新 `TagStatisticsManager.kt` 中的 `clearTagStatisticsCacheForTag` 方法，实现递归更新父标签
2. 测试修复，确保引用标签图片数量变化时统计信息正确刷新
3. 验证数据库和基于文件的存储系统都能正常工作

## 要修改的文件
- `d:\app\YuMoBox\app\src\main\java\com\example\yumoflatimagemanager\feature\tag\manager\TagStatisticsManager.kt`

此修复将确保当引用标签的图片数量发生变化时，所有引用它的标签（直接或间接）都会立即更新其统计信息，提供更响应式的用户体验。