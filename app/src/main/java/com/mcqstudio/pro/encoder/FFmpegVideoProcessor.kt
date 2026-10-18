package com.mcqstudio.pro.encoder

import android.graphics.Bitmap
import android.util.Log
import com.antonkarpenko.ffmpegkit.FFmpegKit
import com.antonkarpenko.ffmpegkit.ReturnCode
import com.mcqstudio.pro.engine.MCQCanvasRenderer
import com.mcqstudio.pro.model.MCQItem
import com.mcqstudio.pro.model.MCQProjectConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object FFmpegVideoProcessor {

    private const val TAG = "FFmpegProcessor"

    /**
     * Pass 2: Muxes silent video and synthesized WAV audio into the final MP4 using ultra-fast stream copy.
     * ffmpeg -y -i silent_video.mp4 -i audio.wav -c:v copy -c:a aac -b:a 192k -shortest final.mp4
     */
    suspend fun muxVideoAndAudio(
        silentVideoFile: File,
        audioWavFile: File,
        outputMuxedFile: File
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            if (outputMuxedFile.exists()) outputMuxedFile.delete()
            outputMuxedFile.parentFile?.mkdirs()

            val cmd = "-y -i \"${silentVideoFile.absolutePath}\" -i \"${audioWavFile.absolutePath}\" -c:v copy -c:a aac -b:a 192k -shortest \"${outputMuxedFile.absolutePath}\""
            Log.d(TAG, "Executing FFmpeg mux command: $cmd")

            val session = FFmpegKit.execute(cmd)
            val returnCode = session.returnCode

            if (ReturnCode.isSuccess(returnCode)) {
                Log.d(TAG, "FFmpeg mux successful: ${outputMuxedFile.absolutePath} (size=${outputMuxedFile.length()})")
                outputMuxedFile
            } else {
                val failLogs = session.allLogsAsString
                Log.e(TAG, "FFmpeg mux failed: $failLogs")
                throw RuntimeException("FFmpeg muxing failed with code $returnCode: $failLogs")
            }
        }
    }

    /**
     * Secondary Engine: Renders video via FFmpeg image sequence when user selects FFmpeg engine.
     */
    suspend fun encodeVideoWithFFmpeg(
        outputFile: File,
        items: List<MCQItem>,
        config: MCQProjectConfig,
        renderer: MCQCanvasRenderer,
        cacheDir: File,
        onProgress: (progress: Float, currentFrame: Int, totalFrames: Int) -> Unit
    ): File = withContext(Dispatchers.IO) {
        val width = config.audioExport.resolution.width
        val height = config.audioExport.resolution.height
        val fps = config.audioExport.fps
        val totalSec = config.timing.totalVideoDuration(items.size)
        val totalFrames = (totalSec * fps).toInt().coerceAtLeast(1)

        val framesDir = File(cacheDir, "ffmpeg_frames_${System.currentTimeMillis()}").apply { mkdirs() }
        val frameBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        try {
            // Render and save frames
            for (f in 0 until totalFrames) {
                val timeSec = f.toFloat() / fps
                renderer.renderFrame(frameBitmap, timeSec, items, config)

                val frameFile = File(framesDir, String.format("frame_%05d.jpg", f))
                FileOutputStream(frameFile).use { out ->
                    frameBitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
                }

                if (f % 5 == 0 || f == totalFrames - 1) {
                    onProgress((f + 1).toFloat() / totalFrames * 0.7f, f + 1, totalFrames)
                }
            }

            if (outputFile.exists()) outputFile.delete()
            outputFile.parentFile?.mkdirs()

            // Encode frames to silent MP4
            val cmd = "-y -framerate $fps -i \"${framesDir.absolutePath}/frame_%05d.jpg\" -c:v libx264 -pix_fmt yuv420p -b:v ${config.audioExport.videoBitrateBps} \"${outputFile.absolutePath}\""
            val session = FFmpegKit.execute(cmd)

            if (!ReturnCode.isSuccess(session.returnCode)) {
                throw RuntimeException("FFmpeg frame encode failed: ${session.allLogsAsString}")
            }

            onProgress(1.0f, totalFrames, totalFrames)
            outputFile
        } finally {
            frameBitmap.recycle()
            framesDir.deleteRecursively()
        }
    }
}
