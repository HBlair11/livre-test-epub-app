package com.epubreader.app.ui

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.text.format.DateFormat
import android.view.View
import android.widget.TextView
import androidx.activity.result.ActivityResultLauncher
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide

import com.epubreader.app.ReaderActivity
import com.epubreader.app.R
import com.epubreader.app.data.AppDatabase
import com.epubreader.app.data.BookEntity
import com.epubreader.app.data.BookRepository
import com.epubreader.app.databinding.ActivityBookDetailsBinding
import com.epubreader.app.epub.EpubImporter
import com.epubreader.app.ui.readingnook.ReadingNookActivity
import com.epubreader.app.util.CurrentlyReadingUndoSnackbar
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Date

class BookDetailsViewController(
    private val activity: AppCompatActivity,
    private val binding: ActivityBookDetailsBinding,
    private val bookId: Long,
    private val coverPicker: ActivityResultLauncher<Array<String>>
) {

    private lateinit var importer: EpubImporter
    private var favoriteUpdateJob: Job? = null
    var shouldRefreshOnResume = false
    fun setup() {
        val headerContainer = binding.headerContainer
        headerContainer.removeAllViews()
        val inflated = android.view.LayoutInflater.from(activity).inflate(R.layout.header_secondary_category_2, headerContainer, true)
        val toolbar = inflated.findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
        activity.setSupportActionBar(toolbar)
        toolbar.setNavigationOnClickListener {
            activity.finish()
        }
        inflated.findViewById<TextView>(R.id.toolbarSubtitle)?.text = activity.getString(R.string.header_book_details)
        inflated.findViewById<View>(R.id.headerBtnRead)?.setOnClickListener {
            binding.btnRead.performClick()
        }
        importer = EpubImporter(activity.applicationContext)
        load()
    }

    fun load() {
        activity.lifecycleScope.launch(Dispatchers.IO) {
            val book = AppDatabase
                .get(activity.applicationContext)
                .bookDao()
                .getById(bookId)
                ?: return@launch

            withContext(Dispatchers.Main) {
                bind(book)
            }
        }
    }

    private fun bind(book: BookEntity) {
        activity.title = book.title
        val toolbarTitle = binding.headerContainer.findViewById<TextView>(R.id.toolbarTitle)
        toolbarTitle?.text = book.title
        binding.title.text = book.title

        book.coverPath?.let { coverPath ->
            Glide.with(activity)
                .load(File(coverPath))
                .into(binding.cover)
        }

        binding.bookValue.text = book.title
        binding.authorValue.text = book.author.ifBlank { activity.getString(R.string.unknown_author) }

        if (!book.series.isNullOrBlank()) {
            binding.seriesContainer.visibility = View.VISIBLE
            binding.seriesValue.text = book.seriesIndex?.let { index ->
                val formattedIndex = if (index % 1.0 == 0.0) index.toInt().toString() else index.toString()
                "${book.series} #$formattedIndex"
            } ?: book.series
        } else {
            binding.seriesContainer.visibility = View.GONE
        }

        val visibleRows = mutableListOf<View>()

        val hasPublisher = !book.publisher.isNullOrBlank()
        val hasPublishYear = book.publishYear != null && book.publishYear > 0

        if (!hasPublisher && !hasPublishYear) {
            binding.row1.visibility = View.GONE
        } else {
            binding.row1.visibility = View.VISIBLE
            visibleRows.add(binding.row1)
            val pParams = binding.publisherContainer.layoutParams as android.widget.LinearLayout.LayoutParams
            val yParams = binding.publishYearContainer.layoutParams as android.widget.LinearLayout.LayoutParams

            if (hasPublisher && hasPublishYear) {
                binding.publisherContainer.visibility = View.VISIBLE
                binding.publisherContainer.setPadding(0, 0, dpToPx(8), 0)
                binding.publisherValue.text = book.publisher
                pParams.weight = 1f
                pParams.width = 0

                binding.publishYearContainer.visibility = View.VISIBLE
                binding.publishYearContainer.setPadding(dpToPx(8), 0, 0, 0)
                binding.publishYearValue.text = book.publishYear.toString()
                yParams.weight = 1f
                yParams.width = 0
            } else if (hasPublisher) {
                binding.publisherContainer.visibility = View.VISIBLE
                binding.publisherContainer.setPadding(0, 0, 0, 0)
                binding.publisherValue.text = book.publisher
                pParams.weight = 1f
                pParams.width = 0

                binding.publishYearContainer.visibility = View.GONE
                yParams.weight = 0f
                yParams.width = 0
            } else {
                binding.publisherContainer.visibility = View.GONE
                pParams.weight = 0f
                pParams.width = 0

                binding.publishYearContainer.visibility = View.VISIBLE
                binding.publishYearContainer.setPadding(0, 0, 0, 0)
                binding.publishYearValue.text = book.publishYear.toString()
                yParams.weight = 1f
                yParams.width = 0
            }
            binding.publisherContainer.layoutParams = pParams
            binding.publishYearContainer.layoutParams = yParams
        }

        val hasLanguage = !book.language.isNullOrBlank()
        val sizeMb = book.fileSize.toDouble() / (1024 * 1024)
        val sizeStr = String.format(java.util.Locale.US, "%.2f MB", sizeMb)
        val lParams = binding.languageContainer.layoutParams as android.widget.LinearLayout.LayoutParams
        val fParams = binding.filesizeContainer.layoutParams as android.widget.LinearLayout.LayoutParams

        binding.row2.visibility = View.VISIBLE
        visibleRows.add(binding.row2)

        if (hasLanguage) {
            binding.languageContainer.visibility = View.VISIBLE
            binding.languageContainer.setPadding(0, 0, dpToPx(8), 0)
            binding.languageValue.text = book.language.uppercase()
            lParams.weight = 1f
            lParams.width = 0

            binding.filesizeContainer.visibility = View.VISIBLE
            binding.filesizeContainer.setPadding(dpToPx(8), 0, 0, 0)
            binding.filesizeValue.text = sizeStr
            fParams.weight = 1f
            fParams.width = 0
        } else {
            binding.languageContainer.visibility = View.GONE
            lParams.weight = 0f
            lParams.width = 0

            binding.filesizeContainer.visibility = View.VISIBLE
            binding.filesizeContainer.setPadding(0, 0, 0, 0)
            binding.filesizeValue.text = sizeStr
            fParams.weight = 1f
            fParams.width = 0
        }
        binding.languageContainer.layoutParams = lParams
        binding.filesizeContainer.layoutParams = fParams

        if (book.addedDate > 0) {
            binding.dateAddedContainer.visibility = View.VISIBLE
            binding.dateAddedValue.text = fmtDate(book.addedDate)
            visibleRows.add(binding.dateAddedContainer)
        } else {
            binding.dateAddedContainer.visibility = View.GONE
        }

        if (!book.subjectTags.isNullOrBlank()) {
            binding.subjectsContainer.visibility = View.VISIBLE
            binding.subjectsValue.text = book.subjectTags
            visibleRows.add(binding.subjectsContainer)
        } else {
            binding.subjectsContainer.visibility = View.GONE
        }

        binding.filenameContainer.visibility = View.VISIBLE
        binding.filenameValue.text = book.sourceFilename ?: File(book.path).name
        visibleRows.add(binding.filenameContainer)

        visibleRows.forEachIndexed { index, rowView ->
            val lp = rowView.layoutParams as android.widget.LinearLayout.LayoutParams
            lp.topMargin = if (index == 0) 0 else dpToPx(12)
            rowView.layoutParams = lp
        }

        binding.progressSectionContainer.visibility = View.VISIBLE
        binding.lastReadContainer.visibility = View.VISIBLE
        val percent = if (book.progress >= 0.90f) 100 else (book.progress * 100).toInt().coerceIn(0, 100)
        if (book.progress >= 0.90f) {
            binding.progressPercentText.text = activity.getString(R.string.progress_completed)
        } else {
            binding.progressPercentText.text = activity.getString(R.string.progress_percent, percent)
        }
        binding.progressBar.progress = percent

        if (book.lastOpenedDate != null) {
            val dateStr = fmtDate(book.lastOpenedDate)
            binding.lastReadValue.text = "${activity.getString(R.string.book_details_date_last_read)} $dateStr"
        } else {
            binding.lastReadValue.text = activity.getString(R.string.progress_not_read_yet)
        }

        if (!book.description.isNullOrBlank()) {
            binding.descriptionContainer.visibility = View.VISIBLE
            binding.description.text = book.description
        } else {
            binding.descriptionContainer.visibility = View.GONE
        }

        binding.btnRead.setOnClickListener {
            shouldRefreshOnResume = true
            activity.lifecycleScope.launch(Dispatchers.IO) {
                AppDatabase.get(activity.applicationContext).bookDao().markOpened(book.id, System.currentTimeMillis())
            }
            activity.startActivity(
                Intent(activity, ReaderActivity::class.java).putExtra(ReaderActivity.EXTRA_BOOK_ID, book.id)
            )
        }

        binding.btnReadingNook.setOnClickListener {
            val intent = Intent(activity, ReadingNookActivity::class.java).apply {
                putExtra(ReadingNookActivity.EXTRA_BOOK_ID, bookId)
            }
            activity.startActivity(intent)
        }

        var favoriteState = book.isFavorite
        fun updateFavoriteUI(isFav: Boolean) {
            binding.btnFavorite.setIconResource(if (isFav) R.drawable.ic_favorite else R.drawable.ic_favorite_border)
            binding.btnFavorite.setText(if (isFav) R.string.book_details_favorite_remove else R.string.book_details_favorite_add)
        }
        updateFavoriteUI(favoriteState)

        binding.btnFavorite.setOnClickListener {
            favoriteState = !favoriteState
            val newFavoriteState = favoriteState
            updateFavoriteUI(newFavoriteState)
            favoriteUpdateJob?.cancel()
            favoriteUpdateJob = activity.lifecycleScope.launch(Dispatchers.IO) {
                AppDatabase.get(activity.applicationContext).bookDao().setFavorite(book.id, newFavoriteState)
            }
        }

        binding.btnEditDetails.setOnClickListener { showEditDialog(book) }

        binding.btnRemoveReading.setOnClickListener {
            activity.lifecycleScope.launch(Dispatchers.IO) {
                AppDatabase.get(activity.applicationContext).bookDao().clearCurrentlyReading(book.id)
            }
            CurrentlyReadingUndoSnackbar.show(activity, binding.root, book.id)
        }

        binding.btnRemove.setOnClickListener {
            confirmDeleteBook(book)
        }
    }

    private fun showEditDialog(book: BookEntity) {
        val view = activity.layoutInflater.inflate(R.layout.dialog_book_edit, null)
        val headerTv = view.findViewById<TextView>(R.id.header)
        headerTv?.text = activity.getString(R.string.edit_book_details)
        val footerLayout = view.findViewById<View>(R.id.footer)
        footerLayout.tag = "editBookFooter"

        val titleEdit = view.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.titleEdit)
        val authorEdit = view.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.authorEdit)
        val seriesEdit = view.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.seriesEdit)
        val seriesIndexEdit = view.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.seriesIndexEdit)
        val publisherEdit = view.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.publisherEdit)
        val yearEdit = view.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.yearEdit)
        val languageEdit = view.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.languageEdit)
        val identifierEdit = view.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.identifierEdit)
        val subjectsEdit = view.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.subjectsEdit)
        val descriptionEdit = view.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.descriptionEdit)

        titleEdit.setText(book.title)
        authorEdit.setText(book.author)
        seriesEdit.setText(book.series.orEmpty())
        seriesIndexEdit.setText(book.seriesIndex?.toString().orEmpty())
        publisherEdit.setText(book.publisher.orEmpty())
        yearEdit.setText(book.publishYear?.toString().orEmpty())
        languageEdit.setText(book.language.orEmpty())
        identifierEdit.setText(book.identifier.orEmpty())
        subjectsEdit.setText(book.subjectTags.orEmpty())
        descriptionEdit.setText(book.description.orEmpty())

        val dialog = AlertDialog.Builder(activity)
            .setView(view)
            .create()

        val btnCover = view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnCover)
        val btnCancel = view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnCancel)
        val btnOk = view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnOk)

        btnCancel.setOnClickListener { dialog.dismiss() }
        btnCover.setOnClickListener {
            dialog.dismiss()
            coverPicker.launch(arrayOf("image/*"))
        }
        btnOk.setOnClickListener {
            val newTitle = titleEdit.text?.toString()?.trim().orEmpty().ifBlank { activity.getString(R.string.untitled) }
            val newAuthor = authorEdit.text?.toString()?.trim().orEmpty()
            val newSeries = seriesEdit.text?.toString()?.trim().orEmpty().ifBlank { null }
            val newSeriesIndex = seriesIndexEdit.text?.toString()?.let { if (it.isBlank()) null else it.trim().toDoubleOrNull() }
            val newPublisher = publisherEdit.text?.toString()?.trim()?.takeIf { it.isNotBlank() }
            val newYear = yearEdit.text?.toString()?.trim()?.toIntOrNull()
            val newLanguage = languageEdit.text?.toString()?.trim()?.takeIf { it.isNotBlank() }
            val newIdentifier = identifierEdit.text?.toString()?.trim()?.takeIf { it.isNotBlank() }
            val newSubjects = subjectsEdit.text?.toString()?.trim()?.takeIf { it.isNotBlank() }
            val newDescription = descriptionEdit.text?.toString()?.trim()?.takeIf { it.isNotBlank() }
            activity.lifecycleScope.launch(Dispatchers.IO) {
                AppDatabase.get(activity.applicationContext).bookDao().updateMetadataOnly(
                    id = book.id, title = newTitle, author = newAuthor, series = newSeries, seriesIndex = newSeriesIndex,
                    language = newLanguage, publisher = newPublisher, description = newDescription, identifier = newIdentifier,
                    publishYear = newYear, subjectTags = newSubjects, sourceUri = book.sourceUri, sourceFilename = book.sourceFilename,
                    sortTitle = newTitle, sortAuthor = newAuthor,
                )
                withContext(Dispatchers.Main) {
                    Snackbar.make(binding.root, R.string.details_saved, Snackbar.LENGTH_SHORT).show()
                    load()
                }
            }
            dialog.dismiss()
        }
        dialog.show()
    }

    fun applyPickedCover(uri: Uri) {
        activity.lifecycleScope.launch(Dispatchers.IO) {
            val book = AppDatabase.get(activity.applicationContext).bookDao().getById(bookId) ?: return@launch
            val dir = File(activity.filesDir, "covers").apply { mkdirs() }
            val target = File(dir, "${book.id}_manual_${System.currentTimeMillis()}.png")
            try {
                activity.contentResolver.openInputStream(uri)?.use { input ->
                    val bitmap = BitmapFactory.decodeStream(input) ?: return@launch
                    target.outputStream().use { out -> bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 95, out) }
                    bitmap.recycle()
                } ?: return@launch
                val old = book.coverPath
                AppDatabase.get(activity.applicationContext).bookDao().update(book.copy(coverPath = target.absolutePath, metadataEdited = true))
                if (!old.isNullOrBlank() && old != target.absolutePath && old.contains(File(activity.filesDir, "covers").absolutePath)) File(old).delete()
                withContext(Dispatchers.Main) {
                    Snackbar.make(binding.root, R.string.details_saved, Snackbar.LENGTH_SHORT).show()
                    load()
                }
            } catch (_: Exception) {
                target.delete()
            }
        }
    }

    private fun dpToPx(dp: Int): Int = (dp * activity.resources.displayMetrics.density).toInt()

    private fun confirmDeleteBook(book: BookEntity) {
        com.epubreader.app.util.CustomConfirmationDialog.show(
            context = activity,
            title = activity.getString(R.string.option_remove_confirmation_title),
            message = activity.getString(R.string.option_remove_confirmation_message, book.title),
            positiveText = activity.getString(R.string.delete),
            negativeText = activity.getString(R.string.cancel)
        ) {
            com.epubreader.app.util.BookDeleteUndoSnackbar.show(activity, binding.root, book, showImmediately = false)
            activity.finish()
        }
    }

    private fun fmtDate(ts: Long): String =
        DateFormat.getMediumDateFormat(activity).format(Date(ts))
}
