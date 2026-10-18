package com.mcqstudio.pro.ui

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mcqstudio.pro.audio.SFXType
import com.mcqstudio.pro.model.*
import com.mcqstudio.pro.parser.CSVQuizParser

@Composable
fun AccordionHeader(
    number: Int,
    title: String,
    icon: ImageVector,
    isExpanded: Boolean,
    onToggle: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .testTag("accordion_header_$number"),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(12.dp),
        tonalElevation = if (isExpanded) 4.dp else 1.dp
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(
                        if (isExpanded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isExpanded) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "SECTION $number",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Icon(
                imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = if (isExpanded) "Collapse" else "Expand",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// -------------------------------------------------------------------------------------------------
// 1. DATA SOURCE SECTION
// -------------------------------------------------------------------------------------------------
@Composable
fun DataSourceSection(
    viewModel: MCQStudioViewModel,
    onPickFileClick: () -> Unit
) {
    var rawText by remember { mutableStateOf("") }
    val items by viewModel.items.collectAsState()

    Column(modifier = Modifier.padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onPickFileClick,
                modifier = Modifier
                    .weight(1f)
                    .testTag("pick_csv_file_button")
            ) {
                Icon(Icons.Default.UploadFile, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Pick CSV/TSV")
            }

            OutlinedButton(
                onClick = {
                    rawText = CSVQuizParser.getSampleCsv()
                    viewModel.loadSampleCsv()
                },
                modifier = Modifier
                    .weight(1f)
                    .testTag("load_sample_csv_button")
            ) {
                Icon(Icons.Default.AutoFixHigh, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Sample Data")
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = rawText,
            onValueChange = {
                rawText = it
                if (it.isNotBlank()) {
                    viewModel.parseCsvText(it)
                }
            },
            label = { Text("Paste CSV, TSV, or Semicolon Quiz Data") },
            placeholder = { Text("Question,Option A,Option B,Option C,Option D,Answer,Explanation...") },
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .testTag("csv_paste_field"),
            shape = RoundedCornerShape(10.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Loaded Questions: ${items.size}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.tertiary,
            fontWeight = FontWeight.Bold
        )
    }
}

// -------------------------------------------------------------------------------------------------
// 2. COLUMN MAPPING SECTION
// -------------------------------------------------------------------------------------------------
@Composable
fun ColumnMappingSection(
    config: MCQProjectConfig,
    onUpdateMapping: (ColumnMapping) -> Unit
) {
    val m = config.columnMapping

    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Specify 0-indexed column numbers in your CSV:", style = MaterialTheme.typography.bodySmall)

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ColumnInput(label = "Question", col = m.questionCol, modifier = Modifier.weight(1f)) {
                onUpdateMapping(m.copy(questionCol = it))
            }
            ColumnInput(label = "Correct Ans", col = m.answerCol, modifier = Modifier.weight(1f)) {
                onUpdateMapping(m.copy(answerCol = it))
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ColumnInput(label = "Option A", col = m.optionACol, modifier = Modifier.weight(1f)) {
                onUpdateMapping(m.copy(optionACol = it))
            }
            ColumnInput(label = "Option B", col = m.optionBCol, modifier = Modifier.weight(1f)) {
                onUpdateMapping(m.copy(optionBCol = it))
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ColumnInput(label = "Option C", col = m.optionCCol, modifier = Modifier.weight(1f)) {
                onUpdateMapping(m.copy(optionCCol = it))
            }
            ColumnInput(label = "Option D", col = m.optionDCol, modifier = Modifier.weight(1f)) {
                onUpdateMapping(m.copy(optionDCol = it))
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ColumnInput(label = "Explanation", col = m.explanationCol, modifier = Modifier.weight(1f)) {
                onUpdateMapping(m.copy(explanationCol = it))
            }
            ColumnInput(label = "Image/URL", col = m.imageCol, modifier = Modifier.weight(1f)) {
                onUpdateMapping(m.copy(imageCol = it))
            }
        }
    }
}

@Composable
private fun ColumnInput(label: String, col: Int, modifier: Modifier, onValChange: (Int) -> Unit) {
    OutlinedTextField(
        value = col.toString(),
        onValueChange = { str -> str.toIntOrNull()?.let(onValChange) },
        label = { Text(label, fontSize = 12.sp) },
        modifier = modifier,
        singleLine = true,
        shape = RoundedCornerShape(8.dp)
    )
}

// -------------------------------------------------------------------------------------------------
// 3. TIMING & CAMERA SECTION
// -------------------------------------------------------------------------------------------------
@Composable
fun TimingAndCameraSection(
    timing: TimingConfig,
    totalItems: Int,
    onUpdateTiming: (TimingConfig) -> Unit
) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // Mode Selector
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = timing.mode == TimingMode.PER_QUESTION,
                onClick = { onUpdateTiming(timing.copy(mode = TimingMode.PER_QUESTION)) },
                label = { Text("Per-Question Mode") },
                modifier = Modifier.testTag("timing_mode_per_q")
            )
            FilterChip(
                selected = timing.mode == TimingMode.TOTAL_DURATION,
                onClick = { onUpdateTiming(timing.copy(mode = TimingMode.TOTAL_DURATION)) },
                label = { Text("Total Duration Mode") },
                modifier = Modifier.testTag("timing_mode_total")
            )
        }

        if (timing.mode == TimingMode.PER_QUESTION) {
            SliderSetting(
                label = "Reading Time",
                value = timing.questionReadingSec,
                range = 1f..8f,
                suffix = "sec"
            ) { onUpdateTiming(timing.copy(questionReadingSec = it)) }

            SliderSetting(
                label = "Countdown Duration",
                value = timing.timerCountdownSec,
                range = 3f..15f,
                suffix = "sec"
            ) { onUpdateTiming(timing.copy(timerCountdownSec = it)) }

            SliderSetting(
                label = "Answer Reveal",
                value = timing.answerRevealSec,
                range = 1f..6f,
                suffix = "sec"
            ) { onUpdateTiming(timing.copy(answerRevealSec = it)) }

            SliderSetting(
                label = "Explanation Duration",
                value = timing.explanationSec,
                range = 1f..8f,
                suffix = "sec"
            ) { onUpdateTiming(timing.copy(explanationSec = it)) }
        } else {
            SliderSetting(
                label = "Total Video Duration",
                value = timing.totalQuizMinutes,
                range = 0.5f..10f,
                suffix = "min"
            ) { onUpdateTiming(timing.copy(totalQuizMinutes = it)) }
        }

        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Per-Question Cycle: ${String.format("%.1f", timing.singleQuestionDuration(totalItems))}s")
                Text(
                    "Total Video: ${String.format("%.1f", timing.totalVideoDuration(totalItems))}s",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

// -------------------------------------------------------------------------------------------------
// 4. IMAGE ZONE SECTION
// -------------------------------------------------------------------------------------------------
@Composable
fun ImageZoneSection(
    imageConfig: ImageConfig,
    onUpdateImage: (ImageConfig) -> Unit
) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Image Zone Alignment & Camera Framing", style = MaterialTheme.typography.bodySmall)

        SliderSetting(
            label = "Crop Zoom",
            value = imageConfig.cropZoom,
            range = 1.0f..2.5f,
            suffix = "x"
        ) { onUpdateImage(imageConfig.copy(cropZoom = it)) }

        SliderSetting(
            label = "Pan X",
            value = imageConfig.panX,
            range = -1.0f..1.0f,
            suffix = ""
        ) { onUpdateImage(imageConfig.copy(panX = it)) }

        SliderSetting(
            label = "Pan Y",
            value = imageConfig.panY,
            range = -1.0f..1.0f,
            suffix = ""
        ) { onUpdateImage(imageConfig.copy(panY = it)) }

        SliderSetting(
            label = "Corner Radius",
            value = imageConfig.cornerRadius,
            range = 0f..40f,
            suffix = "dp"
        ) { onUpdateImage(imageConfig.copy(cornerRadius = it)) }
    }
}

// -------------------------------------------------------------------------------------------------
// 5. THEME & LAYOUT SECTION
// -------------------------------------------------------------------------------------------------
@Composable
fun ThemeAndLayoutSection(
    themeConfig: ThemeConfig,
    onUpdateTheme: (ThemeConfig) -> Unit
) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Theme Presets", style = MaterialTheme.typography.titleSmall)

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ThemePreset.values().forEach { preset ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onUpdateTheme(themeConfig.copy(preset = preset)) }
                        .testTag("theme_preset_${preset.name}"),
                    color = if (themeConfig.preset == preset) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(8.dp),
                    border = if (themeConfig.preset == preset) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = themeConfig.preset == preset,
                            onClick = { onUpdateTheme(themeConfig.copy(preset = preset)) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(preset.title, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))
        Text("Layout Mode", style = MaterialTheme.typography.titleSmall)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            LayoutMode.values().forEach { mode ->
                FilterChip(
                    selected = themeConfig.layoutMode == mode,
                    onClick = { onUpdateTheme(themeConfig.copy(layoutMode = mode)) },
                    label = { Text(if (mode == LayoutMode.CLASSIC_SPLIT) "2x2 Split" else "Vertical Stack", fontSize = 12.sp) }
                )
            }
        }
    }
}

// -------------------------------------------------------------------------------------------------
// 6. ELEMENTS & CARDS SECTION
// -------------------------------------------------------------------------------------------------
@Composable
fun ElementsAndCardsSection(
    elementsConfig: ElementsConfig,
    onUpdateElements: (ElementsConfig) -> Unit
) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Timer Style", style = MaterialTheme.typography.titleSmall)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TimerStyle.values().forEach { style ->
                FilterChip(
                    selected = elementsConfig.timerStyle == style,
                    onClick = { onUpdateElements(elementsConfig.copy(timerStyle = style)) },
                    label = { Text(style.title.split(" ").first(), fontSize = 12.sp) }
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))
        Text("Question Number Style", style = MaterialTheme.typography.titleSmall)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            QuestionNumberStyle.values().forEach { style ->
                FilterChip(
                    selected = elementsConfig.questionNumberStyle == style,
                    onClick = { onUpdateElements(elementsConfig.copy(questionNumberStyle = style)) },
                    label = { Text(style.title.split(" ").first(), fontSize = 12.sp) }
                )
            }
        }
    }
}

// -------------------------------------------------------------------------------------------------
// 7. MOVING ICONS & WATERMARK SECTION
// -------------------------------------------------------------------------------------------------
@Composable
fun MovingIconsAndWatermarkSection(
    branding: BrandingConfig,
    onUpdateBranding: (BrandingConfig) -> Unit
) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        OutlinedTextField(
            value = branding.watermarkText,
            onValueChange = { onUpdateBranding(branding.copy(watermarkText = it)) },
            label = { Text("Watermark / Channel Handle") },
            placeholder = { Text("@MCQVideoStudio") },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("watermark_text_field"),
            shape = RoundedCornerShape(10.dp)
        )

        SliderSetting(
            label = "Watermark Opacity",
            value = branding.watermarkOpacity,
            range = 0.2f..1.0f,
            suffix = ""
        ) { onUpdateBranding(branding.copy(watermarkOpacity = it)) }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Ambient Moving Particles")
            Switch(
                checked = branding.showMovingParticles,
                onCheckedChange = { onUpdateBranding(branding.copy(showMovingParticles = it)) }
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Bottom Overall Progress Bar")
            Switch(
                checked = branding.showProgressBar,
                onCheckedChange = { onUpdateBranding(branding.copy(showProgressBar = it)) }
            )
        }
    }
}

// -------------------------------------------------------------------------------------------------
// 8. SFX & EXPORT SECTION
// -------------------------------------------------------------------------------------------------
@Composable
fun SfxAndExportSection(
    audioExport: AudioExportConfig,
    onUpdateAudioExport: (AudioExportConfig) -> Unit,
    onTestSFX: (SFXType) -> Unit,
    onExportClick: () -> Unit
) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // SFX Controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Countdown Audio Ticks")
            Switch(
                checked = audioExport.ticksEnabled,
                onCheckedChange = { onUpdateAudioExport(audioExport.copy(ticksEnabled = it)) }
            )
        }

        SliderSetting(
            label = "Tick Volume",
            value = audioExport.tickVolume,
            range = 0.1f..1.0f,
            suffix = ""
        ) { onUpdateAudioExport(audioExport.copy(tickVolume = it)) }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = { onTestSFX(SFXType.WHOOSH) },
                modifier = Modifier.weight(1f)
            ) {
                Text("Test Whoosh", fontSize = 11.sp)
            }
            OutlinedButton(
                onClick = { onTestSFX(SFXType.TICK) },
                modifier = Modifier.weight(1f)
            ) {
                Text("Test Tick", fontSize = 11.sp)
            }
            OutlinedButton(
                onClick = { onTestSFX(SFXType.CHIME) },
                modifier = Modifier.weight(1f)
            ) {
                Text("Test Chime", fontSize = 11.sp)
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

        // Resolution & Bitrate
        Text("Export Settings", style = MaterialTheme.typography.titleSmall)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            VideoResolution.values().forEach { res ->
                FilterChip(
                    selected = audioExport.resolution == res,
                    onClick = { onUpdateAudioExport(audioExport.copy(resolution = res)) },
                    label = { Text(res.label.split(" ").first()) }
                )
            }
        }

        // Render Engine Selector
        Text("Render Engine", style = MaterialTheme.typography.titleSmall)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            RenderEngine.values().forEach { engine ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onUpdateAudioExport(audioExport.copy(renderEngine = engine)) }
                        .testTag("render_engine_${engine.name}"),
                    color = if (audioExport.renderEngine == engine) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = audioExport.renderEngine == engine,
                            onClick = { onUpdateAudioExport(audioExport.copy(renderEngine = engine)) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(engine.title, fontWeight = FontWeight.Bold)
                            Text(engine.speedDescription, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Button(
            onClick = onExportClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .testTag("render_and_export_button"),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.MovieFilter, contentDescription = null)
            Spacer(modifier = Modifier.width(10.dp))
            Text("Render & Export Video (16:9)", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun SliderSetting(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    suffix: String,
    onValueChange: (Float) -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(
                if (suffix.isNotBlank()) "${String.format("%.1f", value)} $suffix" else String.format("%.2f", value),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
