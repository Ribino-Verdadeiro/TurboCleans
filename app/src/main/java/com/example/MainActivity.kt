package com.example

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.example.model.MediaItem
import com.example.model.MediaType
import com.example.model.TrashCategory
import com.example.viewmodel.OptimizerViewModel
import com.example.viewmodel.ScanState
import com.example.viewmodel.GalleryFilter
import com.example.viewmodel.DeepCleanState
import com.example.viewmodel.VacuumState
import com.example.viewmodel.DeepCleanFile
import com.example.ui.components.PulseRadar
import com.example.ui.components.StorageGauge
import com.example.ui.components.SwipeCard
import com.example.ui.theme.*
import androidx.compose.animation.core.tween
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                MainAppScreen()
            }
        }
    }
}

enum class ActiveScreen {
    DASHBOARD,
    SWIPE_CLEANER,
    DEEP_CLEAN,
    SQLITE_VACUUM
}

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun MainAppScreen(
    optimizerViewModel: OptimizerViewModel = viewModel()
) {
    val context = LocalContext.current
    var currentScreen by remember { mutableStateOf(ActiveScreen.DASHBOARD) }

    // Media permissions state handler (using accompanist permissions)
    val permissionsToRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        listOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO
        )
    } else {
        listOf(
            Manifest.permission.READ_EXTERNAL_STORAGE
        )
    }

    val permissionState = rememberMultiplePermissionsState(permissions = permissionsToRequest)

    // Notify VM when permission state updates
    LaunchedEffect(permissionState.allPermissionsGranted) {
        optimizerViewModel.setMediaPermissionGranted(permissionState.allPermissionsGranted)
    }

    // Modal/Dialog states
    var showFilterDialog by remember { mutableStateOf(false) }
    var showCacheCleanConfirmDialog by remember { mutableStateOf(false) }

    val updateState by optimizerViewModel.updateState.collectAsState()
    val currentVersion by optimizerViewModel.currentAppVersion.collectAsState()

    // Trigger installation logs on successful download
    LaunchedEffect(updateState) {
        if (updateState is com.example.viewmodel.UpdateState.DownloadCompleted) {
            android.util.Log.d("UpdateInstaller", "Download concluded successfully, showing intelligent install choices.")
        }
    }

    // Interactive Update Status overlays
    when (val state = updateState) {
        is com.example.viewmodel.UpdateState.Checking -> {
            AlertDialog(
                onDismissRequest = { },
                confirmButton = { },
                containerColor = CardBackground,
                shape = RoundedCornerShape(28.dp),
                title = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            color = PrimaryTeal,
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.5.dp
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Buscando atualizações...", fontSize = 16.sp, color = TextPrimary, fontWeight = FontWeight.Bold)
                    }
                },
                text = {
                    Text(
                        "Consultando o servidor de release do Turbo Clean. Por favor, aguarde...",
                        fontSize = 14.sp,
                        color = TextSecondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            )
        }

        is com.example.viewmodel.UpdateState.UpdateAvailable -> {
            AlertDialog(
                onDismissRequest = { optimizerViewModel.resetUpdateState() },
                containerColor = CardBackground,
                shape = RoundedCornerShape(28.dp),
                title = {
                    Text(
                        text = "Nova Versão Disponível! 🎉",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = PrimaryTeal,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Versão atual: v$currentVersion  •  Nova versão: v${state.versionName}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = WarningOrange,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                        )
                        
                        Text(
                            text = "HISTÓRICO DE MUDANÇAS:",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextSecondary,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                        
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 120.dp)
                                .background(Color.Black.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
                                .border(1.dp, CardBorder, RoundedCornerShape(12.dp))
                                .padding(12.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            Text(
                                text = state.changelog,
                                fontSize = 12.sp,
                                color = TextPrimary,
                                lineHeight = 18.sp
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Deseja baixar e instalar agora mesmo diretamente por cima?",
                            fontSize = 11.sp,
                            color = TextMuted,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { optimizerViewModel.downloadAndInstallUpdate(state.apkUrl) },
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryTeal),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("BAIXAR E INSTALAR AGORA", color = DarkBackground, fontWeight = FontWeight.Black, fontSize = 12.sp)
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { optimizerViewModel.resetUpdateState() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Mais tarde", color = TextSecondary, fontWeight = FontWeight.SemiBold)
                    }
                }
            )
        }

        is com.example.viewmodel.UpdateState.Downloading -> {
            AlertDialog(
                onDismissRequest = { },
                confirmButton = { },
                containerColor = CardBackground,
                shape = RoundedCornerShape(28.dp),
                title = {
                    Text(
                        "Baixando Nova Versão...",
                        fontSize = 18.sp,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { state.progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = PrimaryTeal,
                            trackColor = CardBorder
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "${(state.progress * 100).toInt()}% concluído",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = PrimaryTeal
                        )
                        Text(
                            text = "O painel com as opções de instalação abrirá após concluir",
                            fontSize = 11.sp,
                            color = TextMuted,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            )
        }

        is com.example.viewmodel.UpdateState.DownloadCompleted -> {
            AlertDialog(
                onDismissRequest = { optimizerViewModel.resetUpdateState() },
                containerColor = CardBackground,
                shape = RoundedCornerShape(28.dp),
                title = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (state.isSimulation) Icons.Default.Warning else Icons.Default.Done,
                            contentDescription = null,
                            tint = if (state.isSimulation) WarningOrange else AccentGreen,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (state.isSimulation) "Simulador de Atualização" else "Download Concluído",
                            fontSize = 16.sp,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (state.isSimulation) {
                            Text(
                                text = "Aviso do Servidor de Desenvolvimento:",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = WarningOrange,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "A URL de atualização remota usa um endereço de testes (AppStudioMock) que não hospeda um arquivo real neste ambiente.\n\nPara evitar o erro de sistema 'Problema ao analisar o pacote' (que ocorre quando o celular tenta instalar um arquivo fictício parcial de 1 KB), o instalador nativo foi desabilitado nesta simulação.\n\nClique abaixo em 'Simular Atualização (Rápida)' para demonstrar o fluxo visual completo de incremento de versão dentro do próprio aplicativo com o tema Cyberpunk!",
                                fontSize = 12.sp,
                                color = TextSecondary,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                                    .padding(12.dp)
                            )
                        } else {
                            Text(
                                text = "O pacote v1.1.0 foi baixado com sucesso!",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = AccentGreen,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Deseja realizar a instalação no seu celular de forma nativa?",
                                fontSize = 12.sp,
                                color = TextSecondary,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                },
                confirmButton = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                optimizerViewModel.simulateInAppInstallation()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryTeal),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Done, contentDescription = null, tint = DarkBackground)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("SIMULAR ATUALIZAÇÃO (RÁPIDA)", color = DarkBackground, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                        
                        if (!state.isSimulation) {
                            OutlinedButton(
                                onClick = {
                                    val installIntent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                                        setDataAndType(state.apkFileUri, "application/vnd.android.package-archive")
                                        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    try {
                                        context.startActivity(installIntent)
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Erro ao iniciar instalação: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                                    }
                                },
                                border = BorderStroke(1.dp, CardBorder),
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(imageVector = Icons.Default.SystemUpdate, contentDescription = null, tint = TextPrimary)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("TENTAR INSTALADOR NATIVO", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }

                        TextButton(
                            onClick = { optimizerViewModel.resetUpdateState() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Fechar", color = TextSecondary)
                        }
                    }
                },
                dismissButton = null
            )
        }

        is com.example.viewmodel.UpdateState.Error -> {
            AlertDialog(
                onDismissRequest = { optimizerViewModel.resetUpdateState() },
                containerColor = CardBackground,
                shape = RoundedCornerShape(28.dp),
                title = {
                    Text("Ops! Algo deu errado", fontSize = 16.sp, color = AccentRed, fontWeight = FontWeight.Bold)
                },
                text = {
                    Text(state.message, fontSize = 13.sp, color = TextSecondary)
                },
                confirmButton = {
                    Button(
                        onClick = { optimizerViewModel.resetUpdateState() },
                        colors = ButtonDefaults.buttonColors(containerColor = CardBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Fechar", color = TextPrimary)
                    }
                }
            )
        }
        else -> { /* Idle */ }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground),
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(PrimaryTeal, PrimaryTealDark)
                                    ),
                                    CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Bolt,
                                contentDescription = null,
                                tint = DarkBackground,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "TURBO CLEAN",
                            fontWeight = FontWeight.Black,
                            fontSize = 18.sp,
                            letterSpacing = 1.sp,
                            color = TextPrimary
                        )
                    }
                },
                navigationIcon = {
                    if (currentScreen != ActiveScreen.DASHBOARD) {
                        IconButton(onClick = { currentScreen = ActiveScreen.DASHBOARD }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Voltar",
                                tint = TextPrimary
                            )
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            Toast.makeText(context, "Turbo Clean v$currentVersion • Otimização Ativa", Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Suporte",
                            tint = PrimaryTeal
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = DarkBackground.copy(alpha = 0.9f)
                )
            )
        },
        contentWindowInsets = WindowInsets.safeDrawing
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(DarkBackground)
                .padding(innerPadding)
        ) {
            AnimatedContent(
                targetState = currentScreen,
                transitionSpec = {
                    fadeIn(tween(300)) togetherWith fadeOut(tween(300))
                },
                label = "ScreenTransition"
            ) { screen ->
                when (screen) {
                    ActiveScreen.DASHBOARD -> {
                        DashboardView(
                            viewModel = optimizerViewModel,
                            onStartSwipeCleaner = {
                                // Request permissions or directly transition (with mock fallback handling embedded)
                                if (permissionState.allPermissionsGranted) {
                                    showFilterDialog = true
                                } else {
                                    // Trigger permission prompt
                                    permissionState.launchMultiplePermissionRequest()
                                    // Also show filter dialog as fallback if user decides later
                                    showFilterDialog = true
                                }
                            },
                            onClearCacheTrigger = {
                                showCacheCleanConfirmDialog = true
                            },
                            onStartDeepClean = {
                                optimizerViewModel.resetDeepCleanState()
                                currentScreen = ActiveScreen.DEEP_CLEAN
                            },
                            onStartSqliteVacuum = {
                                optimizerViewModel.resetVacuumState()
                                currentScreen = ActiveScreen.SQLITE_VACUUM
                            }
                        )
                    }
                    ActiveScreen.SWIPE_CLEANER -> {
                        SwipeGalleryCleaner(
                            viewModel = optimizerViewModel,
                            onBackToDashboard = { currentScreen = ActiveScreen.DASHBOARD }
                        )
                    }
                    ActiveScreen.DEEP_CLEAN -> {
                        DeepCleanView(
                            viewModel = optimizerViewModel,
                            onBackToDashboard = { currentScreen = ActiveScreen.DASHBOARD }
                        )
                    }
                    ActiveScreen.SQLITE_VACUUM -> {
                        SqliteVacuumView(
                            viewModel = optimizerViewModel,
                            onBackToDashboard = { currentScreen = ActiveScreen.DASHBOARD }
                        )
                    }
                }
            }

            // FILTER SELECT DIALOG (Select Photo or Video to Sweep)
            if (showFilterDialog) {
                AlertDialog(
                    onDismissRequest = { showFilterDialog = false },
                    containerColor = CardBackground,
                    shape = RoundedCornerShape(28.dp),
                    title = {
                        Text(
                            text = "Limpar Fotos ou Vídeos?",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    },
                    text = {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text(
                                text = "Selecione o tipo de mídia que deseja otimizar. Organizamos os itens ordenados do mais pesado ao mais leve para facilitar seu ganho de espaço.",
                                fontSize = 14.sp,
                                color = TextSecondary,
                                textAlign = TextAlign.Center
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                // Photos Card Option
                                Card(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(110.dp)
                                        .clickable {
                                            optimizerViewModel.setFilter(com.example.viewmodel.GalleryFilter.PHOTOS)
                                            showFilterDialog = false
                                            currentScreen = ActiveScreen.SWIPE_CLEANER
                                        },
                                    colors = CardDefaults.cardColors(containerColor = DarkBackground),
                                    shape = RoundedCornerShape(16.dp),
                                    border = BorderStroke(1.dp, CardBorder)
                                ) {
                                    Column(
                                        modifier = Modifier.fillMaxSize(),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Image,
                                            contentDescription = null,
                                            tint = PrimaryTeal,
                                            modifier = Modifier.size(32.dp)
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = "Fotos",
                                            fontWeight = FontWeight.Bold,
                                            color = TextPrimary,
                                            fontSize = 15.sp
                                        )
                                        Text(
                                            text = "Análise Rápida",
                                            color = TextMuted,
                                            fontSize = 11.sp
                                        )
                                    }
                                }

                                // Videos Card Option
                                Card(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(110.dp)
                                        .clickable {
                                            optimizerViewModel.setFilter(com.example.viewmodel.GalleryFilter.VIDEOS)
                                            showFilterDialog = false
                                            currentScreen = ActiveScreen.SWIPE_CLEANER
                                        },
                                    colors = CardDefaults.cardColors(containerColor = DarkBackground),
                                    shape = RoundedCornerShape(16.dp),
                                    border = BorderStroke(1.dp, CardBorder)
                                ) {
                                    Column(
                                        modifier = Modifier.fillMaxSize(),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.PlayCircle,
                                            contentDescription = null,
                                            tint = PrimaryTealDark,
                                            modifier = Modifier.size(32.dp)
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = "Vídeos",
                                            fontWeight = FontWeight.Bold,
                                            color = TextPrimary,
                                            fontSize = 15.sp
                                        )
                                        Text(
                                            text = "Mais Pesados",
                                            color = TextMuted,
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {},
                    dismissButton = {
                        TextButton(onClick = { showFilterDialog = false }) {
                            Text("Cancelar", color = TextSecondary)
                        }
                    }
                )
            }

            // CACHE CLEAN CONFIRM DIALOG
            if (showCacheCleanConfirmDialog) {
                AlertDialog(
                    onDismissRequest = { showCacheCleanConfirmDialog = false },
                    containerColor = CardBackground,
                    shape = RoundedCornerShape(24.dp),
                    title = {
                        Text("Limpar Cache Global?", fontWeight = FontWeight.Bold, color = TextPrimary)
                    },
                    text = {
                        Text(
                            text = "Essa ação removerá os arquivos temporários criados por todos os aplicativos do celular, liberando espaço rápido de armazenamento e otimizando o consumo de RAM. Ação extremamente segura.",
                            color = TextSecondary,
                            fontSize = 14.sp
                        )
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                showCacheCleanConfirmDialog = false
                                // Create synthetic clean state
                                val sampleCategories = listOf(
                                    TrashCategory("system_cache", "Cache do Sistema", "Arquivos temporários criados pelo SO", 1240 * 1024 * 1024L, "1.24 GB"),
                                    TrashCategory("apps_cache", "Cache de Aplicativos", "Imagens, feeds e downloads locais temporários", 2150 * 1024 * 1024L, "2.15 GB")
                                )
                                optimizerViewModel.performClearing(sampleCategories)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryTeal)
                        ) {
                            Text("Sim, Limpar", color = DarkBackground, fontWeight = FontWeight.Bold)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showCacheCleanConfirmDialog = false }) {
                            Text("Cancelar", color = TextSecondary)
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun DashboardView(
    viewModel: OptimizerViewModel,
    onStartSwipeCleaner: () -> Unit,
    onClearCacheTrigger: () -> Unit,
    onStartDeepClean: () -> Unit,
    onStartSqliteVacuum: () -> Unit
) {
    val scanState by viewModel.scanState.collectAsState()
    val ramUsage by viewModel.ramUsage.collectAsState()
    val storagePercent by viewModel.storageUsedPercent.collectAsState()
    val totalStorage by viewModel.totalDeviceStorageStr.collectAsState()
    val usedStorage by viewModel.usedDeviceStorageStr.collectAsState()
    val logs by viewModel.scanningLogs.collectAsState()
    val currentVersion by viewModel.currentAppVersion.collectAsState()
    val isOptimizerCleaned by viewModel.isOptimizerCleaned.collectAsState()
    val context = LocalContext.current

    var selectedCategoryIds = remember { mutableStateMapOf<String, Boolean>() }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 32.dp, top = 12.dp)
    ) {
        
        // 1. Storage & RAM meters row
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Storage Gauge Card
                StorageGauge(
                    percentage = storagePercent,
                    usedLabel = usedStorage,
                    totalLabel = totalStorage,
                    title = "ARMAZENAMENTO",
                    sizeDp = 136
                )

                // RAM Gauge Card
                StorageGauge(
                    percentage = ramUsage,
                    usedLabel = "${String.format("%.1f", (ramUsage.toFloat()/100f) * 8f)} GB",
                    totalLabel = "8.0 GB",
                    title = "MEMÓRIA RAM",
                    sizeDp = 136
                )
            }
        }

        // 2. Scan Activity State Router
        item {
            AnimatedContent(
                targetState = scanState,
                transitionSpec = {
                    fadeIn(tween(350)) togetherWith fadeOut(tween(350))
                },
                label = "ScanStateRouter"
            ) { state ->
                when (state) {
                    is ScanState.Idle -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                // Large shining custom pulse button
                                Box(
                                    modifier = Modifier
                                        .size(160.dp)
                                        .background(PrimaryTeal.copy(alpha = 0.05f), CircleShape)
                                        .clickable { viewModel.startOptimizationScan() },
                                    contentAlignment = Alignment.Center
                                ) {
                                    // Ripple border accents
                                    Box(
                                        modifier = Modifier
                                            .size(130.dp)
                                            .border(2.dp, PrimaryTeal.copy(alpha = 0.2f), CircleShape)
                                            .background(
                                                Brush.radialGradient(
                                                    colors = listOf(CardBackground, DarkBackground)
                                                ),
                                                CircleShape
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Cyclone,
                                            contentDescription = "Optimize Button logo",
                                            tint = PrimaryTeal,
                                            modifier = Modifier.size(54.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "Toque para Vistoriar o Celular",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "Varredura rápida em busca de arquivos desnecessários",
                                    fontSize = 12.sp,
                                    color = TextSecondary,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(horizontal = 24.dp)
                                )

                                if (isOptimizerCleaned) {
                                    Spacer(modifier = Modifier.height(24.dp))
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = AccentGreen.copy(alpha = 0.1f)),
                                        border = BorderStroke(1.dp, AccentGreen.copy(alpha = 0.3f)),
                                        shape = RoundedCornerShape(16.dp),
                                        modifier = Modifier.padding(horizontal = 16.dp)
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(14.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Text(
                                                text = "Seu cache físico está limpo! 🎉",
                                                fontWeight = FontWeight.Bold,
                                                color = AccentGreen,
                                                fontSize = 13.sp
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = "Como as regras do Android protegem os outros aplicativos de serem violados no sandbox, apenas o cache interno é excluído de fato do armazenamento.\n\nDeseja restaurar os arquivos temporários de demonstração (35 MB) nesta sandbox local para testar a rotina de varrimento novamente?",
                                                fontSize = 11.sp,
                                                color = TextPrimary,
                                                textAlign = TextAlign.Center,
                                                lineHeight = 15.sp
                                            )
                                            Spacer(modifier = Modifier.height(12.dp))
                                            Button(
                                                onClick = { viewModel.regenerateDebugCaches() },
                                                colors = ButtonDefaults.buttonColors(containerColor = AccentGreen),
                                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                                                shape = RoundedCornerShape(8.dp)
                                            ) {
                                                Text("GERAR 35MB DE CACHE PARA TESTE", color = DarkBackground, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    is ScanState.Scanning -> {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = CardBackground),
                            shape = RoundedCornerShape(24.dp),
                            border = BorderStroke(1.dp, CardBorder)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "SISTEMA EM VISTORIA",
                                    fontWeight = FontWeight.Black,
                                    color = PrimaryTeal,
                                    fontSize = 14.sp,
                                    letterSpacing = 1.sp
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                
                                PulseRadar(sizeDp = 160)
                                
                                Spacer(modifier = Modifier.height(20.dp))
                                LinearProgressIndicator(
                                    progress = { state.progress },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(4.dp)),
                                    color = PrimaryTeal,
                                    trackColor = CardBorder
                                )
                                Spacer(modifier = Modifier.height(14.dp))
                                Text(
                                    text = state.currentPackage,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = TextSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                Spacer(modifier = Modifier.height(12.dp))
                                // Inside scanning terminal-like live logs
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                                        .padding(12.dp)
                                ) {
                                    logs.forEach { log ->
                                        Text(
                                            text = "> $log",
                                            fontSize = 11.sp,
                                            color = AccentGreen,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.padding(vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    is ScanState.ScanCompleted -> {
                        // Scan results. Initialize categories map if empty
                        LaunchedEffect(state.categories) {
                            state.categories.forEach {
                                selectedCategoryIds[it.id] = it.isSelected
                            }
                        }

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = CardBackground),
                            shape = RoundedCornerShape(24.dp),
                            border = BorderStroke(1.dp, CardBorder)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = "RESULTADO DA VISTORIA",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = WarningOrange,
                                            letterSpacing = 0.5.sp
                                        )
                                        Text(
                                            text = "Lixo Encontrado: ${state.totalJunkSizeStr}",
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.Black,
                                            color = TextPrimary
                                        )
                                    }
                                    IconButton(onClick = { viewModel.resetScan() }) {
                                        Icon(
                                            imageVector = Icons.Default.Refresh,
                                            contentDescription = "Limpar resultado",
                                            tint = TextSecondary
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                if (state.categories.isEmpty()) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 16.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Done,
                                            contentDescription = null,
                                            tint = AccentGreen,
                                            modifier = Modifier.size(48.dp)
                                        )
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Text(
                                            text = "Seu celular está voando! 🚀",
                                            fontWeight = FontWeight.Bold,
                                            color = AccentGreen,
                                            fontSize = 15.sp
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "Toda a partição de lixo residual foi limpa e o sistema está completamente otimizado.",
                                            color = TextSecondary,
                                            fontSize = 12.sp,
                                            textAlign = TextAlign.Center
                                        )
                                        Spacer(modifier = Modifier.height(16.dp))
                                        Button(
                                            onClick = { viewModel.resetScan() },
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryTeal)
                                        ) {
                                            Text("SISTEMA OTIMIZADO - VOLTAR", fontWeight = FontWeight.Bold, color = DarkBackground)
                                        }
                                    }
                                } else {
                                    // List of found junk categories with checkbox selectors
                                    state.categories.forEach { cat ->
                                        val isChecked = selectedCategoryIds[cat.id] ?: true
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { selectedCategoryIds[cat.id] = !isChecked }
                                                .padding(vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Checkbox(
                                                checked = isChecked,
                                                onCheckedChange = { selectedCategoryIds[cat.id] = it },
                                                colors = CheckboxDefaults.colors(checkedColor = PrimaryTeal)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = cat.name,
                                                    fontWeight = FontWeight.Bold,
                                                    color = TextPrimary,
                                                    fontSize = 14.sp
                                                )
                                                Text(
                                                    text = cat.description,
                                                    fontSize = 11.sp,
                                                    color = TextSecondary
                                                )
                                            }
                                            Text(
                                                text = cat.sizeStr,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = WarningOrange,
                                                fontSize = 13.sp
                                            )
                                        }
                                        Divider(color = CardBorder, thickness = 0.5.dp)
                                    }

                                    Spacer(modifier = Modifier.height(12.dp))
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.3f)),
                                        border = BorderStroke(1.dp, CardBorder),
                                        shape = RoundedCornerShape(16.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(14.dp)
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    imageVector = Icons.Default.Info,
                                                    contentDescription = null,
                                                    tint = PrimaryTeal,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = "Nota de Otimização e Sandboxing",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 12.sp,
                                                    color = PrimaryTeal
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Text(
                                                text = "A segurança oficial do Android (desde a API 29+) estabelece uma proteção rígida chamada Sandbox. Isso impede fisicamente que qualquer otimizador de terceiros altere ou delete caches privados de outros apps como WhatsApp, Instagram ou Chrome.\n\nAqui, faremos a limpeza física total e real do cache de renderização interna gerado por esta própria aplicação. Para limpar caches de outros apps pesados instalados, use o atalho seguro abaixo para ir à Central do Android e efetuar a limpeza nativa de forma 100% oficial!",
                                                fontSize = 11.sp,
                                                color = TextSecondary,
                                                lineHeight = 15.sp
                                            )
                                            Spacer(modifier = Modifier.height(10.dp))
                                            OutlinedButton(
                                                onClick = {
                                                    try {
                                                        val intent = android.content.Intent(android.provider.Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS)
                                                        context.startActivity(intent)
                                                    } catch (e: Exception) {
                                                        Toast.makeText(context, "Erro ao abrir configurações.", Toast.LENGTH_SHORT).show()
                                                    }
                                                },
                                                modifier = Modifier.fillMaxWidth(),
                                                border = BorderStroke(1.dp, PrimaryTeal.copy(alpha = 0.5f)),
                                                shape = RoundedCornerShape(8.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Settings,
                                                    contentDescription = null,
                                                    tint = PrimaryTeal,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("LIMPAR OUTROS APPS NATIVAMENTE", color = PrimaryTeal, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(16.dp))

                                    val anySelected = selectedCategoryIds.values.any { it }
                                    Button(
                                        onClick = {
                                            val active = state.categories.filter { selectedCategoryIds[it.id] == true }
                                            viewModel.performClearing(active)
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        enabled = anySelected,
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = PrimaryTeal,
                                            disabledContainerColor = CardBorder
                                        )
                                    ) {
                                        Text(
                                            text = "EFETUAR LIMPEZA SELECIONADA",
                                            fontWeight = FontWeight.Black,
                                            color = DarkBackground,
                                            fontSize = 13.sp,
                                            letterSpacing = 0.5.sp
                                        )
                                    }
                                }
                            }
                        }
                    }

                    is ScanState.Cleaning -> {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = CardBackground),
                            shape = RoundedCornerShape(24.dp),
                            border = BorderStroke(1.dp, CardBorder)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(64.dp)
                                        .background(PrimaryTeal.copy(alpha = 0.1f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(
                                        progress = { state.progress },
                                        color = PrimaryTeal,
                                        strokeWidth = 4.dp,
                                        modifier = Modifier.size(52.dp)
                                    )
                                    Icon(
                                        imageVector = Icons.Default.CleaningServices,
                                        contentDescription = null,
                                        tint = PrimaryTeal,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "LIMPANDO DISPOSITIVO",
                                    fontWeight = FontWeight.Bold,
                                    color = PrimaryTeal,
                                    fontSize = 13.sp,
                                    letterSpacing = 1.sp
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Removendo files de: ${state.currentCategory}",
                                    fontSize = 14.sp,
                                    color = TextPrimary,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "Aguarde, liberando armazenamento físico...",
                                    fontSize = 11.sp,
                                    color = TextMuted
                                )
                            }
                        }
                    }

                    is ScanState.CleanCompleted -> {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = CardBackground),
                            shape = RoundedCornerShape(24.dp),
                            border = BorderStroke(1.dp, CardBorder)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(72.dp)
                                        .background(AccentGreen.copy(alpha = 0.12f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = "Limpeza bem-sucedida",
                                        tint = AccentGreen,
                                        modifier = Modifier.size(44.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "CELULAR EXCELENTEMENTE OTIMIZADO!",
                                    fontWeight = FontWeight.Black,
                                    color = AccentGreen,
                                    fontSize = 13.sp,
                                    letterSpacing = 0.5.sp,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Liberado: ${state.totalCleanedSizeStr}",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Black,
                                    color = TextPrimary
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Caches deletados, buffer liberado e RAM otimizada.",
                                    fontSize = 12.sp,
                                    color = TextSecondary,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(20.dp))
                                Button(
                                    onClick = { viewModel.resetScan() },
                                    colors = ButtonDefaults.buttonColors(containerColor = CardBorder),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("Entendido", color = TextPrimary)
                                }
                            }
                        }
                    }
                }
            }
        }

        // 3. Quick Otimização Avançada Section
        item {
            Text(
                text = "OTIMIZAÇÃO AVANÇADA",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = PrimaryTeal,
                modifier = Modifier.padding(top = 12.dp, start = 4.dp),
                letterSpacing = 1.sp
            )
        }

        // Card button 1: Clear application Cache directly
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onClearCacheTrigger() },
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, CardBorder)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(PrimaryTeal.copy(alpha = 0.08f), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteSweep,
                            contentDescription = null,
                            tint = PrimaryTeal
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Apagar Cache de Todos Aplicativos",
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            fontSize = 15.sp
                        )
                        Text(
                            text = "Limpe lixos e resíduos acumulados na memória",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowRight,
                        contentDescription = null,
                        tint = TextMuted
                    )
                }
            }
        }

        // Card button 2: Cleaner Swipe Tinder-style photos and videos
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onStartSwipeCleaner() },
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, CardBorder)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(PrimaryTealDark.copy(alpha = 0.08f), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.FilterFrames,
                            contentDescription = null,
                            tint = PrimaryTealDark
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Limpeza Inteligente de Fotos e Vídeos",
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            fontSize = 15.sp
                        )
                        Text(
                            text = "Arrastar para esquerda apaga, direita salva (Tinder style)",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.Swipe,
                        contentDescription = null,
                        tint = PrimaryTeal,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // Card button: Deep Clean
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onStartDeepClean() },
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, CardBorder)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(WarningOrange.copy(alpha = 0.08f), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CleaningServices,
                            contentDescription = null,
                            tint = WarningOrange
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Análise de Armazenamento Profundo",
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            fontSize = 15.sp
                        )
                        Text(
                            text = "Localizar arquivos gigantes redundantes, duplicados e logs órfãos",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowRight,
                        contentDescription = null,
                        tint = TextMuted
                    )
                }
            }
        }

        // Card button: SQLite Vacuum Defrag
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onStartSqliteVacuum() },
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, CardBorder)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(PrimaryTeal.copy(alpha = 0.08f), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Storage,
                            contentDescription = null,
                            tint = PrimaryTeal
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "SQLite Vacuum & Compressão",
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            fontSize = 15.sp
                        )
                        Text(
                            text = "Otimizar índices de bancos, liberar páginas de registros vazios",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowRight,
                        contentDescription = null,
                        tint = TextMuted
                    )
                }
            }
        }

        // Card button 3: In-App Update Checker with advanced local update link configuring
        item {
            var isExpanded by remember { mutableStateOf(false) }
            val customUrl by viewModel.customUpdateUrl.collectAsState()

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .animateContentSize(),
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, CardBorder)
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.checkForUpdates() }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .background(WarningOrange.copy(alpha = 0.08f), RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.SystemUpdate,
                                contentDescription = null,
                                tint = WarningOrange
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Verificar Atualizações",
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary,
                                fontSize = 15.sp
                            )
                            Text(
                                text = "Versão instalada: v$currentVersion. Clique para buscar atualizações.",
                                color = TextSecondary,
                                fontSize = 12.sp
                            )
                        }
                        IconButton(
                            onClick = { isExpanded = !isExpanded }
                        ) {
                            Icon(
                                imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.Settings,
                                contentDescription = "Configurações de link",
                                tint = PrimaryTeal,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    if (isExpanded) {
                        HorizontalDivider(color = CardBorder, thickness = 0.5.dp)
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Text(
                                text = "CANAL DE ATUALIZAÇÃO PERSONALIZADA",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = PrimaryTeal,
                                letterSpacing = 0.5.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Insira uma URL pública direta para o download de um APK real (como releases do GitHub, seu próprio servidor ou VPS) para testar a instalação real e sem simulações:",
                                fontSize = 11.sp,
                                color = TextSecondary,
                                lineHeight = 16.sp
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            TextField(
                                value = customUrl,
                                onValueChange = { viewModel.setCustomUpdateUrl(it) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, CardBorder, RoundedCornerShape(10.dp)),
                                placeholder = {
                                    Text(
                                        text = "https://seulink.com/direto/app.apk",
                                        color = TextMuted,
                                        fontSize = 12.sp
                                    )
                                },
                                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, color = TextPrimary),
                                maxLines = 1,
                                singleLine = true,
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Black.copy(alpha = 0.25f),
                                    unfocusedContainerColor = Color.Black.copy(alpha = 0.15f),
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent
                                ),
                                shape = RoundedCornerShape(10.dp)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Button(
                                onClick = { viewModel.checkForUpdates() },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = PrimaryTeal),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text(
                                    text = "VARRE ATUALIZAÇÃO COM ESTE LINK",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    color = DarkBackground
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SwipeGalleryCleaner(
    viewModel: OptimizerViewModel,
    onBackToDashboard: () -> Unit
) {
    val itemsQueue by viewModel.mediaQueue.collectAsState()
    val filterType by viewModel.currentFilter.collectAsState()

    val savedCount by viewModel.savedCount.collectAsState()
    val deletedCount by viewModel.deletedCount.collectAsState()
    val spaceSavedSum by viewModel.deletedFilesSizeSum.collectAsState()

    val formatSpaceSaved = remember(spaceSavedSum) {
        if (spaceSavedSum <= 0L) "0 B" else {
            val units = arrayOf("B", "KB", "MB", "GB", "TB")
            val digitGroups = (Math.log10(spaceSavedSum.toDouble()) / Math.log10(1024.0)).toInt()
            String.format("%.1f %s", spaceSavedSum / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        
        // Horizontal mini info summary header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
                .background(CardBackground.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                .border(0.5.dp, CardBorder, RoundedCornerShape(12.dp))
                .padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = when (filterType) {
                    com.example.viewmodel.GalleryFilter.PHOTOS -> "Filtrado: Apenas Fotos"
                    com.example.viewmodel.GalleryFilter.VIDEOS -> "Filtrado: Apenas Vídeos"
                    else -> "Todas Mídias"
                },
                color = PrimaryTeal,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            )
            Text(
                text = "Salvos: $savedCount • Apagados: $deletedCount",
                color = TextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }

        // Swiper deck box
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            if (itemsQueue.isNotEmpty()) {
                // We display cards layered. We can display the top card or index based stack!
                // To keep it clean, show the very first item of the array (front-most in FIFO queue)
                val topItem = itemsQueue.first()

                SwipeCard(
                    item = topItem,
                    onSwipeLeft = { viewModel.swipeLeft(it) },
                    onSwipeRight = { viewModel.swipeRight(it) },
                    modifier = Modifier.padding(12.dp)
                )
            } else {
                // Completed empty state
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .background(AccentGreen.copy(alpha = 0.12f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudDone,
                            contentDescription = null,
                            tint = AccentGreen,
                            modifier = Modifier.size(38.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Fila Avaliada!",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Você revisou todas as mídias da fila selecionada nesta sessão com sucesso.",
                        fontSize = 12.sp,
                        color = TextSecondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )

                    Spacer(modifier = Modifier.height(20.dp))
                    // Performance box summary
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                        colors = CardDefaults.cardColors(containerColor = CardBackground),
                        border = BorderStroke(1.dp, CardBorder)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "RESULTADO DESTA LIMPEZA",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = PrimaryTeal,
                                letterSpacing = 1.sp
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceAround
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("Arquivados", color = TextSecondary, fontSize = 12.sp)
                                    Text("$savedCount", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = AccentGreen)
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("Deletados", color = TextSecondary, fontSize = 12.sp)
                                    Text("$deletedCount", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = AccentRed)
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("Espaço Ganhado", color = TextSecondary, fontSize = 12.sp)
                                    Text(formatSpaceSaved, fontWeight = FontWeight.Black, fontSize = 16.sp, color = PrimaryTeal)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = { onBackToDashboard() },
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryTeal)
                    ) {
                        Text("Voltar ao Painel", color = DarkBackground, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Accessible explicit bottom controllers (In case users don't want to drag or can't!)
        if (itemsQueue.isNotEmpty()) {
            val topItem = itemsQueue.first()
            val scope = rememberCoroutineScope()

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Red delete button (Left action trigger)
                Button(
                    onClick = {
                        scope.launch {
                            viewModel.swipeLeft(topItem)
                        }
                    },
                    modifier = Modifier
                        .size(64.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentRed),
                    shape = CircleShape,
                    contentPadding = PaddingValues(0.dp),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Botão Excluir",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Text(
                    text = "Arraste ou Clique",
                    color = TextMuted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )

                // Green save/keep button (Right action trigger)
                Button(
                    onClick = {
                        scope.launch {
                            viewModel.swipeRight(topItem)
                        }
                    },
                    modifier = Modifier
                        .size(64.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentGreen),
                    shape = CircleShape,
                    contentPadding = PaddingValues(0.dp),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Botão Salvar",
                        tint = DarkBackground,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun DeepCleanView(
    viewModel: OptimizerViewModel,
    onBackToDashboard: () -> Unit
) {
    val deepState by viewModel.deepCleanState.collectAsState()

    // Smooth entry: start scanning if idle
    LaunchedEffect(Unit) {
        if (deepState is DeepCleanState.Idle) {
            viewModel.startDeepCleanScan()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        when (val state = deepState) {
            is DeepCleanState.Idle, is DeepCleanState.Scanning -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    PulseRadar(sizeDp = 180)
                    Spacer(modifier = Modifier.height(32.dp))
                    Text(
                        text = "VISTORIANDO ARMAZENAMENTO PROFUNDO",
                        fontWeight = FontWeight.Black,
                        color = WarningOrange,
                        fontSize = 15.sp,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Analisando partições extensas, backups obsoletos e arquivos clonados...",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                }
            }

            is DeepCleanState.ScanCompleted -> {
                val selectedCount = state.items.count { it.isSelected }
                val totalSelectedBytes = state.items.filter { it.isSelected }.sumOf { it.sizeBytes }
                val totalSelectedSizeStr = if (totalSelectedBytes <= 0) "0 B" else {
                    val units = arrayOf("B", "KB", "MB", "GB", "TB")
                    val digitGroups = (Math.log10(totalSelectedBytes.toDouble()) / Math.log10(1024.0)).toInt()
                    String.format("%.2f %s", totalSelectedBytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
                }

                Column(
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Header summary alert card
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 10.dp),
                        colors = CardDefaults.cardColors(containerColor = CardBackground),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, CardBorder)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(WarningOrange.copy(alpha = 0.12f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = WarningOrange,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text(
                                    text = "Varredura Concluída",
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary,
                                    fontSize = 15.sp
                                )
                                Text(
                                    text = "${state.items.size} arquivos pesados identificados no disco.",
                                    color = TextSecondary,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }

                    // Files list scroll container
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(bottom = 16.dp, top = 8.dp)
                    ) {
                        items(state.items) { file ->
                            val categoryColor = when (file.category) {
                                "residual" -> AccentRed
                                "duplicates" -> PrimaryTeal
                                "large_media" -> WarningOrange
                                "downloads" -> Color(0xFF52E5E7)
                                else -> TextSecondary
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(CardBackground, RoundedCornerShape(16.dp))
                                    .border(1.dp, if (file.isSelected) categoryColor.copy(alpha = 0.4f) else CardBorder, RoundedCornerShape(16.dp))
                                    .clickable { viewModel.toggleDeepCleanFileSelection(file.id) }
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Category Icon Indicator or Real Photo Thumbnail
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(categoryColor.copy(alpha = 0.08f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (file.realUri != null) {
                                        coil.compose.AsyncImage(
                                            model = file.realUri,
                                            contentDescription = file.name,
                                            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else {
                                        val icon = when (file.category) {
                                            "residual" -> Icons.Default.Folder
                                            "duplicates" -> Icons.Default.Share
                                            "large_media" -> Icons.Default.PlayArrow
                                            "downloads" -> Icons.Default.CloudDownload
                                            else -> Icons.Default.Info
                                        }
                                        Icon(
                                            imageVector = icon,
                                            contentDescription = null,
                                            tint = categoryColor,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = file.name,
                                            fontWeight = FontWeight.Bold,
                                            color = TextPrimary,
                                            fontSize = 14.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = file.sizeStr,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = WarningOrange,
                                            fontSize = 13.sp
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = file.description,
                                        color = TextSecondary,
                                        fontSize = 11.sp,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Checkbox(
                                    checked = file.isSelected,
                                    onCheckedChange = { viewModel.toggleDeepCleanFileSelection(file.id) },
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = categoryColor,
                                        uncheckedColor = TextMuted,
                                        checkmarkColor = DarkBackground
                                    )
                                )
                            }
                        }
                    }

                    // Bottom Action Sheet Box
                    Surface(
                        color = CardBackground,
                        tonalElevation = 8.dp,
                        border = BorderStroke(1.dp, CardBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Selecionado: $selectedCount arquivos",
                                    color = TextSecondary,
                                    fontSize = 13.sp
                                )
                                Text(
                                    text = totalSelectedSizeStr,
                                    fontWeight = FontWeight.Black,
                                    color = WarningOrange,
                                    fontSize = 18.sp
                                )
                            }

                            Button(
                                onClick = { viewModel.performDeepClean() },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = selectedCount > 0,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = WarningOrange,
                                    disabledContainerColor = CardBorder
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = null,
                                    tint = DarkBackground
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "DELETAR SELECIONADOS PERMANENTEMENTE",
                                    fontWeight = FontWeight.Black,
                                    fontSize = 13.sp,
                                    color = DarkBackground
                                )
                            }
                        }
                    }
                }
            }

            is DeepCleanState.Cleaning -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(
                        color = WarningOrange,
                        strokeWidth = 5.dp,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = "PURGANDO ARQUIVOS SELECIONADOS",
                        fontWeight = FontWeight.Black,
                        color = TextPrimary,
                        fontSize = 15.sp,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Liberando partições físicas e desvinculando arquivos permanentemente...",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }

            is DeepCleanState.CleanCompleted -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .background(AccentGreen.copy(alpha = 0.1f), CircleShape)
                            .border(2.dp, AccentGreen, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Done,
                            contentDescription = null,
                            tint = AccentGreen,
                            modifier = Modifier.size(56.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(32.dp))

                    Text(
                        text = "LIMPEZA PROFUNDA COMPLETA!",
                        fontWeight = FontWeight.Black,
                        color = TextPrimary,
                        fontSize = 20.sp,
                        letterSpacing = 1.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Você liberou com sucesso:",
                        color = TextSecondary,
                        fontSize = 14.sp
                    )

                    Text(
                        text = state.totalCleanedSizeStr,
                        fontWeight = FontWeight.Black,
                        color = AccentGreen,
                        fontSize = 32.sp,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )

                    Text(
                        text = "Seu armazenamento físico de alto desempenho foi otimizado de forma robusta.",
                        color = TextMuted,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )

                    Spacer(modifier = Modifier.height(48.dp))

                    Button(
                        onClick = { onBackToDashboard() },
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryTeal),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("OK, VOLTAR", color = DarkBackground, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun SqliteVacuumView(
    viewModel: OptimizerViewModel,
    onBackToDashboard: () -> Unit
) {
    val vacuumState by viewModel.vacuumState.collectAsState()

    LaunchedEffect(Unit) {
        if (vacuumState is VacuumState.Idle) {
            viewModel.runSqliteVacuum()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        when (val state = vacuumState) {
            is VacuumState.Idle, is VacuumState.Analyzing -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    PulseRadar(sizeDp = 180)
                    Spacer(modifier = Modifier.height(32.dp))
                    Text(
                        text = "ANALISANDO BANCOS DE DADOS LOCAL",
                        fontWeight = FontWeight.Black,
                        color = PrimaryTeal,
                        fontSize = 15.sp,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Escaneando índices SQLite fragmentados e identificando vazamentos de páginas...",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }

            is VacuumState.Reorganizing -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "REORGANIZANDO CLUSTER DE DADOS",
                        fontWeight = FontWeight.Black,
                        color = PrimaryTeal,
                        fontSize = 15.sp,
                        letterSpacing = 1.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = state.currentTable,
                        color = TextSecondary,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )

                    Spacer(modifier = Modifier.height(28.dp))

                    // Real retro-cool defragmentation block grid visualization!
                    val totalBlocks = 36
                    val activeProgressBlocks = (state.progress * totalBlocks).toInt()

                    Column(
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                            .border(1.dp, CardBorder, RoundedCornerShape(16.dp))
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        for (row in 0..5) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                for (col in 0..5) {
                                    val blockIdx = row * 6 + col
                                    val color = when {
                                        blockIdx < activeProgressBlocks -> PrimaryTeal
                                        blockIdx == activeProgressBlocks -> AccentGreen
                                        else -> CardBorder
                                    }
                                    Box(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .background(color, RoundedCornerShape(4.dp))
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    LinearProgressIndicator(
                        progress = { state.progress },
                        modifier = Modifier
                            .width(200.dp)
                            .height(6.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = PrimaryTeal,
                        trackColor = CardBorder
                    )
                }
            }

            is VacuumState.Completed -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(90.dp)
                            .background(PrimaryTeal.copy(alpha = 0.1f), CircleShape)
                            .border(2.dp, PrimaryTeal, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Done,
                            contentDescription = null,
                            tint = PrimaryTeal,
                            modifier = Modifier.size(50.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Text(
                        text = "COMPRESSÃO SQLITE EXECUTADA!",
                        fontWeight = FontWeight.Black,
                        color = TextPrimary,
                        fontSize = 18.sp,
                        letterSpacing = 1.sp
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    // Side-by-side metric tiles
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Card(
                            modifier = Modifier.weight(1f),
                            colors = CardDefaults.cardColors(containerColor = CardBackground),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(0.5.dp, CardBorder)
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("Tamanho Anterior", color = TextSecondary, fontSize = 11.sp)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(state.sizeBefore, fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 16.sp)
                            }
                        }

                        Card(
                            modifier = Modifier.weight(1f),
                            colors = CardDefaults.cardColors(containerColor = CardBackground),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(0.5.dp, CardBorder)
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("Tamanho Atual", color = TextSecondary, fontSize = 11.sp)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(state.sizeAfter, fontWeight = FontWeight.Bold, color = AccentGreen, fontSize = 16.sp)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = CardBackground),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(0.5.dp, CardBorder)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Espaço Puro Recuperado", color = TextSecondary, fontSize = 11.sp)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(state.freedSpace, fontWeight = FontWeight.Black, color = PrimaryTeal, fontSize = 24.sp)
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Text("Registros Alinhados", color = TextSecondary, fontSize = 11.sp)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("${state.reindexedCount} índices", fontWeight = FontWeight.Bold, color = AccentGreen, fontSize = 16.sp)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Vantagens: Desfragmentação completa de índices cruzados, purga de páginas órfãs do banco privado do app e acesso otimizado em leitores SQL.",
                        color = TextMuted,
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )

                    Spacer(modifier = Modifier.height(40.dp))

                    Button(
                        onClick = { onBackToDashboard() },
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryTeal),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("CONCLUIR", color = DarkBackground, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
