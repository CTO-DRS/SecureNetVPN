package com.securenet.vpn.ui.scanner

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.securenet.vpn.R
import com.securenet.vpn.data.model.ThreatLevel
import com.securenet.vpn.data.model.ThreatReport
import com.securenet.vpn.databinding.ItemThreatBinding

class ThreatAdapter :
    ListAdapter<ThreatReport, ThreatAdapter.ThreatViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ThreatViewHolder {
        val binding = ItemThreatBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ThreatViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ThreatViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ThreatViewHolder(val binding: ItemThreatBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(report: ThreatReport) {
            val context = binding.root.context

            binding.tvThreatTitle.text = report.title
            binding.tvThreatDescription.text = report.description
            binding.tvThreatRecommendation.text = report.recommendation

            val (levelRes, colorRes, iconRes) = when (report.level) {
                ThreatLevel.SAFE ->
                    Triple(R.string.threat_level_safe, R.color.threat_safe, R.drawable.ic_check)
                ThreatLevel.LOW ->
                    Triple(R.string.threat_level_low, R.color.threat_low, R.drawable.ic_warning)
                ThreatLevel.MEDIUM ->
                    Triple(R.string.threat_level_medium, R.color.threat_medium, R.drawable.ic_warning)
                ThreatLevel.HIGH ->
                    Triple(R.string.threat_level_high, R.color.threat_high, R.drawable.ic_warning)
                ThreatLevel.CRITICAL ->
                    Triple(R.string.threat_level_critical, R.color.threat_critical, R.drawable.ic_warning)
            }

            binding.tvThreatLevel.setText(levelRes)
            binding.tvThreatLevel.setTextColor(ContextCompat.getColor(context, colorRes))
            binding.imgThreatIcon.setImageResource(iconRes)
            binding.imgThreatIcon.setColorFilter(
                ContextCompat.getColor(context, colorRes)
            )
            binding.tvThreatLevel.visibility =
                if (report.level == ThreatLevel.SAFE) View.GONE else View.VISIBLE
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<ThreatReport>() {
            override fun areItemsTheSame(oldItem: ThreatReport, newItem: ThreatReport) =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: ThreatReport, newItem: ThreatReport) =
                oldItem == newItem
        }
    }
}
