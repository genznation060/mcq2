package com.mcqstudio.pro.encoder

import android.graphics.Bitmap
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.opengl.*
import android.util.Log
import android.view.Surface
import com.mcqstudio.pro.engine.MCQCanvasRenderer
import com.mcqstudio.pro.model.MCQItem
import com.mcqstudio.pro.model.MCQProjectConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/**
 * High-speed hardware video encoder using Android MediaCodec + EGL + MediaMuxer.
 * Renders Canvas frames directly onto an EGL hardware surface at 5-10× realtime speed.
 */
class MediaCodecVideoEncoder {

    companion object {
        private const val TAG = "MediaCodecEncoder"
        private const val MIME_TYPE = MediaFormat.MIMETYPE_VIDEO_AVC // H.264
        private const val TIMEOUT_USEC = 10_000L
    }

    suspend fun encodeVideo(
        outputFile: File,
        items: List<MCQItem>,
        config: MCQProjectConfig,
        renderer: MCQCanvasRenderer,
        onProgress: (progress: Float, currentFrame: Int, totalFrames: Int) -> Unit
    ): File = withContext(Dispatchers.Default) {
        val width = config.audioExport.resolution.width
        val height = config.audioExport.resolution.height
        val fps = config.audioExport.fps
        val bitrate = config.audioExport.videoBitrateBps
        val totalSec = config.timing.totalVideoDuration(items.size)
        val totalFrames = (totalSec * fps).toInt().coerceAtLeast(1)

        if (outputFile.exists()) outputFile.delete()
        outputFile.parentFile?.mkdirs()

        // Configure MediaFormat
        val format = MediaFormat.createVideoFormat(MIME_TYPE, width, height).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
            setInteger(MediaFormat.KEY_FRAME_RATE, fps)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }

        val codec = MediaCodec.createEncoderByType(MIME_TYPE)
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        val inputSurface = codec.createInputSurface()
        codec.start()

        val muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var trackIndex = -1
        var muxerStarted = false

        val eglCore = CodecInputSurface(inputSurface, width, height)
        eglCore.makeCurrent()

        // Allocate reusable bitmap for Canvas renderer
        val frameBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val bufferInfo = MediaCodec.BufferInfo()

        try {
            for (frameIdx in 0 until totalFrames) {
                val timeSec = frameIdx.toFloat() / fps

                // Render frame onto bitmap using native Android Canvas + Paint
                renderer.renderFrame(frameBitmap, timeSec, items, config)

                // Draw bitmap to EGL surface
                eglCore.drawBitmap(frameBitmap)

                // Set exact presentation timestamp in nanoseconds
                val ptsNs = frameIdx * 1_000_000_000L / fps
                eglCore.setPresentationTime(ptsNs)
                eglCore.swapBuffers()

                // Drain encoder output
                drainEncoder(codec, muxer, bufferInfo, false) { newTrack ->
                    trackIndex = newTrack
                    muxerStarted = true
                }

                // Report progress every 3 frames
                if (frameIdx % 3 == 0 || frameIdx == totalFrames - 1) {
                    val p = (frameIdx + 1).toFloat() / totalFrames
                    onProgress(p, frameIdx + 1, totalFrames)
                }
            }

            // Signal End-of-Stream to encoder
            codec.signalEndOfInputStream()
            drainEncoder(codec, muxer, bufferInfo, true) { newTrack ->
                trackIndex = newTrack
                muxerStarted = true
            }

        } finally {
            try {
                codec.stop()
            } catch (e: Exception) {
                Log.w(TAG, "Codec stop exception: ${e.message}")
            }
            try {
                codec.release()
            } catch (e: Exception) {
                Log.w(TAG, "Codec release exception: ${e.message}")
            }
            try {
                if (muxerStarted) {
                    muxer.stop()
                }
                muxer.release()
            } catch (e: Exception) {
                Log.w(TAG, "Muxer release exception: ${e.message}")
            }
            eglCore.release()
            frameBitmap.recycle()
        }

        outputFile
    }

    private fun drainEncoder(
        codec: MediaCodec,
        muxer: MediaMuxer,
        bufferInfo: MediaCodec.BufferInfo,
        endOfStream: Boolean,
        onMuxerStart: (trackIndex: Int) -> Unit
    ) {
        var trackIndex = -1
        while (true) {
            val encoderStatus = codec.dequeueOutputBuffer(bufferInfo, TIMEOUT_USEC)
            if (encoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
                if (!endOfStream) break
            } else if (encoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                val newFormat = codec.outputFormat
                trackIndex = muxer.addTrack(newFormat)
                muxer.start()
                onMuxerStart(trackIndex)
            } else if (encoderStatus >= 0) {
                val encodedData = codec.getOutputBuffer(encoderStatus)
                    ?: throw RuntimeException("Encoder outputBuffer $encoderStatus was null")

                if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                    bufferInfo.size = 0
                }

                if (bufferInfo.size != 0) {
                    encodedData.position(bufferInfo.offset)
                    encodedData.limit(bufferInfo.offset + bufferInfo.size)
                    muxer.writeSampleData(trackIndex.coerceAtLeast(0), encodedData, bufferInfo)
                }

                codec.releaseOutputBuffer(encoderStatus, false)

                if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    break
                }
            }
        }
    }

    /**
     * Helper to wrap EGL14 context, EGLSurface, and simple texture quad renderer.
     */
    private class CodecInputSurface(
        private val surface: Surface,
        private val width: Int,
        private val height: Int
    ) {
        private var eglDisplay: EGLDisplay = EGL14.EGL_NO_DISPLAY
        private var eglContext: EGLContext = EGL14.EGL_NO_CONTEXT
        private var eglSurface: EGLSurface = EGL14.EGL_NO_SURFACE

        private var programId = 0
        private var textureId = 0
        private var aPositionLoc = 0
        private var aTexCoordLoc = 0
        private var uTextureLoc = 0

        private val vertexBuffer: FloatBuffer
        private val texCoordBuffer: FloatBuffer

        init {
            // Fullscreen quad coordinates
            val vertices = floatArrayOf(
                -1f, -1f,
                 1f, -1f,
                -1f,  1f,
                 1f,  1f
            )
            // Invert Y for OpenGL coordinate system
            val texCoords = floatArrayOf(
                0f, 1f,
                1f, 1f,
                0f, 0f,
                1f, 0f
            )

            vertexBuffer = ByteBuffer.allocateDirect(vertices.size * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
                .put(vertices)
            vertexBuffer.position(0)

            texCoordBuffer = ByteBuffer.allocateDirect(texCoords.size * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
                .put(texCoords)
            texCoordBuffer.position(0)

            initEGL()
            initGL()
        }

        private fun initEGL() {
            eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            if (eglDisplay == EGL14.EGL_NO_DISPLAY) {
                throw RuntimeException("unable to get EGL14 display")
            }

            val version = IntArray(2)
            if (!EGL14.eglInitialize(eglDisplay, version, 0, version, 1)) {
                throw RuntimeException("unable to initialize EGL14")
            }

            val attribList = intArrayOf(
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                0x3142, 1, // EGL_RECORDABLE_ANDROID
                EGL14.EGL_NONE
            )

            val configs = arrayOfNulls<EGLConfig>(1)
            val numConfigs = IntArray(1)
            if (!EGL14.eglChooseConfig(eglDisplay, attribList, 0, configs, 0, configs.size, numConfigs, 0) || numConfigs[0] == 0) {
                // Fallback without EGL_RECORDABLE_ANDROID
                val fallbackAttribs = intArrayOf(
                    EGL14.EGL_RED_SIZE, 8,
                    EGL14.EGL_GREEN_SIZE, 8,
                    EGL14.EGL_BLUE_SIZE, 8,
                    EGL14.EGL_ALPHA_SIZE, 8,
                    EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                    EGL14.EGL_NONE
                )
                EGL14.eglChooseConfig(eglDisplay, fallbackAttribs, 0, configs, 0, configs.size, numConfigs, 0)
            }

            val config = configs[0] ?: throw RuntimeException("Unable to find suitable EGLConfig")

            val contextAttribs = intArrayOf(
                EGL14.EGL_CONTEXT_CLIENT_VERSION, 2,
                EGL14.EGL_NONE
            )
            eglContext = EGL14.eglCreateContext(eglDisplay, config, EGL14.EGL_NO_CONTEXT, contextAttribs, 0)

            val surfaceAttribs = intArrayOf(EGL14.EGL_NONE)
            eglSurface = EGL14.eglCreateWindowSurface(eglDisplay, config, surface, surfaceAttribs, 0)
        }

        private fun initGL() {
            makeCurrent()

            val vertexShaderCode = """
                attribute vec4 aPosition;
                attribute vec2 aTexCoord;
                varying vec2 vTexCoord;
                void main() {
                    gl_Position = aPosition;
                    vTexCoord = aTexCoord;
                }
            """.trimIndent()

            val fragmentShaderCode = """
                precision mediump float;
                varying vec2 vTexCoord;
                uniform sampler2D uTexture;
                void main() {
                    gl_FragColor = texture2D(uTexture, vTexCoord);
                }
            """.trimIndent()

            val vShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexShaderCode)
            val fShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderCode)

            programId = GLES20.glCreateProgram()
            GLES20.glAttachShader(programId, vShader)
            GLES20.glAttachShader(programId, fShader)
            GLES20.glLinkProgram(programId)

            aPositionLoc = GLES20.glGetAttribLocation(programId, "aPosition")
            aTexCoordLoc = GLES20.glGetAttribLocation(programId, "aTexCoord")
            uTextureLoc = GLES20.glGetUniformLocation(programId, "uTexture")

            val textures = IntArray(1)
            GLES20.glGenTextures(1, textures, 0)
            textureId = textures[0]

            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)

            GLES20.glViewport(0, 0, width, height)
        }

        private fun loadShader(type: Int, code: String): Int {
            val shader = GLES20.glCreateShader(type)
            GLES20.glShaderSource(shader, code)
            GLES20.glCompileShader(shader)
            return shader
        }

        fun makeCurrent() {
            EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)
        }

        fun drawBitmap(bitmap: Bitmap) {
            makeCurrent()
            GLES20.glClearColor(0f, 0f, 0f, 1f)
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

            GLES20.glUseProgram(programId)

            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
            GLES20.glUniform1i(uTextureLoc, 0)

            GLES20.glEnableVertexAttribArray(aPositionLoc)
            GLES20.glVertexAttribPointer(aPositionLoc, 2, GLES20.GL_FLOAT, false, 0, vertexBuffer)

            GLES20.glEnableVertexAttribArray(aTexCoordLoc)
            GLES20.glVertexAttribPointer(aTexCoordLoc, 2, GLES20.GL_FLOAT, false, 0, texCoordBuffer)

            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

            GLES20.glDisableVertexAttribArray(aPositionLoc)
            GLES20.glDisableVertexAttribArray(aTexCoordLoc)
        }

        fun setPresentationTime(nsecs: Long) {
            EGLExt.eglPresentationTimeANDROID(eglDisplay, eglSurface, nsecs)
        }

        fun swapBuffers() {
            EGL14.eglSwapBuffers(eglDisplay, eglSurface)
        }

        fun release() {
            if (eglDisplay != EGL14.EGL_NO_DISPLAY) {
                EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
                if (eglSurface != EGL14.EGL_NO_SURFACE) {
                    EGL14.eglDestroySurface(eglDisplay, eglSurface)
                }
                if (eglContext != EGL14.EGL_NO_CONTEXT) {
                    EGL14.eglDestroyContext(eglDisplay, eglContext)
                }
                EGL14.eglTerminate(eglDisplay)
            }
            eglDisplay = EGL14.EGL_NO_DISPLAY
            eglContext = EGL14.EGL_NO_CONTEXT
            eglSurface = EGL14.EGL_NO_SURFACE
        }
    }
}
