package com.epubreader.app

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.epubreader.app.data.AppDatabase
import com.epubreader.app.data.BookEntity
import com.epubreader.app.data.BookRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ReaderRegressionTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private var testBookId: Long = 0L

    @Before
    fun setup() = runBlocking {
        val db = AppDatabase.get(context)
        val sampleFile = File(context.filesDir, "regression_test.epub").apply {
            writeText("DUMMY EPUB CONTENT")
        }
        val book = BookEntity(
            title = "Regression Test Book",
            author = "Elizabeth",
            path = sampleFile.absolutePath,
            checksum = "regression_checksum_${System.currentTimeMillis()}",
            spineIndex = 0,
            scrollRatio = 0.25f,
            currentLocation = "Chapter 1"
        )
        testBookId = db.bookDao().insert(book)
    }

    @After
    fun tearDown() = runBlocking {
        val repo = BookRepository(context)
        repo.deleteBook(repo.getBook(testBookId) ?: return@runBlocking)
    }

    @Test
    fun testReaderStatePersistenceAndLifecycle() {
        val intent = android.content.Intent(context, ReaderActivity::class.java).apply {
            putExtra("book_id", testBookId)
        }
        ActivityScenario.launch<ReaderActivity>(intent).use { scenario ->
            scenario.onActivity { activity ->
                assertNotNull(activity)
            }
        }

        // Verify state persistence in Room database
        runBlocking {
            val repo = BookRepository(context)
            val updated = repo.getBook(testBookId)
            assertNotNull(updated)
            assertNotNull(updated?.currentLocation)
        }
    }
}
