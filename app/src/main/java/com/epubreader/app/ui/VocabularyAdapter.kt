package com.epubreader.app.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.epubreader.app.R
import com.epubreader.app.data.DictionaryHistoryEntity

/**
 * Enhanced Vocabulary Sanctuary Adapter using item_vocabulary_word.xml for pure XML UI styling.
 */
class VocabularyAdapter(
    private val onMasteryCycle: (DictionaryHistoryEntity) -> Unit,
    private val onPronounce: (String) -> Unit,
    private val onDelete: (DictionaryHistoryEntity) -> Unit,
) : ListAdapter<DictionaryHistoryEntity, VocabularyAdapter.VH>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_vocabulary_word, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(getItem(position), onMasteryCycle, onPronounce, onDelete)
    }

    class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val wordTv: TextView = itemView.findViewById(R.id.vocabWordText)
        private val posBadge: TextView = itemView.findViewById(R.id.vocabPosBadge)
        private val audioBtn: ImageButton = itemView.findViewById(R.id.vocabAudioBtn)
        private val masteryPill: TextView = itemView.findViewById(R.id.vocabMasteryPill)
        private val deleteBtn: ImageButton = itemView.findViewById(R.id.vocabDeleteBtn)
        private val defTv: TextView = itemView.findViewById(R.id.vocabDefinitionText)
        private val quoteTv: TextView = itemView.findViewById(R.id.vocabQuoteText)
        private val sourceTv: TextView = itemView.findViewById(R.id.vocabSourceText)

        fun bind(
            item: DictionaryHistoryEntity,
            onMasteryCycle: (DictionaryHistoryEntity) -> Unit,
            onPronounce: (String) -> Unit,
            onDelete: (DictionaryHistoryEntity) -> Unit,
        ) {
            val ctx = itemView.context
            wordTv.text = item.word.uppercase()

            if (!item.partOfSpeech.isNullOrBlank()) {
                posBadge.visibility = View.VISIBLE
                posBadge.text = item.partOfSpeech.lowercase()
            } else {
                posBadge.visibility = View.GONE
            }

            audioBtn.setOnClickListener { onPronounce(item.word) }

            // Mastery Level Pill (0: New, 1: Learning, 2: Mastered)
            when (item.masteryLevel) {
                1 -> {
                    masteryPill.text = ctx.getString(R.string.vocab_chip_learning)
                    masteryPill.backgroundTintList = android.content.res.ColorStateList.valueOf(ContextCompat.getColor(ctx, R.color.dark_surface))
                    masteryPill.setTextColor(ContextCompat.getColor(ctx, R.color.white))
                }
                2 -> {
                    masteryPill.text = ctx.getString(R.string.vocab_mastered_badge)
                    masteryPill.backgroundTintList = android.content.res.ColorStateList.valueOf(ContextCompat.getColor(ctx, R.color.accent_pop))
                    masteryPill.setTextColor(ContextCompat.getColor(ctx, R.color.white))
                }
                else -> {
                    masteryPill.text = ctx.getString(R.string.vocab_chip_new)
                    masteryPill.backgroundTintList = android.content.res.ColorStateList.valueOf(ContextCompat.getColor(ctx, R.color.dark_surface))
                    masteryPill.setTextColor(ContextCompat.getColor(ctx, R.color.white))
                }
            }
            masteryPill.setOnClickListener { onMasteryCycle(item) }

            deleteBtn.setOnClickListener { onDelete(item) }

            defTv.text = item.definition ?: ctx.getString(R.string.dictionary_not_found)

            if (!item.example.isNullOrBlank()) {
                quoteTv.visibility = View.VISIBLE
                quoteTv.text = ctx.getString(R.string.vocab_quote_format, item.example)
            } else {
                quoteTv.visibility = View.GONE
            }

            if (!item.bookTitle.isNullOrBlank()) {
                sourceTv.visibility = View.VISIBLE
                sourceTv.text = ctx.getString(R.string.vocab_discovered_in, item.bookTitle)
            } else {
                sourceTv.visibility = View.GONE
            }
        }
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<DictionaryHistoryEntity>() {
            override fun areItemsTheSame(o: DictionaryHistoryEntity, n: DictionaryHistoryEntity) = o.id == n.id
            override fun areContentsTheSame(o: DictionaryHistoryEntity, n: DictionaryHistoryEntity) = o == n
        }
    }
}
