package com.epubreader.app.ui

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.epubreader.app.R
import com.epubreader.app.databinding.ItemSortOptionBinding

data class ViewModeOption(
    val label: String,
    val isGrid: Boolean,
    val columns: Int = 1
)

class ViewModeOptionAdapter(
    private val options: List<ViewModeOption>,
    private val selectedIndex: Int,
    private val onSelect: (ViewModeOption) -> Unit
) : RecyclerView.Adapter<ViewModeOptionAdapter.VH>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = ItemSortOptionBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(b)
    }

    override fun getItemCount(): Int = options.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val option = options[position]
        val isSelected = position == selectedIndex
        holder.bind(option, isSelected)
    }

    inner class VH(private val b: ItemSortOptionBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(option: ViewModeOption, isSelected: Boolean) {
            val context = b.root.context
            b.title.text = option.label
            b.root.setBackgroundResource(
                if (isSelected) R.drawable.drawer_item_selected_bg else R.drawable.item_background_borderless_material
            )
            b.title.setTextColor(
                ContextCompat.getColor(context, if (isSelected) R.color.palette_mint else R.color.white)
            )
            b.check.visibility = if (isSelected) View.VISIBLE else View.GONE
            b.check.imageTintList = ColorStateList.valueOf(
                ContextCompat.getColor(context, R.color.palette_violet)
            )
            b.direction.visibility = View.GONE

            b.root.setOnClickListener {
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    onSelect(options[pos])
                }
            }
        }
    }
}
