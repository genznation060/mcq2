package com.mcqstudio.pro

import com.mcqstudio.pro.model.ColumnMapping
import com.mcqstudio.pro.model.MCQProjectConfig
import com.mcqstudio.pro.model.TimingMode
import com.mcqstudio.pro.parser.CSVQuizParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CSVQuizParserTest {

    @Test
    fun parseSampleCsv_loadsQuestionsCorrectly() {
        val sampleCsv = CSVQuizParser.getSampleCsv()
        val items = CSVQuizParser.parse(sampleCsv, ColumnMapping())

        assertEquals(5, items.size)
        assertTrue(items[0].question.contains("mitochondria", ignoreCase = true))
        assertEquals(1, items[0].correctIndex) // B is index 1
        assertEquals("ATP energy generation", items[0].optionB)
    }

    @Test
    fun timingCalculations_perQuestionMode() {
        val config = MCQProjectConfig()
        val singleQ = config.timing.singleQuestionDuration(5)
        // 3.0 + 5.0 + 2.5 + 3.5 = 14.0s
        assertEquals(14.0f, singleQ, 0.01f)

        val total = config.timing.totalVideoDuration(5)
        assertEquals(70.0f, total, 0.01f)
    }

    @Test
    fun timingCalculations_totalDurationMode() {
        var config = MCQProjectConfig()
        config = config.copy(
            timing = config.timing.copy(
                mode = TimingMode.TOTAL_DURATION,
                totalQuizMinutes = 2.0f // 120s
            )
        )
        val singleQ = config.timing.singleQuestionDuration(4)
        assertEquals(30.0f, singleQ, 0.01f)
        assertEquals(120.0f, config.timing.totalVideoDuration(4), 0.01f)
    }
}
