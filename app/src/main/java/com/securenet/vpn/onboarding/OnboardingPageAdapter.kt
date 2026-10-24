package com.securenet.vpn.onboarding

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.securenet.vpn.R
import com.securenet.vpn.databinding.ItemOnboardingPageBinding

/**
 * RecyclerView-backed adapter for ViewPager2 (no fragment lifecycle churn
 * for three static slides).
 */
class OnboardingPageAdapter(
    private val slides: List<OnboardingActivity.Slide>
) : RecyclerView.Adapter<OnboardingPageAdapter.PageViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageViewHolder {
        val binding = ItemOnboardingPageBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return PageViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PageViewHolder, position: Int) {
        val slide = slides[position]
        holder.binding.onboardingTitle.setText(slide.titleRes)
        holder.binding.onboardingDescription.setText(slide.descRes)
        holder.binding.onboardingAnimation.setAnimation(slide.animationRes)
        holder.binding.onboardingAnimation.playAnimation()
    }

    override fun getItemCount(): Int = slides.size

    class PageViewHolder(val binding: ItemOnboardingPageBinding) :
        RecyclerView.ViewHolder(binding.root)
}
