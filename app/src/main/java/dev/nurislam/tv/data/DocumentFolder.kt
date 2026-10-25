package dev.nurislam.tv.data

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract

/** Uses only a user-granted SAF tree, never broad storage or filesystem permissions. */
data class ImportDocument(val id: String, val name: String, val directory: Boolean)
object DocumentFolder {
    fun treeOrNull(value: String?): Uri? = runCatching {
        require(!value.isNullOrBlank() && value.length <= 8192)
        Uri.parse(value).also {
            require(it.scheme == "content" && !it.authority.isNullOrBlank() && DocumentsContract.isTreeUri(it))
            require(DocumentsContract.getTreeDocumentId(it).isNotBlank())
        }
    }.getOrNull()
    fun list(resolver: ContentResolver, tree: Uri, parentId: String): List<ImportDocument> {
        require(DocumentsContract.isTreeUri(tree)) { "Выберите папку через системный диалог" }
        val uri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentId)
        val columns = arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE)
        return (resolver.query(uri, columns, null, null, null) ?: error("Папка недоступна")).use { cursor ->
            val values = mutableListOf<ImportDocument>()
            var count = 0
            while (cursor.moveToNext()) {
                check(++count <= 1000) { "В папке больше 1000 элементов. Выберите отдельную папку с расписаниями." }
                val id = cursor.getString(0) ?: continue
                val name = cursor.getString(1) ?: continue
                val directory = cursor.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR
                if (directory || name.endsWith(".json", true) || name.endsWith(".xlsx", true)) values += ImportDocument(id, name.take(200), directory)
            }
            values.sortedWith(compareByDescending<ImportDocument> { it.directory }.thenBy { it.name.lowercase(java.util.Locale.ROOT) })
        }
    }
}
