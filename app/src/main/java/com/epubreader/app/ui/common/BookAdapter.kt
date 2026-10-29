package com.epubreader.app.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.epubreader.app.data.BookEntity
import com.epubreader.app.databinding.ItemBookGridBinding
import com.epubreader.app.databinding.ItemBookListBinding

class BookAdapter(
    val grid: Boolean,
    private val onClick: (BookEntity) -> Unit,
    private val onLongClick: (BookEntity) -> Boolean,
    private val onDetails: (BookEntity) -> Unit = { onClick(it) }
) : ListAdapter<BookEntity, RecyclerView.ViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (grid) {
            BookGridHolder(ItemBookGridBinding.inflate(inflater, parent, false))
        } else {
            BookListHolder(ItemBookListBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val book = getItem(position)
        when (holder) {
            is BookGridHolder -> holder.bind(book)
            is BookListHolder -> holder.bind(book)
        }
    }

    inner class BookGridHolder(private val b: ItemBookGridBinding) : RecyclerView.ViewHolder(b.root) {
        init {
            itemView.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onClick(getItem(position))
                }
            }
            itemView.setOnLongClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onLongClick(getItem(position))
                } else {
                    false
                }
            }
        }

        fun bind(book: BookEntity) {
            b.title.text = book.title
            val authorStr = book.author.ifBlank {
                itemView.context.getString(com.epubreader.app.R.string.unknown_author)
            }
            b.author.text = authorStr

            loadCover(book, b.cover)

            // Favorites badge on top right corner of cover
            b.favoriteBadge.visibility = if (book.isFavorite) View.VISIBLE else View.GONE

            // Clamp progress to a safe 0.0 - 1.0 range.
            val progress = book.progress.coerceIn(0f, 1f)
            val pct = (progress * 100).toInt()
            val statusDesc = if (progress >= 0.90f) "finished" else if (pct > 0) "$pct percent read" else "unread"
            b.root.contentDescription = "${book.title}, $authorStr, $statusDesc"

            // Progress badge above progress bar on bottom left of cover
            if (progress >= 0.90f) {
                b.progressBadge.visibility = View.VISIBLE
                b.progressBadge.text = "✓"
            } else if (progress > 0f) {
                b.progressBadge.visibility = View.VISIBLE
                b.progressBadge.text = "$pct%"
            } else {
                b.progressBadge.visibility = View.GONE
            }

            // Progress bar
            b.progressBar.progress = pct
            if (progress >= 0.90f) {
                b.progressBar.visibility = View.GONE
            } else if (progress > 0f) {
                b.progressBar.visibility = View.VISIBLE
            } else {
                b.progressBar.visibility = View.GONE
            }
        }
    }

    inner class BookListHolder(private val b: ItemBookListBinding) : RecyclerView.ViewHolder(b.root) {
        init {
            b.root.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onClick(getItem(position))
                }
            }
            b.root.setOnLongClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onLongClick(getItem(position))
                } else {
                    false
                }
            }
            b.infoArea.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onDetails(getItem(position))
                }
            }
        }

        fun bind(book: BookEntity) {
            b.title.text = book.title
            val authorStr = book.author.ifBlank { itemView.context.getString(com.epubreader.app.R.string.unknown_author) }

            // Favorites badge next to title on top right
            b.favoriteBadge.visibility = if (book.isFavorite) View.VISIBLE else View.GONE

            if (!book.series.isNullOrBlank()) {
                val seriesStr = book.seriesIndex?.let { index ->
                    val formattedIndex = if (index % 1.0 == 0.0) {
                        index.toInt().toString()
                    } else {
                        index.toString()
                    }
                    "${book.series} #$formattedIndex"
                } ?: book.series
                b.authorAndSeries.text = "$authorStr • $seriesStr"
            } else {
                b.authorAndSeries.text = authorStr
            }
            val progress = book.progress.coerceIn(0f, 1f)
            val pct = (progress * 100).toInt()
            val statusDesc = if (progress >= 0.90f) "finished" else if (pct > 0) "$pct percent read" else "unread"
            b.root.contentDescription = "${book.title}, $authorStr, $statusDesc"

            b.progressBar.progress = pct
            if (progress >= 0.90f) {
                b.completedBadge.visibility = View.VISIBLE
                b.progressPct.visibility = View.GONE
                b.progressBar.visibility = View.GONE
            } else {
                b.completedBadge.visibility = View.GONE
                b.progressPct.visibility = View.VISIBLE
                b.progressBar.visibility = View.VISIBLE
                b.progressPct.text = "$pct%"
            }
            loadCover(book, b.cover)
        }
    }

    private fun loadCover(book: BookEntity, view: android.widget.ImageView) {
        val coverPath = book.coverPath
        if (coverPath != null) {
            Glide.with(view)
                .load(java.io.File(coverPath))
                .override(300, 450)
                .centerCrop()
                .placeholder(com.epubreader.app.R.drawable.cover_frame)
                .into(view)
        } else {
            Glide.with(view).clear(view)
            view.setImageResource(com.epubreader.app.R.drawable.cover_frame)
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<BookEntity>() {
            override fun areItemsTheSame(o: BookEntity, n: BookEntity) = o.id == n.id
            override fun areContentsTheSame(o: BookEntity, n: BookEntity) = o == n
        }
    }
}
