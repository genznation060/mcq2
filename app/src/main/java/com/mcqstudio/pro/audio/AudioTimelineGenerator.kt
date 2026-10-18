package com.mcqstudio.pro.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.mcqstudio.pro.model.AudioExportConfig
import com.mcqstudio.pro.model.MCQItem
import com.mcqstudio.pro.model.TimingConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

enum class SFXType {
    WHOOSH,
    TICK,
    CHIME
}

object AudioTimelineGenerator {

    const val SAMPLE_RATE = 44100
    const val CHANNELS = 2
    const val BITS_PER_SAMPLE = 16

    /**
     * Synthesizes the complete offline audio timeline for the video and writes it to a 16-bit PCM WAV file.
     */
    suspend fun generateWavTimeline(
        outputWavFile: File,
        items: List<MCQItem>,
        timing: TimingConfig,
        audioConfig: AudioExportConfig
    ): File = withContext(Dispatchers.IO) {
        val totalSec = timing.totalVideoDuration(items.size)
        val singleQSec = timing.singleQuestionDuration(items.size)
        val totalSamples = (totalSec * SAMPLE_RATE).toInt()

        // Allocate audio mix buffer (float for smooth mixing and saturation prevention)
        // Mixing in chunks or in-memory if reasonable
        val leftChannel = FloatArray(totalSamples)
        val rightChannel = FloatArray(totalSamples)

        val tickSfx = generateTickSample(audioConfig.tickVolume)
        val whooshSfx = generateWhooshSample(audioConfig.whooshVolume)
        val chimeSfx = generateChimeSample(audioConfig.chimeVolume)

        for (qIndex in items.indices) {
            val qStartTime = qIndex * singleQSec

            // 1. Whoosh at start of each question slide transition
            mixSample(
                leftChannel,
                rightChannel,
                whooshSfx,
                (qStartTime * SAMPLE_RATE).toInt()
            )

            // 2. Ticks during countdown phase
            if (audioConfig.ticksEnabled) {
                val countdownStart = qStartTime + timing.questionReadingSec
                val countdownDuration = timing.timerCountdownSec
                val countdownSeconds = countdownDuration.toInt()

                for (s in 0 until countdownSeconds) {
                    val tickTime = countdownStart + s
                    val sampleIdx = (tickTime * SAMPLE_RATE).toInt()
                    mixSample(leftChannel, rightChannel, tickSfx, sampleIdx)
                }
            }

            // 3. Chime at Answer Reveal instant
            val revealTime = qStartTime + timing.questionReadingSec + timing.timerCountdownSec
            val chimeSampleIdx = (revealTime * SAMPLE_RATE).toInt()
            mixSample(leftChannel, rightChannel, chimeSfx, chimeSampleIdx)
        }

        // Write WAV file with standard 44-byte RIFF header
        writeWavFile(outputWavFile, leftChannel, rightChannel, totalSamples)

        outputWavFile
    }

    /**
     * Generates a single crisp tick waveform (woodblock / click).
     */
    fun generateTickSample(volume: Float): FloatArray {
        val durationSec = 0.08f
        val samples = (durationSec * SAMPLE_RATE).toInt()
        val buffer = FloatArray(samples)

        for (i in 0 until samples) {
            val t = i.toFloat() / SAMPLE_RATE
            val envelope = exp(-35f * t)
            // Crisp fundamental + upper snap harmonic
            val wave = 0.7f * sin(2.0 * PI * 1100.0 * t).toFloat() +
                    0.3f * sin(2.0 * PI * 2200.0 * t).toFloat()
            buffer[i] = wave * envelope * volume
        }
        return buffer
    }

    /**
     * Generates a smooth aerodynamic whoosh waveform for question transitions.
     */
    fun generateWhooshSample(volume: Float): FloatArray {
        val durationSec = 0.45f
        val samples = (durationSec * SAMPLE_RATE).toInt()
        val buffer = FloatArray(samples)

        var noiseState = 0x12345678
        for (i in 0 until samples) {
            val t = i.toFloat() / SAMPLE_RATE
            val progress = t / durationSec

            // Bell-shaped envelope peaking around 40% of duration
            val envelope = sin(progress * PI.toFloat()).let { it * it }

            // Pitch bend from low to mid to low
            val freq = 120.0 + 450.0 * sin(progress * PI)
            val tone = sin(2.0 * PI * freq * t).toFloat()

            // Subtle colored noise for air texture
            noiseState = noiseState * 1664525 + 1013904223
            val white = ((noiseState shr 16) and 0xFFFF) / 32768f - 1f

            buffer[i] = (tone * 0.75f + white * 0.25f) * envelope * volume
        }
        return buffer
    }

    /**
     * Generates a harmonic triumphant chime chord for correct answer reveal.
     */
    fun generateChimeSample(volume: Float): FloatArray {
        val durationSec = 1.4f
        val samples = (durationSec * SAMPLE_RATE).toInt()
        val buffer = FloatArray(samples)

        // Musical frequencies: C5 (523.25Hz), E5 (659.25Hz), G5 (783.99Hz), C6 (1046.5Hz)
        val freqs = doubleArrayOf(523.25, 659.25, 783.99, 1046.5)
        val weights = floatArrayOf(0.35f, 0.25f, 0.25f, 0.15f)

        for (i in 0 until samples) {
            val t = i.toFloat() / SAMPLE_RATE
            val env = exp(-2.8f * t) // lingering bell decay
            var sum = 0f
            for (f in freqs.indices) {
                sum += (sin(2.0 * PI * freqs[f] * t).toFloat() * weights[f])
            }
            buffer[i] = sum * env * volume
        }
        return buffer
    }

    private fun mixSample(
        leftDst: FloatArray,
        rightDst: FloatArray,
        src: FloatArray,
        startIdx: Int
    ) {
        val len = src.size
        for (i in 0 until len) {
            val idx = startIdx + i
            if (idx in leftDst.indices) {
                leftDst[idx] = (leftDst[idx] + src[i]).coerceIn(-1.0f, 1.0f)
                rightDst[idx] = (rightDst[idx] + src[i]).coerceIn(-1.0f, 1.0f)
            }
        }
    }

    private fun writeWavFile(
        file: File,
        left: FloatArray,
        right: FloatArray,
        totalSamples: Int
    ) {
        if (file.exists()) file.delete()
        file.parentFile?.mkdirs()

        val dataSize = totalSamples * CHANNELS * (BITS_PER_SAMPLE / 8)
        val totalSize = 36 + dataSize

        FileOutputStream(file).use { out ->
            val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
            // RIFF header
            header.put("RIFF".toByteArray())
            header.putInt(totalSize)
            header.put("WAVE".toByteArray())

            // fmt subchunk
            header.put("fmt ".toByteArray())
            header.putInt(16) // Subchunk1Size for PCM
            header.putShort(1.toShort()) // AudioFormat 1 = PCM
            header.putShort(CHANNELS.toShort())
            header.putInt(SAMPLE_RATE)
            header.putInt(SAMPLE_RATE * CHANNELS * (BITS_PER_SAMPLE / 8)) // ByteRate
            header.putShort((CHANNELS * (BITS_PER_SAMPLE / 8)).toShort()) // BlockAlign
            header.putShort(BITS_PER_SAMPLE.toShort())

            // data subchunk
            header.put("data".toByteArray())
            header.putInt(dataSize)

            out.write(header.array())

            // Stream PCM samples in 4KB chunks
            val chunkSize = 2048
            val pcmBytes = ByteArray(chunkSize * CHANNELS * 2)
            var offset = 0

            while (offset < totalSamples) {
                val samplesToWrite = minOf(chunkSize, totalSamples - offset)
                var byteIdx = 0
                for (s in 0 until samplesToWrite) {
                    val currentIdx = offset + s
                    val leftShort = (left[currentIdx].coerceIn(-1f, 1f) * 32767f).toInt().toShort()
                    val rightShort = (right[currentIdx].coerceIn(-1f, 1f) * 32767f).toInt().toShort()

                    // Left channel (Little-Endian)
                    pcmBytes[byteIdx++] = (leftShort.toInt() and 0xFF).toByte()
                    pcmBytes[byteIdx++] = ((leftShort.toInt() shr 8) and 0xFF).toByte()

                    // Right channel (Little-Endian)
                    pcmBytes[byteIdx++] = (rightShort.toInt() and 0xFF).toByte()
                    pcmBytes[byteIdx++] = ((rightShort.toInt() shr 8) and 0xFF).toByte()
                }
                out.write(pcmBytes, 0, samplesToWrite * CHANNELS * 2)
                offset += samplesToWrite
            }
        }
    }

    /**
     * Plays an in-memory preview of the specified SFX for the user directly in the UI.
     */
    fun playPreviewSFX(type: SFXType, volume: Float = 0.9f) {
        val rawFloats = when (type) {
            SFXType.WHOOSH -> generateWhooshSample(volume)
            SFXType.TICK -> generateTickSample(volume)
            SFXType.CHIME -> generateChimeSample(volume)
        }

        val shortBuf = ShortArray(rawFloats.size)
        for (i in rawFloats.indices) {
            shortBuf[i] = (rawFloats[i].coerceIn(-1f, 1f) * 32767f).toInt().toShort()
        }

        val minBufSize = AudioTrack.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        try {
            val audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(maxOf(shortBuf.size * 2, minBufSize))
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()

            audioTrack.write(shortBuf, 0, shortBuf.size)
            audioTrack.play()
        } catch (_: Exception) {
            // Safe fallback if audio device unavailable
        }
    }
}
