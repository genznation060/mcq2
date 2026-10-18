package com.mcqstudio.pro.model

enum class TimingMode {
    PER_QUESTION,
    TOTAL_DURATION
}

enum class ImageFitMode {
    COVER,
    CONTAIN,
    FIT_WIDTH
}

enum class ThemePreset(val title: String) {
    NEON_CYBER("Neon Cyber (Violet & Amber)"),
    DEEP_NAVY("Deep Navy (Blue & Cyan)"),
    ROYAL_PURPLE("Royal Purple & Gold"),
    SLATE_DARK("Modern Slate & Emerald"),
    CRIMSON_EMBER("Crimson & Flame")
}

enum class LayoutMode(val title: String) {
    CLASSIC_SPLIT("Split (Left Question, Right Options)"),
    VERTICAL_STACK("Centered Stack (Top-Down)"),
    MEDIA_RIGHT("Media Right (Side-by-Side)")
}

enum class TimerStyle(val title: String) {
    CIRCULAR_RING("Circular Glowing Ring"),
    LINEAR_BAR("Top Linear Progress Bar"),
    DIGITAL_BADGE("Digital Flip Badge")
}

enum class QuestionNumberStyle(val title: String) {
    BADGE_PILL("Floating Pill Badge"),
    RIBBON("Banner Ribbon"),
    MINIMAL_TEXT("Minimalist Modern")
}

enum class OptionCardStyle(val title: String) {
    GLASS_CARD("Frosted Glass Border"),
    ROUNDED_FILL("Solid Elevated Rounded"),
    OUTLINED("Minimal High-Contrast Outline")
}

enum class ExplanationStyle(val title: String) {
    BOTTOM_SHEET_DRAWER("Slide-Up Bottom Card"),
    HIGHLIGHT_CARD("Centered Spotlight Box")
}

enum class RenderEngine(val title: String, val speedDescription: String) {
    MEDIA_CODEC("MediaCodec Hardware (Fastest, 5-10×)", "Hardware H.264 Encoder + Surface Pipeline"),
    FFMPEG("FFmpeg Software Fallback", "Pixel-by-pixel software rendering pipeline")
}

enum class VideoResolution(val width: Int, val height: Int, val label: String) {
    FHD_1080P(1920, 1080, "1080p Full HD (1920×1080)"),
    HD_720P(1280, 720, "720p HD (1280×720)")
}

data class ColumnMapping(
    val questionCol: Int = 0,
    val optionACol: Int = 1,
    val optionBCol: Int = 2,
    val optionCCol: Int = 3,
    val optionDCol: Int = 4,
    val answerCol: Int = 5,
    val explanationCol: Int = 6,
    val imageCol: Int = 7
)

data class TimingConfig(
    val mode: TimingMode = TimingMode.PER_QUESTION,
    val questionReadingSec: Float = 3.0f,
    val timerCountdownSec: Float = 5.0f,
    val answerRevealSec: Float = 2.5f,
    val explanationSec: Float = 3.5f,
    val totalQuizMinutes: Float = 2.0f
) {
    /**
     * Duration of a single question cycle in seconds.
     */
    fun singleQuestionDuration(totalItems: Int): Float {
        return when (mode) {
            TimingMode.PER_QUESTION -> questionReadingSec + timerCountdownSec + answerRevealSec + explanationSec
            TimingMode.TOTAL_DURATION -> {
                val totalSec = totalQuizMinutes * 60f
                if (totalItems > 0) totalSec / totalItems else 14.0f
            }
        }
    }

    /**
     * Total video duration in seconds.
     */
    fun totalVideoDuration(totalItems: Int): Float {
        if (totalItems <= 0) return 0f
        return singleQuestionDuration(totalItems) * totalItems
    }
}

data class ImageConfig(
    val aspectRatio: Float = 16f / 9f,
    val fitMode: ImageFitMode = ImageFitMode.COVER,
    val cropZoom: Float = 1.0f,
    val panX: Float = 0.0f,
    val panY: Float = 0.0f,
    val cornerRadius: Float = 24.0f,
    val glowColor: Long = 0xFF7C4DFF,
    val glowRadius: Float = 16.0f
)

data class ThemeConfig(
    val preset: ThemePreset = ThemePreset.NEON_CYBER,
    val accentColor: Long = 0xFF7C4DFF,
    val secondaryColor: Long = 0xFFFFAB00,
    val correctColor: Long = 0xFF00E676,
    val wrongDimColor: Long = 0x55333333,
    val cardBgColor: Long = 0xE6141824,
    val layoutMode: LayoutMode = LayoutMode.CLASSIC_SPLIT,
    val fontChoice: String = "MONOSPACE" // "SANS", "SERIF", "MONO", "ROUNDED"
)

data class ElementsConfig(
    val timerStyle: TimerStyle = TimerStyle.CIRCULAR_RING,
    val questionNumberStyle: QuestionNumberStyle = QuestionNumberStyle.BADGE_PILL,
    val optionCardStyle: OptionCardStyle = OptionCardStyle.GLASS_CARD,
    val explanationStyle: ExplanationStyle = ExplanationStyle.BOTTOM_SHEET_DRAWER
)

data class BrandingConfig(
    val watermarkText: String = "@MCQVideoStudio",
    val watermarkOpacity: Float = 0.8f,
    val showMovingParticles: Boolean = true,
    val showProgressBar: Boolean = true
)

data class AudioExportConfig(
    val ticksEnabled: Boolean = true,
    val tickVolume: Float = 0.8f,
    val whooshVolume: Float = 0.7f,
    val chimeVolume: Float = 0.9f,
    val resolution: VideoResolution = VideoResolution.FHD_1080P,
    val fps: Int = 30,
    val videoBitrateBps: Int = 8_000_000,
    val renderEngine: RenderEngine = RenderEngine.MEDIA_CODEC
)

data class MCQProjectConfig(
    val columnMapping: ColumnMapping = ColumnMapping(),
    val timing: TimingConfig = TimingConfig(),
    val image: ImageConfig = ImageConfig(),
    val theme: ThemeConfig = ThemeConfig(),
    val elements: ElementsConfig = ElementsConfig(),
    val branding: BrandingConfig = BrandingConfig(),
    val audioExport: AudioExportConfig = AudioExportConfig()
)
