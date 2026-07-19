package com.galeriva.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.Photo
import androidx.compose.material.icons.rounded.PhotoAlbum
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.galeriva.app.ui.GalleryViewModel
import com.galeriva.app.data.AlbumExporter
import com.galeriva.app.ui.albums.AlbumsScreen
import com.galeriva.app.ui.calendar.CalendarScreen
import com.galeriva.app.ui.calendar.DayScreen
import com.galeriva.app.ui.calendar.localDate
import com.galeriva.app.ui.components.FloatingNavBar
import com.galeriva.app.ui.components.GalerivaHeader
import com.galeriva.app.ui.components.NavTab
import com.galeriva.app.ui.duplicates.DuplicatesScreen
import com.galeriva.app.ui.duplicates.SimilarScreen
import com.galeriva.app.ui.gallery.GalleryScreen
import com.galeriva.app.ui.gallery.PhotoThumbnail
import com.galeriva.app.ui.stats.StatsScreen
import com.galeriva.app.ui.theme.Brand
import com.galeriva.app.ui.theme.GalerivaTheme
import com.galeriva.app.ui.vault.VaultScreen
import com.galeriva.app.ui.vault.authenticateVault
import com.galeriva.app.ui.viewer.PhotoViewerScreen

// FragmentActivity is required by BiometricPrompt (vault authentication).
class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val startDest = if (intent?.getStringExtra("dest") == "search") "search" else "gallery"
        setContent {
            GalerivaTheme {
                Surface(
                    Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    PermissionGate(startDest)
                }
            }
        }
    }
}

private fun requiredPermission(): String =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
        Manifest.permission.READ_MEDIA_IMAGES
    else
        Manifest.permission.READ_EXTERNAL_STORAGE

@Composable
private fun PermissionGate(startDest: String = "gallery") {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, requiredPermission()) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results -> granted = results[requiredPermission()] == true }

    if (granted) {
        // Ask once for notification permission (Android 13+) so the indexing
        // progress notification is visible; indexing runs either way.
        val notifLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { }
        androidx.compose.runtime.LaunchedEffect(Unit) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(
                    context, Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        GalerivaShell(startDest)
    } else {
        OnboardingScreen(onAllow = {
            val permissions =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                    arrayOf(
                        Manifest.permission.READ_MEDIA_IMAGES,
                        Manifest.permission.READ_MEDIA_VIDEO
                    )
                else
                    arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
            launcher.launch(permissions)
        })
    }
}

@Composable
private fun OnboardingScreen(onAllow: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.surfaceVariant,
                        MaterialTheme.colorScheme.background
                    )
                )
            )
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Galeriva",
                style = MaterialTheme.typography.displaySmall.copy(brush = Brand.Sheen)
            )
            Text(
                "Galeri pintar. Sepenuhnya privat.",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp, bottom = 36.dp)
            )
            FeatureRow("🔍", "Cari dengan kalimat", "“rapat di kantor”, “anak di pantai” — AI memahaminya.")
            FeatureRow("✨", "Rapi otomatis", "Album pintar, duplikat, dan foto mirip tersusun sendiri.")
            FeatureRow("🔒", "Tidak pernah diunggah", "Seluruh AI berjalan di perangkat Anda. Tanpa server.")
            Spacer(Modifier.height(40.dp))
            Button(
                onClick = onAllow,
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Text("Izinkan Akses Foto", style = MaterialTheme.typography.labelLarge)
            }
            Text(
                "Izin hanya dipakai untuk menampilkan & mengelola foto Anda.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 12.dp)
            )
        }
    }
}

@Composable
private fun FeatureRow(emoji: String, title: String, body: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.size(46.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(emoji, style = MaterialTheme.typography.titleLarge)
            }
        }
        Column(Modifier.padding(start = 14.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun GalerivaShell(startDest: String = "gallery") {
    val navController: NavHostController = rememberNavController()
    val viewModel: GalleryViewModel = viewModel()
    val photos by viewModel.photos.collectAsStateWithLifecycle()
    val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()
    val lockedPhotos by viewModel.lockedPhotos.collectAsStateWithLifecycle()
    val indexedCount by viewModel.indexedCount.collectAsStateWithLifecycle()
    val activity = LocalContext.current as FragmentActivity

    val tabs = listOf(
        NavTab("gallery", "Foto", Icons.Rounded.Photo),
        NavTab("albums", "Album", Icons.Rounded.PhotoAlbum),
        NavTab("search", "Cari", Icons.Rounded.Search)
    )

    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val showChrome = currentRoute in tabs.map { it.route }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            if (showChrome) {
                GalerivaHeader(
                    subtitle = when (currentRoute) {
                        "gallery" ->
                            if (photos.isEmpty()) null
                            else "${photos.size} foto" +
                                if (indexedCount < photos.size) " • mengindeks $indexedCount/${photos.size}" else ""
                        "albums" -> "Album pintar & alat perapih"
                        "search" -> "Pencarian AI — semua di perangkat"
                        else -> null
                    },
                    actions = {
                        if (currentRoute == "gallery") {
                            IconButton(onClick = { viewModel.refresh() }) {
                                Icon(
                                    Icons.Rounded.Sync,
                                    contentDescription = "Pindai foto baru",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = { navController.navigate("calendar") }) {
                                Icon(
                                    Icons.Rounded.CalendarMonth,
                                    contentDescription = "Kalender",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                )
            }
            NavHost(
                navController = navController,
                startDestination = startDest,
                enterTransition = { fadeIn(tween(220)) },
                exitTransition = { fadeOut(tween(160)) },
                popEnterTransition = { fadeIn(tween(220)) },
                popExitTransition = { fadeOut(tween(160)) },
                modifier = Modifier.fillMaxSize()
            ) {
                composable("gallery") {
                    GalleryScreen(viewModel) { photo ->
                        navController.navigate("viewer/all/${photo.id}")
                    }
                }
                composable("albums") {
                    AlbumsScreen(
                        viewModel = viewModel,
                        onAlbumClick = { album -> navController.navigate("album/${album.id}") },
                        onDuplicatesClick = { navController.navigate("duplicates") },
                        onSimilarClick = { navController.navigate("similar") },
                        onVaultClick = {
                            authenticateVault(activity) { navController.navigate("vault") }
                        },
                        onStatsClick = { navController.navigate("stats") }
                    )
                }
                composable("stats") {
                    StatsScreen(
                        viewModel = viewModel,
                        onBack = { navController.popBackStack() }
                    )
                }
                composable("search") {
                    com.galeriva.app.ui.search.SearchScreen(viewModel) { photo ->
                        navController.navigate("viewer/search/${photo.id}")
                    }
                }
                composable("duplicates") {
                    DuplicatesScreen(viewModel) { navController.popBackStack() }
                }
                composable("similar") {
                    SimilarScreen(viewModel) { navController.popBackStack() }
                }
                composable("calendar") {
                    CalendarScreen(
                        viewModel = viewModel,
                        onDayClick = { date ->
                            navController.navigate("day/${date.toEpochDay()}")
                        },
                        onBack = { navController.popBackStack() }
                    )
                }
                composable("day/{epochDay}") { entry ->
                    val epochDay = entry.arguments?.getString("epochDay")?.toLongOrNull()
                        ?: return@composable
                    DayScreen(
                        viewModel = viewModel,
                        epochDay = epochDay,
                        onPhotoClick = { photo ->
                            navController.navigate("viewer/day-$epochDay/${photo.id}")
                        },
                        onBack = { navController.popBackStack() }
                    )
                }
                composable("vault") {
                    VaultScreen(
                        viewModel = viewModel,
                        onPhotoClick = { photo ->
                            navController.navigate("viewer/vault/${photo.id}")
                        },
                        onBack = { navController.popBackStack() }
                    )
                }
                composable("album/{albumId}") { entry ->
                    val albumId = entry.arguments?.getString("albumId") ?: return@composable
                    val album = viewModel.albumById(albumId)
                    if (album != null) {
                        AlbumDetailScreen(
                            album = album,
                            viewModel = viewModel,
                            onBack = { navController.popBackStack() },
                            onPhotoClick = { photo ->
                                navController.navigate("viewer/${albumId}/${photo.id}")
                            }
                        )
                    }
                }
                composable(
                    "viewer/{source}/{photoId}",
                    enterTransition = {
                        scaleIn(initialScale = 0.92f, animationSpec = tween(220)) +
                            fadeIn(tween(220))
                    },
                    popExitTransition = {
                        scaleOut(targetScale = 0.94f, animationSpec = tween(180)) +
                            fadeOut(tween(180))
                    }
                ) { entry ->
                    val source = entry.arguments?.getString("source") ?: "all"
                    val photoId = entry.arguments?.getString("photoId")?.toLongOrNull()
                        ?: return@composable
                    val list = when {
                        source == "all" -> photos
                        source == "search" -> searchResults
                        source == "vault" -> lockedPhotos
                        source.startsWith("day-") -> {
                            val day = source.removePrefix("day-").toLongOrNull()
                            if (day == null) photos
                            else photos.filter { it.localDate().toEpochDay() == day }
                        }
                        else -> viewModel.albumById(source)?.photos ?: photos
                    }
                    PhotoViewerScreen(
                        viewModel = viewModel,
                        initialPhotoId = photoId,
                        photos = list,
                        onBack = { navController.popBackStack() }
                    )
                }
            }
        }

        if (showChrome) {
            FloatingNavBar(
                tabs = tabs,
                currentRoute = currentRoute,
                onSelect = { tab ->
                    navController.navigate(tab.route) {
                        popUpTo("gallery") { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 14.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AlbumDetailScreen(
    album: com.galeriva.app.ui.SmartAlbum,
    viewModel: GalleryViewModel,
    onBack: () -> Unit,
    onPhotoClick: (com.galeriva.app.data.Photo) -> Unit
) {
    val favorites by viewModel.favoriteIds.collectAsStateWithLifecycle()
    val exportProgress by viewModel.exportProgress.collectAsStateWithLifecycle()
    val selected by viewModel.selectedIds.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val deleteLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) viewModel.onDeleteConfirmed()
    }
    val isCustomFolder = album.id.startsWith("custom:")
    val isDeviceFolder = album.id.startsWith("folder:")
    var showDeleteFolderDialog by remember { mutableStateOf(false) }

    if (showDeleteFolderDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showDeleteFolderDialog = false },
            title = { Text("Hapus Isi Folder?") },
            text = {
                Text(
                    "Semua ${album.photos.size} foto/video di \"${album.title}\" akan " +
                        "dihapus dari perangkat (dengan konfirmasi sistem)."
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    showDeleteFolderDialog = false
                    viewModel.deletePhotoIds(album.photos.map { it.id }.toSet()) { sender ->
                        deleteLauncher.launch(
                            androidx.activity.result.IntentSenderRequest.Builder(sender).build()
                        )
                    }
                }) { Text("Hapus Semua", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(
                    onClick = { showDeleteFolderDialog = false }
                ) { Text("Batal") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (selected.isEmpty()) album.title else "${selected.size} dipilih",
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (selected.isEmpty()) onBack() else viewModel.clearSelection()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Kembali")
                    }
                },
                actions = {
                    if (selected.isNotEmpty()) {
                        IconButton(onClick = {
                            viewModel.selectAll(album.photos.map { it.id })
                        }) {
                            Icon(
                                Icons.Rounded.SelectAll,
                                contentDescription = "Pilih semua di album ini"
                            )
                        }
                        if (isCustomFolder) {
                            IconButton(onClick = {
                                viewModel.excludeFromFolder(album.id, selected)
                            }) {
                                Icon(
                                    Icons.Rounded.RemoveCircleOutline,
                                    contentDescription = "Keluarkan dari folder (foto tidak dihapus)",
                                    tint = MaterialTheme.colorScheme.secondary
                                )
                            }
                        }
                        IconButton(onClick = {
                            viewModel.deletePhotoIds(selected) { sender ->
                                deleteLauncher.launch(
                                    androidx.activity.result.IntentSenderRequest.Builder(sender).build()
                                )
                            }
                        }) {
                            Icon(
                                Icons.Rounded.DeleteOutline,
                                contentDescription = "Hapus foto dari perangkat",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                        return@TopAppBar
                    }
                    val progress = exportProgress
                    if (progress != null) {
                        Text(
                            "${progress.first}/${progress.second}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(end = 12.dp)
                        )
                    } else {
                        IconButton(onClick = {
                            viewModel.exportAlbum(album) { file ->
                                AlbumExporter.share(context, file)
                            }
                        }) {
                            Icon(
                                Icons.Rounded.Archive,
                                contentDescription = "Ekspor album sebagai ZIP",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    if (isCustomFolder) {
                        IconButton(onClick = {
                            viewModel.deleteCustomFolder(album.id)
                            onBack()
                        }) {
                            Icon(
                                Icons.Rounded.DeleteOutline,
                                contentDescription = "Hapus folder pintar",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    if (isDeviceFolder) {
                        IconButton(onClick = { showDeleteFolderDialog = true }) {
                            Icon(
                                Icons.Rounded.DeleteOutline,
                                contentDescription = "Hapus seluruh isi folder",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(104.dp),
            contentPadding = PaddingValues(start = 4.dp, end = 4.dp, top = 4.dp, bottom = 40.dp),
            modifier = Modifier.padding(padding)
        ) {
            items(album.photos, key = { it.id }) { photo ->
                PhotoThumbnail(
                    photo = photo,
                    isFavorite = photo.id in favorites,
                    isSelected = photo.id in selected,
                    onClick = {
                        if (selected.isNotEmpty()) viewModel.toggleSelection(photo.id)
                        else onPhotoClick(photo)
                    },
                    onLongClick = { viewModel.toggleSelection(photo.id) }
                )
            }
        }
    }
}
