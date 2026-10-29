package com.epubreader.app.ui


import android.content.Intent
import android.text.format.DateFormat
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import com.epubreader.app.MainActivity
import com.epubreader.app.R
import com.epubreader.app.data.BookEntity
import com.epubreader.app.databinding.ViewHomeBinding

class HomeViewController(
    private val activity: MainActivity,
    private val onOpenBook: (BookEntity) -> Unit,
    private val onOpenBookDetails: (BookEntity) -> Unit,
    private val onShowBookOptions: (BookEntity) -> Boolean,
    private val onSetView: (ShelfView) -> Unit,
    private val onLoadCover: (BookEntity, ImageView) -> Unit
) {

    private val homeAdapters = mutableMapOf<Int, HomeBookAdapter>()
    private var isInitialized = false

    fun setup(binding: ViewHomeBinding) {
        if (isInitialized) return
        isInitialized = true

        val specs = listOf(
            R.id.homeRecentlyAdded to binding.homeRecentlyAdded,
            R.id.homeFavorites to binding.homeFavorites,
        )
        specs.forEach { (key, recycler) ->
            val adapter = HomeBookAdapter(onOpenBook, onShowBookOptions)
            recycler.layoutManager = LinearLayoutManager(activity, LinearLayoutManager.HORIZONTAL, false)
            recycler.adapter = adapter
            homeAdapters[key] = adapter
        }
    }

    fun render(binding: ViewHomeBinding, content: HomeContent) {
        setup(binding)

        binding.homeEmpty.visibility = if (content.hasBooks) View.GONE else View.VISIBLE
        binding.homeRecentlyAddedSection.visibility =
            if (content.recentlyAdded.isEmpty()) View.GONE else View.VISIBLE
        val hasContinueReading = content.continueReading != null
        binding.homeContinueSection.visibility = if (hasContinueReading) View.VISIBLE else View.GONE
        binding.homeContinueCard.visibility = if (hasContinueReading) View.VISIBLE else View.GONE

        binding.homeContinueViewAll.setOnClickListener { onSetView(ShelfView.Reading) }
        binding.homeRecentlyAddedViewAll.setOnClickListener {
            val intent = Intent(activity, RecentlyAddedActivity::class.java)
            activity.startActivity(intent)
        }
        binding.homeFavoritesViewAll.setOnClickListener { onSetView(ShelfView.Favorites) }
        binding.homeAllAuthorsBtn.setOnClickListener { onSetView(ShelfView.AuthorsList) }
        binding.homeAllSeriesBtn.setOnClickListener { onSetView(ShelfView.SeriesList) }

        content.continueReading?.let { book ->
            val dateStr = DateFormat.format("MMM dd, yyyy", book.lastOpenedDate ?: book.addedDate).toString()
            binding.homeContinueDate.text = "Opened $dateStr"
            binding.homeContinueTitle.text = book.title

            val authorStr = book.author.ifBlank { activity.getString(R.string.unknown_author) }
            val seriesText = book.series
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?.let { series ->
                    val index = book.seriesIndex
                    if (index != null) {
                        val formattedIndex = if (index % 1.0 == 0.0) index.toInt().toString() else index.toString()
                        " • $series #$formattedIndex"
                    } else {
                        " • $series"
                    }
                }.orEmpty()
            binding.homeContinueAuthorSeries.text = "by $authorStr$seriesText"

            val location = book.currentLocation?.trim().orEmpty()
            binding.homeContinueLocation.text = if (location.isBlank()) {
                ""
            } else {
                "Current section: \"$location\""
            }
            binding.homeContinueLocation.visibility = if (location.isBlank()) View.GONE else View.VISIBLE

            val pct = (book.progress * 100).toInt()
            binding.homeContinueProgress.text = if (book.progress >= 0.90f) {
                activity.getString(R.string.progress_completed)
            } else {
                "$pct%"
            }
            binding.homeContinueProgressBar.progress = if (book.progress >= 0.90f) 100 else pct

            onLoadCover(book, binding.homeContinueCover)
            binding.homeContinueResumeBtn.setOnClickListener { onOpenBook(book) }
            binding.homeContinueDetailsBtn.setOnClickListener { onOpenBookDetails(book) }
            binding.homeContinueCard.setOnClickListener { onOpenBook(book) }
        }

        submitHomeShelf(R.id.homeRecentlyAdded, content.recentlyAdded)
        binding.homeFavoritesSection.visibility =
            if (content.favorites.isEmpty()) View.GONE else View.VISIBLE
        submitHomeShelf(R.id.homeFavorites, content.favorites)

        populateAuthorRows(binding, content.topAuthors)
        populateSeriesRows(binding, content.topSeries)
        binding.homeAuthorsSection.visibility =
            if (content.topAuthors.isEmpty()) View.GONE else View.VISIBLE
        binding.homeSeriesSection.visibility = if (content.topSeries.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun submitHomeShelf(key: Int, books: List<BookEntity>) {
        homeAdapters[key]?.submitList(books)
    }

    private fun populateAuthorRows(binding: ViewHomeBinding, authors: List<HomeGroup>) {
        val container = binding.homeAuthorRowsContainer
        container.removeAllViews()
        authors.take(4).forEach { group ->
            val row =
                activity.layoutInflater.inflate(R.layout.item_home_author_series, container, false) as ViewGroup
            val tvTitle = row.findViewById<TextView>(R.id.title)
            val tvCount = row.findViewById<TextView>(R.id.count)

            tvTitle.text = group.name
            tvCount.text = "${group.count} ${if (group.count == 1) "book" else "books"}"
            row.setOnClickListener { onSetView(ShelfView.AuthorDetail(group.name)) }
            container.addView(row)
        }
    }

    private fun populateSeriesRows(binding: ViewHomeBinding, seriesList: List<HomeGroup>) {
        val container = binding.homeSeriesRowsContainer
        container.removeAllViews()
        seriesList.take(4).forEach { group ->
            val row =
                activity.layoutInflater.inflate(R.layout.item_home_author_series, container, false) as ViewGroup
            val tvTitle = row.findViewById<TextView>(R.id.title)
            val tvCount = row.findViewById<TextView>(R.id.count)

            tvTitle.text = group.name
            tvCount.text = "${group.count} ${if (group.count == 1) "book" else "books"}"
            row.setOnClickListener { onSetView(ShelfView.SeriesDetail(group.name)) }
            container.addView(row)
        }
    }

    fun scrollToTop(binding: ViewHomeBinding) {
        binding.homeScroll.post {
            binding.homeScroll.scrollTo(0, 0)
        }
    }
}
