package com.galeriva.app.ui.vault

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
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
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.galeriva.app.data.Photo
import com.galeriva.app.ui.GalleryViewModel
import com.galeriva.app.ui.gallery.PhotoThumbnail

/**
 * Prompts for biometric/device-credential auth. If the device has no lock
 * configured at all, proceeds directly (nothing to authenticate against).
 */
fun authenticateVault(activity: FragmentActivity, onSuccess: () -> Unit) {
    val authenticators = BIOMETRIC_WEAK or DEVICE_CREDENTIAL
    val canAuth = BiometricManager.from(activity).canAuthenticate(authenticators)
    if (canAuth != BiometricManager.BIOMETRIC_SUCCESS) {
        onSuccess()
        return
    }
    val prompt = BiometricPrompt(
        activity,
        ContextCompat.getMainExecutor(activity),
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onSuccess()
            }
        }
    )
    prompt.authenticate(
        BiometricPrompt.PromptInfo.Builder()
            .setTitle("Buka Brankas")
            .setSubtitle("Verifikasi identitas untuk melihat foto terkunci")
            .setAllowedAuthenticators(authenticators)
            .build()
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultScreen(
    viewModel: GalleryViewModel,
    onPhotoClick: (Photo) -> Unit,
    onBack: () -> Unit
) {
    val lockedPhotos by viewModel.lockedPhotos.collectAsStateWithLifecycle()
    val selected by viewModel.selectedIds.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Brankas") },
                navigationIcon = {
                    IconButton(onClick = { viewModel.clearSelection(); onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Kembali")
                    }
                },
                actions = {
                    if (selected.isNotEmpty()) {
                        IconButton(onClick = { viewModel.unlockPhotos(selected) }) {
                            Icon(Icons.Filled.LockOpen, "Keluarkan dari Brankas")
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (lockedPhotos.isEmpty()) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "Brankas kosong.\n\nTekan-lama foto di galeri lalu ketuk ikon " +
                        "gembok untuk menyembunyikannya di sini. Foto di Brankas " +
                        "tidak muncul di galeri, album, maupun hasil pencarian Galeriva.",
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(104.dp),
                contentPadding = PaddingValues(4.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                items(lockedPhotos, key = { it.id }) { photo ->
                    PhotoThumbnail(
                        photo = photo,
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
}
