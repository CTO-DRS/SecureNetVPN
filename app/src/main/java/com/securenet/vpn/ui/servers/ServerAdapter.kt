package com.securenet.vpn.ui.servers

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.securenet.vpn.R
import com.securenet.vpn.data.model.VpnServer
import com.securenet.vpn.databinding.ItemServerBinding

/** Glass-morphism server cards with latency, load and best-in-class badge. */
class ServerAdapter(
    private val selectedIdProvider: () -> String?,
    private val onClick: (VpnServer) -> Unit,
    /** v1.5: long-press → QR / delete menu (imported profiles & export). */
    private val onLongClick: (VpnServer) -> Unit = {}
) : ListAdapter<VpnServer, ServerAdapter.ServerViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ServerViewHolder {
        val binding = ItemServerBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ServerViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ServerViewHolder, position: Int) {
        val server = getItem(position)
        holder.bind(server, selectedIdProvider())
        holder.binding.root.setOnClickListener { onClick(server) }
        holder.binding.root.setOnLongClickListener {
            onLongClick(server)
            true
        }
    }

    class ServerViewHolder(val binding: ItemServerBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(server: VpnServer, selectedId: String?) {
            binding.tvCountryCode.text = server.countryCode.uppercase()
            binding.tvServerName.text = "${server.city}, ${server.countryCode.uppercase()}"
            binding.tvServerLoad.text = binding.root.context.getString(
                R.string.server_load, server.loadPercent
            )
            binding.tvServerLatency.text = if (server.latencyMs >= 0) {
                binding.root.context.getString(R.string.server_latency_ms, server.latencyMs.toString())
            } else {
                "--"
            }
            binding.tvBestBadge.visibility =
                if (position == 0 && server.latencyMs >= 0 && !server.id.startsWith(CUSTOM_PREFIX)) {
                    View.VISIBLE
                } else {
                    View.GONE
                }
            binding.tvCustomBadge.visibility =
                if (server.id.startsWith(CUSTOM_PREFIX)) View.VISIBLE else View.GONE
            binding.imgSelected.visibility =
                if (server.id == selectedId) View.VISIBLE else View.GONE
            binding.tvServerLatency.setTextColor(
                ContextCompat.getColor(
                    binding.root.context,
                    when {
                        server.latencyMs < 0 -> R.color.on_glass_secondary
                        server.latencyMs < 60 -> R.color.status_connected
                        server.latencyMs < 140 -> R.color.status_connecting
                        else -> R.color.status_failed
                    }
                )
            )
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<VpnServer>() {
            override fun areItemsTheSame(oldItem: VpnServer, newItem: VpnServer) =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: VpnServer, newItem: VpnServer) =
                oldItem == newItem
        }

        /** v1.5: imported profiles carry this id prefix. */
        const val CUSTOM_PREFIX = "custom_"
    }
}
