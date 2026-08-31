package com.winter.muplayer.config

/**
 * 嵌套子 slot 路径与操作工具 —— 布局编辑器用于定位 / 替换任意深度的命名子 slot。
 *
 * 模型：一个 slot 的组件列表里，带 `extra["children"]`（Map<String, List<ComponentEntry>>）
 * 的条目是容器（命名子 slot / slot 型组件）。子 slot 的列表里可以再嵌套容器，形成任意深度。
 *
 * [NestedHop] 表示进入下一层的跳转：在「当前列表」的 [containerIndex] 位置取容器条目，
 * 进入其子 slot [childSlot] 的列表。一串 [NestedHop] 组成从根列表到目标列表的路径；
 * 空路径表示根列表本身。
 */
data class NestedHop(val containerIndex: Int, val childSlot: String)

/** 沿路径定位列表（只读）；路径非法时返回空列表。 */
fun nestedListOf(root: List<ComponentEntry>, path: List<NestedHop>): List<ComponentEntry> {
    var list = root
    for (hop in path) {
        val entry = list.getOrNull(hop.containerIndex) ?: return emptyList()
        val children = entry.extra["children"]
        if (children !is Map<*, *>) return emptyList()
        @Suppress("UNCHECKED_CAST")
        list = (children[hop.childSlot] as? List<ComponentEntry>) ?: return emptyList()
    }
    return list
}

/**
 * 沿路径替换目标列表，返回新的根列表；路径非法返回 null。
 * 路径为空 → 替换根列表本身（直接返回 [newList]）。
 */
fun replaceNestedRoot(
    root: List<ComponentEntry>,
    path: List<NestedHop>,
    newList: List<ComponentEntry>,
): List<ComponentEntry>? {
    if (path.isEmpty()) return newList

    // 沿路径收集每级：容器条目下标、容器条目、进入的子 slot 名
    var list = root
    val levels = mutableListOf<Triple<Int, ComponentEntry, String>>()
    for (hop in path) {
        val entry = list.getOrNull(hop.containerIndex) ?: return null
        val children = entry.extra["children"]
        if (children !is Map<*, *>) return null
        levels.add(Triple(hop.containerIndex, entry, hop.childSlot))
        @Suppress("UNCHECKED_CAST")
        list = (children[hop.childSlot] as? List<ComponentEntry>) ?: return null
    }

    // 从最内层向外重建：目标列表 → 逐层写回容器，最后写回根列表
    var replaced: List<ComponentEntry> = newList
    for (i in levels.indices.reversed()) {
        val (idx, entry, childSlot) = levels[i]
        val children = entry.extra["children"] as Map<*, *>
        val childMap = children.mapKeys { it.key.toString() }.mapValues {
            @Suppress("UNCHECKED_CAST")
            (it.value as List<ComponentEntry>)
        }.toMutableMap()
        childMap[childSlot] = replaced
        val newEntry = entry.copy(extra = entry.extra + ("children" to childMap))
        if (i == 0) {
            // 最外层：写回根列表
            val newRoot = root.toMutableList()
            newRoot[idx] = newEntry
            return newRoot
        }
        // 中间层：写回父容器（levels[i - 1]）的子列表
        val (pIdx, pEntry, pChildSlot) = levels[i - 1]
        val pChildren = pEntry.extra["children"] as Map<*, *>
        @Suppress("UNCHECKED_CAST")
        val pList = (pChildren[pChildSlot] as List<ComponentEntry>).toMutableList()
        pList[idx] = newEntry
        replaced = pList
    }
    return null
}
