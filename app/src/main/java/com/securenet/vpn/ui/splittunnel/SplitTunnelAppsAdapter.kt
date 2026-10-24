package com.securenet.vpn.ui.splittunnel

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.securenet.vpn.databinding.ItemAppToggleBinding

/**
 * Row model for the split-tunnel picker: launchable user apps with a toggle.
 */
data class AppRow(
    val packageName: String,
    val label: String,
    var selected: Boolean
)

class SplitTunnelAppsAdapter(
    private val onChanged: () -> Unit
) : ListAdapter<AppRow, SplitTunnelAppsAdapter.Holder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val binding = ItemAppToggleBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return Holder(binding)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.bind(currentList[position])
    }

    fun selectedPackages(): Set<String> =
        currentList.filter { it.selected }.map { it.packageName }.toSet()

    fun setSelection(packages: Set<String>) {
        currentList.forEach { it.selected = it.packageName in packages }
        notifyDataSetChanged()
    }

    inner class Holder(private val binding: ItemAppToggleBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(row: AppRow) {
            binding.tvAppLabel.text = row.label
            binding.tvAppPackage.text = row.packageName
            binding.switchApp.setOnCheckedChangeListener(null)
            binding.switchApp.isChecked = row.selected
            binding.switchApp.setOnCheckedChangeListener { _, checked ->
                row.selected = checked
                onChanged()
            }
            binding.root.setOnClickListener {
                binding.switchApp.isChecked = !binding.switchApp.isChecked
            }
        }
    }

    private companion object {
        val DIFF = object : DiffUtil.ItemCallback<AppRow>() {
            override fun areItemsTheSame(oldItem: AppRow, newItem: AppRow) =
                oldItem.packageName == newItem.packageName

            override fun areContentsTheSame(oldItem: AppRow, newItem: AppRow) =
                oldItem == newItem
        }
    }
}
