package com.mcqstudio.pro.parser

import com.mcqstudio.pro.model.ColumnMapping
import com.mcqstudio.pro.model.MCQItem
import com.opencsv.CSVParserBuilder
import com.opencsv.CSVReaderBuilder
import java.io.StringReader

object CSVQuizParser {

    /**
     * Parses raw CSV/TSV text into a list of MCQItem objects based on column mappings.
     */
    fun parse(rawText: String, mapping: ColumnMapping): List<MCQItem> {
        val trimmed = rawText.trim()
        if (trimmed.isEmpty()) return emptyList()

        // Auto-detect delimiter: check count of tabs vs semicolons vs commas
        val firstFewLines = trimmed.lines().take(5).joinToString("\n")
        val tabCount = firstFewLines.count { it == '\t' }
        val semiCount = firstFewLines.count { it == ';' }
        val commaCount = firstFewLines.count { it == ',' }

        val delimiter = when {
            tabCount > commaCount && tabCount > semiCount -> '\t'
            semiCount > commaCount && semiCount > tabCount -> ';'
            else -> ','
        }

        val parser = CSVParserBuilder()
            .withSeparator(delimiter)
            .withIgnoreQuotations(false)
            .build()

        val reader = CSVReaderBuilder(StringReader(trimmed))
            .withCSVParser(parser)
            .build()

        val rows = reader.readAll()
        if (rows.isEmpty()) return emptyList()

        val items = mutableListOf<MCQItem>()

        // Check if first row is a header
        val firstRow = rows[0]
        val isHeader = firstRow.any { cell ->
            val lower = cell.lowercase().trim()
            lower.contains("question") || lower.contains("option") || lower.contains("ans") || lower.contains("explanation")
        }

        val dataRows = if (isHeader && rows.size > 1) rows.drop(1) else rows

        for (row in dataRows) {
            if (row.isEmpty() || row.all { it.isBlank() }) continue

            fun getCol(index: Int): String {
                return if (index >= 0 && index < row.size) row[index].trim() else ""
            }

            val question = getCol(mapping.questionCol)
            if (question.isBlank()) continue

            val optA = getCol(mapping.optionACol)
            val optB = getCol(mapping.optionBCol)
            val optC = getCol(mapping.optionCCol)
            val optD = getCol(mapping.optionDCol)
            val ans = getCol(mapping.answerCol)
            val exp = getCol(mapping.explanationCol)
            val img = getCol(mapping.imageCol)

            items.add(
                MCQItem(
                    question = question,
                    optionA = if (optA.isNotBlank()) optA else "Option A",
                    optionB = if (optB.isNotBlank()) optB else "Option B",
                    optionC = if (optC.isNotBlank()) optC else "Option C",
                    optionD = if (optD.isNotBlank()) optD else "Option D",
                    answer = if (ans.isNotBlank()) ans else "A",
                    explanation = exp,
                    imageUriOrPath = if (img.isNotBlank()) img else null
                )
            )
        }

        return items
    }

    /**
     * Generates a sample CSV dataset with 5 high quality science & tech quiz questions.
     */
    fun getSampleCsv(): String {
        return """Question,Option A,Option B,Option C,Option D,Answer,Explanation
What is the primary function of the mitochondria?,Protein synthesis,ATP energy generation,DNA replication,Lipid transport,B,Mitochondria generate most of the chemical energy needed to power biochemical reactions via ATP.
Which planet in our solar system has the most moons?,Mars,Jupiter,Saturn,Neptune,C,Saturn has 146 confirmed moons surpassing Jupiter with 95 moons.
In computer science what does GPU stand for?,General Processing Utility,Graphics Processing Unit,Global Protocol Unifier,Grid Parallel Unit,B,GPU stands for Graphics Processing Unit specialized for parallel processing and compute.
What is the speed of light in vacuum approximately?,300000 km/s,150000 km/s,450000 km/s,1000000 km/s,A,The exact speed of light in a vacuum is 299,792,458 meters per second or ~300,000 km/s.
Who is known as the father of modern computer science?,Nikola Tesla,Alan Turing,Thomas Edison,Charles Babbage,B,Alan Turing played a pivotal role in cracking intercepted codes and foundational computation theory."""
    }

    /**
     * Preloaded sample items for immediate preview upon app launch.
     */
    fun getSampleItems(): List<MCQItem> {
        return parse(getSampleCsv(), ColumnMapping())
    }
}
