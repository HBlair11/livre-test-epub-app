package com.epubreader.app.ui

import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.epubreader.app.MainActivity
import com.epubreader.app.data.BookEntity
import com.epubreader.app.util.CurrentlyReadingUndoSnackbar

class ReadingViewController(
    private val activity: MainActivity,
    private val onClearCurrentlyReading: (Long) -> Unit
) {

    private var touchHelper: ItemTouchHelper? = null

    fun attachSwipeToDismiss(recycler: RecyclerView?, getBookAt: (Int) -> BookEntity?) {
        touchHelper?.attachToRecyclerView(null)
        val cb = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
            override fun onMove(
                rv: RecyclerView,
                vh: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ) = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, dir: Int) {
                val pos = viewHolder.bindingAdapterPosition
                val book = getBookAt(pos) ?: return
                onClearCurrentlyReading(book.id)
                CurrentlyReadingUndoSnackbar.show(activity, activity.findViewById(android.R.id.content), book.id)
            }
        }
        touchHelper = ItemTouchHelper(cb).also { r -> recycler?.let { r.attachToRecyclerView(it) } }
    }

    fun detachSwipeToDismiss() {
        touchHelper?.attachToRecyclerView(null)
    }
}
