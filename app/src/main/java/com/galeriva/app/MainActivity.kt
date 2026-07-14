package com.galeriva.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.PhotoAlbum
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.galeriva.app.ui.GalleryViewModel
import com.galeriva.app.ui.albums.AlbumsScreen
import com.galeriva.app.ui.gallery.GalleryScreen
import com.galeriva.app.ui.gallery.PhotoThumbnail
import com.galeriva.app.ui.search.SearchScreen
import com.galeriva.app.ui.theme.GalerivaTheme
import com.galeriva.app.ui.viewer.PhotoViewerScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GalerivaTheme {
                PermissionGate()
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
private fun PermissionGate() {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, requiredPermission()) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted = it }

    if (granted) {
        GalerivaNavHost()
    } else {
        Column(
            Modifier
                .fillMaxSize()
                .padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Galeriva",
                style = MaterialTheme.typography.headlineMedium
            )
            Text(
                "Izinkan akses foto untuk mulai mengelola galeri Anda. " +
                    "Semua pemrosesan dilakukan di perangkat — foto tidak pernah diunggah.",
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(vertical = 16.dp)
            )
            Button(onClick = { launcher.launch(requiredPermission()) }) {
                Text("Izinkan Akses Foto")
            }
        }
    }
}

private data class Tab(val route: String, val label: String, val icon: @Composable () -> Unit)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GalerivaNavHost() {
    val navController: NavHostController = rememberNavController()
    val viewModel: GalleryViewModel = viewModel()
    val photos by viewModel.photos.collectAsStateWithLifecycle()
    val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()

    val tabs = listOf(
        Tab("gallery", "Foto") { Icon(Icons.Filled.Photo, null) },
        Tab("albums", "Album") { Icon(Icons.Filled.PhotoAlbum, null) },
        Tab("search", "Cari") { Icon(Icons.Filled.Search, null) }
    )

    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val showBars = currentRoute in tabs.map { it.route }

    Scaffold(
        topBar = {
            if (showBars) {
                TopAppBar(title = { Text("Galeriva") })
            }
        },
        bottomBar = {
            if (showBars) {
                NavigationBar {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = currentRoute == tab.route,
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo("gallery") { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = tab.icon,
                            label = { Text(tab.label) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = "gallery",
            modifier = Modifier.padding(padding)
        ) {
            composable("gallery") {
                GalleryScreen(viewModel) { photo ->
                    navController.navigate("viewer/all/${photo.id}")
                }
            }
            composable("albums") {
                AlbumsScreen(viewModel) { album ->
                    navController.navigate("album/${album.id}")
                }
            }
            composable("search") {
                SearchScreen(viewModel) { photo ->
                    navController.navigate("viewer/search/${photo.id}")
                }
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
            composable("viewer/{source}/{photoId}") { entry ->
                val source = entry.arguments?.getString("source") ?: "all"
                val photoId = entry.arguments?.getString("photoId")?.toLongOrNull()
                    ?: return@composable
                val list = when (source) {
                    "all" -> photos
                    "search" -> searchResults
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
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(album.title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Kembali")
                    }
                }
            )
        }
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(104.dp),
            contentPadding = PaddingValues(4.dp),
            modifier = Modifier.padding(padding)
        ) {
            items(album.photos, key = { it.id }) { photo ->
                PhotoThumbnail(
                    photo = photo,
                    isFavorite = photo.id in favorites,
                    onClick = { onPhotoClick(photo) }
                )
            }
        }
    }
}
