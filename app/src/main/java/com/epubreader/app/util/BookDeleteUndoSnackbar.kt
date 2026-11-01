package com.epubreader.app.util

import android.content.Context
import android.os.SystemClock
import android.view.View
import com.epubreader.app.R
import com.epubreader.app.data.AppDatabase
import com.epubreader.app.data.BookEntity
import com.epubreader.app.data.BookRepository
import com.epubreader.app.epub.EpubImporter
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Keeps the Library book deletion undo action alive across Activity transitions
 * (e.g. returning from BookDetailsActivity to MainActivity upon deletion).
 */
object BookDeleteUndoSnackbar {
    private const val DURATION_MS = 4000L

    private data class PendingDelete(
        val book: BookEntity,
        val expiresAt: Long,
    )

    private var pending: PendingDelete? = null
    private var visible: Snackbar? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    fun show(context: Context, anchor: View, book: BookEntity) {
        val appContext = context.applicationContext
        // Remove from Room DB immediately so it disappears from UI lists
        scope.launch(Dispatchers.IO) {
            BookRepository(appContext).deleteBook(book)
        }

        pending = PendingDelete(book, SystemClock.elapsedRealtime() + DURATION_MS)
        showPending(context, anchor)
    }

    fun showPending(context: Context, anchor: View) {
        val item = pending ?: return
        val remaining = item.expiresAt - SystemClock.elapsedRealtime()
        if (remaining <= 0L) {
            finalizeDelete(context.applicationContext)
            return
        }

        if (visible?.isShown == true) return

        val appContext = context.applicationContext
        visible = Snackbar.make(anchor, R.string.book_removed_from_library, Snackbar.LENGTH_LONG)
            .setDuration(remaining.toInt().coerceAtLeast(1500))
            .setAction(R.string.undo) {
                val pendingBook = pending?.book ?: return@setAction
                pending = null
                visible = null
                // Restore book in Room DB on Undo
                scope.launch(Dispatchers.IO) {
                    AppDatabase.get(appContext).bookDao().insert(pendingBook)
                }
            }
            .also { snackbar ->
                snackbar.addCallback(object : Snackbar.Callback() {
                    override fun onDismissed(transientBottomBar: Snackbar?, event: Int) {
                        if (visible === transientBottomBar) visible = null
                        if (event != DISMISS_EVENT_ACTION) {
                            finalizeDelete(appContext)
                        }
                    }
                })
                snackbar.show()
            }
    }

    private fun finalizeDelete(appContext: Context) {
        val bookToDelete = pending?.book ?: return
        pending = null
        visible = null
        scope.launch(Dispatchers.IO) {
            EpubImporter(appContext).deleteImported(bookToDelete)
        }
    }
}
