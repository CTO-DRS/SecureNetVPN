package com.securenet.vpn.onboarding

import android.animation.ObjectAnimator
import android.os.Bundle
import android.view.View
import android.view.animation.AnticipateOvershootInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.viewpager2.widget.ViewPager2
import com.airbnb.lottie.LottieAnimationView
import com.google.android.material.button.MaterialButton
import com.securenet.vpn.R
import com.securenet.vpn.databinding.ActivityOnboardingBinding
import com.securenet.vpn.di.AppProvider

/**
 * Three-slide onboarding: privacy, security scanning, performance.
 * Animated page indicators; last slide unlocks the dashboard.
 */
class OnboardingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOnboardingBinding
    private lateinit var dots: List<ImageView>

    private val slides = listOf(
        Slide(R.string.onboarding_title_1, R.string.onboarding_desc_1, R.raw.lottie_connected),
        Slide(R.string.onboarding_title_2, R.string.onboarding_desc_2, R.raw.lottie_scanning),
        Slide(R.string.onboarding_title_3, R.string.onboarding_desc_3, R.raw.lottie_connecting)
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOnboardingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val adapter = OnboardingPageAdapter(slides)
        binding.onboardingPager.adapter = adapter
        buildDots()

        binding.onboardingPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                updateDots(position)
                animateContent(position)
                binding.btnNext.text = if (position == slides.lastIndex) {
                    getString(R.string.onboarding_get_started)
                } else {
                    getString(R.string.onboarding_next)
                }
            }
        })

        binding.btnNext.setOnClickListener {
            val current = binding.onboardingPager.currentItem
            if (current < slides.lastIndex) {
                binding.onboardingPager.currentItem = current + 1
            } else {
                completeOnboarding()
            }
        }

        binding.btnSkip.setOnClickListener { completeOnboarding() }
    }

    private fun completeOnboarding() {
        (application as AppProvider).provideContainer()
            .securePrefs.onboardingCompleted = true

        com.securenet.vpn.MainActivity.start(this)
        finish()
    }

    private fun buildDots() {
        binding.onboardingIndicator.removeAllViews()
        dots = slides.map { _ ->
            ImageView(this).apply {
                val size = (12 * resources.displayMetrics.density).toInt()
                val dotParams = LinearLayout.LayoutParams(size, size).apply {
                    marginEnd = (8 * resources.displayMetrics.density).toInt()
                }
                layoutParams = dotParams
                setImageDrawable(ContextCompat.getDrawable(context, R.drawable.dot_inactive))
            }
        }
        dots.forEach { binding.onboardingIndicator.addView(it) }
        updateDots(0)
    }

    private fun updateDots(position: Int) {
        dots.forEachIndexed { index, dot ->
            dot.setImageDrawable(
                ContextCompat.getDrawable(
                    this,
                    if (index == position) R.drawable.dot_active else R.drawable.dot_inactive
                )
            )
        }
    }

    private fun animateContent(position: Int) {
        // Subtle button pop on slide change; Lottie pages animate themselves.
        ObjectAnimator.ofFloat(binding.btnNext, View.SCALE_X, 0.92f, 1f).apply {
            duration = 220
            interpolator = AnticipateOvershootInterpolator()
            start()
        }
        // Prime the page's Lottie file so the first frame is instant.
        val page = binding.onboardingPager.getChildAt(0)
        page?.findViewById<LottieAnimationView>(R.id.onboardingAnimation)
            ?.setAnimation(slides[position].animationRes)
    }

    data class Slide(val titleRes: Int, val descRes: Int, val animationRes: Int)
}
