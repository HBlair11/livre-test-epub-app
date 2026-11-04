package com.epubreader.app.util

import android.content.Context
import android.os.SystemClock
import android.view.View
import com.epubreader.app.R
import com.epubreader.app.data.BookEntity
import com.epubreader.app.data.BookRepository
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Keeps the "Moved to Trash" undo action alive across Activity transitions
 * (e.g. returning from BookDetailsActivity to MainActivity upon moving to trash).
 */
object BookDeleteUndoSnackbar {
    private const val DURATION_MS = 4000L

    private data class PendingTrash(
        val book: BookEntity,
        val expiresAt: Long,
    )

    private var pending: PendingTrash? = null
    private var visible: Snackbar? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    fun show(context: Context, anchor: View, book: BookEntity, showImmediately: Boolean = true) {
        val appContext = context.applicationContext
        // Move to trash in Room DB immediately so it disappears from active lists
        scope.launch(Dispatchers.IO) {
            BookRepository(appContext).markInTrash(book.id)
        }

        pending = PendingTrash(book, SystemClock.elapsedRealtime() + DURATION_MS)
        if (showImmediately) {
            showPending(context, anchor)
        }
    }

    fun showPending(context: Context, anchor: View) {
        val item = pending ?: return
        val remaining = item.expiresAt - SystemClock.elapsedRealtime()
        if (remaining <= 0L) {
            pending = null
            visible = null
            return
        }

        if (visible?.isShown == true) return

        val appContext = context.applicationContext
        visible = Snackbar.make(anchor, R.string.book_moved_to_trash, Snackbar.LENGTH_LONG)
            .setDuration(remaining.toInt().coerceAtLeast(1500))
            .setAction(R.string.undo) {
                val pendingBook = pending?.book ?: return@setAction
                pending = null
                visible = null
                // Restore book from trash in Room DB on Undo
                scope.launch(Dispatchers.IO) {
                    BookRepository(appContext).restoreFromTrash(pendingBook.id)
                }
            }
            .also { snackbar ->
                snackbar.addCallback(object : Snackbar.Callback() {
                    override fun onDismissed(transientBottomBar: Snackbar?, event: Int) {
                        if (visible === transientBottomBar) visible = null
                        if (SystemClock.elapsedRealtime() >= (pending?.expiresAt ?: 0L)) {
                            pending = null
                        }
                    }
                })
                snackbar.show()
            }
    }
}
