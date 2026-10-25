package com.securenet.vpn.ui.servers

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
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.snackbar.Snackbar
import com.securenet.vpn.MainActivity
import com.securenet.vpn.R
import com.securenet.vpn.ui.guide.GuideUiBuilder

/**
 * v1.7 — quick bottom-sheet peek at "Where to get WireGuard configs",
 * shown from the import dialog. v1.8: all rendering is delegated to the
 * shared [GuideUiBuilder] (steps cards, iconed entries, collapsible
 * sections, tips) and a link opens the full-screen [com.securenet.vpn.ui.guide.GuideFragment]
 * with search and filters.
 */
class ConfigGuideDialogFragment : BottomSheetDialogFragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.dialog_config_guide, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val sectionsHost = view.findViewById<LinearLayout>(R.id.guideSections)
        GuideUiBuilder(
            requireContext(),
            onOpenUrl = ::openUrl,
            onCopyUrl = ::copyUrl,
            onOpenFullGuide = ::openFullGuide
        ).renderSections(sectionsHost)
    }

    private fun openFullGuide() {
        dismissAllowingStateLoss()
        (activity as? MainActivity)?.openConfigGuide()
    }

    private fun openUrl(url: String) {
        val launched = runCatching {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }.isSuccess
        if (!launched) {
            view?.let {
                Snackbar.make(it, R.string.guide_no_browser, Snackbar.LENGTH_SHORT).show()
            }
        }
    }

    private fun copyUrl(url: String) {
        runCatching {
            val clipboard =
                requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("WireGuard config source", url))
        }
        view?.let { Snackbar.make(it, R.string.guide_link_copied, Snackbar.LENGTH_SHORT).show() }
    }
}
