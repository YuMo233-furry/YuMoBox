### 修复"未分组"标签过滤bug

**问题**："未分组"标签组不显示任何标签，应该显示所有没有组的标签

**原因**：当前`getTagsByTagGroupId`方法只返回直接属于该标签组的标签，而"未分组"标签组应该返回所有不属于任何标签组的标签

**修复方案**：
1. 修改`MainViewModel.kt`中的`getTagsByTagGroupId`方法
2. 当tagGroupId为1（未分组）时，实现特殊逻辑：
   - 获取所有标签组的标签ID集合
   - 计算所有标签ID集合
   - 找出不在任何标签组中的标签ID
   - 返回这些标签

### 修复标签组修改后标签过滤没有及时刷新的问题

**问题**：修改标签组后，标签过滤没有及时刷新，需要重复取消再选中才会刷新

**原因**：
- 当标签组内容发生变化时，没有触发标签列表的重新过滤
- 标签组变化的通知机制没有与标签过滤的缓存清理机制关联

**修复方案**：
1. 在`TagGroupFileManager.kt`的`addTagToTagGroup`和`removeTagFromTagGroup`方法中，确保触发`tagGroupChanges`事件
2. 修改`TagManagerDrawer.kt`，在标签组变化时清除搜索和过滤状态
3. 确保`TagFilterManager`的过滤缓存在标签组变化时被清除
4. 在`MainViewModel.kt`中添加监听标签组变化的逻辑，当标签组变化时更新标签过滤状态

### 实现步骤

1. 首先修复`MainViewModel.kt`中的`getTagsByTagGroupId`方法，处理"未分组"标签组的特殊逻辑
2. 检查并确保`TagGroupFileManager`的`addTagToTagGroup`和`removeTagFromTagGroup`方法正确触发`tagGroupChanges`事件
3. 修改`TagManagerDrawer.kt`，在标签组变化时重置相关状态
4. 测试修复后的功能，确保两个bug都被解决

### 关键修改文件

- `MainViewModel.kt`：修复"未分组"标签组的过滤逻辑
- `TagGroupFileManager.kt`：确保标签组变化时触发通知
- `TagManagerDrawer.kt`：监听标签组变化并重置状态
- `TagFilterManager.kt`：添加清除过滤缓存的机制