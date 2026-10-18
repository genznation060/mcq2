package com.mcqstudio.pro.model

/**
 * Represents a single multiple-choice question item.
 */
data class MCQItem(
    val id: String = java.util.UUID.randomUUID().toString(),
    val question: String,
    val optionA: String,
    val optionB: String,
    val optionC: String,
    val optionD: String,
    val answer: String,
    val explanation: String = "",
    val imageUriOrPath: String? = null
) {
    /**
     * Resolves the 0-indexed correct option (0=A, 1=B, 2=C, 3=D).
     * Supports letters ("A", "b"), indices ("1", "4"), or full answer matching text.
     */
    val correctIndex: Int
        get() {
            val trimmed = answer.trim()
            if (trimmed.isEmpty()) return 0

            // Direct letter match
            if (trimmed.equals("A", ignoreCase = true) || trimmed.startsWith("A)", ignoreCase = true)) return 0
            if (trimmed.equals("B", ignoreCase = true) || trimmed.startsWith("B)", ignoreCase = true)) return 1
            if (trimmed.equals("C", ignoreCase = true) || trimmed.startsWith("C)", ignoreCase = true)) return 2
            if (trimmed.equals("D", ignoreCase = true) || trimmed.startsWith("D)", ignoreCase = true)) return 3

            // Numeric 1-4 or 0-3
            val num = trimmed.toIntOrNull()
            if (num != null) {
                if (num in 1..4) return num - 1
                if (num in 0..3) return num
            }

            // Text matching
            if (trimmed.equals(optionA.trim(), ignoreCase = true)) return 0
            if (trimmed.equals(optionB.trim(), ignoreCase = true)) return 1
            if (trimmed.equals(optionC.trim(), ignoreCase = true)) return 2
            if (trimmed.equals(optionD.trim(), ignoreCase = true)) return 3

            return 0
        }

    fun getOption(index: Int): String {
        return when (index) {
            0 -> optionA
            1 -> optionB
            2 -> optionC
            3 -> optionD
            else -> ""
        }
    }
}
