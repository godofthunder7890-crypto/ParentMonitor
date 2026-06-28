package com.parent.monitor

import android.animation.*
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator

object AnimationUtils {

    fun fadeSlideUp(view: View, delayMs: Long = 0, durationMs: Long = 400) {
        view.alpha = 0f
        view.translationY = 80f
        view.visibility = View.VISIBLE
        view.animate()
            .alpha(1f).translationY(0f)
            .setDuration(durationMs).setStartDelay(delayMs)
            .setInterpolator(DecelerateInterpolator(2f))
            .start()
    }

    fun popIn(view: View, delayMs: Long = 0) {
        view.scaleX = 0f; view.scaleY = 0f; view.alpha = 0f
        view.visibility = View.VISIBLE
        view.animate()
            .scaleX(1f).scaleY(1f).alpha(1f)
            .setDuration(400).setStartDelay(delayMs)
            .setInterpolator(OvershootInterpolator(1.3f))
            .start()
    }

    fun pulse(view: View) {
        val anim = ObjectAnimator.ofPropertyValuesHolder(
            view,
            PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.4f, 1f),
            PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.4f, 1f),
            PropertyValuesHolder.ofFloat(View.ALPHA,   1f, 0.3f, 1f)
        ).apply {
            duration = 2000; repeatCount = ObjectAnimator.INFINITE
            interpolator = DecelerateInterpolator()
        }
        anim.start()
    }

    fun breathe(view: View) {
        ObjectAnimator.ofPropertyValuesHolder(
            view,
            PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.08f, 1f),
            PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.08f, 1f)
        ).apply {
            duration = 3000; repeatCount = ObjectAnimator.INFINITE
            interpolator = DecelerateInterpolator()
        }.start()
    }

    fun countUp(from: Int, to: Int, durationMs: Long = 600, onUpdate: (Int) -> Unit) {
        ValueAnimator.ofInt(from, to).apply {
            duration = durationMs
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { onUpdate(it.animatedValue as Int) }
        }.start()
    }

    fun shake(view: View) {
        val anim = ObjectAnimator.ofFloat(view, View.TRANSLATION_X,
            0f, -20f, 20f, -16f, 16f, -10f, 10f, -6f, 6f, 0f)
        anim.duration = 500; anim.start()
    }

    fun staggerCards(vararg views: View) {
        views.forEachIndexed { i, v -> fadeSlideUp(v, delayMs = (i * 80).toLong()) }
    }
}
