package com.mcqstudio.pro.engine

import android.content.Context
import android.graphics.*
import android.net.Uri
import android.os.Build
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.mcqstudio.pro.model.*
import java.io.InputStream
import kotlin.math.*

/**
 * High-performance native Android Canvas & Paint rendering engine.
 * Renders complete 16:9 MCQ video frames with dynamic layouts, auto-fitting text,
 * animated timers, option highlights, and explanation panels.
 */
class MCQCanvasRenderer(private val context: Context? = null) {

    // Reusable Paints to avoid GC churn during 30fps offline encoding
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val cardStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
    private val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val timerTrackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val timerProgressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val particlePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val imagePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    // Reusable geometric structures
    private val tempRectF = RectF()
    private val tempClipPath = Path()

    // Bitmap cache for question images
    private val imageCache = HashMap<String, Bitmap>()

    /**
     * Renders a single frame at the given timestamp onto the provided destination Bitmap.
     */
    fun renderFrame(
        destBitmap: Bitmap,
        timeSec: Float,
        items: List<MCQItem>,
        config: MCQProjectConfig
    ) {
        if (items.isEmpty()) return

        val canvas = Canvas(destBitmap)
        val width = destBitmap.width.toFloat()
        val height = destBitmap.height.toFloat()
        val scale = width / 1920f

        val totalItems = items.size
        val singleQSec = config.timing.singleQuestionDuration(totalItems)
        val totalSec = config.timing.totalVideoDuration(totalItems)

        // Determine current question index and local timeline progress
        val qIndex = (timeSec / singleQSec).toInt().coerceIn(0, totalItems - 1)
        val localTime = (timeSec - qIndex * singleQSec).coerceAtLeast(0f)
        val item = items[qIndex]

        // Phase calculations
        val tReading = config.timing.questionReadingSec
        val tCountdown = config.timing.timerCountdownSec
        val tReveal = config.timing.answerRevealSec
        val tTotalSingle = singleQSec

        val isReadingPhase = localTime < tReading
        val isCountdownPhase = localTime >= tReading && localTime < (tReading + tCountdown)
        val isRevealPhase = localTime >= (tReading + tCountdown)
        val isExplanationPhase = localTime >= (tReading + tCountdown + tReveal)

        val countdownProgress = when {
            isReadingPhase -> 1.0f
            isCountdownPhase -> 1.0f - ((localTime - tReading) / tCountdown).coerceIn(0f, 1f)
            else -> 0.0f
        }

        // 1. Draw Background
        drawBackground(canvas, width, height, timeSec, config, scale)

        // 2. Draw Moving Ambient Particles / Grid Dots
        if (config.branding.showMovingParticles) {
            drawAmbientParticles(canvas, width, height, timeSec, scale, config.theme.accentColor)
        }

        // 3. Draw Watermark & Channel Branding
        drawWatermark(canvas, width, height, config.branding, scale)

        // 4. Draw Question Header & Badge
        drawQuestionBadge(canvas, qIndex + 1, totalItems, config, scale)

        // 5. Draw Question Box & Optional Question Image
        val contentTop = 130f * scale
        val hasImage = !item.imageUriOrPath.isNullOrBlank()
        val questionAreaBottom = drawQuestionZone(canvas, item, hasImage, contentTop, width, height, config, scale)

        // 6. Draw Timer (Circular, Bar, or Digital)
        drawTimer(canvas, countdownProgress, isCountdownPhase, isRevealPhase, width, height, config, scale)

        // 7. Draw Options (A, B, C, D)
        val optionsTop = max(questionAreaBottom + 30f * scale, height * 0.46f)
        drawOptions(canvas, item, optionsTop, width, height, isRevealPhase, localTime - (tReading + tCountdown), config, scale)

        // 8. Draw Explanation Panel (if in explanation phase and explanation present)
        if (isExplanationPhase && item.explanation.isNotBlank()) {
            val expProgress = ((localTime - (tReading + tCountdown + tReveal)) / config.timing.explanationSec).coerceIn(0f, 1f)
            drawExplanationPanel(canvas, item.explanation, expProgress, width, height, config, scale)
        }

        // 9. Overall Video Progress Bar at the very bottom
        if (config.branding.showProgressBar) {
            val overallProgress = (timeSec / totalSec).coerceIn(0f, 1f)
            drawBottomProgressBar(canvas, overallProgress, totalItems, width, height, config, scale)
        }
    }

    private fun drawBackground(
        canvas: Canvas,
        width: Float,
        height: Float,
        timeSec: Float,
        config: MCQProjectConfig,
        scale: Float
    ) {
        val (c1, c2, c3) = when (config.theme.preset) {
            ThemePreset.NEON_CYBER -> Triple(0xFF090A14.toInt(), 0xFF140D2B.toInt(), 0xFF1E0E3D.toInt())
            ThemePreset.DEEP_NAVY -> Triple(0xFF050E1F.toInt(), 0xFF0A1B38.toInt(), 0xFF0E2854.toInt())
            ThemePreset.ROYAL_PURPLE -> Triple(0xFF0F0721.toInt(), 0xFF1D0B3D.toInt(), 0xFF2B0F59.toInt())
            ThemePreset.SLATE_DARK -> Triple(0xFF0D0F12.toInt(), 0xFF151920.toInt(), 0xFF1E242E.toInt())
            ThemePreset.CRIMSON_EMBER -> Triple(0xFF140509.toInt(), 0xFF260A12.toInt(), 0xFF3D0E1C.toInt())
        }

        // High quality diagonal linear gradient
        val shader = LinearGradient(
            0f, 0f, width, height,
            intArrayOf(c1, c2, c3),
            floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP
        )
        bgPaint.shader = shader
        canvas.drawRect(0f, 0f, width, height, bgPaint)
        bgPaint.shader = null

        // Soft ambient glowing light orb in top right
        val orbRadius = 400f * scale
        val orbX = width * 0.85f + sin(timeSec * 0.5f) * 40f * scale
        val orbY = height * 0.2f + cos(timeSec * 0.5f) * 30f * scale
        val accent = (config.theme.accentColor.toInt() and 0x00FFFFFF) or 0x22000000
        val orbShader = RadialGradient(
            orbX, orbY, orbRadius,
            intArrayOf(accent, 0x00000000),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP
        )
        glowPaint.shader = orbShader
        canvas.drawCircle(orbX, orbY, orbRadius, glowPaint)
        glowPaint.shader = null
    }

    private fun drawAmbientParticles(
        canvas: Canvas,
        width: Float,
        height: Float,
        timeSec: Float,
        scale: Float,
        accentColor: Long
    ) {
        particlePaint.color = (accentColor.toInt() and 0x00FFFFFF) or 0x33000000
        particlePaint.style = Paint.Style.FILL

        val count = 28
        for (i in 0 until count) {
            val seed = i * 137.5f
            val px = ((seed * 17f + timeSec * 18f) % width)
            val py = ((seed * 29f + sin(timeSec * 0.7f + i) * 35f) % height)
            val radius = (1.5f + (i % 3) * 1.2f) * scale
            canvas.drawCircle(px, py, radius, particlePaint)
        }
    }

    private fun drawWatermark(
        canvas: Canvas,
        width: Float,
        height: Float,
        branding: BrandingConfig,
        scale: Float
    ) {
        val text = branding.watermarkText.trim()
        if (text.isEmpty()) return

        val alpha = (branding.watermarkOpacity.coerceIn(0.1f, 1f) * 255).toInt()
        textPaint.color = Color.WHITE
        textPaint.alpha = alpha
        textPaint.textSize = 24f * scale
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)

        val textWidth = textPaint.measureText(text)
        val rightX = width - 60f * scale
        val topY = 46f * scale

        // Draw pill background for watermark
        val padX = 18f * scale
        val padY = 8f * scale
        tempRectF.set(rightX - textWidth - padX, topY - padY - 14f * scale, rightX + padX, topY + padY + 6f * scale)
        badgePaint.color = 0x55000000.toInt()
        canvas.drawRoundRect(tempRectF, 16f * scale, 16f * scale, badgePaint)

        // Accent border
        cardStrokePaint.color = 0x44FFFFFF.toInt()
        cardStrokePaint.strokeWidth = 1.5f * scale
        canvas.drawRoundRect(tempRectF, 16f * scale, 16f * scale, cardStrokePaint)

        canvas.drawText(text, rightX - textWidth, topY, textPaint)
    }

    private fun drawQuestionBadge(
        canvas: Canvas,
        currentNum: Int,
        totalItems: Int,
        config: MCQProjectConfig,
        scale: Float
    ) {
        val left = 60f * scale
        val top = 36f * scale
        val badgeText = String.format("QUESTION %02d / %02d", currentNum, totalItems)

        textPaint.textSize = 22f * scale
        textPaint.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        val textWidth = textPaint.measureText(badgeText)

        val padX = 22f * scale
        val height = 40f * scale
        tempRectF.set(left, top, left + textWidth + padX * 2, top + height)

        when (config.elements.questionNumberStyle) {
            QuestionNumberStyle.BADGE_PILL -> {
                badgePaint.color = config.theme.accentColor.toInt()
                canvas.drawRoundRect(tempRectF, height / 2f, height / 2f, badgePaint)

                textPaint.color = Color.WHITE
                canvas.drawText(badgeText, left + padX, top + 27f * scale, textPaint)
            }
            QuestionNumberStyle.RIBBON -> {
                badgePaint.color = config.theme.secondaryColor.toInt()
                canvas.drawRoundRect(tempRectF, 8f * scale, 8f * scale, badgePaint)

                textPaint.color = Color.BLACK
                canvas.drawText(badgeText, left + padX, top + 27f * scale, textPaint)
            }
            QuestionNumberStyle.MINIMAL_TEXT -> {
                textPaint.color = config.theme.secondaryColor.toInt()
                canvas.drawText(badgeText, left, top + 28f * scale, textPaint)
            }
        }
    }

    private fun drawQuestionZone(
        canvas: Canvas,
        item: MCQItem,
        hasImage: Boolean,
        top: Float,
        width: Float,
        height: Float,
        config: MCQProjectConfig,
        scale: Float
    ): Float {
        val margin = 60f * scale
        val qWidth = if (hasImage) (width - margin * 2) * 0.58f else (width - margin * 2)
        val qHeight = 220f * scale

        // Question Card Box
        tempRectF.set(margin, top, margin + qWidth, top + qHeight)
        cardPaint.color = config.theme.cardBgColor.toInt()
        val cornerRad = 24f * scale
        canvas.drawRoundRect(tempRectF, cornerRad, cornerRad, cardPaint)

        // Accent left border
        cardStrokePaint.color = config.theme.accentColor.toInt()
        cardStrokePaint.strokeWidth = 3f * scale
        canvas.drawRoundRect(tempRectF, cornerRad, cornerRad, cardStrokePaint)

        // Draw Question Text (Auto-fitting)
        val textPad = 28f * scale
        val availableTextWidth = (qWidth - textPad * 2).toInt().coerceAtLeast(100)
        val availableTextHeight = qHeight - textPad * 2

        textPaint.color = Color.WHITE
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)

        // Auto-scale font size
        var fontSize = 42f * scale
        var layout: StaticLayout
        while (fontSize > 20f * scale) {
            textPaint.textSize = fontSize
            layout = createStaticLayout(item.question, textPaint, availableTextWidth)
            if (layout.height <= availableTextHeight) break
            fontSize -= 2f * scale
        }
        textPaint.textSize = fontSize
        layout = createStaticLayout(item.question, textPaint, availableTextWidth)

        canvas.save()
        val textY = top + (qHeight - layout.height) / 2f
        canvas.translate(margin + textPad, textY)
        layout.draw(canvas)
        canvas.restore()

        // Draw Image Zone if image is present
        if (hasImage) {
            val imgLeft = margin + qWidth + 24f * scale
            val imgWidth = width - margin - imgLeft
            val imgRect = RectF(imgLeft, top, imgLeft + imgWidth, top + qHeight)
            drawImageZone(canvas, item.imageUriOrPath!!, imgRect, config.image, scale)
        }

        return top + qHeight
    }

    private fun drawImageZone(
        canvas: Canvas,
        imagePath: String,
        destRect: RectF,
        imgConfig: ImageConfig,
        scale: Float
    ) {
        val bitmap = loadCachedBitmap(imagePath)

        tempClipPath.reset()
        tempClipPath.addRoundRect(destRect, imgConfig.cornerRadius * scale, imgConfig.cornerRadius * scale, Path.Direction.CW)
        canvas.save()
        canvas.clipPath(tempClipPath)

        if (bitmap != null) {
            // Draw image with fit mode, crop zoom, and pan
            val srcW = bitmap.width.toFloat()
            val srcH = bitmap.height.toFloat()
            val zoom = imgConfig.cropZoom.coerceIn(1.0f, 3.0f)
            val cropW = srcW / zoom
            val cropH = srcH / zoom

            val panOffsetMaxX = (srcW - cropW) / 2f
            val panOffsetMaxY = (srcH - cropH) / 2f
            val panX = imgConfig.panX.coerceIn(-1f, 1f) * panOffsetMaxX
            val panY = imgConfig.panY.coerceIn(-1f, 1f) * panOffsetMaxY

            val cropLeft = ((srcW - cropW) / 2f + panX).coerceIn(0f, srcW - cropW)
            val cropTop = ((srcH - cropH) / 2f + panY).coerceIn(0f, srcH - cropH)

            val srcRect = Rect(cropLeft.toInt(), cropTop.toInt(), (cropLeft + cropW).toInt(), (cropTop + cropH).toInt())
            canvas.drawBitmap(bitmap, srcRect, destRect, imagePaint)
        } else {
            // Clean placeholder card if image not yet loaded
            cardPaint.color = 0xFF1C2236.toInt()
            canvas.drawRect(destRect, cardPaint)
            textPaint.color = 0x88FFFFFF.toInt()
            textPaint.textSize = 24f * scale
            val phText = "Quiz Visual"
            val tw = textPaint.measureText(phText)
            canvas.drawText(phText, destRect.centerX() - tw / 2f, destRect.centerY() + 8f * scale, textPaint)
        }
        canvas.restore()

        // Draw glow border
        cardStrokePaint.color = imgConfig.glowColor.toInt()
        cardStrokePaint.strokeWidth = 2.5f * scale
        canvas.drawRoundRect(destRect, imgConfig.cornerRadius * scale, imgConfig.cornerRadius * scale, cardStrokePaint)
    }

    private fun drawTimer(
        canvas: Canvas,
        progress: Float,
        isCountdown: Boolean,
        isReveal: Boolean,
        width: Float,
        height: Float,
        config: MCQProjectConfig,
        scale: Float
    ) {
        val timerSecRemaining = ceil(config.timing.timerCountdownSec * progress).toInt().coerceAtLeast(0)

        when (config.elements.timerStyle) {
            TimerStyle.CIRCULAR_RING -> {
                val cx = width - 110f * scale
                val cy = 100f * scale
                val radius = 42f * scale

                // Background track
                timerTrackPaint.color = 0x33FFFFFF.toInt()
                timerTrackPaint.strokeWidth = 7f * scale
                canvas.drawCircle(cx, cy, radius, timerTrackPaint)

                // Progress sweep arc
                val sweepAngle = 360f * progress
                val timerColor = when {
                    isReveal -> config.theme.correctColor.toInt()
                    progress < 0.25f -> 0xFFFF3D00.toInt() // Red alert when low time
                    progress < 0.5f -> 0xFFFFAB00.toInt()  // Amber
                    else -> config.theme.accentColor.toInt()
                }

                timerProgressPaint.color = timerColor
                timerProgressPaint.strokeWidth = 7f * scale
                tempRectF.set(cx - radius, cy - radius, cx + radius, cy + radius)
                canvas.drawArc(tempRectF, -90f, sweepAngle, false, timerProgressPaint)

                // Timer text (Seconds remaining)
                textPaint.color = if (isReveal) config.theme.correctColor.toInt() else Color.WHITE
                textPaint.textSize = 30f * scale
                textPaint.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                val secStr = if (isReveal) "✓" else timerSecRemaining.toString()
                val tw = textPaint.measureText(secStr)
                canvas.drawText(secStr, cx - tw / 2f, cy + 10f * scale, textPaint)
            }

            TimerStyle.LINEAR_BAR -> {
                val barLeft = 60f * scale
                val barRight = width - 60f * scale
                val barTop = 96f * scale
                val barHeight = 8f * scale

                // Track
                timerTrackPaint.color = 0x33FFFFFF.toInt()
                timerTrackPaint.strokeWidth = barHeight
                canvas.drawLine(barLeft, barTop, barRight, barTop, timerTrackPaint)

                // Progress
                val activeRight = barLeft + (barRight - barLeft) * progress
                val barColor = if (progress < 0.25f) 0xFFFF3D00.toInt() else config.theme.accentColor.toInt()
                timerProgressPaint.color = barColor
                timerProgressPaint.strokeWidth = barHeight
                canvas.drawLine(barLeft, barTop, activeRight, barTop, timerProgressPaint)
            }

            TimerStyle.DIGITAL_BADGE -> {
                val bx = width - 150f * scale
                val by = 40f * scale
                val bw = 110f * scale
                val bh = 48f * scale
                tempRectF.set(bx, by, bx + bw, by + bh)

                badgePaint.color = 0xAA000000.toInt()
                canvas.drawRoundRect(tempRectF, 12f * scale, 12f * scale, badgePaint)

                cardStrokePaint.color = if (progress < 0.25f) 0xFFFF3D00.toInt() else config.theme.accentColor.toInt()
                cardStrokePaint.strokeWidth = 2f * scale
                canvas.drawRoundRect(tempRectF, 12f * scale, 12f * scale, cardStrokePaint)

                val digitalStr = String.format("00:%02d", timerSecRemaining)
                textPaint.color = Color.WHITE
                textPaint.textSize = 22f * scale
                textPaint.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                val tw = textPaint.measureText(digitalStr)
                canvas.drawText(digitalStr, bx + (bw - tw) / 2f, by + 32f * scale, textPaint)
            }
        }
    }

    private fun drawOptions(
        canvas: Canvas,
        item: MCQItem,
        top: Float,
        width: Float,
        height: Float,
        isReveal: Boolean,
        revealTime: Float,
        config: MCQProjectConfig,
        scale: Float
    ) {
        val margin = 60f * scale
        val correctIndex = item.correctIndex
        val letters = arrayOf("A", "B", "C", "D")

        when (config.theme.layoutMode) {
            LayoutMode.CLASSIC_SPLIT -> {
                // 2x2 Grid Layout
                val gridGap = 20f * scale
                val colW = (width - margin * 2 - gridGap) / 2f
                val rowH = 135f * scale

                for (i in 0 until 4) {
                    val col = i % 2
                    val row = i / 2
                    val cardX = margin + col * (colW + gridGap)
                    val cardY = top + row * (rowH + gridGap)
                    val isCorrect = (i == correctIndex)

                    drawSingleOptionCard(
                        canvas, letters[i], item.getOption(i),
                        cardX, cardY, colW, rowH,
                        isCorrect, isReveal, revealTime,
                        config, scale
                    )
                }
            }

            LayoutMode.VERTICAL_STACK, LayoutMode.MEDIA_RIGHT -> {
                // 4 Stacked Horizontal Rows
                val gap = 16f * scale
                val cardW = width - margin * 2
                val cardH = ((height - top - 90f * scale - gap * 3) / 4f).coerceIn(80f * scale, 115f * scale)

                for (i in 0 until 4) {
                    val cardY = top + i * (cardH + gap)
                    val isCorrect = (i == correctIndex)

                    drawSingleOptionCard(
                        canvas, letters[i], item.getOption(i),
                        margin, cardY, cardW, cardH,
                        isCorrect, isReveal, revealTime,
                        config, scale
                    )
                }
            }
        }
    }

    private fun drawSingleOptionCard(
        canvas: Canvas,
        letter: String,
        optionText: String,
        x: Float,
        y: Float,
        w: Float,
        h: Float,
        isCorrect: Boolean,
        isReveal: Boolean,
        revealTime: Float,
        config: MCQProjectConfig,
        scale: Float
    ) {
        val cornerRad = 20f * scale
        tempRectF.set(x, y, x + w, y + h)

        // Color and state selection
        val baseCardColor = config.theme.cardBgColor.toInt()
        val correctColor = config.theme.correctColor.toInt()

        val cardBg: Int
        val strokeColor: Int
        val strokeWidth: Float
        val letterBgColor: Int
        val textAlpha: Int

        if (isReveal) {
            if (isCorrect) {
                // Glowing pulsating winner card
                val pulse = (sin(revealTime.toDouble() * 6.0) * 0.15 + 0.85).toFloat()
                cardBg = blendColors(baseCardColor, correctColor, 0.35f * pulse)
                strokeColor = correctColor
                strokeWidth = 4.5f * scale
                letterBgColor = correctColor
                textAlpha = 255

                // Draw outer neon glow
                val glowRad = 12f * scale
                tempRectF.inset(-glowRad, -glowRad)
                glowPaint.color = (correctColor and 0x00FFFFFF) or 0x44000000
                canvas.drawRoundRect(tempRectF, cornerRad + glowRad, cornerRad + glowRad, glowPaint)
                tempRectF.inset(glowRad, glowRad)
            } else {
                // Dimmed incorrect card
                cardBg = blendColors(baseCardColor, 0xFF000000.toInt(), 0.5f)
                strokeColor = 0x22FFFFFF.toInt()
                strokeWidth = 1.5f * scale
                letterBgColor = 0x44333333.toInt()
                textAlpha = 140
            }
        } else {
            // Standard unrevealed state
            cardBg = baseCardColor
            strokeColor = 0x44FFFFFF.toInt()
            strokeWidth = 1.8f * scale
            letterBgColor = config.theme.accentColor.toInt()
            textAlpha = 255
        }

        // Draw Card Body
        cardPaint.color = cardBg
        canvas.drawRoundRect(tempRectF, cornerRad, cornerRad, cardPaint)

        // Draw Border
        cardStrokePaint.color = strokeColor
        cardStrokePaint.strokeWidth = strokeWidth
        canvas.drawRoundRect(tempRectF, cornerRad, cornerRad, cardStrokePaint)

        // Draw Letter Circle Badge ("A", "B", "C", "D")
        val circleRad = min(h * 0.36f, 32f * scale)
        val circleCx = x + 30f * scale + circleRad
        val circleCy = y + h / 2f

        badgePaint.color = letterBgColor
        canvas.drawCircle(circleCx, circleCy, circleRad, badgePaint)

        textPaint.color = if (isReveal && isCorrect) Color.BLACK else Color.WHITE
        textPaint.textSize = 28f * scale
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        val lw = textPaint.measureText(letter)
        canvas.drawText(letter, circleCx - lw / 2f, circleCy + 10f * scale, textPaint)

        // Draw Option Text
        val textStartX = circleCx + circleRad + 22f * scale
        val availableTextWidth = (x + w - textStartX - 20f * scale).toInt().coerceAtLeast(50)

        textPaint.color = Color.WHITE
        textPaint.alpha = textAlpha
        textPaint.textSize = 26f * scale
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)

        val layout = createStaticLayout(optionText, textPaint, availableTextWidth)
        canvas.save()
        val textY = y + (h - layout.height) / 2f
        canvas.translate(textStartX, textY)
        layout.draw(canvas)
        canvas.restore()

        // Checkmark badge for winner
        if (isReveal && isCorrect) {
            textPaint.color = correctColor
            textPaint.textSize = 32f * scale
            textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            val checkmark = "✓"
            val cw = textPaint.measureText(checkmark)
            canvas.drawText(checkmark, x + w - cw - 24f * scale, y + h / 2f + 11f * scale, textPaint)
        }
    }

    private fun drawExplanationPanel(
        canvas: Canvas,
        explanation: String,
        progress: Float,
        width: Float,
        height: Float,
        config: MCQProjectConfig,
        scale: Float
    ) {
        val margin = 50f * scale
        val panelH = 220f * scale

        // Smooth slide-up transition using ease-out cubic
        val ease = (1f - (1f - progress).pow(3)).coerceIn(0f, 1f)
        val panelY = height - (panelH + 30f * scale) * ease

        tempRectF.set(margin, panelY, width - margin, panelY + panelH)

        // Elevated dark card with neon amber/gold accent
        cardPaint.color = 0xF510162A.toInt()
        val cornerRad = 24f * scale
        canvas.drawRoundRect(tempRectF, cornerRad, cornerRad, cardPaint)

        cardStrokePaint.color = config.theme.secondaryColor.toInt()
        cardStrokePaint.strokeWidth = 3f * scale
        canvas.drawRoundRect(tempRectF, cornerRad, cornerRad, cardStrokePaint)

        // Header: "💡 EXPLANATION"
        textPaint.color = config.theme.secondaryColor.toInt()
        textPaint.textSize = 24f * scale
        textPaint.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        canvas.drawText("💡 EXPLANATION", margin + 28f * scale, panelY + 40f * scale, textPaint)

        // Body Text
        val availableW = (width - margin * 2 - 56f * scale).toInt().coerceAtLeast(100)
        textPaint.color = 0xFFECEFF1.toInt()
        textPaint.textSize = 23f * scale
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)

        val layout = createStaticLayout(explanation, textPaint, availableW)
        canvas.save()
        canvas.translate(margin + 28f * scale, panelY + 56f * scale)
        layout.draw(canvas)
        canvas.restore()
    }

    private fun drawBottomProgressBar(
        canvas: Canvas,
        progress: Float,
        totalItems: Int,
        width: Float,
        height: Float,
        config: MCQProjectConfig,
        scale: Float
    ) {
        val barH = 7f * scale
        val barY = height - barH

        // Full bar track
        timerTrackPaint.color = 0x33000000.toInt()
        timerTrackPaint.strokeWidth = barH
        timerTrackPaint.strokeCap = Paint.Cap.SQUARE
        canvas.drawLine(0f, barY, width, barY, timerTrackPaint)

        // Progress line
        timerProgressPaint.color = config.theme.accentColor.toInt()
        timerProgressPaint.strokeWidth = barH
        timerProgressPaint.strokeCap = Paint.Cap.SQUARE
        canvas.drawLine(0f, barY, width * progress, barY, timerProgressPaint)

        // Question division markers
        if (totalItems > 1) {
            particlePaint.color = 0x66FFFFFF.toInt()
            particlePaint.strokeWidth = 2f * scale
            for (i in 1 until totalItems) {
                val tickX = width * (i.toFloat() / totalItems)
                canvas.drawLine(tickX, barY - 4f * scale, tickX, height, particlePaint)
            }
        }
    }

    private fun createStaticLayout(text: String, paint: TextPaint, width: Int): StaticLayout {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(0f, 1.25f)
                .setIncludePad(false)
                .build()
        } else {
            @Suppress("DEPRECATION")
            StaticLayout(
                text, paint, width,
                Layout.Alignment.ALIGN_NORMAL,
                1.25f, 0f, false
            )
        }
    }

    private fun loadCachedBitmap(path: String): Bitmap? {
        if (imageCache.containsKey(path)) return imageCache[path]

        return try {
            val stream: InputStream? = if (path.startsWith("content://") || path.startsWith("file://")) {
                context?.contentResolver?.openInputStream(Uri.parse(path))
            } else {
                java.io.File(path).inputStream()
            }
            stream?.use {
                val bmp = BitmapFactory.decodeStream(it)
                if (bmp != null) {
                    imageCache[path] = bmp
                }
                bmp
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun blendColors(color1: Int, color2: Int, ratio: Float): Int {
        val inverseRatio = 1f - ratio
        val a = (Color.alpha(color1) * inverseRatio + Color.alpha(color2) * ratio).toInt()
        val r = (Color.red(color1) * inverseRatio + Color.red(color2) * ratio).toInt()
        val g = (Color.green(color1) * inverseRatio + Color.green(color2) * ratio).toInt()
        val b = (Color.blue(color1) * inverseRatio + Color.blue(color2) * ratio).toInt()
        return Color.argb(a, r, g, b)
    }

    fun clearCache() {
        imageCache.values.forEach { it.recycle() }
        imageCache.clear()
    }
}
