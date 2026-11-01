package com.epubreader.app.ui

import android.content.res.ColorStateList
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.epubreader.app.MainActivity
import com.epubreader.app.R
import com.epubreader.app.data.BookEntity
import com.epubreader.app.data.BookRepository
import com.epubreader.app.data.PrefsManager
import com.epubreader.app.epub.EpubImporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ShelfDialogsHelper(
    private val activity: MainActivity,
    private val viewModel: BookshelfViewModel,
    private val importer: EpubImporter,
    private val onOpenBook: (BookEntity) -> Unit,
    private val onOpenDetails: (BookEntity) -> Unit,
    private val onSelectTreeFolder: () -> Unit,
    private val onRescanFolder: () -> Unit,
    private val onRefreshMetadataFolder: () -> Unit,
    private val onRemoveFolder: () -> Unit,
    private val onScrollToTop: () -> Unit
) {

    // ---------------------------------------------------------------- Sort Dialogs
    fun showSortDialog() {
        val keys = listOf(
            PrefsManager.SortOption.RECENTLY_ADDED,
            PrefsManager.SortOption.RECENTLY_READ,
            PrefsManager.SortOption.TITLE,
            PrefsManager.SortOption.SERIES,
            PrefsManager.SortOption.AUTHOR
        )
        val labels = activity.resources.getStringArray(R.array.sort_field_options)
        val options = labels.indices.map { labels[it] to keys[it] }
        val current = options.indexOfFirst { it.second == viewModel.sort.value }
        val asc = viewModel.sortAscending.value ?: true

        val dialogView = activity.layoutInflater.inflate(R.layout.dialog_general_options, null, false)
        val headerTv = dialogView.findViewById<TextView>(R.id.header)
        headerTv.text = activity.getString(R.string.action_sort)
        val recycler = dialogView.findViewById<RecyclerView>(R.id.recycler)
        recycler.layoutManager = LinearLayoutManager(activity)

        val dialog = AlertDialog.Builder(activity)
            .setView(dialogView)
            .create()

        recycler.adapter = object : RecyclerView.Adapter<CustomDialogViewHolder>() {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CustomDialogViewHolder {
                val view = activity.layoutInflater.inflate(R.layout.item_sort_option, parent, false)
                return CustomDialogViewHolder(view)
            }

            override fun onBindViewHolder(holder: CustomDialogViewHolder, position: Int) {
                holder.titleTv.text = labels[position]
                val isSelected = position == (if (current < 0) 0 else current)
                holder.itemView.setBackgroundResource(if (isSelected) R.drawable.drawer_item_selected_bg else R.drawable.item_background_borderless_material)
                holder.titleTv.setTextColor(ContextCompat.getColor(activity, if (isSelected) R.color.palette_mint else R.color.white))
                holder.checkIv.visibility = View.GONE
                if (isSelected) {
                    holder.directionTv?.visibility = View.VISIBLE
                    val dirText = if (keys[position] == PrefsManager.SortOption.RECENTLY_ADDED || keys[position] == PrefsManager.SortOption.RECENTLY_READ) {
                        if (asc) activity.getString(R.string.sort_newer_first) else activity.getString(R.string.sort_older_first)
                    } else {
                        if (asc) activity.getString(R.string.sort_a_z) else activity.getString(R.string.sort_z_a)
                    }
                    holder.directionTv?.text = dirText
                } else {
                    holder.directionTv?.visibility = View.GONE
                }

                holder.itemView.setOnClickListener {
                    dialog.dismiss()
                    showSortDirectionDialog(options[position].first, options[position].second)
                }
            }

            override fun getItemCount() = labels.size
        }

        dialog.show()
    }

    private fun showSortDirectionDialog(fieldLabel: String, fieldKey: String) {
        val asc = viewModel.sortAscending.value ?: true
        val labels = if (fieldKey == PrefsManager.SortOption.RECENTLY_ADDED || fieldKey == PrefsManager.SortOption.RECENTLY_READ) {
            activity.resources.getStringArray(R.array.sort_order_added)
        } else {
            activity.resources.getStringArray(R.array.sort_order_standard)
        }
        val checked = if (asc) 0 else 1

        val dialogView = activity.layoutInflater.inflate(R.layout.dialog_general_options, null, false)
        val headerTv = dialogView.findViewById<TextView>(R.id.header)
        headerTv.text = "$fieldLabel · ${activity.getString(R.string.sort_choose_order)}"
        val recycler = dialogView.findViewById<RecyclerView>(R.id.recycler)
        recycler.layoutManager = LinearLayoutManager(activity)

        val dialog = AlertDialog.Builder(activity)
            .setView(dialogView)
            .create()

        recycler.adapter = object : RecyclerView.Adapter<CustomDialogViewHolder>() {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CustomDialogViewHolder {
                val view = activity.layoutInflater.inflate(R.layout.item_sort_option, parent, false)
                return CustomDialogViewHolder(view)
            }

            override fun onBindViewHolder(holder: CustomDialogViewHolder, position: Int) {
                holder.titleTv.text = labels[position]
                val isSelected = position == checked
                holder.itemView.setBackgroundResource(if (isSelected) R.drawable.drawer_item_selected_bg else R.drawable.item_background_borderless_material)
                holder.titleTv.setTextColor(ContextCompat.getColor(activity, if (isSelected) R.color.palette_mint else R.color.white))
                holder.checkIv.visibility = if (isSelected) View.VISIBLE else View.GONE
                holder.checkIv.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(activity, R.color.palette_violet))
                holder.directionTv?.visibility = View.GONE

                holder.itemView.setOnClickListener {
                    onScrollToTop()
                    viewModel.setSort(fieldKey, position == 0)
                    dialog.dismiss()
                }
            }

            override fun getItemCount() = labels.size
        }

        dialog.show()
    }

    // ---------------------------------------------------------------- View Mode Dialog
    fun showViewModeDialog() {
        val grid = viewModel.viewModeGrid.value == true
        val cols = viewModel.gridColumns.value ?: 3
        val options = listOf(
            ViewModeOption(activity.getString(R.string.view_mode_list), isGrid = false, columns = 1),
            ViewModeOption(activity.getString(R.string.view_mode_grid) + " 2", isGrid = true, columns = 2),
            ViewModeOption(activity.getString(R.string.view_mode_grid) + " 3", isGrid = true, columns = 3),
            ViewModeOption(activity.getString(R.string.view_mode_grid) + " 4", isGrid = true, columns = 4),
        )
        val selectedIndex = if (!grid) 0 else (cols - 1).coerceIn(1, 3)

        val dialogView = activity.layoutInflater.inflate(R.layout.dialog_general_options, null, false)
        val headerTv = dialogView.findViewById<TextView>(R.id.header)
        headerTv.text = activity.getString(R.string.action_view_mode)
        val recycler = dialogView.findViewById<RecyclerView>(R.id.recycler)
        recycler.layoutManager = LinearLayoutManager(activity)

        val dialog = AlertDialog.Builder(activity)
            .setView(dialogView)
            .create()

        recycler.adapter = ViewModeOptionAdapter(options, selectedIndex) { option ->
            dialog.dismiss()
            if (!option.isGrid) {
                viewModel.setViewMode(false)
            } else {
                viewModel.setViewMode(true)
                viewModel.setGridColumns(option.columns)
            }
        }

        dialog.show()
    }

    // ---------------------------------------------------------------- Book Options Dialog
    fun showBookOptions(book: BookEntity): Boolean {
        val labels = mutableListOf<String>()
        val actions = mutableListOf<() -> Unit>()
        labels += activity.getString(R.string.option_open)
        actions += { onOpenBook(book) }
        labels += activity.getString(R.string.option_details)
        actions += { onOpenDetails(book) }
        labels += if (book.isFavorite) activity.getString(R.string.option_favorite_remove) else activity.getString(R.string.option_favorite_add)
        actions += { viewModel.toggleFavorite(book) }
        if (book.isCurrentlyReading || viewModel.view.value is ShelfView.Reading) {
            labels += activity.getString(R.string.option_remove_reading)
            actions += {
                viewModel.clearCurrentlyReading(book.id)
                val anchor = activity.findViewById<View>(android.R.id.content) ?: activity.window.decorView
                com.epubreader.app.util.CurrentlyReadingUndoSnackbar.show(activity, anchor, book.id)
            }
        }
        labels += activity.getString(R.string.option_remove)
        actions += { confirmDeleteBook(book) }

        val dialogView = activity.layoutInflater.inflate(R.layout.dialog_general_options, null, false)
        val headerTv = dialogView.findViewById<TextView>(R.id.header)
        headerTv.text = book.title
        val recycler = dialogView.findViewById<RecyclerView>(R.id.recycler)
        recycler.layoutManager = LinearLayoutManager(activity)

        val dialog = AlertDialog.Builder(activity)
            .setView(dialogView)
            .create()

        recycler.adapter = object : RecyclerView.Adapter<CustomDialogViewHolder>() {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CustomDialogViewHolder {
                val view = activity.layoutInflater.inflate(R.layout.item_sort_option, parent, false)
                return CustomDialogViewHolder(view)
            }

            override fun onBindViewHolder(holder: CustomDialogViewHolder, position: Int) {
                holder.titleTv.text = labels[position]
                holder.itemView.setBackgroundResource(R.drawable.item_background_borderless_material)
                holder.titleTv.setTextColor(ContextCompat.getColor(activity, R.color.white))
                holder.checkIv.visibility = View.GONE
                holder.directionTv?.visibility = View.GONE

                holder.itemView.setOnClickListener {
                    dialog.dismiss()
                    actions[position]()
                }
            }

            override fun getItemCount() = labels.size
        }

        dialog.show()
        return true
    }

    private fun confirmDeleteBook(book: BookEntity) {
        com.epubreader.app.util.CustomConfirmationDialog.show(
            context = activity,
            title = activity.getString(R.string.option_remove_confirmation_title),
            message = activity.getString(R.string.option_remove_confirmation_message, book.title),
            positiveText = activity.getString(R.string.delete),
            negativeText = activity.getString(R.string.cancel)
        ) {
            val anchor = activity.findViewById<View>(android.R.id.content) ?: activity.window.decorView
            com.epubreader.app.util.BookDeleteUndoSnackbar.show(activity, anchor, book)
        }
    }

    // ---------------------------------------------------------------- Folder Options Dialog
    fun showFolderOptionsDialog() {
        val items = arrayOf(
            activity.getString(R.string.folder_select),
            activity.getString(R.string.folder_scan_now),
            activity.getString(R.string.folder_refresh_metadata),
            activity.getString(R.string.folder_remove)
        )

        val dialogView = activity.layoutInflater.inflate(R.layout.dialog_general_options, null, false)
        val headerTv = dialogView.findViewById<TextView>(R.id.header)
        headerTv.text = activity.getString(R.string.folder_options)
        val recycler = dialogView.findViewById<RecyclerView>(R.id.recycler)
        recycler.layoutManager = LinearLayoutManager(activity)

        val dialog = AlertDialog.Builder(activity)
            .setView(dialogView)
            .create()

        recycler.adapter = object : RecyclerView.Adapter<CustomDialogViewHolder>() {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CustomDialogViewHolder {
                val view = activity.layoutInflater.inflate(R.layout.item_sort_option, parent, false)
                return CustomDialogViewHolder(view)
            }

            override fun onBindViewHolder(holder: CustomDialogViewHolder, position: Int) {
                holder.titleTv.text = items[position]
                holder.itemView.setBackgroundResource(R.drawable.item_background_borderless_material)
                holder.titleTv.setTextColor(ContextCompat.getColor(activity, R.color.white))
                holder.checkIv.visibility = View.GONE
                holder.directionTv?.visibility = View.GONE

                holder.itemView.setOnClickListener {
                    dialog.dismiss()
                    when (position) {
                        0 -> onSelectTreeFolder()
                        1 -> onRescanFolder()
                        2 -> onRefreshMetadataFolder()
                        3 -> onRemoveFolder()
                    }
                }
            }

            override fun getItemCount() = items.size
        }

        dialog.show()
    }

    private class CustomDialogViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val titleTv: TextView = itemView.findViewById(R.id.title)
        val checkIv: ImageView = itemView.findViewById(R.id.check)
        val directionTv: TextView? = itemView.findViewById(R.id.direction)
    }
}
