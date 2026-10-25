package dev.nurislam.tv.data

import android.content.Context
import android.content.Intent
import android.provider.DocumentsContract
import androidx.activity.result.contract.ActivityResultContracts

/** Initial URI is a hint only; the system/provider still controls access and selection. */
class ScheduleDocumentPicker : ActivityResultContracts.OpenDocument() {
    override fun createIntent(context: Context, input: Array<String>): Intent = super.createIntent(context, input)
        .addCategory(Intent.CATEGORY_OPENABLE)
        .putExtra(DocumentsContract.EXTRA_INITIAL_URI, initialDirectory())
    companion object { fun initialDirectory(): android.net.Uri = DocumentsContract.buildDocumentUri("com.android.externalstorage.documents", "primary:Download") }
}
