package com.epubreader.app.ui

import com.epubreader.app.util.SystemBarController

import androidx.activity.result.contract.ActivityResultContracts
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.epubreader.app.databinding.ActivityBookDetailsBinding

class BookDetailsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBookDetailsBinding
    private var bookId: Long = -1L
    private lateinit var bookDetailsViewController: BookDetailsViewController

    private val coverPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { bookDetailsViewController.applyPickedCover(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityBookDetailsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        SystemBarController.apply(this)

        setSupportActionBar(binding.toolbar)
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }

        bookId = intent.getLongExtra(EXTRA_BOOK_ID, -1L)
        if (bookId < 0) {
            finish()
            return
        }

        bookDetailsViewController = BookDetailsViewController(this, binding, bookId, coverPicker)
        bookDetailsViewController.setup()
    }

    override fun onResume() {
        super.onResume()
        if (bookDetailsViewController.shouldRefreshOnResume) {
            bookDetailsViewController.shouldRefreshOnResume = false
            bookDetailsViewController.load()
        }
        binding.root.post {
            com.epubreader.app.util.CurrentlyReadingUndoSnackbar.showPending(this, binding.root)
        }
    }

    companion object {
        const val EXTRA_BOOK_ID = "book_id"
    }
}
