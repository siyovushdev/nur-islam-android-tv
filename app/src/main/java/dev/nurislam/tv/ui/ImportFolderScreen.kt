package dev.nurislam.tv.ui

import android.net.Uri
import android.provider.DocumentsContract
import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.nurislam.tv.data.DocumentFolder
import dev.nurislam.tv.data.ImportDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** D-pad/touch file listing inside a folder chosen once with SAF. */
@Composable
fun ImportFolderScreen(tree: Uri, touchMode: Boolean, onFile: (Uri) -> Unit, onChooseFolder: () -> Unit, onClose: () -> Unit,
    load: suspend (String) -> List<ImportDocument> = { emptyList() }, useSystemLoader: Boolean = true) {
    val resolver = LocalContext.current.contentResolver
    var path by remember(tree) { mutableStateOf(listOf(DocumentsContract.getTreeDocumentId(tree) to "Выбранная папка")) }
    var entries by remember { mutableStateOf<List<ImportDocument>>(emptyList()) }
    var problem by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var selected by remember(path) { mutableIntStateOf(0) }
    val focus = remember { FocusRequester() }
    val scroll = androidx.compose.foundation.lazy.rememberLazyListState()
    fun back() { if (path.size > 1) path = path.dropLast(1) else onClose() }
    LaunchedEffect(path) {
        loading = true; problem = null; entries = emptyList()
        try { entries = if (useSystemLoader) withContext(Dispatchers.IO) { DocumentFolder.list(resolver, tree, path.last().first) } else load(path.last().first) }
        catch (e: Exception) { if (e is kotlinx.coroutines.CancellationException) throw e; problem = "Папка недоступна: ${e.message?.take(180)}. Выберите её заново." }
        finally { loading = false }
    }
    LaunchedEffect(path, entries, loading) { focus.requestFocus() }
    LaunchedEffect(selected) { scroll.animateScrollToItem(selected) }
    BackHandler { back() }
    fun activate(index: Int) {
        when (index) {
            0 -> back()
            1 -> onChooseFolder()
            else -> entries.getOrNull(index - 2)?.let {
                if (it.directory) path = path + (it.id to it.name)
                else onFile(DocumentsContract.buildDocumentUriUsingTree(tree, it.id))
            }
        }
    }
    val colors = LocalBoardAppearance.current
    BoxWithConstraints(Modifier.fillMaxSize().background(colors.Deep).testTag("import-folder")
        .onPreviewKeyEvent { event ->
            val key = event.nativeKeyEvent
            if (key.keyCode !in listOf(KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_BACK)) false
            else {
                if (key.action == KeyEvent.ACTION_DOWN) when (key.keyCode) {
                    KeyEvent.KEYCODE_BACK -> back()
                    KeyEvent.KEYCODE_DPAD_UP -> selected = (selected + entries.size + 1) % (entries.size + 2)
                    KeyEvent.KEYCODE_DPAD_DOWN -> selected = (selected + 1) % (entries.size + 2)
                    else -> activate(selected)
                }; true
            }
        }.focusRequester(focus).focusable()) {
        val size = if (touchMode) 18.sp else (28 * minOf(maxWidth.value / 1080f, maxHeight.value / 1080f)).sp
        Column(Modifier.fillMaxSize().padding(if (touchMode) 12.dp else 32.dp)) {
            Text("Импорт JSON / XLSX", color = colors.Gold, fontSize = size * 1.3f)
            Text(path.last().second, color = colors.Text, fontSize = size)
            Text(problem ?: if (loading) "Чтение папки…" else if (entries.isEmpty()) "Нет JSON/XLSX. Положите файл в выбранную папку или выберите другую." else "Выберите файл. Перед применением откроется проверка.", color = colors.Secondary, fontSize = size * .85f)
            LazyColumn(Modifier.fillMaxWidth().weight(1f), state = scroll) {
                itemsIndexed(listOf("Назад", "Выбрать другую папку") + entries.map { if (it.directory) "Папка: ${it.name}" else it.name }) { index, label ->
                    Text(label, color = colors.Text, fontSize = size, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        .testTag("folder-item-$index").semantics { this[ImportActionHighlighted] = selected == index }.border(if (selected == index) 2.dp else 0.dp, colors.Gold)
                        .clickable { selected = index; activate(index) }.padding(12.dp))
                }
            }
        }
    }
}
