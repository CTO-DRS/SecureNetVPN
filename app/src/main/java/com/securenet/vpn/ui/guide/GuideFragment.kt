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
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.snackbar.Snackbar
import com.securenet.vpn.MainActivity
import com.securenet.vpn.R
import com.securenet.vpn.databinding.FragmentGuideBinding
import com.securenet.vpn.guide.GuideCategory
import com.securenet.vpn.guide.GuideDifficulty
import com.securenet.vpn.guide.GuideSearchHistoryProvider
import com.securenet.vpn.guide.GuideSeenCatalog
import com.securenet.vpn.guide.GuideSeenCatalogProvider
import com.securenet.vpn.guide.GuideSmart
import com.securenet.vpn.guide.GuideStore
import com.securenet.vpn.guide.GuideStoreProvider
import com.securenet.vpn.guide.SharedPrefsGuideSearchHistory
import com.securenet.vpn.guide.WireGuideCatalog
import com.securenet.vpn.guide.WireGuideFilter
import com.securenet.vpn.guide.WhatsNew

/**
 * v1.8 — full-screen "Where to get WireGuard configs" guide, reachable from
 * Settings (and from the quick bottom sheet shown by the import dialog).
 * Adds on top of the shared [GuideUiBuilder] rendering:
 *  - an illustrated 4-step flow strip (choose → generate → import → connect);
 *  - live search across names, URLs, EN/AR keywords ([WireGuideFilter]) and
 *    the user's personal notes (v1.8.1), with the v1.8.9 typo-tolerant
 *    fuzzy fallback ([GuideSmart.fuzzySearch]) when nothing matches
 *    exactly;
 *  - category filter chips (All / Favorites / Free / Paid / Self-hosted /
 *    Routers / Apps).
 *
 * v1.8.1 — star ratings, favorites and personal notes persist through
 * [GuideStoreProvider]; favorites float to the top of every list and the
 * Favorites chip shows bookmarked sources only.
 *
 * v1.8.2 — a "Top rated" chip renders the whole catalog as one flat list
 * ordered by the user's signals (favorites → stars) with the "Editor's
 * choice" reliability badge holders breaking ties — the default sort the
 * sections view already applies, available at one tap.
 *
 * v1.8.13 — a Mesh chip filters the new [GuideCategory.MESH] section, and
 * a second, independent chip row above the list is the quick price
 * filter: All / Free / Paid. Free and Paid select every source marked
 * with that price anywhere in the catalog ([WireGuideFilter.filterByPrice]
 * over the category plus the curated free/مجاني, paid/مدفوع keyword
 * markers) — so "مجاني" reaches the free self-hosted, router and mesh
 * tools without typing anything.
 *
 * v1.8.16 — two additions: ① the fresh-sources alert (تنبيه عند إضافة
 * مصادر جديدة): the screen remembers which sources the user was already
 * shown ([GuideSeenCatalog]) and greets catalog updates with a
 * dismissible banner plus 🆕 NEW badges on the affected cards; ② a
 * one-tap entry to the offline WireGuard config checker
 * ([WgCheckerDialogFragment]).
 *
 * v1.8.17 — the filter bar gains a third independent row of setup-
 * difficulty chips ([WireGuideFilter.filterByDifficulty]), and the tools
 * row pairs the checker with the 📊 What's-new sheet
 * ([WhatsNewDialogFragment]) — which also auto-opens once per version so
 * every update introduces itself.
 *
 * Hosting contract: [MainActivity.openConfigGuide] hides the bottom nav and
 * pushes this fragment on the back stack; this fragment restores the nav in
 * onDestroyView so any pop path (button or system back) re-shows it.
 */
class GuideFragment : Fragment() {

    private var _binding: FragmentGuideBinding? = null
    private val binding get() = _binding!!

    private var selectedCategory: GuideCategory? = null

    /** v1.8.13 — quick price filter above the list (null = All). */
    private var selectedPrice: WireGuideFilter.Price? = null

    /** v1.8.17 — quick setup-difficulty filter (null = All). */
    private var selectedDifficulty: GuideDifficulty? = null

    /** v1.8.16 — ids the fresh-sources alert currently highlights. */
    private var newAlertIds: Set<String> = emptySet()

    private lateinit var store: GuideStore
    private var _builder: GuideUiBuilder? = null

    // v1.8.20 — recent-search history (سجل البحث الحديث): confirmed
    // searches (keyboard action or a history chip) are remembered
    // most-recent-first and re-runnable at one tap.
    private lateinit var searchHistory: SharedPrefsGuideSearchHistory
    private var _historyScroll: HorizontalScrollView? = null
    private var _historyRow: LinearLayout? = null

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

        store = GuideStoreProvider.get(requireContext())
        searchHistory = GuideSearchHistoryProvider.get(requireContext())

        // v1.8.16 — fresh-sources alert: the very first visit records the
        // snapshot silently (baseline); later visits announce what arrived
        // since the last acknowledgement.
        val seenCatalog = GuideSeenCatalogProvider.get(requireContext())
        if (seenCatalog.seenIds().isEmpty()) {
            seenCatalog.markAllSeen(WireGuideCatalog.entries.map { it.id })
        } else {
            newAlertIds = GuideSeenCatalog.unseen(
                WireGuideCatalog.entries.map { it.id }, seenCatalog.seenIds()
            ).toSet()
        }

        val builder = GuideUiBuilder(
            requireContext(), store, ::openUrl, ::copyUrl,
            onSignalsChanged = { onFavoritesMembershipChanged() },
            onSignalsCleared = { _builder?.let { onSignalsCleared(it) } },
            onOpenDetail = { entry ->
                (activity as? MainActivity)?.openGuideDetail(entry.id)
            }
        )
        _builder = builder
        builder.highlightNewIds = newAlertIds
        binding.flowHost.addView(buildFlowStrip(builder))
        binding.flowHost.addView(buildToolsRow())
        binding.flowHost.addView(buildNewSourcesBanner(builder, seenCatalog))

        // v1.8.20 — the recent-search chips row lives under the search field.
        binding.historyHost.addView(buildHistoryRow())

        render(builder)

        // v1.8.20 — a search confirmed with the keyboard's search action is
        // recorded into the history (and the keyboard is dismissed).
        binding.etGuideSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                val query = binding.etGuideSearch.text?.toString().orEmpty()
                if (query.isNotBlank()) {
                    searchHistory.record(query)
                    refreshHistoryRow()
                }
                hideKeyboard()
                true
            } else {
                false
            }
        }

        // v1.8.17 — the 📊 What's-new sheet greets every fresh install and
        // every update exactly once (a stored marker remembers the last
        // version acknowledged), and stays reachable from the tools row.
        maybeShowWhatsNew()

        binding.etGuideSearch.doAfterTextChanged {
            render(builder)
        }
        binding.chipGroup.setOnCheckedChangeListener { _, checkedId ->
            selectedCategory = when (checkedId) {
                R.id.chipFree -> GuideCategory.FREE
                R.id.chipPaid -> GuideCategory.PAID
                R.id.chipSelfHosted -> GuideCategory.SELF_HOSTED
                R.id.chipMesh -> GuideCategory.MESH
                R.id.chipRouters -> GuideCategory.ROUTERS
                R.id.chipApps -> GuideCategory.APPS
                else -> null   // All chip — and the Favorites chip (handled below)
            }
            render(builder)
        }
        // v1.8.13 — quick price filter above the list: All / Free / Paid.
        binding.priceGroup.setOnCheckedChangeListener { _, checkedId ->
            selectedPrice = when (checkedId) {
                R.id.chipPriceFree -> WireGuideFilter.Price.FREE
                R.id.chipPricePaid -> WireGuideFilter.Price.PAID
                else -> null   // the All button
            }
            render(builder)
        }
        // v1.8.17 — quick setup-difficulty filter: All / Beginner /
        // Intermediate / Advanced (same labels as the cards' badges).
        binding.difficultyGroup.setOnCheckedChangeListener { _, checkedId ->
            selectedDifficulty = when (checkedId) {
                R.id.chipDifficultyBeginner -> GuideDifficulty.BEGINNER
                R.id.chipDifficultyIntermediate -> GuideDifficulty.INTERMEDIATE
                R.id.chipDifficultyAdvanced -> GuideDifficulty.ADVANCED
                else -> null   // the All button
            }
            render(builder)
        }
    }

    private fun isFavoritesOnly(): Boolean =
        _binding?.chipGroup?.checkedChipId == R.id.chipFav

    /** v1.8.2 — the flat "Top rated" sort chip. */
    private fun isTopRatedOnly(): Boolean =
        _binding?.chipGroup?.checkedChipId == R.id.chipTopRated

    /**
     * Re-render only when signal membership can change: inside the
     * favorites-only flat list. Elsewhere cards repaint in place, so the
     * expand/collapse state of sections is never disturbed.
     */
    private fun onFavoritesMembershipChanged() {
        val builder = _builder ?: return
        if (_binding != null && isFavoritesOnly()) render(builder)
    }

    /**
     * v1.8.3 — after the confirmed bulk delete the whole surface changes
     * (stats card disappears, stars/favorites/notes reset), so re-render
     * unconditionally and confirm with a toast.
     */
    private fun onSignalsCleared(builder: GuideUiBuilder) {
        if (_binding == null) return
        render(builder)
        Toast.makeText(requireContext(), R.string.guide_stats_cleared, Toast.LENGTH_SHORT)
            .show()
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

    /** v1.8.16+17 — guide tools: the offline config checker and the 📊 What's-new sheet. */
    private fun buildToolsRow(): View {
        val row = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(10) }
        }
        row.addView(MaterialButton(
            requireContext(), null,
            com.google.android.material.R.attr.materialButtonOutlinedStyle
        ).apply {
            text = getString(R.string.wgcheck_open)
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 12.5f)
            layoutParams = LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
            ).apply { marginStart = dp(3); marginEnd = dp(3) }
            setOnClickListener {
                WgCheckerDialogFragment()
                    .show(parentFragmentManager, "wg_config_checker")
            }
        })
        row.addView(MaterialButton(
            requireContext(), null,
            com.google.android.material.R.attr.materialButtonOutlinedStyle
        ).apply {
            text = getString(R.string.whatsnew_open)
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 12.5f)
            layoutParams = LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
            ).apply { marginStart = dp(3); marginEnd = dp(3) }
            setOnClickListener {
                WhatsNewDialogFragment()
                    .show(parentFragmentManager, "whats_new")
            }
        })
        // v1.8.19 — 🧭 the setup assistant joins the tools row: three plain
        // answers in, a ranked five-source shortlist out (pure recommender).
        row.addView(MaterialButton(
            requireContext(), null,
            com.google.android.material.R.attr.materialButtonOutlinedStyle
        ).apply {
            text = getString(R.string.recommend_open)
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 12.5f)
            layoutParams = LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
            ).apply { marginStart = dp(3); marginEnd = dp(3) }
            setOnClickListener {
                RecommendDialogFragment()
                    .show(parentFragmentManager, "guide_recommender")
            }
        })
        return row
    }

    /**
     * v1.8.17 — auto-open the 📊 What's-new sheet once per version: the
     * stored marker only ever records versions of this very app, and the
     * dialog shows the shipped releases (newest first) — so a fresh
     * install gets the tour and an update announces exactly what changed.
     */
    private fun maybeShowWhatsNew() {
        val prefs = requireContext()
            .getSharedPreferences("guide_whats_new", Context.MODE_PRIVATE)
        if (prefs.getString("last_seen_version", null) != WhatsNew.CURRENT_VERSION) {
            prefs.edit()
                .putString("last_seen_version", WhatsNew.CURRENT_VERSION)
                .apply()
            WhatsNewDialogFragment().show(parentFragmentManager, "whats_new")
        }
    }

    /**
     * v1.8.16 — the dismissible "new sources added" banner: names the
     * arrivals and acknowledges them (banner + 🆕 badges) in one tap.
     * Sits inside the flow card, above the search field, until dismissed.
     */
    private fun buildNewSourcesBanner(
        builder: GuideUiBuilder,
        seenCatalog: GuideSeenCatalog
    ): View {
        val banner = com.google.android.material.card.MaterialCardView(requireContext()).apply {
            radius = dp(10).toFloat()
            visibility = if (newAlertIds.isEmpty()) View.GONE else View.VISIBLE
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(10) }
        }
        val row = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(8), dp(10), dp(8))
        }
        row.addView(TextView(requireContext()).apply {
            val names = GuideSeenCatalog.namesOf(WireGuideCatalog.entries, newAlertIds)
            val shown = names.take(3).joinToString(" · ") +
                if (names.size > 3) " …" else ""
            text = getString(R.string.guide_new_banner, newAlertIds.size, shown)
            setTextColor(resolveColorAttr(com.google.android.material.R.attr.colorOnSurface))
            textSize = 12f
            setLineSpacing(dp(2).toFloat(), 1f)
            contentDescription = getString(R.string.guide_new_banner_cd)
            layoutParams = LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
            )
        })
        row.addView(TextView(requireContext()).apply {
            text = getString(R.string.guide_new_dismiss)
            setTextColor(resolveColorAttr(com.google.android.material.R.attr.colorPrimary))
            textSize = 12.5f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(dp(8), dp(4), dp(2), dp(4))
            isClickable = true
            isFocusable = true
            setOnClickListener {
                seenCatalog.markAllSeen(WireGuideCatalog.entries.map { it.id })
                newAlertIds = emptySet()
                builder.highlightNewIds = emptySet()
                banner.visibility = View.GONE
                _builder?.let { render(it) }
            }
        })
        banner.addView(row)
        return banner
    }

    private fun resolveColorAttr(attr: Int): Int {
        val value = android.util.TypedValue()
        requireContext().theme.resolveAttribute(attr, value, true)
        return value.data
    }

    /**
     * v1.8.20 — builds the recent-search chips row: a horizontal scroll of
     * one chip per recorded query plus a trailing "Clear" chip. Hidden
     * entirely while the history is empty; [refreshHistoryRow] re-fills it.
     */
    private fun buildHistoryRow(): View {
        val scroll = HorizontalScrollView(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(2) }
            isHorizontalScrollBarEnabled = false
        }
        val row = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
        }
        scroll.addView(row)
        _historyScroll = scroll
        _historyRow = row
        refreshHistoryRow()
        return scroll
    }

    /** v1.8.20 — re-renders the history chips from the persisted store. */
    private fun refreshHistoryRow() {
        val scroll = _historyScroll ?: return
        val row = _historyRow ?: return
        row.removeAllViews()
        val items = searchHistory.history()
        if (items.isEmpty()) {
            scroll.visibility = View.GONE
            return
        }
        scroll.visibility = View.VISIBLE
        items.forEach { item ->
            row.addView(Chip(
                requireContext(), null,
                com.google.android.material.R.attr.chipStyle
            ).apply {
                text = item
                textSize = 12f
                isCloseIconVisible = false
                isCheckable = false
                setOnClickListener {
                    binding.etGuideSearch.setText(item)
                    binding.etGuideSearch.setSelection(item.length)
                    searchHistory.record(item)
                    refreshHistoryRow()
                    hideKeyboard()
                }
            })
        }
        row.addView(Chip(
            requireContext(), null,
            com.google.android.material.R.attr.chipStyle
        ).apply {
            text = getString(R.string.guide_history_clear)
            textSize = 12f
            isCloseIconVisible = false
            isCheckable = false
            setTextColor(resolveColorAttr(com.google.android.material.R.attr.colorPrimary))
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setOnClickListener {
                searchHistory.clear()
                refreshHistoryRow()
            }
        })
    }

    /** v1.8.20 — dismisses the keyboard after confirming or re-running a search. */
    private fun hideKeyboard() {
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE)
            as? InputMethodManager ?: return
        imm.hideSoftInputFromWindow(binding.etGuideSearch.windowToken, 0)
    }

    private fun dp(value: Int): Int =
        (value * requireContext().resources.displayMetrics.density).toInt()

    private fun render(builder: GuideUiBuilder) {
        val query = binding.etGuideSearch.text?.toString().orEmpty()
        val favOnly = isFavoritesOnly()
        val topRated = isTopRatedOnly()
        // Personal notes are searchable too (v1.8.1): snapshot id → note.
        val notes = store.noteIds().mapNotNull { id ->
            store.note(id)?.let { id to it }
        }.toMap()
        val entries = WireGuideFilter.filter(
            WireGuideCatalog.entries, query, selectedCategory,
            notes, if (favOnly) store.favoriteIds() else null
        ).let { WireGuideFilter.filterByPrice(it, selectedPrice) }
            .let { WireGuideFilter.filterByDifficulty(it, selectedDifficulty) }
        val filtering = query.isNotBlank() || selectedCategory != null ||
            favOnly || topRated || selectedPrice != null ||
            selectedDifficulty != null
        if (filtering) {
            binding.tvGuideCount.text =
                getString(R.string.guide_results_count, entries.size)
            binding.tvGuideCount.visibility = View.VISIBLE
            binding.tvGuideEmpty.setText(
                if (favOnly && query.isBlank() && store.favoriteIds().isEmpty())
                    R.string.guide_favorites_empty
                else
                    R.string.guide_no_results
            )
            // v1.8.9 — typo-tolerant fallback: when nothing matches exactly,
            // retry with edit-distance matching before showing the dead end.
            if (entries.isEmpty() && query.isNotBlank() && !favOnly) {
                val fuzzy = GuideSmart.fuzzySearch(WireGuideCatalog.entries, query, notes)
                if (fuzzy.isNotEmpty()) {
                    binding.tvGuideCount.text =
                        getString(R.string.guide_results_count, fuzzy.size)
                    binding.tvGuideEmpty.visibility = View.GONE
                    builder.renderFuzzy(binding.guideSections, fuzzy)
                    return
                }
            }
            binding.tvGuideEmpty.visibility =
                if (entries.isEmpty()) View.VISIBLE else View.GONE
            builder.renderFiltered(binding.guideSections, entries)
        } else {
            binding.tvGuideCount.visibility = View.GONE
            binding.tvGuideEmpty.visibility = View.GONE
            builder.renderSections(binding.guideSections, showStats = true)
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
        _builder = null
        _historyScroll = null
        _historyRow = null
        super.onDestroyView()
    }
}
