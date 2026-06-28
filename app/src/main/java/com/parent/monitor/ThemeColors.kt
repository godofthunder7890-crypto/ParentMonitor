package com.parent.monitor

object ThemeColors {
    const val BG          = 0xFF060612.toInt()
    const val SURFACE     = 0xFF0A0A1E.toInt()
    const val SURFACE2    = 0xFF0D1033.toInt()
    const val PRIMARY     = 0xFF00E5FF.toInt()
    const val PRIMARY_DIM = 0x3300E5FF
    const val SECONDARY   = 0xFF7C4DFF.toInt()
    const val SUCCESS     = 0xFF00C853.toInt()
    const val WARNING     = 0xFFFFD600.toInt()
    const val DANGER      = 0xFFFF1744.toInt()
    const val TEXT_PRI    = 0xFFFFFFFF.toInt()
    const val TEXT_SEC    = 0xFF8899AA.toInt()

    fun moodColor(mood: String) = when (mood.lowercase()) {
        "happy"   -> 0xFFFFD600.toInt()
        "anxious" -> 0xFFFF6D00.toInt()
        "sad"     -> 0xFF7C4DFF.toInt()
        "stressed"-> 0xFFFF1744.toInt()
        else      -> 0xFF00E5FF.toInt()
    }
    fun moodEmoji(mood: String) = when (mood.lowercase()) {
        "happy"   -> "😊"
        "anxious" -> "😟"
        "sad"     -> "😢"
        "stressed"-> "😰"
        else      -> "😐"
    }
    fun gradeColor(grade: String) = when (grade) {
        "A" -> 0xFF00C853.toInt()
        "B" -> 0xFF69F0AE.toInt()
        "C" -> 0xFFFFD600.toInt()
        "D" -> 0xFFFF6D00.toInt()
        else-> 0xFFFF1744.toInt()
    }
}
