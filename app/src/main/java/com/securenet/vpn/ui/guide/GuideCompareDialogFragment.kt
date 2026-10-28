package com.securenet.vpn.ui.guide

import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.core.widget.doAfterTextChanged
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.securenet.vpn.MainActivity
import com.securenet.vpn.R
import com.securenet.vpn.guide.GuideCategory
import com.securenet.vpn.guide.GuideCompare
import com.securenet.vpn.guide.GuideEntry
import com.securenet.vpn.guide.GuideSmart
import com.securenet.vpn.guide.GuideStoreProvider
import com.securenet.vpn.guide.WireGuideCatalog
import com.securenet.vpn.guide.WireGuideFilter

/**
 * v1.8.13 — side-by-side comparison bottom sheet (🔬 "مقارنة جنباً إلى
 * جنب"), opened from the detail page's Compare button. Two phases inside
 * one sheet:
 *
 *  1. picker — searchable list of comparison candidates ordered by
 *     [GuideCompare.candidates] (same section first, then shared
     *     keywords, then the user's signals); a live filter narrows it;
 *  2. table — the comparison itself: both source names as tappable
 *     headers (open the full detail page), then one row per comparable
 *     fact — section, price (the quick filter's free/paid language),
 *     official host, Editor's choice badge, the user's own rating and
 *     bookmark — and a shared-keywords footer explaining in the smart
 *     layer's vocabulary why the two feel similar (or that they don't).
 *
 * Every value comes from [GuideCompare.compare] over catalog data and the
 * user's own signals — nothing invented. Rows build programmatically so
 * the sheet follows the app's theme and mirrors correctly in RTL.
 */
class GuideCompareDialogFragment : BottomSheetDialogFragment() {

    private var host: LinearLayout? = null
    private var baseId: String? = null
    private val store by lazy {
        GuideStoreProvider.get(requireContext())
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        baseId = requireArguments().getString(ARG_BASE_ID)
        if (WireGuideCatalog.byId(baseId.orEmpty()) == null) {
            // Catalog version drift: degrade exactly like the detail page.
            Toast.makeText(
                requireContext(), R.string.guide_detail_unknown, Toast.LENGTH_SHORT
            ).show()
            dismiss()
        }
        val root = ScrollView(requireContext()).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            scrollBarStyle = ScrollView.SCROLLBARS_INSIDE_INSET
        }
        host = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(16), dp(20), dp(24))
        }
        root.addView(host)
        return root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val base = WireGuideCatalog.byId(baseId.orEmpty()) ?: return
        showPicker(base)
    }

    // ------------------------------------------------------------------
    // Phase 1 — pick the second source
    // ------------------------------------------------------------------

    private fun showPicker(base: GuideEntry) {
        val content = host ?: return
        content.removeAllViews()

        content.addView(title(R.string.guide_compare_pick))
        content.addView(caption(R.string.guide_compare_open_hint))

        val query = EditText(requireContext()).apply {
            hint = getString(R.string.guide_compare_search_hint)
            setSingleLine()
            textSize = 13f
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }
        content.addView(query)

        val list = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
        }
        content.addView(list)

        fun refresh(filter: String) {
            list.removeAllViews()
            val candidates = GuideCompare.candidates(base, store)
                .filter {
                    filter.isBlank() ||
                        it.name.lowercase().contains(filter.lowercase().trim())
                }
            if (candidates.isEmpty()) {
                list.addView(caption(R.string.guide_no_results))
                return
            }
            candidates.forEach { candidate ->
                list.addView(pickerRow(base, candidate))
            }
        }
        refresh("")
        query.doAfterTextChanged { refresh(it?.toString().orEmpty()) }
    }

    /** One tappable candidate: icon, name, "section · N shared". */
    private fun pickerRow(base: GuideEntry, candidate: GuideEntry): View {
        val shared = GuideSmart.sharedKeywords(base, candidate).size
        val row = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(10), dp(4), dp(10))
            isClickable = true
            isFocusable = true
            // resolve the selectable-item-background attr to a drawable id
            runCatching {
                val tv = android.util.TypedValue()
                requireContext().theme.resolveAttribute(
                    android.R.attr.selectableItemBackground, tv, true
                )
                setBackgroundResource(tv.resourceId)
            }
        }
        row.addView(ImageView(requireContext()).apply {
            setImageResource(iconFor(candidate.category))
            layoutParams = LinearLayout.LayoutParams(dp(24), dp(24)).apply {
                marginEnd = dp(10)
            }
        })
        row.addView(LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
            )
            addView(TextView(requireContext()).apply {
                text = candidate.name
                setTextColor(resolveColor(com.google.android.material.R.attr.colorOnSurface))
                textSize = 13.5f
                maxLines = 1
            })
            addView(TextView(requireContext()).apply {
                text = getString(categoryLabel(candidate.category)) +
                    if (shared > 0) "  ·  " + shared + " " +
                        getString(R.string.guide_compare_shared_count) else ""
                setTextColor(resolveColor(android.R.attr.textColorSecondary))
                textSize = 11f
                maxLines = 1
            })
        })
        row.addView(TextView(requireContext()).apply {
            text = "›"
            setTextColor(resolveColor(android.R.attr.textColorSecondary))
            textSize = 16f
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { marginStart = dp(8) }
        })
        row.setOnClickListener {
            WireGuideCatalog.byId(candidate.id)?.let { showTable(base, it) }
        }
        return row
    }

    // ------------------------------------------------------------------
    // Phase 2 — the side-by-side table
    // ------------------------------------------------------------------

    private fun showTable(left: GuideEntry, right: GuideEntry) {
        val content = host ?: return
        content.removeAllViews()
        val cmp = GuideCompare.compare(left, right)

        content.addView(title(R.string.guide_compare_title))
        content.addView(caption(R.string.guide_compare_open_hint))

        // Tappable name headers aligned with the value columns.
        content.addView(LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(spacer(0.9f))
            addView(nameCell(left) { openDetail(left.id) })
            addView(nameCell(right) { openDetail(right.id) })
        })

        fun labelRow(labelRes: Int, leftValue: String, rightValue: String) {
            content.addView(LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, dp(8), 0, dp(8))
                addView(labelCell(labelRes))
                addView(valueCell(leftValue))
                addView(valueCell(rightValue))
            })
            content.addView(divider())
        }

        labelRow(
            R.string.guide_compare_row_section,
            getString(categoryLabel(left.category)),
            getString(categoryLabel(right.category))
        )
        labelRow(
            R.string.guide_compare_row_price,
            priceText(cmp.leftPrice),
            priceText(cmp.rightPrice)
        )
        labelRow(
            R.string.guide_compare_row_host,
            cmp.leftHost ?: "—",
            cmp.rightHost ?: "—"
        )
        labelRow(
            R.string.guide_compare_row_editors,
            if (cmp.leftEditorsChoice) "✓" else "—",
            if (cmp.rightEditorsChoice) "✓" else "—"
        )
        labelRow(
            R.string.guide_compare_row_rating,
            stars(store.rating(left.id)),
            stars(store.rating(right.id))
        )
        labelRow(
            R.string.guide_compare_row_bookmark,
            if (store.isFavorite(left.id)) "♥" else "—",
            if (store.isFavorite(right.id)) "♥" else "—"
        )

        // Shared-keywords footer: the smart layer's vocabulary for
        // "why these two feel similar" — or its honest absence.
        content.addView(TextView(requireContext()).apply {
            text = if (cmp.sharedKeywords.isEmpty())
                getString(R.string.guide_compare_no_shared)
            else
                getString(
                    R.string.guide_compare_shared,
                    cmp.sharedKeywords.sorted().joinToString(", ")
                )
            setTextColor(resolveColor(android.R.attr.textColorSecondary))
            textSize = 12f
            setPadding(0, dp(12), 0, 0)
        })

        content.addView(TextView(requireContext()).apply {
            text = getString(R.string.guide_compare_change)
            setTextColor(resolveColor(com.google.android.material.R.attr.colorPrimary))
            textSize = 13f
            isClickable = true
            isFocusable = true
            setPadding(0, dp(16), 0, 0)
            setOnClickListener { showPicker(left) }
        })
    }

    // ------------------------------------------------------------------
    // Cells and small builders
    // ------------------------------------------------------------------

    private fun nameCell(entry: GuideEntry, onClick: () -> Unit): View =
        LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.1f
            ).apply { marginStart = dp(6); marginEnd = dp(6) }
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
            addView(ImageView(requireContext()).apply {
                setImageResource(iconFor(entry.category))
                layoutParams = LinearLayout.LayoutParams(dp(24), dp(24))
            })
            addView(TextView(requireContext()).apply {
                text = entry.name
                setTextColor(resolveColor(com.google.android.material.R.attr.colorPrimary))
                textSize = 13f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                maxLines = 2
                setPadding(0, dp(4), 0, 0)
            })
        }

    private fun spacer(weight: Float): View = View(requireContext()).apply {
        layoutParams = LinearLayout.LayoutParams(0, 1, weight)
    }

    private fun labelCell(labelRes: Int): View = TextView(requireContext()).apply {
        text = getString(labelRes)
        setTextColor(resolveColor(android.R.attr.textColorSecondary))
        textSize = 11.5f
        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.9f)
    }

    private fun valueCell(value: String): View = TextView(requireContext()).apply {
        text = value
        setTextColor(resolveColor(com.google.android.material.R.attr.colorOnSurface))
        textSize = 12.5f
        layoutParams = LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.1f
        ).apply { marginStart = dp(6); marginEnd = dp(6) }
    }

    private fun divider(): View = View(requireContext()).apply {
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(1)
        )
        setBackgroundColor(resolveColor(android.R.attr.dividerHorizontal))
        alpha = 0.35f
    }

    private fun title(textRes: Int): View = TextView(requireContext()).apply {
        text = getString(textRes)
        setTextColor(resolveColor(com.google.android.material.R.attr.colorOnSurface))
        textSize = 16f
        setTypeface(typeface, android.graphics.Typeface.BOLD)
        gravity = Gravity.CENTER
        setPadding(0, 0, 0, dp(6))
    }

    private fun caption(textRes: Int): View = TextView(requireContext()).apply {
        text = getString(textRes)
        setTextColor(resolveColor(android.R.attr.textColorSecondary))
        textSize = 11.5f
        setPadding(0, 0, 0, dp(10))
    }

    private fun priceText(price: WireGuideFilter.Price?): String = when (price) {
        WireGuideFilter.Price.FREE -> getString(R.string.guide_price_free)
        WireGuideFilter.Price.PAID -> getString(R.string.guide_price_paid)
        null -> "—"
    }

    private fun stars(rating: Int): String =
        if (rating in 1..5) "★".repeat(rating) + "☆".repeat(5 - rating) else "—"

    /** v1.8.13 — icon per section (same mapping the cards use). */
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

    private fun openDetail(id: String) {
        (activity as? MainActivity)?.openGuideDetail(id)
        dismiss()
    }

    private fun resolveColor(attr: Int): Int {
        val value = android.util.TypedValue()
        requireContext().theme.resolveAttribute(attr, value, true)
        return value.data
    }

    private fun dp(value: Int): Int =
        (value * requireContext().resources.displayMetrics.density).toInt()

    override fun onDestroyView() {
        host = null
        super.onDestroyView()
    }

    companion object {
        private const val ARG_BASE_ID = "base_source_id"

        fun newInstance(baseSourceId: String): GuideCompareDialogFragment =
            GuideCompareDialogFragment().apply {
                arguments = bundleOf(ARG_BASE_ID to baseSourceId)
            }
    }
}
