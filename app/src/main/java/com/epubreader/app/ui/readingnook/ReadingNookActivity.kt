package com.epubreader.app.ui.readingnook

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.epubreader.app.databinding.ActivityReadingNookBinding
import com.epubreader.app.util.SystemBarController

class ReadingNookActivity : AppCompatActivity() {

    private lateinit var binding: ActivityReadingNookBinding
    private var bookId: Long = -1L
    private lateinit var viewController: ReadingNookViewController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityReadingNookBinding.inflate(layoutInflater)
        setContentView(binding.root)
        SystemBarController.apply(this)

        bookId = intent.getLongExtra(EXTRA_BOOK_ID, -1L)
        if (bookId < 0) {
            finish()
            return
        }

        viewController = ReadingNookViewController(this, binding, bookId)
        viewController.setup()
    }

    override fun dispatchTouchEvent(ev: android.view.MotionEvent): Boolean {
        if (viewController.dispatchTouchEvent(ev)) return true
        return super.dispatchTouchEvent(ev)
    }

    companion object {
        const val EXTRA_BOOK_ID = "book_id"
    }
}
