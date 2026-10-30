package com.securenet.vpn.ui.guide

import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.securenet.vpn.MainActivity
import com.securenet.vpn.R
import com.securenet.vpn.guide.GuideCategory
import com.securenet.vpn.guide.GuideDifficulty
import com.securenet.vpn.guide.GuideRecommender
import com.securenet.vpn.guide.WireGuideCatalog
import com.securenet.vpn.guide.WireGuideFilter

/**
 * v1.8.19 — 🧭 "pick for me" setup assistant (مساعد اختيار المصدر), opened
 * from the guide's new tools-row button. Three plain questions — your
 * comfort level, your budget, and which kind of setup interests you — feed
 * the pure [GuideRecommender], which answers with a ranked shortlist of at
 * most five sources, each carrying its human-readable reasons ("fits your
 * level", "free", "Editor's choice"). Tapping a suggestion opens its full
 * detail page; "answer again" re-opens the questions. Programmatic UI in
 * the checker/compare sheet style — no new XML layouts, no network: the
 * whole thing is the shipped catalog scored on-device.
 */
class RecommendDialogFragment : BottomSheetDialogFragment() {

    private var host: LinearLayout? = null

    private var level: GuideDifficulty? = null
    private var budget: GuideRecommender.Budget = GuideRecommender.Budget.ANY
    private var section: GuideCategory? = null

    private val levelButtons = mutableListOf<Pair<MaterialButton, GuideDifficulty>>()
    private val budgetButtons =
        mutableListOf<Pair<MaterialButton, GuideRecommender.Budget>>()
    private val sectionButtons = mutableListOf<Pair<MaterialButton, GuideCategory?>>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val root = ScrollView(requireContext()).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            scrollBarStyle = ScrollView.SCROLLBARS_INSIDE_INSET
        }
        val content = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(16), dp(20), dp(24))
        }
        host = content
        root.addView(content)
        return root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        showQuestions()
    }

    // ------------------------------------------------------------------
    // Phase one — the three questions
    // ------------------------------------------------------------------

    private fun showQuestions() {
        val content = host ?: return
        content.removeAllViews()

        content.addView(title())
        content.addView(caption(getString(R.string.recommend_caption)))

        content.addView(questionLabel(R.string.recommend_level_q))
        content.addView(choiceRow { add ->
            listOf(
                GuideDifficulty.BEGINNER,
                GuideDifficulty.INTERMEDIATE,
                GuideDifficulty.ADVANCED
            ).forEach { difficulty ->
                add(difficultyButton(WireGuideCatalog.difficultyLabelRes(difficulty), difficulty))
            }
        })

        content.addView(questionLabel(R.string.recommend_budget_q))
        content.addView(choiceRow { add ->
            add(
                budgetButton(
                    R.string.recommend_budget_free, GuideRecommender.Budget.FREE_ONLY
                )
            )
            add(
                budgetButton(
                    R.string.recommend_budget_any, GuideRecommender.Budget.ANY
                )
            )
        })

        content.addView(questionLabel(R.string.recommend_section_q))
        content.addView(choiceRow { add ->
            add(sectionButton(R.string.recommend_section_all, null))
            GuideCategory.entries.forEach { category ->
                add(sectionButton(sectionRes(category), category))
            }
        })

        content.addView(runButton())
    }

    /** One horizontal, scrollable row of mutually-exclusive choice buttons. */
    private fun choiceRow(fill: (add: (MaterialButton) -> Unit) -> Unit): View =
        android.widget.HorizontalScrollView(requireContext()).apply {
            isHorizontalScrollBarEnabled = false
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(8) }
            addView(LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                fill { button -> addView(button) }
            })
        }

    private fun difficultyButton(
        @Suppress("UNUSED_PARAMETER") labelRes: Int,
        difficulty: GuideDifficulty
    ): MaterialButton = outlinedChoice(
        requireContext().getString(WireGuideCatalog.difficultyLabelRes(difficulty))
    ).apply {
        setOnClickListener {
            level = difficulty
            repaint(levelButtons) { it.first }
        }
        levelButtons += this to difficulty
    }

    private fun budgetButton(
        labelRes: Int,
        value: GuideRecommender.Budget
    ): MaterialButton = outlinedChoice(getString(labelRes)).apply {
        setOnClickListener {
            budget = value
            repaint(budgetButtons) { it.first }
        }
        budgetButtons += this to value
    }

    private fun sectionButton(
        labelRes: Int,
        value: GuideCategory?
    ): MaterialButton = outlinedChoice(getString(labelRes)).apply {
        setOnClickListener {
            section = value
            repaint(sectionButtons) { it.first }
        }
        sectionButtons += this to value
    }

    private fun outlinedChoice(text: String): MaterialButton =
        MaterialButton(
            requireContext(), null,
            com.google.android.material.R.attr.materialButtonOutlinedStyle
        ).apply {
            this.text = text
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 12.5f)
            setPadding(dp(10), 0, dp(10), 0)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                dp(40)
            ).apply { marginEnd = dp(6) }
        }

    /** Repaints one choice group so only the current selection looks active. */
    private fun repaint(
        group: List<Pair<MaterialButton, *>>,
        buttonOf: (Pair<MaterialButton, *>) -> MaterialButton
    ) {
        val primary = resolveColor(com.google.android.material.R.attr.colorPrimary)
        val secondary = resolveColor(android.R.attr.textColorSecondary)
        group.forEach { pair ->
            val button = buttonOf(pair)
            val selected = when (pair.second) {
                is GuideDifficulty -> level == pair.second
                is GuideRecommender.Budget -> budget == pair.second
                else -> section == pair.second
            }
            button.setTextColor(if (selected) primary else secondary)
            button.setTypeface(button.typeface, if (selected) Typeface.BOLD else Typeface.NORMAL)
            button.strokeWidth = if (selected) dp(2) else dp(1)
        }
    }

    private fun runButton(): View = MaterialButton(requireContext()).apply {
        text = getString(R.string.recommend_run)
        setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 13f)
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dp(46)
        ).apply { topMargin = dp(6) }
        setOnClickListener { run() }
    }

    private fun run() {
        val chosenLevel = level ?: run {
            Toast.makeText(
                requireContext(), R.string.recommend_pick_level, Toast.LENGTH_SHORT
            ).show()
            return
        }
        val picks = GuideRecommender.recommend(
            WireGuideCatalog.entries, chosenLevel, budget, section
        )
        showResults(picks)
    }

    // ------------------------------------------------------------------
    // Phase two — the ranked shortlist
    // ------------------------------------------------------------------

    private fun showResults(picks: List<GuideRecommender.Suggestion>) {
        val content = host ?: return
        content.removeAllViews()

        content.addView(title())
        content.addView(caption(getString(R.string.recommend_result_caption)))

        if (picks.isEmpty()) {
            content.addView(TextView(requireContext()).apply {
                text = getString(R.string.recommend_none)
                setTextColor(resolveColor(android.R.attr.textColorSecondary))
                textSize = 12.5f
                gravity = Gravity.CENTER
                setPadding(0, dp(10), 0, dp(14))
            })
        } else {
            picks.forEachIndexed { index, suggestion ->
                if (index > 0) content.addView(divider())
                content.addView(suggestionRow(suggestion))
            }
        }

        content.addView(MaterialButton(requireContext(), null,
            com.google.android.material.R.attr.materialButtonOutlinedStyle
        ).apply {
            text = getString(R.string.recommend_again)
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 12.5f)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(44)
            ).apply { topMargin = dp(14) }
            setOnClickListener { level = null; showQuestions() }
        })
    }

    /** One suggestion: name, "section · difficulty" meta line and the reasons. */
    private fun suggestionRow(suggestion: GuideRecommender.Suggestion): View {
        val entry = suggestion.entry
        val primary = resolveColor(com.google.android.material.R.attr.colorPrimary)
        val row = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                cornerRadius = dp(10).toFloat()
                setColor(primary and 0x0FFFFFFF)
                setStroke(dp(1), primary and 0x33FFFFFF)
            }
            setPadding(dp(12), dp(10), dp(12), dp(10))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(2) }
            setOnClickListener {
                (activity as? MainActivity)?.openGuideDetail(entry.id)
                dismiss()
            }
            contentDescription = entry.name
        }

        row.addView(TextView(requireContext()).apply {
            text = entry.name
            setTextColor(primary)
            textSize = 13.5f
            setTypeface(typeface, Typeface.BOLD)
        })
        row.addView(TextView(requireContext()).apply {
            text = getString(sectionRes(entry.category)) + " · " +
                getString(WireGuideCatalog.difficultyLabelRes(entry.difficulty))
            setTextColor(resolveColor(android.R.attr.textColorSecondary))
            textSize = 11.5f
            setPadding(0, dp(2), 0, dp(4))
        })
        row.addView(TextView(requireContext()).apply {
            text = suggestion.reasons.joinToString(" · ") { reason ->
                getString(GuideRecommender.reasonLabelRes(reason))
            }
            setTextColor(primary)
            textSize = 11.5f
        })
        return row
    }

    // ------------------------------------------------------------------
    // Shared helpers (mirroring the other sheets)
    // ------------------------------------------------------------------

    private fun title(): View = TextView(requireContext()).apply {
        text = getString(R.string.recommend_title)
        setTextColor(resolveColor(com.google.android.material.R.attr.colorOnSurface))
        textSize = 16f
        setTypeface(typeface, Typeface.BOLD)
        gravity = Gravity.CENTER
        setPadding(0, 0, 0, dp(2))
        contentDescription = getString(R.string.recommend_cd)
    }

    private fun caption(text: String): View = TextView(requireContext()).apply {
        this.text = text
        setTextColor(resolveColor(android.R.attr.textColorSecondary))
        textSize = 11.5f
        gravity = Gravity.CENTER
        setPadding(0, 0, 0, dp(10))
    }

    private fun questionLabel(textRes: Int): View = TextView(requireContext()).apply {
        text = getString(textRes)
        setTextColor(resolveColor(com.google.android.material.R.attr.colorOnSurface))
        textSize = 13f
        setTypeface(typeface, Typeface.BOLD)
        setPadding(0, dp(8), 0, dp(4))
    }

    private fun sectionRes(category: GuideCategory): Int = when (category) {
        GuideCategory.FREE -> R.string.guide_section_free
        GuideCategory.PAID -> R.string.guide_section_paid
        GuideCategory.SELF_HOSTED -> R.string.guide_section_selfhosted
        GuideCategory.ROUTERS -> R.string.guide_section_routers
        GuideCategory.APPS -> R.string.guide_section_apps
        GuideCategory.MESH -> R.string.guide_section_mesh
    }

    private fun divider(): View = View(requireContext()).apply {
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(1)
        ).apply { setMargins(0, dp(6), 0, dp(2)) }
        setBackgroundColor(
            resolveColor(android.R.attr.textColorSecondary) and 0x33FFFFFF
        )
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
        levelButtons.clear()
        budgetButtons.clear()
        sectionButtons.clear()
        super.onDestroyView()
    }
}
