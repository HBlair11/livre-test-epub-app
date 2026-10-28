package com.epubreader.app.ui

import android.view.View
import android.widget.TextView
import com.epubreader.app.MainActivity
import com.epubreader.app.R
import com.epubreader.app.data.PrefsManager
import com.google.android.material.snackbar.Snackbar

class FoldersViewController(
    private val activity: MainActivity,
    private val prefs: PrefsManager,
    private val onScanNow: () -> Unit,
    private val onRefreshMetadata: () -> Unit,
    private val onSelectFolder: () -> Unit,
    private val getFolderDisplayName: (String) -> String
) {

    fun show(foldersRoot: View, snackbarContainer: View) {
        foldersRoot.visibility = View.VISIBLE

        val tvFolderTitle = foldersRoot.findViewById<TextView>(R.id.tvFolderTitle)
        val tvFolderHint = foldersRoot.findViewById<TextView>(R.id.tvFolderHint)
        val btnScanNow = foldersRoot.findViewById<View>(R.id.btnScanNow)
        val btnRefreshMetadata = foldersRoot.findViewById<View>(R.id.btnRefreshMetadata)
        val btnSelectFolder = foldersRoot.findViewById<View>(R.id.btnSelectFolder)
        val btnRemoveFolder = foldersRoot.findViewById<View>(R.id.btnRemoveFolder)

        val uri = prefs.selectedFolderUri
        tvFolderTitle?.text = if (uri != null) {
            activity.getString(R.string.folder_selected, getFolderDisplayName(uri))
        } else {
            activity.getString(R.string.folder_none)
        }
        tvFolderHint?.text = activity.getString(R.string.folder_hint)

        if (uri != null) {
            btnScanNow?.visibility = View.VISIBLE
            btnRefreshMetadata?.visibility = View.VISIBLE
            btnRemoveFolder?.visibility = View.VISIBLE

            btnScanNow?.setOnClickListener { onScanNow() }
            btnRefreshMetadata?.setOnClickListener { onRefreshMetadata() }
            btnSelectFolder?.setOnClickListener { onSelectFolder() }
            btnRemoveFolder?.setOnClickListener {
                prefs.selectedFolderUri = null
                show(foldersRoot, snackbarContainer)
                Snackbar.make(snackbarContainer, R.string.folder_none, Snackbar.LENGTH_SHORT).show()
            }
        } else {
            btnScanNow?.visibility = View.GONE
            btnRefreshMetadata?.visibility = View.GONE
            btnRemoveFolder?.visibility = View.GONE

            btnSelectFolder?.setOnClickListener { onSelectFolder() }
        }
    }
}
