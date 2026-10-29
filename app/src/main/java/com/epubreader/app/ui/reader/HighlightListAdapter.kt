package com.epubreader.app.ui

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.epubreader.app.R
import com.epubreader.app.data.HighlightEntity
import com.google.android.material.button.MaterialButton

class HighlightListAdapter(
    private val onClick: (HighlightEntity) -> Unit,
    private val onEditNote: (HighlightEntity, String?) -> Unit,
    private val getChapterInfo: (HighlightEntity) -> String,
    private val onDelete: ((HighlightEntity) -> Unit)? = null,
) : ListAdapter<HighlightEntity, HighlightListAdapter.VH>(DIFF) {

    var activeEditingHighlightId: Long? = null
        set(value) {
            if (field != value) {
                field = value
                notifyDataSetChanged()
            }
        }

    fun clearEditingState(context: Context) {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(null, 0)
        activeEditingHighlightId = null
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.view_highlight_card, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(getItem(position), onClick, onEditNote, getChapterInfo, onDelete, activeEditingHighlightId == getItem(position).id) { newEditingId ->
            activeEditingHighlightId = newEditingId
        }
    }

    class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val colorDot: View = itemView.findViewById(R.id.colorDot)
        private val tvChapterInfo: TextView = itemView.findViewById(R.id.tvChapterInfo)
        private val text: TextView = itemView.findViewById(R.id.highlightText)
        private val textContainer: View = itemView.findViewById(R.id.textContainer)
        private val leftAccentBar: View = itemView.findViewById(R.id.leftAccentBar)
        private val btnEdit: ImageButton = itemView.findViewById(R.id.btnEdit)
        private val delete: ImageButton = itemView.findViewById(R.id.btnDelete)
        private val layoutAttachedNote: LinearLayout = itemView.findViewById(R.id.layoutAttachedNote)
        private val tvAttachedNoteText: TextView = itemView.findViewById(R.id.tvAttachedNoteText)
        private val btnAddPersonalNote: TextView = itemView.findViewById(R.id.btnAddPersonalNote)
        private val layoutNoteInput: LinearLayout = itemView.findViewById(R.id.layoutNoteInput)
        private val etNoteInput: EditText = itemView.findViewById(R.id.etNoteInput)
        private val btnCancelNote: MaterialButton = itemView.findViewById(R.id.btnCancelNote)
        private val btnSaveNote: MaterialButton = itemView.findViewById(R.id.btnSaveNote)

        fun bind(
            item: HighlightEntity,
            onClick: (HighlightEntity) -> Unit,
            onEditNote: (HighlightEntity, String?) -> Unit,
            getChapterInfo: (HighlightEntity) -> String,
            onDelete: ((HighlightEntity) -> Unit)?,
            isEditing: Boolean,
            setEditingId: (Long?) -> Unit,
        ) {
            val ctx = itemView.context
            val imm = ctx.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager

            val r = android.graphics.Color.red(item.color)
            val g = android.graphics.Color.green(item.color)
            val b = android.graphics.Color.blue(item.color)
            val baseColor = android.graphics.Color.rgb(r, g, b)

            val dotShape = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor(baseColor)
            }
            colorDot.background = dotShape
            leftAccentBar.setBackgroundColor(baseColor)

            val lightTint = (item.color and 0x00FFFFFF) or 0x33000000
            textContainer.background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = 8f * ctx.resources.displayMetrics.density
                setColor(lightTint)
            }

            tvChapterInfo.text = getChapterInfo(item)

            val formattedText = "\"${item.text}\""
            text.text = android.text.Html.fromHtml("<i>$formattedText</i>", android.text.Html.FROM_HTML_MODE_LEGACY)

            fun closeKeyboard() {
                imm?.hideSoftInputFromWindow(itemView.windowToken, 0)
                etNoteInput.clearFocus()
            }

            if (isEditing) {
                layoutNoteInput.visibility = View.VISIBLE
                btnAddPersonalNote.visibility = View.GONE
                layoutAttachedNote.visibility = View.GONE
                etNoteInput.setText(item.note ?: "")
                etNoteInput.requestFocus()
                etNoteInput.post {
                    imm?.showSoftInput(etNoteInput, InputMethodManager.SHOW_IMPLICIT)
                }
            } else {
                layoutNoteInput.visibility = View.GONE
                if (!item.note.isNullOrBlank()) {
                    layoutAttachedNote.visibility = View.VISIBLE
                    tvAttachedNoteText.text = item.note
                    btnAddPersonalNote.visibility = View.GONE
                } else {
                    layoutAttachedNote.visibility = View.GONE
                    btnAddPersonalNote.visibility = View.VISIBLE
                    btnAddPersonalNote.text = ctx.getString(R.string.highlight_add_personal_note)
                }
            }

            btnAddPersonalNote.setOnClickListener {
                setEditingId(item.id)
            }
            btnEdit.setOnClickListener {
                setEditingId(item.id)
            }
            btnCancelNote.setOnClickListener {
                closeKeyboard()
                setEditingId(null)
            }
            btnSaveNote.setOnClickListener {
                closeKeyboard()
                val noteStr = etNoteInput.text.toString().trim().ifEmpty { null }
                setEditingId(null)
                onEditNote(item, noteStr)
            }

            delete.visibility = if (onDelete != null) View.VISIBLE else View.GONE
            itemView.setOnClickListener {
                closeKeyboard()
                if (isEditing) setEditingId(null)
                onClick(item)
            }
            delete.setOnClickListener {
                closeKeyboard()
                setEditingId(null)
                onDelete?.invoke(item)
            }
        }
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<HighlightEntity>() {
            override fun areItemsTheSame(o: HighlightEntity, n: HighlightEntity) = o.id == n.id
            override fun areContentsTheSame(o: HighlightEntity, n: HighlightEntity) = o == n
        }
    }
}
