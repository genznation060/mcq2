package com.mcqstudio.pro.engine

import android.content.Context
import android.net.Uri
import android.util.Log
import com.mcqstudio.pro.audio.AudioTimelineGenerator
import com.mcqstudio.pro.encoder.FFmpegVideoProcessor
import com.mcqstudio.pro.encoder.MediaCodecVideoEncoder
import com.mcqstudio.pro.model.MCQItem
import com.mcqstudio.pro.model.MCQProjectConfig
import com.mcqstudio.pro.model.RenderEngine
import com.mcqstudio.pro.storage.MediaStoreSaver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File

sealed class ExportState {
    object Idle : ExportState()
    data class Exporting(
        val stage: String,
        val progress: Float, // 0.0 to 1.0
        val currentPass: Int,
        val currentFrame: Int = 0,
        val totalFrames: Int = 0
    ) : ExportState()
    data class Success(val galleryUri: Uri, val localFile: File) : ExportState()
    data class Error(val message: String) : ExportState()
}

class VideoExportManager(private val context: Context) {

    companion object {
        private const val TAG = "VideoExportManager"
    }

    private val _exportState = MutableStateFlow<ExportState>(ExportState.Idle)
    val exportState: StateFlow<ExportState> = _exportState.asStateFlow()

    private val mediaCodecEncoder = MediaCodecVideoEncoder()
    private val renderer = MCQCanvasRenderer(context)

    suspend fun startExport(
        items: List<MCQItem>,
        config: MCQProjectConfig
    ): Result<Uri> = withContext(Dispatchers.Default) {
        if (items.isEmpty()) {
            val err = "Cannot export video: Quiz has no questions."
            _exportState.value = ExportState.Error(err)
            return@withContext Result.failure(IllegalArgumentException(err))
        }

        val cacheDir = context.cacheDir
        val timestamp = System.currentTimeMillis()
        val silentVideoFile = File(cacheDir, "mcq_silent_$timestamp.mp4")
        val audioWavFile = File(cacheDir, "mcq_audio_$timestamp.wav")
        val muxedVideoFile = File(cacheDir, "mcq_muxed_$timestamp.mp4")

        try {
            // ============================================================
            // PASS 1: Video Frames Offscreen Render
            // ============================================================
            _exportState.value = ExportState.Exporting(
                stage = "Pass 1/2: Rendering Video Frames (${config.audioExport.renderEngine.title})",
                progress = 0.05f,
                currentPass = 1
            )

            if (config.audioExport.renderEngine == RenderEngine.MEDIA_CODEC) {
                mediaCodecEncoder.encodeVideo(
                    outputFile = silentVideoFile,
                    items = items,
                    config = config,
                    renderer = renderer,
                    onProgress = { p, cur, total ->
                        _exportState.value = ExportState.Exporting(
                            stage = "Pass 1/2: Hardware Encoding ($cur / $total frames)",
                            progress = p * 0.65f, // Pass 1 takes 0% -> 65%
                            currentPass = 1,
                            currentFrame = cur,
                            totalFrames = total
                        )
                    }
                )
            } else {
                FFmpegVideoProcessor.encodeVideoWithFFmpeg(
                    outputFile = silentVideoFile,
                    items = items,
                    config = config,
                    renderer = renderer,
                    cacheDir = cacheDir,
                    onProgress = { p, cur, total ->
                        _exportState.value = ExportState.Exporting(
                            stage = "Pass 1/2: FFmpeg Software Encoding ($cur / $total frames)",
                            progress = p * 0.65f,
                            currentPass = 1,
                            currentFrame = cur,
                            totalFrames = total
                        )
                    }
                )
            }

            // ============================================================
            // PASS 2: Offline Audio Timeline Synthesis (16-bit PCM 44.1kHz)
            // ============================================================
            _exportState.value = ExportState.Exporting(
                stage = "Pass 2/2: Synthesizing Offline Audio Timeline (WAV)",
                progress = 0.70f,
                currentPass = 2
            )

            AudioTimelineGenerator.generateWavTimeline(
                outputWavFile = audioWavFile,
                items = items,
                timing = config.timing,
                audioConfig = config.audioExport
            )

            // ============================================================
            // PASS 2 MUX: FFmpeg Stream-Copy
            // ffmpeg -y -i silent.mp4 -i audio.wav -c:v copy -c:a aac -shortest final.mp4
            // ============================================================
            _exportState.value = ExportState.Exporting(
                stage = "Muxing Video & Audio (FFmpeg Stream Copy)",
                progress = 0.85f,
                currentPass = 2
            )

            val muxResult = FFmpegVideoProcessor.muxVideoAndAudio(
                silentVideoFile = silentVideoFile,
                audioWavFile = audioWavFile,
                outputMuxedFile = muxedVideoFile
            )

            val finalFile = muxResult.getOrThrow()

            // ============================================================
            // PUBLIC STORAGE: MediaStore API ("Movies/MCQ Studio")
            // ============================================================
            _exportState.value = ExportState.Exporting(
                stage = "Saving into Gallery (Movies/MCQ Studio)...",
                progress = 0.95f,
                currentPass = 2
            )

            val saveResult = MediaStoreSaver.saveVideoToGallery(
                context = context,
                sourceFile = finalFile,
                baseName = "MCQ_Studio_${config.audioExport.resolution.name}"
            )

            val galleryUri = saveResult.getOrThrow()
            _exportState.value = ExportState.Success(galleryUri, finalFile)

            // Clean up intermediate temp files
            silentVideoFile.delete()
            audioWavFile.delete()

            Log.i(TAG, "Export completed successfully! URI: $galleryUri")
            Result.success(galleryUri)

        } catch (e: Exception) {
            Log.e(TAG, "Export error: ${e.message}", e)
            _exportState.value = ExportState.Error(e.message ?: "Unknown export failure")
            Result.failure(e)
        }
    }

    fun resetState() {
        _exportState.value = ExportState.Idle
    }
}
