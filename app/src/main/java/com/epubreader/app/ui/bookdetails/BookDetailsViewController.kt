package com.epubreader.app.ui

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.text.format.DateFormat
import android.text.format.Formatter
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

        if (!book.publisher.isNullOrBlank()) {
            binding.publisherContainer.visibility = View.VISIBLE
            binding.publisherValue.text = book.publisher
        } else {
            binding.publisherContainer.visibility = View.GONE
        }

        if (book.publishYear != null && book.publishYear > 0) {
            binding.publishYearContainer.visibility = View.VISIBLE
            binding.publishYearValue.text = book.publishYear.toString()
        } else {
            binding.publishYearContainer.visibility = View.GONE
        }

        if (!book.language.isNullOrBlank()) {
            binding.languageContainer.visibility = View.VISIBLE
            binding.languageValue.text = book.language
        } else {
            binding.languageContainer.visibility = View.GONE
        }

        if (!book.subjectTags.isNullOrBlank()) {
            binding.subjectsContainer.visibility = View.VISIBLE
            binding.subjectsValue.text = book.subjectTags
        } else {
            binding.subjectsContainer.visibility = View.GONE
        }

        binding.filenameValue.text = book.sourceFilename ?: File(book.path).name
        binding.filesizeValue.text = Formatter.formatFileSize(activity, book.fileSize)
        binding.dateAddedValue.text = fmtDate(book.addedDate)

        if (book.lastOpenedDate != null) {
            binding.lastReadContainer.visibility = View.VISIBLE
            val percent = (book.progress * 100).toInt().coerceIn(0, 100)
            binding.progressPercentText.text = activity.getString(R.string.progress_percent, percent)
            binding.progressBar.progress = percent

            val dateStr = fmtDate(book.lastOpenedDate)
            val progressText = if (book.progress >= 0.90f) {
                activity.getString(R.string.progress_completed)
            } else {
                "$percent% read"
            }
            binding.lastReadValue.text = "$dateStr • $progressText"
        } else {
            binding.lastReadContainer.visibility = View.GONE
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

    private fun confirmDeleteBook(book: BookEntity) {
        AlertDialog
            .Builder(activity)
            .setTitle(R.string.option_remove)
            .setMessage(activity.getString(R.string.option_remove) + ": " + book.title)
            .setPositiveButton(R.string.ok) { _, _ ->
                activity.lifecycleScope.launch(Dispatchers.IO) {
                    importer.deleteImported(book)
                    BookRepository(activity.applicationContext).deleteBook(book)
                    withContext(Dispatchers.Main) {
                        activity.finish()
                    }
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun fmtDate(ts: Long): String =
        DateFormat.getMediumDateFormat(activity).format(Date(ts))
}
