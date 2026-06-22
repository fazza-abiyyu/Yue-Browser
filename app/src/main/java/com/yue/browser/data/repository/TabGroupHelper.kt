package com.yue.browser.data.repository

import com.yue.browser.domain.model.BrowserTab
import com.yue.browser.domain.model.TabGroup
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.UUID

object TabGroupHelper {
    fun createGroup(
        tabsFlow: MutableStateFlow<List<BrowserTab>>,
        groupsFlow: MutableStateFlow<Map<String, TabGroup>>,
        name: String,
        colorIndex: Int,
        tabIds: List<String>
    ): String {
        val newGroupId = UUID.randomUUID().toString()
        val group = TabGroup(id = newGroupId, name = name, colorIndex = colorIndex)
        
        val updatedGroups = groupsFlow.value.toMutableMap()
        updatedGroups[newGroupId] = group
        groupsFlow.value = updatedGroups
        
        val currentTabs = tabsFlow.value.toMutableList()
        tabIds.forEach { id ->
            val idx = currentTabs.indexOfFirst { it.id == id }
            if (idx != -1) {
                currentTabs[idx] = currentTabs[idx].copy(groupId = newGroupId)
            }
        }
        tabsFlow.value = currentTabs
        return newGroupId
    }

    fun addTabToGroup(
        tabsFlow: MutableStateFlow<List<BrowserTab>>,
        groupsFlow: MutableStateFlow<Map<String, TabGroup>>,
        tabId: String,
        groupId: String
    ) {
        val currentTabs = tabsFlow.value.toMutableList()
        val idx = currentTabs.indexOfFirst { it.id == tabId }
        if (idx != -1 && groupsFlow.value.containsKey(groupId)) {
            currentTabs[idx] = currentTabs[idx].copy(groupId = groupId)
            tabsFlow.value = currentTabs
        }
    }

    fun removeTabFromGroup(
        tabsFlow: MutableStateFlow<List<BrowserTab>>,
        groupsFlow: MutableStateFlow<Map<String, TabGroup>>,
        tabId: String
    ) {
        val currentTabs = tabsFlow.value.toMutableList()
        val idx = currentTabs.indexOfFirst { it.id == tabId }
        if (idx != -1) {
            currentTabs[idx] = currentTabs[idx].copy(groupId = null)
            tabsFlow.value = currentTabs
            cleanEmptyGroups(tabsFlow, groupsFlow)
        }
    }

    fun renameGroup(
        groupsFlow: MutableStateFlow<Map<String, TabGroup>>,
        groupId: String,
        newName: String
    ) {
        val currentGroups = groupsFlow.value.toMutableMap()
        val group = currentGroups[groupId]
        if (group != null) {
            currentGroups[groupId] = group.copy(name = newName)
            groupsFlow.value = currentGroups
        }
    }

    fun updateGroupColor(
        groupsFlow: MutableStateFlow<Map<String, TabGroup>>,
        groupId: String,
        colorIndex: Int
    ) {
        val currentGroups = groupsFlow.value.toMutableMap()
        val group = currentGroups[groupId]
        if (group != null) {
            currentGroups[groupId] = group.copy(colorIndex = colorIndex)
            groupsFlow.value = currentGroups
        }
    }

    fun deleteGroup(
        tabsFlow: MutableStateFlow<List<BrowserTab>>,
        groupsFlow: MutableStateFlow<Map<String, TabGroup>>,
        groupId: String
    ) {
        val currentGroups = groupsFlow.value.toMutableMap()
        if (currentGroups.remove(groupId) != null) {
            groupsFlow.value = currentGroups
            
            val currentTabs = tabsFlow.value.toMutableList()
            currentTabs.forEachIndexed { idx, tab ->
                if (tab.groupId == groupId) {
                    currentTabs[idx] = tab.copy(groupId = null)
                }
            }
            tabsFlow.value = currentTabs
        }
    }

    fun moveTab(
        tabsFlow: MutableStateFlow<List<BrowserTab>>,
        fromIndex: Int,
        toIndex: Int
    ) {
        val currentList = tabsFlow.value.toMutableList()
        if (fromIndex in currentList.indices && toIndex in currentList.indices) {
            val tab = currentList.removeAt(fromIndex)
            currentList.add(toIndex, tab)
            tabsFlow.value = currentList
        }
    }

    fun cleanEmptyGroups(
        tabsFlow: MutableStateFlow<List<BrowserTab>>,
        groupsFlow: MutableStateFlow<Map<String, TabGroup>>
    ) {
        val activeGroupIds = tabsFlow.value.mapNotNull { it.groupId }.toSet()
        val currentGroups = groupsFlow.value
        val updatedGroups = currentGroups.filterKeys { it in activeGroupIds }
        if (updatedGroups.size != currentGroups.size) {
            groupsFlow.value = updatedGroups
        }
    }
}
