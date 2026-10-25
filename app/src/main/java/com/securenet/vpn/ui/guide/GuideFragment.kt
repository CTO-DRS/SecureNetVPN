package com.securenet.vpn.ui.guide

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import com.google.android.material.snackbar.Snackbar
import com.securenet.vpn.MainActivity
import com.securenet.vpn.R
import com.securenet.vpn.databinding.FragmentGuideBinding
import com.securenet.vpn.guide.GuideCategory
import com.securenet.vpn.guide.WireGuideCatalog
import com.securenet.vpn.guide.WireGuideFilter

/**
 * v1.8 — full-screen "Where to get WireGuard configs" guide, reachable from
 * Settings (and from the quick bottom sheet shown by the import dialog).
 * Adds on top of the shared [GuideUiBuilder] rendering:
 *  - an illustrated 4-step flow strip (choose → generate → import → connect);
 *  - live search across names, URLs and EN/AR keywords ([WireGuideFilter]);
 *  - category filter chips (All / Free / Paid / Self-hosted / Routers / Apps).
 *
 * Hosting contract: [MainActivity.openConfigGuide] hides the bottom nav and
 * pushes this fragment on the back stack; this fragment restores the nav in
 * onDestroyView so any pop path (button or system back) re-shows it.
 */
class GuideFragment : Fragment() {

    private var _binding: FragmentGuideBinding? = null
    private val binding get() = _binding!!

    private var selectedCategory: GuideCategory? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentGuideBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnBack.setOnClickListener { parentFragmentManager.popBackStack() }

        val builder = GuideUiBuilder(requireContext(), ::openUrl, ::copyUrl)
        binding.flowHost.addView(buildFlowStrip(builder))

        render(builder)

        binding.etGuideSearch.doAfterTextChanged {
            render(builder)
        }
        binding.chipGroup.setOnCheckedChangeListener { _, checkedId ->
            selectedCategory = when (checkedId) {
                R.id.chipFree -> GuideCategory.FREE
                R.id.chipPaid -> GuideCategory.PAID
                R.id.chipSelfHosted -> GuideCategory.SELF_HOSTED
                R.id.chipRouters -> GuideCategory.ROUTERS
                R.id.chipApps -> GuideCategory.APPS
                else -> null
            }
            render(builder)
        }
    }

    /** choose a source → generate the .conf → import into SecureNet → connect */
    private fun buildFlowStrip(builder: GuideUiBuilder): LinearLayout {
        val strip = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
        }
        val steps = listOf(
            R.drawable.ic_globe to R.string.guide_flow_1,
            R.drawable.ic_lock to R.string.guide_flow_2,
            R.drawable.ic_scan to R.string.guide_flow_3,
            R.drawable.ic_shield to R.string.guide_flow_4
        )
        steps.forEachIndexed { index, (iconRes, labelRes) ->
            if (index > 0) strip.addView(builder.flowArrow())
            strip.addView(builder.flowStep(iconRes, labelRes))
        }
        return strip
    }

    private fun render(builder: GuideUiBuilder) {
        val query = binding.etGuideSearch.text?.toString().orEmpty()
        val entries = WireGuideFilter.filter(
            WireGuideCatalog.entries, query, selectedCategory
        )
        val filtering = query.isNotBlank() || selectedCategory != null
        if (filtering) {
            binding.tvGuideCount.text =
                getString(R.string.guide_results_count, entries.size)
            binding.tvGuideCount.visibility = View.VISIBLE
            binding.tvGuideEmpty.visibility =
                if (entries.isEmpty()) View.VISIBLE else View.GONE
            builder.renderFiltered(binding.guideSections, entries)
        } else {
            binding.tvGuideCount.visibility = View.GONE
            binding.tvGuideEmpty.visibility = View.GONE
            builder.renderSections(binding.guideSections)
        }
    }

    private fun openUrl(url: String) {
        val launched = runCatching {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }.isSuccess
        if (!launched) {
            Snackbar.make(binding.root, R.string.guide_no_browser, Snackbar.LENGTH_SHORT)
                .show()
        }
    }

    private fun copyUrl(url: String) {
        runCatching {
            val clipboard =
                requireContext().getSystemService(Context.CLIPBOARD_SERVICE)
                    as ClipboardManager
            clipboard.setPrimaryClip(
                ClipData.newPlainText("WireGuard config source", url)
            )
        }
        Snackbar.make(binding.root, R.string.guide_link_copied, Snackbar.LENGTH_SHORT)
            .show()
    }

    override fun onDestroyView() {
        (activity as? MainActivity)?.setBottomNavVisible(true)
        _binding = null
        super.onDestroyView()
    }
}
