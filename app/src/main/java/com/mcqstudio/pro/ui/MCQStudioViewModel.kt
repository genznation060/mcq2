package com.mcqstudio.pro.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mcqstudio.pro.audio.AudioTimelineGenerator
import com.mcqstudio.pro.audio.SFXType
import com.mcqstudio.pro.engine.ExportState
import com.mcqstudio.pro.engine.VideoExportManager
import com.mcqstudio.pro.model.*
import com.mcqstudio.pro.parser.CSVQuizParser
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MCQStudioViewModel(application: Application) : AndroidViewModel(application) {

    private val exportManager = VideoExportManager(application)
    val exportState: StateFlow<ExportState> = exportManager.exportState

    private val _items = MutableStateFlow<List<MCQItem>>(CSVQuizParser.getSampleItems())
    val items: StateFlow<List<MCQItem>> = _items.asStateFlow()

    private val _config = MutableStateFlow(MCQProjectConfig())
    val config: StateFlow<MCQProjectConfig> = _config.asStateFlow()

    private val _currentTimeSec = MutableStateFlow(0f)
    val currentTimeSec: StateFlow<Float> = _currentTimeSec.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private var playbackJob: Job? = null

    val totalDurationSec: Float
        get() = _config.value.timing.totalVideoDuration(_items.value.size)

    val currentQuestionIndex: Int
        get() {
            val total = _items.value.size
            if (total == 0) return 0
            val singleQ = _config.value.timing.singleQuestionDuration(total)
            return (_currentTimeSec.value / singleQ).toInt().coerceIn(0, total - 1)
        }

    fun seekTo(timeSec: Float) {
        val total = totalDurationSec.coerceAtLeast(0.1f)
        _currentTimeSec.value = timeSec.coerceIn(0f, total)
    }

    fun togglePlayPause() {
        if (_isPlaying.value) {
            pause()
        } else {
            play()
        }
    }

    private fun play() {
        _isPlaying.value = true
        playbackJob?.cancel()
        playbackJob = viewModelScope.launch {
            val fps = 30
            val intervalMs = (1000L / fps)
            val dt = 1f / fps

            while (_isPlaying.value) {
                delay(intervalMs)
                val total = totalDurationSec
                if (total <= 0f) break

                var nextTime = _currentTimeSec.value + dt
                if (nextTime >= total) {
                    nextTime = 0f // Loop preview
                }
                _currentTimeSec.value = nextTime
            }
        }
    }

    fun pause() {
        _isPlaying.value = false
        playbackJob?.cancel()
        playbackJob = null
    }

    fun jumpToQuestion(index: Int) {
        val total = _items.value.size
        if (total == 0) return
        val targetIdx = index.coerceIn(0, total - 1)
        val singleQ = _config.value.timing.singleQuestionDuration(total)
        seekTo(targetIdx * singleQ)
    }

    fun nextQuestion() {
        jumpToQuestion(currentQuestionIndex + 1)
    }

    fun previousQuestion() {
        jumpToQuestion(currentQuestionIndex - 1)
    }

    fun loadSampleCsv() {
        val sampleItems = CSVQuizParser.getSampleItems()
        _items.value = sampleItems
        _currentTimeSec.value = 0f
    }

    fun parseCsvText(rawText: String) {
        val parsed = CSVQuizParser.parse(rawText, _config.value.columnMapping)
        if (parsed.isNotEmpty()) {
            _items.value = parsed
            _currentTimeSec.value = 0f
        }
    }

    fun updateConfig(reducer: (MCQProjectConfig) -> MCQProjectConfig) {
        _config.value = reducer(_config.value)
    }

    fun playTestSFX(type: SFXType) {
        viewModelScope.launch {
            AudioTimelineGenerator.playPreviewSFX(type)
        }
    }

    fun startExport() {
        pause()
        viewModelScope.launch {
            exportManager.startExport(_items.value, _config.value)
        }
    }

    fun resetExport() {
        exportManager.resetState()
    }

    override fun onCleared() {
        super.onCleared()
        pause()
    }
}
