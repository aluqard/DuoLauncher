package com.jake.duolauncher

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckBox
import androidx.compose.material.icons.rounded.CheckBoxOutlineBlank
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject

/** Text kept by a memo or checklist card; stored on this device only, in schema-9 widgetContent. */
data class NoteContent(val title: String = "", val text: String = "", val items: List<ChecklistItem> = emptyList())
data class ChecklistItem(val text: String, val done: Boolean = false)

const val MAX_CHECKLIST_ITEMS = 60

fun encodeNoteContent(value: NoteContent): String = JSONObject().put("title", value.title).put("text", value.text)
    .put("items", JSONArray().also { array -> value.items.forEach { array.put(JSONObject().put("t", it.text).put("d", it.done)) } })
    .toString()

fun decodeNoteContent(raw: String?): NoteContent = runCatching {
    if (raw.isNullOrBlank()) return NoteContent()
    val j = JSONObject(raw)
    val items = j.optJSONArray("items")?.let { array ->
        List(array.length()) { val item = array.getJSONObject(it); ChecklistItem(item.getString("t"), item.optBoolean("d")) }
    }.orEmpty().filter { it.text.isNotBlank() }.take(MAX_CHECKLIST_ITEMS)
    NoteContent(j.optString("title"), j.optString("text"), items)
}.getOrDefault(NoteContent())

/** Provides the model to launcher-drawn cards that edit their own content. */
val LocalLauncherModel = staticCompositionLocalOf<LauncherModel?> { null }

@Composable
internal fun NoteCard(id: Int, slot: Int, onReplace: () -> Unit) {
    val model = LocalLauncherModel.current
    val raw = model?.state?.collectAsState()?.value?.extras?.widgetContent?.get(slot)
    val content = remember(raw) { decodeNoteContent(raw) }
    var editing by remember { mutableStateOf(false) }
    fun save(next: NoteContent) {
        val encoded = encodeNoteContent(next).take(MAX_WIDGET_CONTENT_CHARS)
        model?.updateExtras { it.copy(widgetContent = it.widgetContent + (slot to encoded)) }
    }
    GlassCard(Modifier.testTag("note-card-$slot"), onClick = { editing = true }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.EditNote, null, tint = Color.White, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(content.title.ifBlank { if (id == MEMO_WIDGET) "Memo" else "To do" }, color = Color.White, fontSize = 13.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (id == MEMO_WIDGET) {
            Text(content.text.ifBlank { "Tap to write a memo" }, color = Color.White.copy(alpha = if (content.text.isBlank()) .7f else 1f),
                fontSize = 13.sp, lineHeight = 17.sp, maxLines = 5, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        } else Column(Modifier.weight(1f)) {
            if (content.items.isEmpty()) Text("Tap to add to-dos", color = Color.White.copy(alpha = .7f), fontSize = 13.sp)
            content.items.take(4).forEachIndexed { index, item ->
                Row(Modifier.clickable { save(content.copy(items = content.items.toMutableList().also {
                    it[index] = item.copy(done = !item.done) })) }, verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (item.done) Icons.Rounded.CheckBox else Icons.Rounded.CheckBoxOutlineBlank, null,
                        tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(item.text, color = Color.White.copy(alpha = if (item.done) .6f else 1f), fontSize = 13.sp, maxLines = 1,
                        overflow = TextOverflow.Ellipsis, textDecoration = if (item.done) TextDecoration.LineThrough else null)
                }
            }
            if (content.items.size > 4) Text("+${content.items.size - 4} more", color = Color.White.copy(alpha = .7f), fontSize = 11.sp)
        }
    }
    if (editing) NoteEditor(id, content, onDismiss = { editing = false }, onReplace = { editing = false; onReplace() },
        onSave = { save(it); editing = false })
}

@Composable
private fun NoteEditor(id: Int, initial: NoteContent, onDismiss: () -> Unit, onReplace: () -> Unit, onSave: (NoteContent) -> Unit) {
    var title by remember { mutableStateOf(initial.title) }
    var text by remember { mutableStateOf(initial.text) }
    var items by remember { mutableStateOf(initial.items) }
    var newItem by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text(if (id == MEMO_WIDGET) "Memo" else "Checklist") },
        text = {
            Column(Modifier.testTag("note-editor")) {
                OutlinedTextField(title, { title = it.take(40) }, label = { Text("Title") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                if (id == MEMO_WIDGET) OutlinedTextField(text, { text = it.take(4000) }, label = { Text("Memo") },
                    minLines = 4, modifier = Modifier.fillMaxWidth().testTag("note-text"))
                else {
                    LazyColumn(Modifier.heightIn(max = 260.dp)) {
                        itemsIndexed(items) { index, item ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(item.done, { checked -> items = items.toMutableList().also { it[index] = item.copy(done = checked) } })
                                Text(item.text, Modifier.weight(1f), maxLines = 2)
                                IconButton({ items = items.toMutableList().also { it.removeAt(index) } }) { Icon(Icons.Rounded.Close, "Remove") }
                            }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(newItem, { newItem = it.take(120) }, label = { Text("Add item") }, singleLine = true,
                            modifier = Modifier.weight(1f).testTag("note-new-item"))
                        TextButton(enabled = newItem.isNotBlank() && items.size < MAX_CHECKLIST_ITEMS, onClick = {
                            items = items + ChecklistItem(newItem.trim()); newItem = ""
                        }) { Text("Add") }
                    }
                    if (items.any(ChecklistItem::done)) TextButton({ items = items.filterNot(ChecklistItem::done) }) { Text("Clear done items") }
                }
                Text("Saved only on this phone.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
            }
        },
        confirmButton = { TextButton({ onSave(NoteContent(title.trim(), text, items + listOfNotNull(
            newItem.trim().takeIf { it.isNotEmpty() }?.let(::ChecklistItem)))) }, Modifier.testTag("note-save")) { Text("Save") } },
        dismissButton = { Row { TextButton(onReplace) { Text("Replace widget") }; TextButton(onDismiss) { Text("Cancel") } } })
}
