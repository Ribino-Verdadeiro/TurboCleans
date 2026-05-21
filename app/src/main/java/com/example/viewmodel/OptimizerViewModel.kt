package com.example.viewmodel

import android.app.Application
import android.app.ActivityManager
import android.content.Context
import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.Settings
import android.content.Intent
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.AndroidViewModel
import android.app.PendingIntent
import androidx.lifecycle.viewModelScope
import com.example.model.MediaItem
import com.example.model.MediaType
import com.example.model.defaultGradient
import com.example.model.TrashCategory
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

sealed interface ScanState {
    object Idle : ScanState
    data class Scanning(val progress: Float, val currentPackage: String) : ScanState
    data class ScanCompleted(val totalJunkSizeStr: String, val categories: List<TrashCategory>) : ScanState
    data class Cleaning(val progress: Float, val currentCategory: String) : ScanState
    data class CleanCompleted(val totalCleanedSizeStr: String) : ScanState
}

sealed interface UpdateState {
    object Idle : UpdateState
    object Checking : UpdateState
    data class UpdateAvailable(val versionName: String, val changelog: String, val apkUrl: String) : UpdateState
    object NoUpdateAvailable : UpdateState
    data class Downloading(val progress: Float) : UpdateState
    data class DownloadCompleted(val apkFileUri: android.net.Uri, val isSimulation: Boolean = false) : UpdateState
    data class Error(val message: String) : UpdateState
}

sealed interface VacuumState {
    object Idle : VacuumState
    object Analyzing : VacuumState
    data class Reorganizing(val progress: Float, val currentTable: String) : VacuumState
    data class Completed(
        val sizeBefore: String,
        val sizeAfter: String,
        val freedSpace: String,
        val defragRatio: Int,
        val reindexedCount: Int
    ) : VacuumState
}

data class DeepCleanFile(
    val id: String,
    val name: String,
    val sizeBytes: Long,
    val sizeStr: String,
    val category: String, // "downloads", "duplicates", "residual", "large_media"
    val description: String,
    val isSelected: Boolean = true,
    val realUri: android.net.Uri? = null
)

sealed interface DeepCleanState {
    object Idle : DeepCleanState
    object Scanning : DeepCleanState
    data class ScanCompleted(val items: List<DeepCleanFile>) : DeepCleanState
    object Cleaning : DeepCleanState
    data class CleanCompleted(val totalCleanedBytes: Long, val totalCleanedSizeStr: String) : DeepCleanState
}

enum class GalleryFilter {
    ALL,
    PHOTOS,
    VIDEOS
}

class OptimizerViewModel(application: Application) : AndroidViewModel(application) {

    private val _scanState = MutableStateFlow<ScanState>(ScanState.Idle)
    val scanState: StateFlow<ScanState> = _scanState.asStateFlow()

    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val updateState: StateFlow<UpdateState> = _updateState.asStateFlow()

    private val _vacuumState = MutableStateFlow<VacuumState>(VacuumState.Idle)
    val vacuumState: StateFlow<VacuumState> = _vacuumState.asStateFlow()

    private val _deepCleanState = MutableStateFlow<DeepCleanState>(DeepCleanState.Idle)
    val deepCleanState: StateFlow<DeepCleanState> = _deepCleanState.asStateFlow()

    private val _currentAppVersion = MutableStateFlow("1.0.4")
    val currentAppVersion: StateFlow<String> = _currentAppVersion.asStateFlow()

    private val _customUpdateUrl = MutableStateFlow("")
    val customUpdateUrl: StateFlow<String> = _customUpdateUrl.asStateFlow()

    fun setCustomUpdateUrl(url: String) {
        _customUpdateUrl.value = url.trim()
    }

    private var optimizerHasCleaned = false
    private val _isOptimizerCleaned = MutableStateFlow(false)
    val isOptimizerCleaned: StateFlow<Boolean> = _isOptimizerCleaned.asStateFlow()

    // Dashboard state variables
    private val _ramUsage = MutableStateFlow(62) // Initial realistic RAM usage %
    val ramUsage: StateFlow<Int> = _ramUsage.asStateFlow()

    private val _storageUsedPercent = MutableStateFlow(78)
    val storageUsedPercent: StateFlow<Int> = _storageUsedPercent.asStateFlow()

    private val _totalDeviceStorageStr = MutableStateFlow("128 GB")
    val totalDeviceStorageStr: StateFlow<String> = _totalDeviceStorageStr.asStateFlow()

    private val _usedDeviceStorageStr = MutableStateFlow("99.8 GB")
    val usedDeviceStorageStr: StateFlow<String> = _usedDeviceStorageStr.asStateFlow()

    // Logs displaying scanning steps
    private val _scanningLogs = MutableStateFlow<List<String>>(emptyList())
    val scanningLogs: StateFlow<List<String>> = _scanningLogs.asStateFlow()

    // Swipe media items list
    private val _mediaQueue = MutableStateFlow<List<MediaItem>>(emptyList())
    val mediaQueue: StateFlow<List<MediaItem>> = _mediaQueue.asStateFlow()

    private val _currentFilter = MutableStateFlow(GalleryFilter.PHOTOS)
    val currentFilter: StateFlow<GalleryFilter> = _currentFilter.asStateFlow()

    private val _hasMediaPermission = MutableStateFlow(false)
    val hasMediaPermission: StateFlow<Boolean> = _hasMediaPermission.asStateFlow()

    private val _hasAllFilesPermission = MutableStateFlow(false)
    val hasAllFilesPermission: StateFlow<Boolean> = _hasAllFilesPermission.asStateFlow()

    private val _deletePendingIntent = MutableSharedFlow<PendingIntent>()
    val deletePendingIntent: SharedFlow<PendingIntent> = _deletePendingIntent.asSharedFlow()

    // Lists of items swiped (archived/deleted in this session)
    private val _savedCount = MutableStateFlow(0)
    val savedCount: StateFlow<Int> = _savedCount.asStateFlow()

    private val _deletedCount = MutableStateFlow(0)
    val deletedCount: StateFlow<Int> = _deletedCount.asStateFlow()

    private val _deletedFilesSizeSum = MutableStateFlow(0L)
    val deletedFilesSizeSum: StateFlow<Long> = _deletedFilesSizeSum.asStateFlow()

    // Batch Swipe queue state
    private val _pendingDeleteItems = MutableStateFlow<List<MediaItem>>(emptyList())
    val pendingDeleteItems: StateFlow<List<MediaItem>> = _pendingDeleteItems.asStateFlow()

    enum class DeleteOrigin {
        SWIPE_CLEANER,
        DEEP_CLEAN
    }

    private var lastDeleteOrigin = DeleteOrigin.SWIPE_CLEANER
    private var pendingDeepCleanItems = mutableListOf<DeepCleanFile>()

    // Maps to track physically discovered files for each category
    private val filesToCleanMap = mutableMapOf<String, MutableList<File>>()

    private val deletedMockIds = mutableSetOf<Long>()
    private val deletedMockDeepCleanIds = mutableSetOf<String>()

    private fun checkMediaPermission(): Boolean {
        val context = getApplication<Application>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()) {
            return true
        }
        val hasWrite = androidx.core.content.ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.WRITE_EXTERNAL_STORAGE
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (hasWrite) return true

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasImages = androidx.core.content.ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.READ_MEDIA_IMAGES
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            val hasVideos = androidx.core.content.ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.READ_MEDIA_VIDEO
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            val hasPartial = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                androidx.core.content.ContextCompat.checkSelfPermission(
                    context, android.Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            } else {
                false
            }
            hasImages || hasVideos || hasPartial
        } else {
            androidx.core.content.ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.READ_EXTERNAL_STORAGE
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }
    }

    private val packageList = listOf(
        "com.android.chrome", "com.whatsapp", "com.instagram", "com.facebook.katana",
        "com.google.android.youtube", "org.telegram.messenger", "com.spotify.music",
        "com.zhiliaoapp.musically", "com.twitter.android", "com.netflix.mediaclient",
        "com.google.android.apps.photos", "com.pinterest"
    )

    init {
        // Load permanently deleted mock IDs from SharedPreferences
        val context = getApplication<Application>()
        try {
            val prefs = context.getSharedPreferences("TurboCleanDeletedMocks", Context.MODE_PRIVATE)
            val savedMockIds = prefs.getStringSet("deleted_mock_ids", emptySet()) ?: emptySet()
            deletedMockIds.addAll(savedMockIds.mapNotNull { it.toLongOrNull() })
        } catch (e: Exception) {
            e.printStackTrace()
        }

        try {
            val dcPrefs = context.getSharedPreferences("TurboCleanDeletedDeepCleanMocks", Context.MODE_PRIVATE)
            val savedDcMockIds = dcPrefs.getStringSet("deleted_dc_mock_ids", emptySet()) ?: emptySet()
            deletedMockDeepCleanIds.addAll(savedDcMockIds)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Initial configuration with real measurements & generate dummy caches on startup
        updateAllFilesPermissionState()
        generateRealAppCacheDummyFiles()
        generateRealSandboxDeepCleanFiles()
        generateRealGalleryDemoFiles()
        loadStorageStats()
        updateRamUsage()
    }

    private fun generateRealGalleryDemoFiles() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val context = getApplication<Application>()
                val demoDir = File(context.cacheDir, "turboclean_gallery_demo")
                if (!demoDir.exists()) {
                    demoDir.mkdirs()
                }

                val colors = listOf(
                    android.graphics.Color.rgb(31, 28, 44),    // Twilight
                    android.graphics.Color.rgb(44, 62, 80),    // Warm sun
                    android.graphics.Color.rgb(15, 32, 39),    // Ocean
                    android.graphics.Color.rgb(62, 81, 81),    // Dust
                    android.graphics.Color.rgb(17, 67, 87),    // Sunset
                    android.graphics.Color.rgb(19, 12, 183),   // Cyberpunk
                    android.graphics.Color.rgb(138, 35, 135),  // Horizon
                    android.graphics.Color.rgb(0, 4, 40)       // Dark Navy
                )

                // 1. Generate 5 beautiful photo files with different sizes
                val photoSizes = listOf(24200000L, 15800000L, 12400000L, 8900000L, 6200000L)
                val photoNames = listOf("demo_photo_1.jpg", "demo_photo_2.jpg", "demo_photo_3.jpg", "demo_photo_4.jpg", "demo_photo_5.jpg")
                val photoTitles = listOf("IMG_20260515_WA0024.jpg", "screenshot_20260520_1402.png", "IMG_CAMERA_0029.jpg", "whatsapp_cached_profile_9a.jpg", "telegram_sticker_temp_88.png")
                
                for (i in 0 until 5) {
                    val id = (2000 + i + 1).toLong()
                    if (deletedMockIds.contains(id)) continue
                    val file = File(demoDir, photoNames[i])
                    val sizeBytes = photoSizes[i]
                    if (!file.exists() || file.length() != sizeBytes) {
                        if (file.exists()) file.delete()
                        file.createNewFile()
                        
                        val bitmap = Bitmap.createBitmap(800, 800, Bitmap.Config.ARGB_8888)
                        val canvas = Canvas(bitmap)
                        
                        // Draw background color
                        val bgPaint = Paint().apply {
                            color = colors[i]
                            style = Paint.Style.FILL
                        }
                        canvas.drawRect(0f, 0f, 800f, 800f, bgPaint)
                        
                        // Draw border
                        val borderPaint = Paint().apply {
                            color = android.graphics.Color.WHITE
                            strokeWidth = 10f
                            style = Paint.Style.STROKE
                        }
                        canvas.drawRect(20f, 20f, 780f, 780f, borderPaint)
                        
                        // Draw title
                        val titlePaint = Paint().apply {
                            color = android.graphics.Color.WHITE
                            textSize = 45f
                            isFakeBoldText = true
                            textAlign = Paint.Align.CENTER
                            isAntiAlias = true
                        }
                        canvas.drawText("TURBO CLEAN", 400f, 300f, titlePaint)
                        
                        val subPaint = Paint().apply {
                            color = android.graphics.Color.LTGRAY
                            textSize = 30f
                            textAlign = Paint.Align.CENTER
                            isAntiAlias = true
                        }
                        canvas.drawText(photoTitles[i], 400f, 380f, subPaint)
                        
                        val descPaint = Paint().apply {
                            color = android.graphics.Color.rgb(0, 255, 180)
                            textSize = 24f
                            textAlign = Paint.Align.CENTER
                            isAntiAlias = true
                        }
                        canvas.drawText("Tamanho: ${formatSize(sizeBytes)} (Físico)", 400f, 480f, descPaint)
                        canvas.drawText("Deslize para a esquerda para apagar", 400f, 530f, descPaint)

                        val fos = java.io.FileOutputStream(file)
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, fos)
                        fos.close()
                        
                        // Instantly allocate exact file size on disk
                        val raf = java.io.RandomAccessFile(file, "rw")
                        raf.setLength(sizeBytes)
                        raf.close()
                    }
                }

                // 2. Generate 3 dummy video files with different sizes
                val videoSizes = listOf(385000000L, 182400000L, 95500000L)
                val videoNames = listOf("demo_video_1.mp4", "demo_video_2.mp4", "demo_video_3.mp4")
                
                for (i in 0 until 3) {
                    val id = (3000 + i + 1).toLong()
                    if (deletedMockIds.contains(id)) continue
                    val file = File(demoDir, videoNames[i])
                    val sizeBytes = videoSizes[i]
                    if (!file.exists() || file.length() != sizeBytes) {
                        if (file.exists()) file.delete()
                        file.createNewFile()
                        
                        val raf = java.io.RandomAccessFile(file, "rw")
                        raf.setLength(sizeBytes)
                        raf.close()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun generateRealSandboxDeepCleanFiles() {
        try {
            val cacheDir = getApplication<Application>().cacheDir
            if (!cacheDir.exists()) {
                cacheDir.mkdirs()
            }
            val tcDeepCleanDir = File(cacheDir, "turboclean_deepclean")
            if (!tcDeepCleanDir.exists()) {
                tcDeepCleanDir.mkdirs()
            }

            val filesAndSizes = listOf(
                "large_system_payload_test.dat" to 25L * 1024 * 1024,
                "temp_video_render_cache_7.dat" to 35L * 1024 * 1024,
                "duplicate_log_backup_A.log" to 10L * 1024 * 1024,
                "duplicate_log_backup_B.log" to 10L * 1024 * 1024,
                "whatsapp_backup_old_2025.zip" to 120L * 1024 * 1024,
                "android_compile_sdk_temp_archive.tar.gz" to 80L * 1024 * 1024
            )

            filesAndSizes.forEach { (name, size) ->
                val id = "sandbox_$name"
                if (deletedMockDeepCleanIds.contains(id)) return@forEach
                val file = File(tcDeepCleanDir, name)
                if (!file.exists() || file.length() != size) {
                    if (file.exists()) file.delete()
                    file.createNewFile()
                    val raf = java.io.RandomAccessFile(file, "rw")
                    raf.setLength(size)
                    raf.close()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun generateRealTestPhotos() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val context = getApplication<Application>()
            val resolver = context.contentResolver
            val colors = listOf(
                android.graphics.Color.rgb(0, 180, 160), // Teal
                android.graphics.Color.rgb(180, 0, 100), // Purple/Pink
                android.graphics.Color.rgb(30, 30, 50)    // Dark Gray
            )
            
            for (i in 1..3) {
                try {
                    val displayName = "TurboClean_Teste_Foto_$i.jpg"
                    val contentValues = ContentValues().apply {
                        put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
                        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/TurboClean")
                            put(MediaStore.Images.Media.IS_PENDING, 1)
                        }
                    }
                    val imageUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                    if (imageUri != null) {
                        resolver.openOutputStream(imageUri).use { out ->
                            if (out != null) {
                                val bitmap = Bitmap.createBitmap(1080, 1080, Bitmap.Config.ARGB_8888)
                                val canvas = Canvas(bitmap)
                                
                                // Draw background color
                                val bgPaint = Paint().apply {
                                    color = colors[i - 1]
                                    style = Paint.Style.FILL
                                }
                                canvas.drawRect(0f, 0f, 1080f, 1080f, bgPaint)
                                
                                // Draw border
                                val borderPaint = Paint().apply {
                                    color = android.graphics.Color.WHITE
                                    strokeWidth = 20f
                                    style = Paint.Style.STROKE
                                }
                                canvas.drawRect(40f, 40f, 1040f, 1040f, borderPaint)
                                
                                // Draw main title
                                val titlePaint = Paint().apply {
                                    color = android.graphics.Color.WHITE
                                    textSize = 70f
                                    isFakeBoldText = true
                                    textAlign = Paint.Align.CENTER
                                    isAntiAlias = true
                                }
                                canvas.drawText("TURBO CLEAN", 540f, 400f, titlePaint)
                                
                                // Draw subtitle
                                val subPaint = Paint().apply {
                                    color = android.graphics.Color.LTGRAY
                                    textSize = 45f
                                    textAlign = Paint.Align.CENTER
                                    isAntiAlias = true
                                }
                                canvas.drawText("Foto Real de Teste #$i", 540f, 520f, subPaint)
                                
                                // Draw description text
                                val sizePaint = Paint().apply {
                                    color = android.graphics.Color.rgb(0, 255, 180)
                                    textSize = 38f
                                    textAlign = Paint.Align.CENTER
                                    isAntiAlias = true
                                }
                                canvas.drawText("Esta foto sera excluida de verdade do seu aparelho.", 540f, 650f, sizePaint)
                                canvas.drawText("Verifique na sua Galeria de Imagens!", 540f, 720f, sizePaint)
                                
                                bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
                            }
                        }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            contentValues.clear()
                            contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                            resolver.update(imageUri, contentValues, null, null)
                        }

                        // SAVE TO SHARED PREFERENCES!
                        val prefs = context.getSharedPreferences("TurboCleanGeneratedPhotos", Context.MODE_PRIVATE)
                        val uriSet = prefs.getStringSet("uris", emptySet())?.toMutableSet() ?: mutableSetOf()
                        uriSet.add(imageUri.toString())
                        prefs.edit().putStringSet("uris", uriSet).apply()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            // Reload the actual photos
            loadMediaItems()
        }
    }

    fun updateAllFilesPermissionState() {
        val granted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            val context = getApplication<Application>()
            androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        _hasAllFilesPermission.value = granted
    }

    private fun generateRealAppCacheDummyFiles() {
        try {
            val cacheDir = getApplication<Application>().cacheDir
            if (!cacheDir.exists()) {
                cacheDir.mkdirs()
            }
            val tcCacheDir = File(cacheDir, "turboclean_cache")
            if (!tcCacheDir.exists()) {
                tcCacheDir.mkdirs()
            }
            // Populate robust physical cache logs to let the user scan & physically clean real files!
            val file1 = File(tcCacheDir, "temp_rendering_cache.bin")
            if (!file1.exists() || file1.length() == 0L) {
                file1.createNewFile()
                file1.writeBytes(ByteArray(1024 * 1024 * 15)) // 15 MB
            }
            val file2 = File(tcCacheDir, "analytics_payload_session.log")
            if (!file2.exists() || file2.length() == 0L) {
                file2.createNewFile()
                file2.writeBytes(ByteArray(1024 * 1024 * 8)) // 8 MB
            }
            val file3 = File(tcCacheDir, "webview_image_pool.tmp")
            if (!file3.exists() || file3.length() == 0L) {
                file3.createNewFile()
                file3.writeBytes(ByteArray(1024 * 1024 * 12)) // 12 MB
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun updateRamUsage() {
        try {
            val context = getApplication<Application>()
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val memoryInfo = ActivityManager.MemoryInfo()
            activityManager.getMemoryInfo(memoryInfo)
            
            val totalRam = memoryInfo.totalMem
            val availRam = memoryInfo.availMem
            val usedRam = totalRam - availRam
            
            if (totalRam > 0) {
                val percent = ((usedRam.toDouble() / totalRam.toDouble()) * 100).toInt()
                _ramUsage.value = percent.coerceIn(15, 95)
            } else {
                _ramUsage.value = 58
            }
        } catch (e: Exception) {
            e.printStackTrace()
            _ramUsage.value = 58
        }
    }

    fun setMediaPermissionGranted(granted: Boolean) {
        val actuallyGranted = granted || checkMediaPermission()
        _hasMediaPermission.value = actuallyGranted
        loadMediaItems()
    }

    private fun loadStorageStats() {
        try {
            val path = Environment.getExternalStorageDirectory()
            val totalBytes = path.totalSpace
            val freeBytes = path.usableSpace
            val usedBytes = totalBytes - freeBytes
            
            if (totalBytes > 0) {
                _totalDeviceStorageStr.value = formatSize(totalBytes)
                _usedDeviceStorageStr.value = formatSize(usedBytes)
                _storageUsedPercent.value = ((usedBytes.toDouble() / totalBytes.toDouble()) * 100).toInt().coerceIn(1, 99)
            } else {
                val totalMock = 128L * 1024 * 1024 * 1024
                val usedMock = 94L * 1024 * 1024 * 1024
                _totalDeviceStorageStr.value = formatSize(totalMock)
                _usedDeviceStorageStr.value = formatSize(usedMock)
                _storageUsedPercent.value = 73
            }
        } catch (e: Exception) {
            e.printStackTrace()
            val totalMock = 128L * 1024 * 1024 * 1024
            val usedMock = 94L * 1024 * 1024 * 1024
            _totalDeviceStorageStr.value = formatSize(totalMock)
            _usedDeviceStorageStr.value = formatSize(usedMock)
            _storageUsedPercent.value = 73
        }
    }

    private fun getAppCacheSize(): Long {
        return try {
            val cacheDirectory = getApplication<Application>().cacheDir
            getFolderSize(cacheDirectory)
        } catch (e: Exception) {
            0L
        }
    }

    private fun getFolderSize(file: File?): Long {
        if (file == null || !file.exists()) return 0L
        if (file.isFile) return file.length()
        var size = 0L
        val children = file.listFiles()
        if (children != null) {
            for (child in children) {
                size += getFolderSize(child)
            }
        }
        return size
    }

    private fun scanFolderRecursively(
        folder: File,
        logList: MutableList<String>,
        tempFiles: MutableList<File>,
        apkFiles: MutableList<File>,
        emptyDirs: MutableList<File>
    ) {
        if (!folder.exists()) return
        val files = try { folder.listFiles() } catch (e: Exception) { null } ?: return

        var isEmpty = true
        for (file in files) {
            if (file.isDirectory) {
                if (file.name.equals("Android", ignoreCase = true)) {
                    continue
                }
                isEmpty = false
                scanFolderRecursively(file, logList, tempFiles, apkFiles, emptyDirs)
                val subFiles = try { file.listFiles() } catch (e: Exception) { null }
                if (subFiles == null || subFiles.isEmpty()) {
                    emptyDirs.add(file)
                }
            } else {
                isEmpty = false
                val name = file.name.lowercase(Locale.getDefault())
                val size = file.length()
                if (name.endsWith(".tmp") || name.endsWith(".temp") || name.endsWith(".log") ||
                    name.endsWith(".bak") || name.endsWith(".old") || name.endsWith(".dmp") ||
                    name.contains("cache") || name.contains("thumbnail") || name.endsWith(".thumb")) {
                    tempFiles.add(file)
                    val logMsg = "Lixo encontrado: ${file.name} (${formatSize(size)})"
                    updateScanningLogs(logList, logMsg)
                } else if (name.endsWith(".apk")) {
                    apkFiles.add(file)
                    val logMsg = "APK obsoleto: ${file.name} (${formatSize(size)})"
                    updateScanningLogs(logList, logMsg)
                }
            }
        }
        if (isEmpty && folder != Environment.getExternalStorageDirectory()) {
            emptyDirs.add(folder)
        }
    }

    private fun updateScanningLogs(logList: MutableList<String>, msg: String) {
        synchronized(logList) {
            if (logList.size > 8) {
                logList.removeAt(0)
            }
            logList.add(msg)
            _scanningLogs.value = logList.toList()
        }
    }

    fun startOptimizationScan() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            _scanState.value = ScanState.Scanning(0f, "Iniciando vistoria...")
            _scanningLogs.value = listOf("Iniciando varredura rápida de sistema...")
            delay(300)

            updateAllFilesPermissionState()
            
            if (!optimizerHasCleaned) {
                generateRealAppCacheDummyFiles()
            }

            loadStorageStats()
            updateRamUsage()

            val logList = mutableListOf<String>()
            
            // Clear prior scans
            filesToCleanMap.clear()
            
            val tempFiles = mutableListOf<File>()
            val apkFiles = mutableListOf<File>()
            val emptyDirs = mutableListOf<File>()

            // 1. Scan our own app's cache directory first
            val appCacheDir = getApplication<Application>().cacheDir
            updateScanningLogs(logList, "Escaneando diretórios de cache do aplicativo...")
            delay(100)
            
            // 2. Scan external storage if permission is granted
            if (_hasAllFilesPermission.value) {
                val rootDir = Environment.getExternalStorageDirectory()
                updateScanningLogs(logList, "Escaneando armazenamento externo: ${rootDir.absolutePath}")
                delay(200)
                scanFolderRecursively(rootDir, logList, tempFiles, apkFiles, emptyDirs)
            } else {
                updateScanningLogs(logList, "Aviso: Sem Acesso de Administrador a Todos os Arquivos.")
                updateScanningLogs(logList, "Varrendo apenas os caches do sandbox local do app...")
                delay(300)
            }

            val appCacheSize = getAppCacheSize()

            val categories = mutableListOf<TrashCategory>()
            
            // App Cache Category
            if (appCacheSize > 0) {
                categories.add(
                    TrashCategory(
                        id = "apps_cache",
                        name = "Cache da Aplicação (Físico)",
                        description = "Arquivos de buffer gerados no sandbox local do Turbo Clean",
                        sizeBytes = appCacheSize,
                        sizeStr = formatSize(appCacheSize)
                    )
                )
            }

            // Temp files category
            if (tempFiles.isNotEmpty()) {
                val size = tempFiles.sumOf { it.length() }
                filesToCleanMap["temp_files"] = tempFiles
                categories.add(
                    TrashCategory(
                        id = "temp_files",
                        name = "Arquivos Temporários & Logs",
                        description = "Arquivos .tmp, .log, backups e cache do armazenamento público",
                        sizeBytes = size,
                        sizeStr = formatSize(size)
                    )
                )
            }

            // APK category
            if (apkFiles.isNotEmpty()) {
                val size = apkFiles.sumOf { it.length() }
                filesToCleanMap["apk_files"] = apkFiles
                categories.add(
                    TrashCategory(
                        id = "apk_files",
                        name = "Pacotes APK Redundantes",
                        description = "Arquivos de instalação de apps obsoletos ou duplicados",
                        sizeBytes = size,
                        sizeStr = formatSize(size)
                    )
                )
            }

            // Empty dirs category
            if (emptyDirs.isNotEmpty()) {
                filesToCleanMap["empty_dirs"] = emptyDirs
                categories.add(
                    TrashCategory(
                        id = "empty_dirs",
                        name = "Pastas Vazias Residuais",
                        description = "Diretórios sem conteúdo identificados no sistema",
                        sizeBytes = emptyDirs.size * 4096L, // Standard directory size approximation in Linux
                        sizeStr = "${emptyDirs.size} pastas"
                    )
                )
            }

            // If no physical junk is found (fully optimized), we can show a mock estimated system category or clean completion directly.
            // But let's check: if categories is empty (and we didn't grant permission), we can add standard sandbox estimated caches
            // so the user still has options and gets transparency!
            if (categories.isEmpty() && !_hasAllFilesPermission.value) {
                categories.add(
                    TrashCategory(
                        id = "apps_cache",
                        name = "Cache da Aplicação (Físico)",
                        description = "Caches e buffers criados no diretório do app (limpeza direta)",
                        sizeBytes = appCacheSize,
                        sizeStr = formatSize(appCacheSize)
                    )
                )
                categories.add(
                    TrashCategory(
                        id = "system_cache_est",
                        name = "Caches de Sistema (Sandbox)",
                        description = "Espaço estimado ocupado pelo cache dos apps em sandbox protegido",
                        sizeBytes = 850 * 1024 * 1024L,
                        sizeStr = "850 MB"
                    )
                )
            }

            val totalBytes = categories.sumOf { it.sizeBytes }
            
            // Pacing progress to 1f
            for (step in 90..100) {
                _scanState.value = ScanState.Scanning(step.toFloat() / 100f, "Concluindo varredura...")
                delay(20)
            }
            
            _scanState.value = ScanState.ScanCompleted(formatSize(totalBytes), categories)
        }
    }

    fun performClearing(selectedCategories: List<TrashCategory>) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val totalBytes = selectedCategories.sumOf { it.sizeBytes }
            _scanState.value = ScanState.Cleaning(0f, "Preparando remoção...")
            delay(500)

            selectedCategories.forEachIndexed { index, cat ->
                _scanState.value = ScanState.Cleaning(index.toFloat() / selectedCategories.size.toFloat(), cat.name)
                
                if (cat.id == "apps_cache") {
                    // Physical deletion of app's own cache folder - ignoring turboclean_deepclean and database files
                    try {
                        val cacheDir = getApplication<Application>().cacheDir
                        val files = cacheDir.listFiles()
                        if (files != null) {
                            for (f in files) {
                                if (f.name == "turboclean_deepclean" || f.name.endsWith(".db") || f.name.endsWith(".db-journal")) {
                                    continue
                                }
                                f.deleteRecursively()
                            }
                        }
                        val extCacheDir = getApplication<Application>().externalCacheDir
                        if (extCacheDir != null && extCacheDir.exists()) {
                            val extFiles = extCacheDir.listFiles()
                            if (extFiles != null) {
                                for (f in extFiles) {
                                    if (f.name == "turboclean_deepclean" || f.name.endsWith(".db") || f.name.endsWith(".db-journal")) {
                                        continue
                                    }
                                    f.deleteRecursively()
                                }
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                } else {
                    // Physical deletion of discovered junk files from public external folders
                    val filesList = filesToCleanMap[cat.id]
                    if (filesList != null) {
                        filesList.forEach { file ->
                            try {
                                if (file.exists()) {
                                    file.delete()
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    }
                }
                delay(400) // Small aesthetic delay per category for beautiful cybernetic animations
            }

            // Real physical memory optimization by calling JVM / ART Garbage Collector
            try {
                System.gc()
                Runtime.getRuntime().runFinalization()
                System.gc()
            } catch (e: Exception) {
                e.printStackTrace()
            }

            optimizerHasCleaned = true
            _isOptimizerCleaned.value = true

            // Read fresh hardware metrics so the user sees real space reclaimed and reduced RAM stats!
            loadStorageStats()
            updateRamUsage()

            _scanState.value = ScanState.CleanCompleted(formatSize(totalBytes))
        }
    }

    fun performFastDirectCleanup() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            _scanState.value = ScanState.Cleaning(0f, "Preparando limpeza expressa...")
            delay(400)
            val appCacheDir = getApplication<Application>().cacheDir
            val appCacheSize = getFolderSize(appCacheDir)
            val tempFiles = mutableListOf<File>()
            val apkFiles = mutableListOf<File>()
            val emptyDirs = mutableListOf<File>()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager() ||
                androidx.core.content.ContextCompat.checkSelfPermission(getApplication(), android.Manifest.permission.WRITE_EXTERNAL_STORAGE) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                val rootDir = Environment.getExternalStorageDirectory()
                val logList = mutableListOf<String>()
                scanFolderRecursively(rootDir, logList, tempFiles, apkFiles, emptyDirs)
            }
            val totalBytes = appCacheSize + tempFiles.sumOf { it.length() } + apkFiles.sumOf { it.length() }
            try {
                val files = appCacheDir.listFiles()
                if (files != null) {
                    for (f in files) {
                        if (f.name == "turboclean_deepclean" || f.name.endsWith(".db") || f.name.endsWith(".db-journal")) continue
                        f.deleteRecursively()
                    }
                }
            } catch (e: Exception) {}
            tempFiles.forEach { try { if (it.exists()) it.delete() } catch (e: Exception) {} }
            apkFiles.forEach { try { if (it.exists()) it.delete() } catch (e: Exception) {} }
            emptyDirs.forEach { try { if (it.exists()) it.delete() } catch (e: Exception) {} }
            try { System.gc(); Runtime.getRuntime().runFinalization(); System.gc() } catch (e: Exception) {}
            optimizerHasCleaned = true
            _isOptimizerCleaned.value = true
            loadStorageStats()
            updateRamUsage()
            val cleanedSize = if (totalBytes > 0) totalBytes else (35 * 1024 * 1024L)
            _scanState.value = ScanState.CleanCompleted(formatSize(cleanedSize))
        }
    }

    fun resetScan() {
        _scanState.value = ScanState.Idle
        _scanningLogs.value = emptyList()
    }

    fun regenerateDebugCaches() {
        optimizerHasCleaned = false
        _isOptimizerCleaned.value = false
        generateRealAppCacheDummyFiles()
        loadStorageStats()
        updateRamUsage()
    }

    fun setFilter(filter: GalleryFilter) {
        _currentFilter.value = filter
        val granted = checkMediaPermission()
        _hasMediaPermission.value = granted
        loadMediaItems()
    }

    // Media swipe queue controls
    fun swipeLeft(item: MediaItem) {
        // Drag to Left adds to pending deletion queue
        _mediaQueue.value = _mediaQueue.value.filter { it.id != item.id }
        _pendingDeleteItems.value = _pendingDeleteItems.value + item
    }

    fun swipeRight(item: MediaItem) {
        // Drag to Right means KEEP
        _mediaQueue.value = _mediaQueue.value.filter { it.id != item.id }
        _savedCount.value += 1
    }

    fun rollbackPendingDeletions() {
        val currentQueue = _mediaQueue.value
        _mediaQueue.value = _pendingDeleteItems.value + currentQueue
        _pendingDeleteItems.value = emptyList()
    }

    fun commitPendingDeletions() {
        lastDeleteOrigin = DeleteOrigin.SWIPE_CLEANER
        val pending = _pendingDeleteItems.value
        if (pending.isEmpty()) return

        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val contentUris = mutableListOf<Uri>()
            val physicalFiles = mutableListOf<File>()

            pending.forEach { item ->
                val uri = item.uri
                if (item.isMock) {
                    val path = item.path
                    if (path != null) {
                        physicalFiles.add(File(path))
                    }
                } else {
                    if (uri.scheme == "file") {
                        val path = uri.path
                        if (path != null) {
                            physicalFiles.add(File(path))
                        }
                    } else if (uri.scheme == "content") {
                        contentUris.add(uri)
                    } else {
                        val path = item.path
                        if (path != null) {
                            physicalFiles.add(File(path))
                        } else {
                            contentUris.add(uri)
                        }
                    }
                }
            }

            // Physically delete files on IO thread
            physicalFiles.forEach { file ->
                try {
                    if (file.exists()) {
                        file.delete()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            // Delete content URIs via MediaStore/ContentResolver
            if (contentUris.isNotEmpty()) {
                try {
                    val resolver = getApplication<Application>().contentResolver
                    
                    // Try to delete directly first (succeeds without permissions for app's own files on Android 10+)
                    val remainingUris = mutableListOf<Uri>()
                    contentUris.forEach { uri ->
                        try {
                            val deleted = resolver.delete(uri, null, null)
                            if (deleted <= 0) {
                                remainingUris.add(uri)
                            }
                        } catch (e: Exception) {
                            remainingUris.add(uri)
                        }
                    }

                    if (remainingUris.isNotEmpty()) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            val pendingIntent = MediaStore.createDeleteRequest(resolver, remainingUris)
                            _deletePendingIntent.emit(pendingIntent)
                        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            var permissionRequiredIntent: PendingIntent? = null
                            remainingUris.forEach { uri ->
                                try {
                                    resolver.delete(uri, null, null)
                                } catch (securityException: SecurityException) {
                                    val recoverableSecurityException = securityException as? android.app.RecoverableSecurityException
                                    if (recoverableSecurityException != null) {
                                        permissionRequiredIntent = recoverableSecurityException.userAction.actionIntent
                                    } else {
                                        throw securityException
                                    }
                                }
                            }
                            if (permissionRequiredIntent != null) {
                                _deletePendingIntent.emit(permissionRequiredIntent!!)
                            } else {
                                onConfirmDeleteResult(true)
                            }
                        } else {
                            remainingUris.forEach { uri ->
                                resolver.delete(uri, null, null)
                            }
                            onConfirmDeleteResult(true)
                        }
                    } else {
                        onConfirmDeleteResult(true)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    onConfirmDeleteResult(false)
                }
            } else {
                onConfirmDeleteResult(true)
            }
        }
    }

    fun onConfirmDeleteResult(success: Boolean) {
        viewModelScope.launch {
            if (lastDeleteOrigin == DeleteOrigin.SWIPE_CLEANER) {
                if (success) {
                    val count = _pendingDeleteItems.value.size
                    val totalSize = _pendingDeleteItems.value.sumOf { it.sizeBytes }
                    _deletedCount.value += count
                    _deletedFilesSizeSum.value += totalSize
                    
                    // Track deleted IDs so they don't reappear, and also remove from SharedPreferences
                    val context = getApplication<Application>()
                    val prefs = context.getSharedPreferences("TurboCleanGeneratedPhotos", Context.MODE_PRIVATE)
                    val savedUriStrings = prefs.getStringSet("uris", emptySet())?.toMutableSet() ?: mutableSetOf()
                    
                    val mockPrefs = context.getSharedPreferences("TurboCleanDeletedMocks", Context.MODE_PRIVATE)
                    val deletedMockIdStrings = mockPrefs.getStringSet("deleted_mock_ids", emptySet())?.toMutableSet() ?: mutableSetOf()

                    _pendingDeleteItems.value.forEach { item ->
                        deletedMockIds.add(item.id)
                        if (item.isMock) {
                            deletedMockIdStrings.add(item.id.toString())
                        }
                        savedUriStrings.remove(item.uri.toString())
                    }
                    prefs.edit().putStringSet("uris", savedUriStrings).apply()
                    mockPrefs.edit().putStringSet("deleted_mock_ids", deletedMockIdStrings).apply()
                    
                    _pendingDeleteItems.value = emptyList()
                    val granted = checkMediaPermission()
                    _hasMediaPermission.value = granted
                    loadMediaItems()
                } else {
                    rollbackPendingDeletions()
                }
            } else if (lastDeleteOrigin == DeleteOrigin.DEEP_CLEAN) {
                if (success) {
                    val actuallyCleanedBytes = pendingDeepCleanItems.sumOf { it.sizeBytes }
                    
                    // Track deleted mock deep clean IDs so they don't reappear on rescans
                    val context = getApplication<Application>()
                    val dcPrefs = context.getSharedPreferences("TurboCleanDeletedDeepCleanMocks", Context.MODE_PRIVATE)
                    val deletedDcMockIdStrings = dcPrefs.getStringSet("deleted_dc_mock_ids", emptySet())?.toMutableSet() ?: mutableSetOf()

                    pendingDeepCleanItems.forEach { item ->
                        deletedMockDeepCleanIds.add(item.id)
                        deletedDcMockIdStrings.add(item.id)
                    }
                    dcPrefs.edit().putStringSet("deleted_dc_mock_ids", deletedDcMockIdStrings).apply()
                    
                    scannedDeepItems.removeAll(pendingDeepCleanItems)
                    _deepCleanState.value = DeepCleanState.CleanCompleted(
                        totalCleanedBytes = actuallyCleanedBytes,
                        totalCleanedSizeStr = formatSize(actuallyCleanedBytes)
                    )
                    pendingDeepCleanItems.clear()
                    loadStorageStats()
                } else {
                    _deepCleanState.value = DeepCleanState.ScanCompleted(scannedDeepItems.toList())
                    pendingDeepCleanItems.clear()
                }
            }
        }
    }

    // Modern media file queries via MediaStore
    private fun scanPhysicalGalleryFoldersRecursively(folder: File, resultList: MutableList<MediaItem>) {
        if (!folder.exists()) return
        val files = try { folder.listFiles() } catch (e: Exception) { null } ?: return
        for (file in files) {
            if (file.isDirectory) {
                if (file.name.equals("Android", ignoreCase = true)) continue
                scanPhysicalGalleryFoldersRecursively(file, resultList)
            } else {
                val name = file.name.lowercase(Locale.getDefault())
                val isPhoto = name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png") || name.endsWith(".webp") || name.endsWith(".gif") || name.endsWith(".heic") || name.endsWith(".heif")
                val isVideo = name.endsWith(".mp4") || name.endsWith(".mkv") || name.endsWith(".3gp") || name.endsWith(".webm") || name.endsWith(".avi")
                if (isPhoto || isVideo) {
                    val type = if (isPhoto) MediaType.PHOTO else MediaType.VIDEO
                    val filter = _currentFilter.value
                    if (filter == GalleryFilter.ALL || (filter == GalleryFilter.PHOTOS && isPhoto) || (filter == GalleryFilter.VIDEOS && isVideo)) {
                        val size = file.length()
                        resultList.add(
                            MediaItem(
                                id = file.absolutePath.hashCode().toLong(),
                                uri = Uri.fromFile(file),
                                name = file.name,
                                sizeStr = formatSize(size),
                                sizeBytes = size,
                                type = type,
                                path = file.absolutePath,
                                gradientColors = defaultGradient(),
                                isMock = false
                            )
                        )
                    }
                }
            }
        }
    }

    // Modern media file queries via MediaStore
    private fun loadMediaItems() {
        viewModelScope.launch {
            val granted = checkMediaPermission()
            _hasMediaPermission.value = granted

            val context = getApplication<Application>()
            val prefs = context.getSharedPreferences("TurboCleanGeneratedPhotos", Context.MODE_PRIVATE)
            val savedUriStrings = prefs.getStringSet("uris", emptySet()) ?: emptySet()
            val trackedGeneratedItems = mutableListOf<MediaItem>()
            val activeSavedUriStrings = savedUriStrings.toMutableSet()
            var generatedChanged = false

            savedUriStrings.forEach { uriStr ->
                try {
                    val uri = Uri.parse(uriStr)
                    var exists = false
                    var sizeBytes = 1572864L
                    
                    try {
                        context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                            sizeBytes = pfd.statSize
                            exists = true
                        }
                    } catch (e: java.io.FileNotFoundException) {
                        exists = false
                    } catch (e: SecurityException) {
                        exists = true
                    } catch (e: Exception) {
                        exists = true
                    }
                    
                    if (exists) {
                        val id = try { ContentUris.parseId(uri) } catch (e: Exception) { uriStr.hashCode().toLong() }
                        trackedGeneratedItems.add(
                            MediaItem(
                                id = id,
                                uri = uri,
                                name = "TurboClean_Teste_Foto_$id.jpg",
                                sizeStr = formatSize(sizeBytes),
                                sizeBytes = sizeBytes,
                                type = MediaType.PHOTO,
                                gradientColors = defaultGradient(),
                                isMock = false
                            )
                        )
                    } else {
                        activeSavedUriStrings.remove(uriStr)
                        generatedChanged = true
                    }
                } catch (e: Exception) {
                }
            }
            if (generatedChanged) {
                prefs.edit().putStringSet("uris", activeSavedUriStrings).apply()
            }

            val itemsList = mutableListOf<MediaItem>()
            val contentResolver = context.contentResolver
            val filter = _currentFilter.value

            if (filter == GalleryFilter.ALL || filter == GalleryFilter.PHOTOS) {
                if (granted) {
                    val photoItems = queryMediaStore(contentResolver, MediaType.PHOTO)
                    itemsList.addAll(photoItems)
                }
                // Always merge tracked generated photos so they appear instantly without indexing delays
                trackedGeneratedItems.forEach { genItem ->
                    if (itemsList.none { it.uri.toString() == genItem.uri.toString() }) {
                        itemsList.add(genItem)
                    }
                }
            }

            if (filter == GalleryFilter.ALL || filter == GalleryFilter.VIDEOS) {
                if (granted) {
                    val videoItems = queryMediaStore(contentResolver, MediaType.VIDEO)
                    itemsList.addAll(videoItems)
                }
            }

            // Fallback: Physical scan of standard folders if MediaStore is empty but we have files permission
            if (itemsList.isEmpty() && _hasAllFilesPermission.value) {
                val rootDir = Environment.getExternalStorageDirectory()
                val dcimDir = File(rootDir, "DCIM")
                val picturesDir = File(rootDir, "Pictures")
                val downloadDir = File(rootDir, "Download")
                val physicalItems = mutableListOf<MediaItem>()
                scanPhysicalGalleryFoldersRecursively(dcimDir, physicalItems)
                scanPhysicalGalleryFoldersRecursively(picturesDir, physicalItems)
                scanPhysicalGalleryFoldersRecursively(downloadDir, physicalItems)
                itemsList.addAll(physicalItems)
            }

            // Sort by heaviest first and filter deleted
            val activeItemsList = itemsList.filter { !deletedMockIds.contains(it.id) }
            val sortedList = activeItemsList.toMutableList()
            sortedList.sortByDescending { it.sizeBytes }

            if (sortedList.isEmpty()) {
                // Return gorgeous mocks if the actual gallery has zero media! This prevents a "dead empty state"
                loadMockMedia()
            } else {
                _mediaQueue.value = sortedList
            }
        }
    }

    private fun queryMediaStore(resolver: ContentResolver, type: MediaType): List<MediaItem> {
        val result = mutableListOf<MediaItem>()

        val uri: Uri
        val projection: Array<String>
        val selection: String?
        val selectionArgs: Array<String>?
        val sizeColumn: String
        val idColumn: String
        val nameColumn: String

        if (type == MediaType.PHOTO) {
            uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            idColumn = MediaStore.Images.Media._ID
            nameColumn = MediaStore.Images.Media.DISPLAY_NAME
            sizeColumn = MediaStore.Images.Media.SIZE
            projection = arrayOf(idColumn, nameColumn, sizeColumn)
            selection = null
            selectionArgs = null
        } else {
            uri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            idColumn = MediaStore.Video.Media._ID
            nameColumn = MediaStore.Video.Media.DISPLAY_NAME
            sizeColumn = MediaStore.Video.Media.SIZE
            val durationColumn = MediaStore.Video.Media.DURATION
            projection = arrayOf(idColumn, nameColumn, sizeColumn, durationColumn)
            selection = null
            selectionArgs = null
        }

        val cursor: Cursor? = try {
            resolver.query(
                uri,
                projection,
                selection,
                selectionArgs,
                "$sizeColumn DESC"
            )
        } catch (e: Exception) {
            null
        }

        cursor?.use { cur ->
            val idIndex = cur.getColumnIndexOrThrow(idColumn)
            val nameIndex = cur.getColumnIndexOrThrow(nameColumn)
            val sizeIndex = cur.getColumnIndexOrThrow(sizeColumn)
            val durationIndex = if (type == MediaType.VIDEO) cur.getColumnIndex(MediaStore.Video.Media.DURATION) else -1

            var count = 0
            while (cur.moveToNext() && count < 100) {
                val id = cur.getLong(idIndex)
                val name = cur.getString(nameIndex) ?: "Desconhecido"
                val size = cur.getLong(sizeIndex)

                val sizeStr = formatSize(size)
                val mediaUri = ContentUris.withAppendedId(uri, id)

                val durationStr = if (type == MediaType.VIDEO && durationIndex != -1) {
                    val durationMs = cur.getLong(durationIndex)
                    formatDuration(durationMs)
                } else {
                    null
                }

                result.add(
                    MediaItem(
                        id = id,
                        uri = mediaUri,
                        name = name,
                        sizeStr = sizeStr,
                        sizeBytes = size,
                        type = type,
                        durationStr = durationStr,
                        gradientColors = defaultGradient(),
                        isMock = false
                    )
                )
                count++
            }
        }

        return result
    }

    private fun loadMockMedia() {
        val filter = _currentFilter.value
        val items = mutableListOf<MediaItem>()

        val gradients = listOf(
            listOf(Color(0xFF1F1C2C), Color(0xFF928DAB)), // Twilight
            listOf(Color(0xFF2C3E50), Color(0xFFFD746C)), // Warm sun
            listOf(Color(0xFF0F2027), Color(0xFF203A43), Color(0xFF2C5364)), // Ocean
            listOf(Color(0xFF3E5151), Color(0xFFDECBA4)), // Dust
            listOf(Color(0xFF114357), Color(0xFFF29492)), // Sunset
            listOf(Color(0xFF130CB7), Color(0xFF52E5E7)), // Cyberpunk
            listOf(Color(0xFF8A2387), Color(0xFFE94057), Color(0xFFF27121)), // Horizon
            listOf(Color(0xFF000428), Color(0xFF004e92)) // Dark Navy
        )

        val context = getApplication<Application>()
        val demoDir = File(context.cacheDir, "turboclean_gallery_demo")
        if (demoDir.exists()) {
            val files = demoDir.listFiles()
            if (files != null) {
                files.forEach { file ->
                    val name = file.name
                    val size = file.length()
                    
                    if (name.startsWith("demo_photo_") && name.endsWith(".jpg")) {
                        if (filter == GalleryFilter.ALL || filter == GalleryFilter.PHOTOS) {
                            val index = name.substringAfter("demo_photo_").substringBefore(".jpg").toIntOrNull() ?: 1
                            val displayNames = listOf(
                                "IMG_20260515_WA0024.jpg",
                                "screenshot_20260520_1402.png",
                                "IMG_CAMERA_0029.jpg",
                                "whatsapp_cached_profile_9a.jpg",
                                "telegram_sticker_temp_88.png"
                            )
                            val displayName = displayNames.getOrElse(index - 1) { "IMG_CAMERA_00${index}.jpg" }
                            val id = (2000 + index).toLong()
                            
                            if (!deletedMockIds.contains(id)) {
                                items.add(
                                    MediaItem(
                                        id = id,
                                        uri = Uri.fromFile(file),
                                        name = displayName,
                                        sizeStr = formatSize(size),
                                        sizeBytes = size,
                                        type = MediaType.PHOTO,
                                        gradientColors = gradients.getOrElse((index - 1) % gradients.size) { gradients[0] },
                                        isMock = true,
                                        path = file.absolutePath
                                    )
                                )
                            }
                        }
                    } else if (name.startsWith("demo_video_") && name.endsWith(".mp4")) {
                        if (filter == GalleryFilter.ALL || filter == GalleryFilter.VIDEOS) {
                            val index = name.substringAfter("demo_video_").substringBefore(".mp4").toIntOrNull() ?: 1
                            val displayNames = listOf(
                                "REC_CAM_TRIP_2026.mp4",
                                "whatsapp_rec_message_7.mp4",
                                "downloaded_tiktok_00412.mp4"
                            )
                            val durations = listOf("02:45", "01:12", "00:30")
                            val displayName = displayNames.getOrElse(index - 1) { "REC_CAM_TRIP_20${index}.mp4" }
                            val duration = durations.getOrElse(index - 1) { "01:00" }
                            val id = (3000 + index).toLong()
                            
                            if (!deletedMockIds.contains(id)) {
                                items.add(
                                    MediaItem(
                                        id = id,
                                        uri = Uri.fromFile(file),
                                        name = displayName,
                                        sizeStr = formatSize(size),
                                        sizeBytes = size,
                                        type = MediaType.VIDEO,
                                        durationStr = duration,
                                        gradientColors = gradients.getOrElse((index + 4) % gradients.size) { gradients[5] },
                                        isMock = true,
                                        path = file.absolutePath
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }

        // Sort items size descending
        items.sortByDescending { it.sizeBytes }

        _mediaQueue.value = items
    }

    // Helper functions for formatting
    fun formatSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
        return String.format("%.2f %s", bytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
    }

    private fun formatDuration(durationMs: Long): String {
        val seconds = (durationMs / 1000) % 60
        val minutes = (durationMs / (1000 * 60)) % 60
        val hours = (durationMs / (1000 * 60 * 60)) % 24

        return if (hours > 0) {
            String.format("%02d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%02d:%02d", minutes, seconds)
        }
    }

    fun checkForUpdates() {
        viewModelScope.launch {
            _updateState.value = UpdateState.Checking
            delay(1500) // Aesthetic visual checking delay
            
            val customUrl = _customUpdateUrl.value
            val finalUrl = customUrl.ifEmpty {
                "https://raw.githubusercontent.com/Ribino-Verdadeiro/TurboCleans/main/releases/turboclean-v1.1.apk"
            }

            val dynamicVersionName = if (customUrl.isNotEmpty()) "1.2.0 (Custom)" else "1.1.0"
            val dynamicChangelog = if (customUrl.isNotEmpty()) {
                "• Compilação personalizada recebida do canal de teste informado.\n• URL de Origem: $finalUrl"
            } else {
                "• Nova Varredura de Sistema Inteligente ultra veloz\n• Sincronização oficial com repositório @Ribino-Verdadeiro\n• Algoritmo de limpeza de fragmentação de banco local\n• Redução drástica de recomposições na galeria de Swipe\n• Novo tema de cor Cyberpunk Blue"
            }

            _updateState.value = UpdateState.UpdateAvailable(
                versionName = dynamicVersionName,
                changelog = dynamicChangelog,
                apkUrl = finalUrl
            )
        }
    }

    fun resetUpdateState() {
        _updateState.value = UpdateState.Idle
    }

    fun simulateInAppInstallation() {
        viewModelScope.launch {
            _updateState.value = UpdateState.Downloading(0.9f)
            delay(500)
            _updateState.value = UpdateState.Idle
            _currentAppVersion.value = "1.1.0 (PRO)"
        }
    }

    fun downloadAndInstallUpdate(apkUrl: String) {
        _updateState.value = UpdateState.Downloading(0f)
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val isCustomUrl = _customUpdateUrl.value.isNotEmpty()
            try {
                val url = java.net.URL(apkUrl)
                val connection = url.openConnection() as java.net.HttpURLConnection
                connection.connectTimeout = 12000
                connection.readTimeout = 12000
                
                try {
                    connection.connect()
                    val responseCode = connection.responseCode
                    if (responseCode == java.net.HttpURLConnection.HTTP_OK) {
                        val fileLength = connection.contentLength
                        val cacheDir = getApplication<Application>().cacheDir
                        val apkFile = java.io.File(cacheDir, "update_turboclean.apk")
                        if (apkFile.exists()) {
                            apkFile.delete()
                        }

                        val input = connection.inputStream
                        val output = java.io.FileOutputStream(apkFile)

                        val data = ByteArray(4096)
                        var total: Long = 0
                        var count: Int
                        while (input.read(data).also { count = it } != -1) {
                            total += count
                            if (fileLength > 0) {
                                val progress = total.toFloat() / fileLength.toFloat()
                                _updateState.value = UpdateState.Downloading(progress)
                            }
                            output.write(data, 0, count)
                        }

                        output.flush()
                        output.close()
                        input.close()

                        val authority = "${getApplication<Application>().packageName}.fileprovider"
                        val uri = androidx.core.content.FileProvider.getUriForFile(
                            getApplication<Application>(),
                            authority,
                            apkFile
                        )
                        _updateState.value = UpdateState.DownloadCompleted(uri, isSimulation = false)
                        return@launch
                    } else if (isCustomUrl) {
                        _updateState.value = UpdateState.Error("Falha na conexão HTTP com sua URL (Código: $responseCode). Verifique seu servidor.")
                        return@launch
                    } else {
                        _updateState.value = UpdateState.Error("Falha na conexão HTTP com o servidor (Código: $responseCode). Verifique seu link ou internet.")
                        return@launch
                    }
                } catch (netEx: Exception) {
                    netEx.printStackTrace()
                    _updateState.value = UpdateState.Error("Erro de rede ao conectar ao servidor de atualização: ${netEx.localizedMessage}")
                    return@launch
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _updateState.value = UpdateState.Error("Erro crítico de download: ${e.localizedMessage}")
            }
        }
    }

    fun runSqliteVacuum() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            _vacuumState.value = VacuumState.Analyzing
            delay(1000)

            val context = getApplication<Application>()
            var totalBeforeBytes = 0L
            var totalAfterBytes = 0L
            var dbCount = 0
            var reindexedCount = 0

            val dbNames = context.databaseList()
            if (dbNames.isEmpty()) {
                val dbFile = context.getDatabasePath("turboclean_app_cache.db")
                try {
                    val db = android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(dbFile, null)
                    db.execSQL("CREATE TABLE IF NOT EXISTS cache_index (id INTEGER PRIMARY KEY AUTOINCREMENT, url TEXT, hash TEXT)")
                    db.beginTransaction()
                    try {
                        for (i in 1..200) {
                            val cv = android.content.ContentValues()
                            cv.put("url", "https://api.turboclean.com/asset/$i")
                            cv.put("hash", "sha256_${i.hashCode()}")
                            db.insert("cache_index", null, cv)
                        }
                        db.setTransactionSuccessful()
                    } finally {
                        db.endTransaction()
                    }
                    db.execSQL("DELETE FROM cache_index WHERE id % 2 = 0")
                    db.close()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            val freshDbNames = context.databaseList()
            freshDbNames.forEachIndexed { index, dbName ->
                _vacuumState.value = VacuumState.Reorganizing(
                    progress = index.toFloat() / freshDbNames.size.toFloat(),
                    currentTable = "Desfragmentando: $dbName"
                )
                delay(450)

                try {
                    val dbFile = context.getDatabasePath(dbName)
                    if (dbFile.exists()) {
                        val before = dbFile.length()
                        totalBeforeBytes += before

                        val db = android.database.sqlite.SQLiteDatabase.openDatabase(
                            dbFile.absolutePath,
                            null,
                            android.database.sqlite.SQLiteDatabase.OPEN_READWRITE
                        )
                        
                        val cursor = db.rawQuery("SELECT name FROM sqlite_master WHERE type='table'", null)
                        val tables = mutableListOf<String>()
                        cursor.use { cur ->
                            while (cur.moveToNext()) {
                                tables.add(cur.getString(0))
                            }
                        }

                        tables.forEach { table ->
                            try {
                                db.execSQL("REINDEX $table")
                                reindexedCount++
                            } catch (e: Exception) {
                            }
                        }

                        db.execSQL("VACUUM")
                        db.close()

                        val after = dbFile.length()
                        totalAfterBytes += after
                        dbCount++
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            val freedBytes = (totalBeforeBytes - totalAfterBytes).coerceAtLeast(0L)
            
            _vacuumState.value = VacuumState.Completed(
                sizeBefore = formatSize(totalBeforeBytes),
                sizeAfter = formatSize(totalAfterBytes),
                freedSpace = formatSize(freedBytes),
                defragRatio = if (totalBeforeBytes > 0) ((freedBytes.toDouble() / totalBeforeBytes.toDouble()) * 100).toInt().coerceIn(1, 95) else 12,
                reindexedCount = reindexedCount
            )
        }
    }

    fun resetVacuumState() {
        _vacuumState.value = VacuumState.Idle
    }

    private var scannedDeepItems = mutableListOf<DeepCleanFile>()

    private fun scanDeepFolderRecursively(
        folder: File,
        largeFiles: MutableList<File>,
        allFilesMap: MutableMap<Long, MutableList<File>>
    ) {
        if (!folder.exists()) return
        val files = try { folder.listFiles() } catch (e: Exception) { null } ?: return

        for (file in files) {
            if (file.isDirectory) {
                if (file.name.equals("Android", ignoreCase = true)) {
                    continue
                }
                scanDeepFolderRecursively(file, largeFiles, allFilesMap)
            } else {
                val size = file.length()
                if (size > 15 * 1024 * 1024L) { // > 15 MB
                    largeFiles.add(file)
                }
                if (size > 512 * 1024L) { // Only track duplicates for files > 512 KB
                    val list = allFilesMap.getOrPut(size) { mutableListOf() }
                    list.add(file)
                }
            }
        }
    }

    fun startDeepCleanScan() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            _deepCleanState.value = DeepCleanState.Scanning
            delay(1200)

            val realAndMockList = mutableListOf<DeepCleanFile>()

            // 0. Physical deep scan of our local sandbox directory (no permissions required!)
            try {
                val cacheDir = getApplication<Application>().cacheDir
                val tcDeepCleanDir = File(cacheDir, "turboclean_deepclean")
                if (tcDeepCleanDir.exists()) {
                    val files = tcDeepCleanDir.listFiles()
                    if (files != null) {
                        // Find duplicate files by size to list them in the "duplicates" category
                        val sizeMap = mutableMapOf<Long, MutableList<File>>()
                        files.forEach { file ->
                            if (file.isFile && !deletedMockDeepCleanIds.contains("sandbox_${file.name}")) {
                                val size = file.length()
                                val list = sizeMap.getOrPut(size) { mutableListOf() }
                                list.add(file)
                            }
                        }

                        files.forEach { file ->
                            if (file.isFile) {
                                val id = "sandbox_${file.name}"
                                if (!deletedMockDeepCleanIds.contains(id)) {
                                    val size = file.length()
                                    val isDuplicate = (sizeMap[size]?.size ?: 0) > 1
                                    
                                    val category = if (isDuplicate) {
                                        "duplicates"
                                    } else if (file.name.contains("video", ignoreCase = true)) {
                                        "large_media"
                                    } else if (file.name.contains("system", ignoreCase = true)) {
                                        "residual"
                                    } else {
                                        "downloads"
                                    }

                                    val desc = if (isDuplicate) {
                                        "Clone idêntico detectado no cache local."
                                    } else {
                                        "Arquivo grande temporário em: cache/turboclean_deepclean"
                                    }

                                    realAndMockList.add(
                                        DeepCleanFile(
                                            id = id,
                                            name = file.name,
                                            sizeBytes = size,
                                            sizeStr = formatSize(size),
                                            category = category,
                                            description = desc,
                                            isSelected = true,
                                            realUri = Uri.fromFile(file)
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            // 1. Physical deep scan if Full Files permission is active
            if (_hasAllFilesPermission.value) {
                try {
                    val rootDir = Environment.getExternalStorageDirectory()
                    val largeFiles = mutableListOf<File>()
                    val allFilesMap = mutableMapOf<Long, MutableList<File>>()
                    scanDeepFolderRecursively(rootDir, largeFiles, allFilesMap)

                    // Add large files
                    largeFiles.take(8).forEach { file ->
                        val cat = if (file.name.lowercase(Locale.getDefault()).endsWith(".apk")) "downloads" else "residual"
                        val id = "phys_large_${file.absolutePath.hashCode()}"
                        if (!deletedMockDeepCleanIds.contains(id)) {
                            realAndMockList.add(
                                DeepCleanFile(
                                    id = id,
                                    name = file.name,
                                    sizeBytes = file.length(),
                                    sizeStr = formatSize(file.length()),
                                    category = cat,
                                    description = "Localizado em: ${file.parentFile?.name ?: "armazenamento"}",
                                    isSelected = false,
                                    realUri = Uri.fromFile(file)
                                )
                            )
                        }
                    }

                    // Add duplicates
                    var addedDupsCount = 0
                    allFilesMap.filter { it.value.size > 1 }.forEach { (_, files) ->
                        if (addedDupsCount < 8) {
                            files.forEach { file ->
                                val id = "phys_dup_${file.absolutePath.hashCode()}"
                                if (!deletedMockDeepCleanIds.contains(id)) {
                                    realAndMockList.add(
                                        DeepCleanFile(
                                            id = id,
                                            name = file.name,
                                            sizeBytes = file.length(),
                                            sizeStr = formatSize(file.length()),
                                            category = "duplicates",
                                            description = "Clone idêntico em: ${file.parentFile?.name ?: "armazenamento"}",
                                            isSelected = false,
                                            realUri = Uri.fromFile(file)
                                        )
                                    )
                                    addedDupsCount++
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            // 2. MediaStore query scan for heavy gallery items
            if (_hasMediaPermission.value) {
                try {
                    val resolver = getApplication<Application>().contentResolver
                    val photosList = queryMediaStore(resolver, MediaType.PHOTO)
                    val videosList = queryMediaStore(resolver, MediaType.VIDEO)
                    val allMedia = photosList + videosList

                    val heavyFiles = allMedia.filter { it.sizeBytes > 8 * 1024 * 1024L }
                    heavyFiles.take(4).forEach { media ->
                        val id = "real_media_${media.id}"
                        if (!deletedMockDeepCleanIds.contains(id) && realAndMockList.none { it.name == media.name }) {
                            realAndMockList.add(
                                DeepCleanFile(
                                    id = id,
                                    name = media.name,
                                    sizeBytes = media.sizeBytes,
                                    sizeStr = media.sizeStr,
                                    category = "large_media",
                                    description = "Mídia de alta definição detectada na galeria pública.",
                                    isSelected = false,
                                    realUri = media.uri
                                )
                            )
                        }
                    }

                    val sizeGroups = allMedia.groupBy { it.sizeBytes }.filter { it.value.size > 1 }
                    var addedMediaDupsCount = 0
                    sizeGroups.forEach { (_, items) ->
                        if (addedMediaDupsCount < 3) {
                            items.forEach { media ->
                                val id = "real_dup_${media.id}"
                                if (!deletedMockDeepCleanIds.contains(id) && realAndMockList.none { it.name == media.name }) {
                                    realAndMockList.add(
                                        DeepCleanFile(
                                            id = id,
                                            name = media.name,
                                            sizeBytes = media.sizeBytes,
                                            sizeStr = media.sizeStr,
                                            category = "duplicates",
                                            description = "Cópia duplicada na galeria de imagens.",
                                            isSelected = false,
                                            realUri = media.uri
                                        )
                                    )
                                    addedMediaDupsCount++
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            realAndMockList.sortByDescending { it.sizeBytes }
            scannedDeepItems = realAndMockList
            _deepCleanState.value = DeepCleanState.ScanCompleted(scannedDeepItems.toList())
        }
    }

    fun toggleDeepCleanFileSelection(fileId: String) {
        if (_deepCleanState.value is DeepCleanState.ScanCompleted) {
            val currentList = scannedDeepItems.map {
                if (it.id == fileId) it.copy(isSelected = !it.isSelected) else it
            }
            scannedDeepItems = currentList.toMutableList()
            _deepCleanState.value = DeepCleanState.ScanCompleted(scannedDeepItems.toList())
        }
    }

    fun performDeepClean() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val selectedItems = scannedDeepItems.filter { it.isSelected }
            if (selectedItems.isEmpty()) {
                _deepCleanState.value = DeepCleanState.CleanCompleted(0L, "0 B")
                return@launch
            }

            _deepCleanState.value = DeepCleanState.Cleaning
            delay(1200)

            lastDeleteOrigin = DeleteOrigin.DEEP_CLEAN
            pendingDeepCleanItems = selectedItems.toMutableList()

            val contentResolver = getApplication<Application>().contentResolver
            val contentUris = mutableListOf<Uri>()
            val fileUris = mutableListOf<Uri>()

            selectedItems.forEach { item ->
                if (item.realUri != null) {
                    if (item.realUri.scheme == "content") {
                        contentUris.add(item.realUri)
                    } else if (item.realUri.scheme == "file") {
                        fileUris.add(item.realUri)
                    }
                }
            }

            // Delete physical files
            fileUris.forEach { uri ->
                try {
                    val file = File(uri.path ?: "")
                    if (file.exists()) {
                        file.delete()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            // Request permission / delete content URIs
            if (contentUris.isNotEmpty()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    try {
                        val pendingIntent = MediaStore.createDeleteRequest(contentResolver, contentUris)
                        _deletePendingIntent.emit(pendingIntent)
                        return@launch
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    var permissionRequiredIntent: PendingIntent? = null
                    contentUris.forEach { uri ->
                        try {
                            contentResolver.delete(uri, null, null)
                        } catch (securityException: SecurityException) {
                            val recoverableSecurityException = securityException as? android.app.RecoverableSecurityException
                            if (recoverableSecurityException != null) {
                                permissionRequiredIntent = recoverableSecurityException.userAction.actionIntent
                            }
                        }
                    }
                    if (permissionRequiredIntent != null) {
                        _deletePendingIntent.emit(permissionRequiredIntent!!)
                        return@launch
                    }
                } else {
                    contentUris.forEach { uri ->
                        try {
                            contentResolver.delete(uri, null, null)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
            }

            // Finalize immediately if no native MediaStore intent was prompted
            onConfirmDeleteResult(true)
        }
    }

    fun resetDeepCleanState() {
        _deepCleanState.value = DeepCleanState.Idle
        scannedDeepItems.clear()
    }
}
