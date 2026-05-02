package lv.zakon.tv.animevost.ui.splash

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import lv.zakon.tv.animevost.R
import kotlin.math.min

class SplashFragment : Fragment(R.layout.fragment_splash) {

    lateinit var posterProvider: SplashPosterProvider
    var onSplashComplete: (() -> Unit)? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val posterContainer = view.findViewById<FrameLayout>(R.id.posterContainer)

        view.isFocusable = false
        view.isFocusableInTouchMode = false
        view.setOnKeyListener { _, _, _ -> true }

        lifecycleScope.launch {
            val posters = posterProvider.getPosters()

            if (posters.isEmpty()) {
                onSplashComplete?.invoke()
                return@launch
            }

            val displayMetrics = resources.displayMetrics
            val screenW = displayMetrics.widthPixels
            val screenH = displayMetrics.heightPixels
            val density = displayMetrics.density

            val posterWpx = (120 * density).toInt()
            val posterHpx = (180 * density).toInt()

            val cols = screenW / posterWpx
            val rows = screenH / posterHpx
            val count = min(cols * rows, 24).coerceAtLeast(1)

            val centerX = (screenW / 2 - posterWpx / 2).toFloat()
            val centerY = (screenH / 2 - posterHpx / 2).toFloat()

            val posterUrls = if (posters.size >= count) {
                posters.take(count)
            } else {
                (0 until count).map { posters[it % posters.size] }
            }

            posterUrls.forEachIndexed { index, url ->
                val imageView = ImageView(requireContext()).apply {
                    layoutParams = FrameLayout.LayoutParams(posterWpx, posterHpx)
                    translationX = centerX
                    translationY = centerY
                }

                posterContainer.addView(imageView)

                Glide.with(this@SplashFragment)
                    .load(url)
                    .into(imageView)

                val col = index % cols
                val row = index / cols

                val targetX = (col * posterWpx).toFloat()
                val targetY = (row * posterHpx).toFloat()

                val animX = ObjectAnimator.ofFloat(imageView, "translationX", centerX, targetX)
                val animY = ObjectAnimator.ofFloat(imageView, "translationY", centerY, targetY)

                val animatorSet = AnimatorSet().apply {
                    playTogether(animX, animY)
                    duration = 800L
                    interpolator = DecelerateInterpolator()
                }

                animatorSet.start()
            }

            delay(2500L)

            if (isAdded && !isDetached) {
                onSplashComplete?.invoke()
            }
        }
    }
}