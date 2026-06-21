package com.yue.browser.presentation.ui.tabswitcher

import com.yue.browser.presentation.ui.*

import com.yue.browser.R
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yue.browser.domain.model.TabGroup

@Composable
fun CreateGroupDialog(
    onConfirm: (name: String, colorIndex: Int) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var selectedColorIndex by remember { mutableStateOf(0) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.tab_switcher_create_group_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.tab_switcher_group_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(stringResource(R.string.tab_switcher_choose_color), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    GroupColors.forEachIndexed { index, color ->
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .border(
                                    width = if (selectedColorIndex == index) 2.dp else 0.dp,
                                    color = if (selectedColorIndex == index) color else Color.Transparent,
                                    shape = CircleShape
                                )
                                .clip(CircleShape)
                                .clickable { selectedColorIndex = index }
                                .padding(4.dp)
                                .clip(CircleShape)
                                .background(color)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        onConfirm(name, selectedColorIndex)
                    }
                },
                enabled = name.isNotBlank()
            ) {
                Text(stringResource(R.string.add))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
fun EditGroupDialog(
    group: TabGroup,
    onConfirm: (name: String, colorIndex: Int) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(group.name) }
    var selectedColorIndex by remember { mutableStateOf(group.colorIndex) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.tab_switcher_edit_group_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.tab_switcher_group_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(stringResource(R.string.tab_switcher_choose_color), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    GroupColors.forEachIndexed { index, color ->
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .border(
                                    width = if (selectedColorIndex == index) 2.dp else 0.dp,
                                    color = if (selectedColorIndex == index) color else Color.Transparent,
                                    shape = CircleShape
                                )
                                .clip(CircleShape)
                                .clickable { selectedColorIndex = index }
                                .padding(4.dp)
                                .clip(CircleShape)
                                .background(color)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        onConfirm(name, selectedColorIndex)
                    }
                },
                enabled = name.isNotBlank()
            ) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
fun MoveToGroupDialog(
    groups: Map<String, TabGroup>,
    onGroupSelected: (String) -> Unit,
    onCreateNewGroup: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.tab_switcher_move_to_group_title)) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (groups.isEmpty()) {
                    Text(stringResource(R.string.tab_switcher_no_groups), color = Color.Gray, fontSize = 14.sp)
                } else {
                    groups.values.forEach { group ->
                        val groupColor = GroupColors.getOrNull(group.colorIndex) ?: Color.Blue
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onGroupSelected(group.id) }
                                .padding(vertical = 10.dp, horizontal = 8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(groupColor)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(text = group.name, fontSize = 16.sp)
                        }
                    }
                    HorizontalDivider()
                }
                
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onCreateNewGroup() }
                        .padding(vertical = 10.dp, horizontal = 8.dp)
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(text = stringResource(R.string.tab_switcher_new_group_option), color = MaterialTheme.colorScheme.primary, fontSize = 16.sp)
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
