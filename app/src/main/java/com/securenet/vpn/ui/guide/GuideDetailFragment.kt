package com.securenet.vpn.ui.guide

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.securenet.vpn.MainActivity
import com.securenet.vpn.R
import com.securenet.vpn.databinding.FragmentGuideDetailBinding
import com.securenet.vpn.guide.GuideCategory
import com.securenet.vpn.guide.GuideDetail
import com.securenet.vpn.guide.GuideDetailQr
import com.securenet.vpn.guide.GuideEntry
import com.securenet.vpn.guide.GuideProgress
import com.securenet.vpn.guide.GuideProgressProvider
import com.securenet.vpn.guide.GuideSignalsData
import com.securenet.vpn.guide.GuideSmart
import com.securenet.vpn.guide.GuideStore
import com.securenet.vpn.guide.GuideStoreProvider
import com.securenet.vpn.guide.WireGuideCatalog

/**
 * v1.8.7 — full-screen detail page for one WireGuard config source, opened
 * by tapping any card in the guide (list, statistics ranking) or a related
 * row here. One screen answers everything about a source at a glance:
 *  - identity: category icon + name + category label + Editor's choice badge
 *    + the favorite heart;
 *  - the link: selectable URL with open / copy / share actions, plus the
 *    v1.8.8 scannable QR of the official URL (tap it to open here) and the
 *    bare host rendered under the code — a glanceable trust signal;
 *  - the full description and the editorial small-print note;
 *  - the category's step-by-step tutorial (same content the section header
 *    carries, so the detail page stands alone);
 *  - "your signals": the big interactive star row, the personal note with
 *    add/edit/remove — the exact same [GuideUiBuilder] widgets the cards
 *    use, so behaviour (tap-to-clear stars, RTL-safe LTR stars) matches;
 *  - the v1.8.13 "Compare side-by-side" button: opens the comparison
 *    bottom sheet ([GuideCompareDialogFragment]) grounded in the pure
 *    [GuideCompare] engine — pick any second source, see section, price,
 *    host, Editor's-choice badge and your own signals for both in one
 *    table, with the shared catalog keywords naming why they feel similar;
 *  - "more in this category": up to five sibling sources ranked by the
 *    user's own signals ([GuideDetail.related]); tapping one swaps this
 *    screen's content in place — a natural way to hop through a category.
 *
 * Hosting contract mirrors [GuideFragment]: pushed by
 * [MainActivity.openGuideDetail] (bottom nav hidden) and restores the nav
 * in onDestroyView for any pop path. An unknown id (catalog version drift
 * after a restore) degrades to a toast + pop instead of a blank screen.
 */
class GuideDetailFragment : Fragment() {

    private var _binding: FragmentGuideDetailBinding? = null
    private val binding get() = _binding!!
    private lateinit var store: GuideStore
    private var builder: GuideUiBuilder? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentGuideDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        store = GuideStoreProvider.get(requireContext())

        val id = requireArguments().getString(ARG_ID)
        val entry = id?.let { WireGuideCatalog.byId(it) }
        if (entry == null) {
            // Catalog version drift (e.g. a restored backup references a
            // newer/renamed source): explain and leave gracefully.
            Toast.makeText(
                requireContext(), R.string.guide_detail_unknown, Toast.LENGTH_SHORT
            ).show()
            parentFragmentManager.popBackStack()
            return
        }

        builder = GuideUiBuilder(
            requireContext(), store, ::openUrl, ::copyUrl
        )
        binding.btnBack.setOnClickListener { parentFragmentManager.popBackStack() }
        binding.btnOpen.setOnClickListener { openUrl(entry.url) }
        binding.btnCopy.setOnClickListener { copyUrl(entry.url) }
        binding.btnShare.setOnClickListener { share(entry) }
        binding.btnNote.setOnClickListener { showNoteDialog(entry) }
        binding.tvUserNote.setOnClickListener { showNoteDialog(entry) }
        // v1.8.13 — side-by-side comparison against a second source.
        binding.btnCompare.setOnClickListener {
            GuideCompareDialogFragment.newInstance(entry.id)
                .show(childFragmentManager, "guide_compare")
        }

        bind(entry)
    }

    /** Renders (or re-renders, on related-hop) the whole screen for [entry]. */
    private fun bind(entry: GuideEntry) {
        val facts = GuideDetail.facts(entry, store)
        val signals = GuideSignalsData.snapshot(store)

        binding.scrollDetail.smoothScrollTo(0, 0)

        binding.ivDetailIcon.setImageResource(iconFor(entry.category))
        binding.tvDetailName.text = entry.name
        // v1.8.14 — section + setup difficulty in one identity line.
        binding.tvDetailCategory.text =
            getString(categoryLabel(entry.category)) + "  ·  " +
            getString(WireGuideCatalog.difficultyLabelRes(entry.difficulty))
        binding.tvDetailBadge.visibility =
            if (facts.editorsChoice) View.VISIBLE else View.GONE
        binding.tvDetailUrl.text = entry.url
        renderQr(entry)
        renderVerdict(entry, signals)
        binding.tvDetailDesc.text = getString(entry.descriptionRes)
        if (entry.noteRes != null) {
            binding.tvDetailNote.text = getString(entry.noteRes)
            binding.tvDetailNote.visibility = View.VISIBLE
        } else {
            binding.tvDetailNote.visibility = View.GONE
        }

        // Signals: the shared widgets repaint in place on tap.
        binding.favHost.removeAllViews()
        binding.favHost.addView(builder!!.favoriteToggle(entry))
        binding.llStars.removeAllViews()
        binding.llStars.addView(builder!!.starRow(entry))
        refreshUserNote(facts)

        // v1.8.15 — interactive setup wizard (معالج الإعداد): the section
        // tutorial becomes a per-source checklist. Tapping a step toggles
        // it, progress persists per source via [GuideProgress], and the
        // caption + bar answer "how far along am I?" at a glance.
        binding.llSteps.removeAllViews()
        val steps = context?.resources?.getStringArray(
            WireGuideCatalog.stepsRes(entry.category)
        )
        if (!steps.isNullOrEmpty()) {
            val progress = GuideProgressProvider.get(requireContext())
            val total = steps.size
            fun repaintWizard() {
                binding.llSteps.removeAllViews()
                val checked = GuideProgress.normalize(
                    progress.checked(entry.id), total
                )

                // caption swaps to a completion line when everything is ticked
                binding.llSteps.addView(TextView(requireContext()).apply {
                    text = if (checked.size == total)
                        getString(R.string.guide_progress_done)
                    else
                        getString(R.string.guide_progress_caption, checked.size, total)
                    setTextColor(
                        resolveColor(
                            if (checked.size == total)
                                com.google.android.material.R.attr.colorPrimary
                            else android.R.attr.textColorSecondary
                        )
                    )
                    textSize = 11.5f
                    setPadding(0, dp(2), 0, dp(4))
                })
                binding.llSteps.addView(
                    com.google.android.material.progressindicator.LinearProgressIndicator(
                        requireContext()
                    ).apply {
                        max = total
                        setProgress(checked.size)
                        layoutParams = LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, dp(4)
                        ).apply { bottomMargin = dp(6) }
                    }
                )

                // one tappable checklist row per step
                steps.forEachIndexed { index, step ->
                    val done = index in checked
                    binding.llSteps.addView(LinearLayout(requireContext()).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        setPadding(0, dp(4), 0, dp(4))
                        isClickable = true
                        isFocusable = true
                        runCatching {
                            val tv = android.util.TypedValue()
                            requireContext().theme.resolveAttribute(
                                android.R.attr.selectableItemBackground, tv, true
                            )
                            setBackgroundResource(tv.resourceId)
                        }
                        setOnClickListener {
                            progress.toggle(entry.id, index, total)
                            repaintWizard()
                        }
                        addView(
                            com.google.android.material.checkbox.MaterialCheckBox(
                                requireContext()
                            ).apply {
                                isChecked = done
                                isClickable = false
                                isFocusable = false
                            }
                        )
                        addView(TextView(requireContext()).apply {
                            text = (index + 1).toString() + " · " + step
                            setTextColor(
                                resolveColor(android.R.attr.textColorSecondary)
                            )
                            textSize = 12f
                            if (done) {
                                paintFlags = paintFlags or
                                    android.graphics.Paint.STRIKE_THRU_TEXT_FLAG
                                alpha = 0.65f
                            }
                        })
                    })
                }

                // reset link only when there is something to reset
                if (checked.isNotEmpty()) {
                    binding.llSteps.addView(TextView(requireContext()).apply {
                        text = getString(R.string.guide_progress_reset)
                        setTextColor(
                            resolveColor(
                                com.google.android.material.R.attr.colorPrimary
                            )
                        )
                        textSize = 12f
                        isClickable = true
                        isFocusable = true
                        setPadding(0, dp(8), 0, 0)
                        setOnClickListener {
                            progress.clear(entry.id)
                            repaintWizard()
                        }
                    })
                }
            }
            repaintWizard()
        }

        renderRelated(entry, signals)
    }

    /**
     * v1.8.10 — the smart verdict line under the identity card: what the
     * user's own signals say about THIS source ("fits your most-engaged
     * category: Paid · 8 signals" / "Editor's pick you haven't rated yet").
     * Hidden entirely when nothing data-grounded applies — the page never
     * invents praise, and a cold start stays neutral.
     */
    private fun renderVerdict(entry: GuideEntry, signals: GuideSignalsData) {
        val verdict = GuideSmart.verdict(entry, signals)
        if (verdict == null) {
            binding.tvSmartVerdict.visibility = View.GONE
            return
        }
        val line = when (verdict.kind) {
            GuideSmart.VerdictKind.TOP_CATEGORY -> getString(
                R.string.guide_detail_verdict_top,
                getString(categoryLabel(verdict.category ?: entry.category)),
                verdict.count
            )
            GuideSmart.VerdictKind.EDITORS_CHOICE ->
                getString(R.string.guide_detail_verdict_editors)
        }
        binding.tvSmartVerdict.text = "✦ $line"
        binding.tvSmartVerdict.visibility = View.VISIBLE
    }

    /** v1.8.8 — QR of the official URL + the bare host under it. */
    private fun renderQr(entry: GuideEntry) {
        val host = GuideDetail.hostOf(entry)
        if (host != null) {
            binding.tvQrHost.text =
                getString(R.string.guide_detail_host_prefix) + " " + host
            binding.tvQrHost.visibility = View.VISIBLE
        } else {
            binding.tvQrHost.visibility = View.GONE
        }
        val qr = GuideDetailQr.bitmap(entry.url)
        if (qr != null) {
            binding.ivQr.setImageBitmap(qr)
            binding.ivQr.setOnClickListener { openUrl(entry.url) }
            binding.ivQr.visibility = View.VISIBLE
            binding.tvQrTitle.visibility = View.VISIBLE
        } else {
            binding.ivQr.visibility = View.GONE
            binding.tvQrTitle.visibility = View.GONE
        }
    }

    private fun refreshUserNote(facts: GuideDetail.Facts) {
        val note = facts.note
        if (note == null) {
            binding.tvUserNote.visibility = View.GONE
            binding.btnNote.setText(R.string.guide_note_add)
        } else {
            binding.tvUserNote.text =
                getString(R.string.guide_user_note_prefix) + " " + note
            binding.tvUserNote.visibility = View.VISIBLE
            binding.btnNote.setText(R.string.guide_note_edit)
        }
    }

    /** "More in this category" — signals-ranked siblings, tap to hop. */
    private fun renderRelated(entry: GuideEntry, signals: GuideSignalsData) {
        val related = GuideDetail.related(entry, store)
        binding.llRelated.removeAllViews()
        if (related.isEmpty()) {
            binding.tvRelatedEmpty.visibility = View.VISIBLE
            return
        }
        binding.tvRelatedEmpty.visibility = View.GONE
        related.forEach { sibling ->
            binding.llRelated.addView(relatedRow(sibling, entry, signals))
        }
    }

    /**
     * v1.8.10 — a sibling row optionally carries a smart reason line under
     * the name ("similar to what you're viewing" / "Editor's pick you
     * haven't rated yet"), so the list explains itself instead of just
     * ranking. No grounded reason → the plain row from v1.8.7.
     */
    private fun relatedRow(
        sibling: GuideEntry,
        current: GuideEntry,
        signals: GuideSignalsData
    ): View {
        val stars = store.rating(sibling.id)
        val fav = store.isFavorite(sibling.id)
        val row = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            isClickable = true
            isFocusable = true
            setPadding(dp(4), dp(6), dp(4), dp(6))
        }
        row.addView(android.widget.ImageView(requireContext()).apply {
            setImageResource(iconFor(sibling.category))
            layoutParams = LinearLayout.LayoutParams(dp(26), dp(26)).apply {
                marginEnd = dp(8)
            }
        })
        row.addView(TextView(requireContext()).apply {
            text = if (fav) "♥ " + sibling.name else sibling.name
            setTextColor(resolveColor(com.google.android.material.R.attr.colorOnSurface))
            textSize = 13f
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            layoutParams = LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
            )
        })
        row.addView(TextView(requireContext()).apply {
            text = "\u2605".repeat(stars).ifEmpty { "\u2606" }
            setTextColor(resolveColor(com.google.android.material.R.attr.colorPrimary))
            textSize = 12f
            layoutDirection = View.LAYOUT_DIRECTION_LTR
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { marginStart = dp(6) }
        })
        row.setOnClickListener {
            WireGuideCatalog.byId(sibling.id)?.let(::bind)
        }

        val reason = GuideSmart.siblingReason(sibling, current, signals)
        if (reason == null) {
            row.layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(4) }
            return row
        }
        return LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(4) }
            row.layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            addView(row)
            addView(TextView(requireContext()).apply {
                text = "✦ " + getString(reasonRes(reason))
                setTextColor(resolveColor(com.google.android.material.R.attr.colorPrimary))
                textSize = 11f
                setPadding(dp(36), 0, dp(4), 0)
            })
        }
    }

    /** v1.8.10 — chip text for a smart reason; mirrors the picks card. */
    private fun reasonRes(reason: GuideSmart.Reason): Int = when (reason) {
        GuideSmart.Reason.TOP_CATEGORY -> R.string.guide_smart_reason_top
        GuideSmart.Reason.EDITORS_CHOICE -> R.string.guide_smart_reason_editors
        GuideSmart.Reason.COLD_START -> R.string.guide_smart_reason_cold
        GuideSmart.Reason.SIMILAR -> R.string.guide_smart_reason_similar
    }

    private fun showNoteDialog(entry: GuideEntry) {
        val input = EditText(requireContext()).apply {
            setText(store.note(entry.id))
            hint = getString(R.string.guide_note_hint)
            setSingleLine(false)
            minLines = 2
            maxLines = 4
        }
        val container = FrameLayout(requireContext()).apply {
            setPadding(dp(20), dp(8), dp(20), 0)
            addView(input, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ))
        }
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.guide_note_dialog_title)
            .setView(container)
            .setPositiveButton(R.string.guide_note_save) { _, _ ->
                store.setNote(entry.id, input.text?.toString())
                refreshUserNote(GuideDetail.facts(entry, store))
            }
            .setNegativeButton(android.R.string.cancel, null)
        if (store.note(entry.id) != null) {
            dialog.setNeutralButton(R.string.guide_note_remove) { _, _ ->
                store.setNote(entry.id, null)
                refreshUserNote(GuideDetail.facts(entry, store))
            }
        }
        dialog.show()
    }

    private fun share(entry: GuideEntry) {
        val launched = runCatching {
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, GuideDetail.shareText(entry))
            }
            startActivity(Intent.createChooser(send, getString(R.string.guide_detail_share_via)))
        }.isSuccess
        if (!launched) {
            view?.let {
                Snackbar.make(it, R.string.guide_no_browser, Snackbar.LENGTH_SHORT).show()
            }
        }
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
                requireContext().getSystemService(Context.CLIPBOARD_SERVICE)
                    as ClipboardManager
            clipboard.setPrimaryClip(
                ClipData.newPlainText("WireGuard config source", url)
            )
        }
        view?.let {
            Snackbar.make(it, R.string.guide_link_copied, Snackbar.LENGTH_SHORT).show()
        }
    }

    private fun iconFor(category: GuideCategory): Int = when (category) {
        GuideCategory.FREE -> R.drawable.ic_globe
        GuideCategory.PAID -> R.drawable.ic_auto_awesome
        GuideCategory.SELF_HOSTED -> R.drawable.ic_dns
        GuideCategory.ROUTERS -> R.drawable.ic_wifi
        GuideCategory.APPS -> R.drawable.ic_qr_code
        GuideCategory.MESH -> R.drawable.ic_mesh
    }

    private fun categoryLabel(category: GuideCategory): Int = when (category) {
        GuideCategory.FREE -> R.string.guide_section_free
        GuideCategory.PAID -> R.string.guide_section_paid
        GuideCategory.SELF_HOSTED -> R.string.guide_section_selfhosted
        GuideCategory.ROUTERS -> R.string.guide_section_routers
        GuideCategory.APPS -> R.string.guide_section_apps
        GuideCategory.MESH -> R.string.guide_section_mesh
    }

    private fun resolveColor(attr: Int): Int {
        val value = android.util.TypedValue()
        requireContext().theme.resolveAttribute(attr, value, true)
        return value.data
    }

    private fun dp(value: Int): Int =
        (value * requireContext().resources.displayMetrics.density).toInt()

    override fun onDestroyView() {
        (activity as? MainActivity)?.setBottomNavVisible(true)
        _binding = null
        builder = null
        super.onDestroyView()
    }

    companion object {
        private const val ARG_ID = "source_id"

        fun newInstance(sourceId: String): GuideDetailFragment =
            GuideDetailFragment().apply { arguments = bundleOf(ARG_ID to sourceId) }
    }
}
