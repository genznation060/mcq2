package com.mcqstudio.pro.ui

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.mcqstudio.pro.audio.SFXType
import com.mcqstudio.pro.engine.ExportState
import com.mcqstudio.pro.engine.MCQCanvasRenderer
import com.mcqstudio.pro.storage.MediaStoreSaver

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MCQStudioScreen(viewModel: MCQStudioViewModel) {
    val context = LocalContext.current
    val items by viewModel.items.collectAsState()
    val config by viewModel.config.collectAsState()
    val currentTime by viewModel.currentTimeSec.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val exportState by viewModel.exportState.collectAsState()

    var expandedSection by remember { mutableStateOf(1) }
    var isFullscreenPreview by remember { mutableStateOf(false) }

    // CSV File Picker launcher
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            try {
                context.contentResolver.openInputStream(it)?.bufferedReader()?.use { reader ->
                    viewModel.parseCsvText(reader.readText())
                }
            } catch (_: Exception) {}
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .background(MaterialTheme.colorScheme.primary, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.MovieFilter, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text("MCQ Video Studio Pro", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                            Text("16:9 Quiz Video Generator", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                },
                actions = {
                    FilledTonalButton(
                        onClick = { viewModel.startExport() },
                        modifier = Modifier.testTag("top_export_button"),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Export", fontWeight = FontWeight.Bold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // =====================================================================================
            // STICKY 16:9 LIVE PREVIEW AT THE TOP
            // =====================================================================================
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                color = Color.Black,
                shape = RoundedCornerShape(14.dp),
                shadowElevation = 6.dp
            ) {
                Column {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                            .clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp))
                            .background(Color.Black)
                            .testTag("sticky_preview_viewport")
                    ) {
                        LiveCanvasView(
                            timeSec = currentTime,
                            items = items,
                            config = config,
                            modifier = Modifier.fillMaxSize()
                        )

                        // Top right watermark badge indicator
                        Surface(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(8.dp),
                            color = Color(0x66000000),
                            shape = CircleShape
                        ) {
                            IconButton(
                                onClick = { isFullscreenPreview = true },
                                modifier = Modifier
                                    .size(36.dp)
                                    .testTag("fullscreen_preview_button")
                            ) {
                                Icon(Icons.Default.Fullscreen, contentDescription = "Fullscreen", tint = Color.White)
                            }
                        }

                        // Bottom Question counter badge
                        Surface(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(8.dp),
                            color = Color(0x88000000),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "Q ${viewModel.currentQuestionIndex + 1} / ${items.size.coerceAtLeast(1)}",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Scrubber and Playback Bar
                    PlaybackControlBar(
                        currentTime = currentTime,
                        totalTime = viewModel.totalDurationSec,
                        isPlaying = isPlaying,
                        onPlayPauseToggle = { viewModel.togglePlayPause() },
                        onSeek = { viewModel.seekTo(it) },
                        onPrevQ = { viewModel.previousQuestion() },
                        onNextQ = { viewModel.nextQuestion() },
                        onTestSfx = { viewModel.playTestSFX(SFXType.CHIME) }
                    )
                }
            }

            // =====================================================================================
            // ACCORDION SECTIONS
            // =====================================================================================
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 10.dp)
            ) {
                // 1. Data Source
                item {
                    AccordionCard(
                        number = 1,
                        title = "Data Source",
                        icon = Icons.Default.Dataset,
                        isExpanded = expandedSection == 1,
                        onToggle = { expandedSection = if (expandedSection == 1) 0 else 1 }
                    ) {
                        DataSourceSection(
                            viewModel = viewModel,
                            onPickFileClick = { filePickerLauncher.launch("*/*") }
                        )
                    }
                }

                // 2. Column Mapping
                item {
                    AccordionCard(
                        number = 2,
                        title = "Column Mapping",
                        icon = Icons.Default.ViewColumn,
                        isExpanded = expandedSection == 2,
                        onToggle = { expandedSection = if (expandedSection == 2) 0 else 2 }
                    ) {
                        ColumnMappingSection(
                            config = config,
                            onUpdateMapping = { viewModel.updateConfig { c -> c.copy(columnMapping = it) } }
                        )
                    }
                }

                // 3. Timing & Camera
                item {
                    AccordionCard(
                        number = 3,
                        title = "Timing & Camera",
                        icon = Icons.Default.Timer,
                        isExpanded = expandedSection == 3,
                        onToggle = { expandedSection = if (expandedSection == 3) 0 else 3 }
                    ) {
                        TimingAndCameraSection(
                            timing = config.timing,
                            totalItems = items.size,
                            onUpdateTiming = { viewModel.updateConfig { c -> c.copy(timing = it) } }
                        )
                    }
                }

                // 4. Image Zone
                item {
                    AccordionCard(
                        number = 4,
                        title = "Image Zone",
                        icon = Icons.Default.Image,
                        isExpanded = expandedSection == 4,
                        onToggle = { expandedSection = if (expandedSection == 4) 0 else 4 }
                    ) {
                        ImageZoneSection(
                            imageConfig = config.image,
                            onUpdateImage = { viewModel.updateConfig { c -> c.copy(image = it) } }
                        )
                    }
                }

                // 5. Theme & Layout
                item {
                    AccordionCard(
                        number = 5,
                        title = "Theme & Layout",
                        icon = Icons.Default.Palette,
                        isExpanded = expandedSection == 5,
                        onToggle = { expandedSection = if (expandedSection == 5) 0 else 5 }
                    ) {
                        ThemeAndLayoutSection(
                            themeConfig = config.theme,
                            onUpdateTheme = { viewModel.updateConfig { c -> c.copy(theme = it) } }
                        )
                    }
                }

                // 6. Elements & Cards
                item {
                    AccordionCard(
                        number = 6,
                        title = "Elements & Cards",
                        icon = Icons.Default.DashboardCustomize,
                        isExpanded = expandedSection == 6,
                        onToggle = { expandedSection = if (expandedSection == 6) 0 else 6 }
                    ) {
                        ElementsAndCardsSection(
                            elementsConfig = config.elements,
                            onUpdateElements = { viewModel.updateConfig { c -> c.copy(elements = it) } }
                        )
                    }
                }

                // 7. Moving Icons & Watermark
                item {
                    AccordionCard(
                        number = 7,
                        title = "Moving Icons & Watermark",
                        icon = Icons.Default.BrandingWatermark,
                        isExpanded = expandedSection == 7,
                        onToggle = { expandedSection = if (expandedSection == 7) 0 else 7 }
                    ) {
                        MovingIconsAndWatermarkSection(
                            branding = config.branding,
                            onUpdateBranding = { viewModel.updateConfig { c -> c.copy(branding = it) } }
                        )
                    }
                }

                // 8. SFX & Export
                item {
                    AccordionCard(
                        number = 8,
                        title = "SFX & Export",
                        icon = Icons.Default.VolumeUp,
                        isExpanded = expandedSection == 8,
                        onToggle = { expandedSection = if (expandedSection == 8) 0 else 8 }
                    ) {
                        SfxAndExportSection(
                            audioExport = config.audioExport,
                            onUpdateAudioExport = { viewModel.updateConfig { c -> c.copy(audioExport = it) } },
                            onTestSFX = { viewModel.playTestSFX(it) },
                            onExportClick = { viewModel.startExport() }
                        )
                    }
                }
            }
        }
    }

    // =============================================================================================
    // FULLSCREEN PREVIEW DIALOG
    // =============================================================================================
    if (isFullscreenPreview) {
        Dialog(
            onDismissRequest = { isFullscreenPreview = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
            ) {
                LiveCanvasView(
                    timeSec = currentTime,
                    items = items,
                    config = config,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .align(Alignment.Center)
                )

                IconButton(
                    onClick = { isFullscreenPreview = false },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                }
            }
        }
    }

    // =============================================================================================
    // EXPORT PROGRESS / SUCCESS / ERROR MODAL
    // =============================================================================================
    when (val state = exportState) {
        is ExportState.Exporting -> {
            AlertDialog(
                onDismissRequest = {},
                confirmButton = {},
                icon = {
                    CircularProgressIndicator(
                        progress = { state.progress },
                        modifier = Modifier.size(56.dp),
                        strokeWidth = 5.dp
                    )
                },
                title = { Text("Rendering MCQ Video", fontWeight = FontWeight.Bold) },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = state.stage,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { state.progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "${(state.progress * 100).toInt()}% completed",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            )
        }

        is ExportState.Success -> {
            AlertDialog(
                onDismissRequest = { viewModel.resetExport() },
                icon = {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .background(Color(0xFF00E676), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = Color.Black, modifier = Modifier.size(32.dp))
                    }
                },
                title = { Text("Video Saved to Gallery!", fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Saved permanently to public storage:")
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "Movies/MCQ Studio\n${state.localFile.name}",
                                modifier = Modifier.padding(10.dp),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Text("The video is now ready in Google Photos / Phone Gallery.")
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            MediaStoreSaver.openVideoInPlayer(context, state.galleryUri)
                        },
                        modifier = Modifier.testTag("open_gallery_button")
                    ) {
                        Icon(Icons.Default.PlayCircle, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Open Video")
                    }
                },
                dismissButton = {
                    OutlinedButton(
                        onClick = {
                            MediaStoreSaver.shareVideo(context, state.galleryUri)
                        },
                        modifier = Modifier.testTag("share_video_button")
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Share")
                    }
                }
            )
        }

        is ExportState.Error -> {
            AlertDialog(
                onDismissRequest = { viewModel.resetExport() },
                icon = {
                    Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(48.dp))
                },
                title = { Text("Export Failed", fontWeight = FontWeight.Bold) },
                text = { Text(state.message) },
                confirmButton = {
                    Button(onClick = { viewModel.resetExport() }) {
                        Text("Dismiss")
                    }
                }
            )
        }

        ExportState.Idle -> {}
    }
}

@Composable
private fun PlaybackControlBar(
    currentTime: Float,
    totalTime: Float,
    isPlaying: Boolean,
    onPlayPauseToggle: () -> Unit,
    onSeek: (Float) -> Unit,
    onPrevQ: () -> Unit,
    onNextQ: () -> Unit,
    onTestSfx: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF10131E))
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        // Scrubber Slider
        Slider(
            value = currentTime.coerceIn(0f, totalTime.coerceAtLeast(0.1f)),
            onValueChange = onSeek,
            valueRange = 0f..totalTime.coerceAtLeast(0.1f),
            modifier = Modifier
                .fillMaxWidth()
                .height(24.dp)
                .testTag("timeline_scrubber")
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Time label
            Text(
                text = "${formatTime(currentTime)} / ${formatTime(totalTime)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Playback buttons
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onPrevQ, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.SkipPrevious, contentDescription = "Previous Question", tint = Color.White)
                }

                IconButton(
                    onClick = onPlayPauseToggle,
                    modifier = Modifier
                        .size(44.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                        .testTag("play_pause_button")
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = Color.White
                    )
                }

                IconButton(onClick = onNextQ, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.SkipNext, contentDescription = "Next Question", tint = Color.White)
                }
            }

            // Test SFX quick button
            IconButton(
                onClick = onTestSfx,
                modifier = Modifier
                    .size(36.dp)
                    .testTag("quick_test_sfx_button")
            ) {
                Icon(Icons.Default.VolumeUp, contentDescription = "Test SFX", tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun LiveCanvasView(
    timeSec: Float,
    items: List<com.mcqstudio.pro.model.MCQItem>,
    config: com.mcqstudio.pro.model.MCQProjectConfig,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val renderer = remember { MCQCanvasRenderer(context) }

    // Render 16:9 preview buffer (960x540 for responsive realtime preview)
    val previewBitmap = remember {
        Bitmap.createBitmap(960, 540, Bitmap.Config.ARGB_8888)
    }

    LaunchedEffect(timeSec, items, config) {
        renderer.renderFrame(previewBitmap, timeSec, items, config)
    }

    // Force recomposition on time update
    val imageBitmap = remember(timeSec, items, config) {
        renderer.renderFrame(previewBitmap, timeSec, items, config)
        previewBitmap.asImageBitmap()
    }

    Canvas(modifier = modifier) {
        drawImage(imageBitmap)
    }
}

@Composable
private fun AccordionCard(
    number: Int,
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column {
            AccordionHeader(
                number = number,
                title = title,
                icon = icon,
                isExpanded = isExpanded,
                onToggle = onToggle
            )

            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                content()
            }
        }
    }
}

private fun formatTime(sec: Float): String {
    val totalSeconds = sec.toInt()
    val m = totalSeconds / 60
    val s = totalSeconds % 60
    return String.format("%02d:%02d", m, s)
}
