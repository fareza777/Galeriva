# Galeriva

Aplikasi galeri foto Android premium dengan pengelolaan pintar **100% on-device** — foto tidak pernah meninggalkan perangkat. Cocok untuk model penjualan sekali-bayar (tidak ada biaya server).

## Fitur

- **Galeri** — grid foto dikelompokkan per hari (bahasa Indonesia), tampilan Material 3 dengan dynamic color (Android 12+) dan dark mode.
- **Pengindeksan otomatis** — ML Kit Image Labeling berjalan di latar belakang (WorkManager) dan menandai setiap foto (meeting, food, beach, document, dll). Hasil disimpan lokal di Room.
- **Pencarian bahasa Indonesia** — ketik "rapat" dan semua foto rapat/whiteboard/presentasi muncul. Kamus kata kunci ID→EN ada di `SearchKeywords.kt`.
- **Album pintar** — kategori otomatis: Rapat & Kerja, Orang, Makanan, Alam, Hewan, Kendaraan, Dokumen, Kota.
- **Album folder** — folder asli perangkat dari MediaStore.
- **Viewer** — swipe antar foto, pinch-to-zoom, favorit, bagikan.

## Teknologi

Kotlin • Jetpack Compose (Material 3) • Room • WorkManager • ML Kit Image Labeling (offline) • Coil • Navigation Compose

- `minSdk 26` (Android 8.0) • `targetSdk 35`

## Build

### Via GitHub Actions (tanpa install apa pun)
Setiap push ke `main` otomatis mem-build APK debug. Unduh dari tab **Actions → Build APK → Artifacts**.

### Lokal (Android Studio)
Buka folder proyek di Android Studio (Ladybug atau lebih baru) dan jalankan konfigurasi `app`.

## Roadmap menuju Play Store

- [ ] Signing key release + `bundleRelease` (AAB)
- [ ] Deteksi foto duplikat/mirip (perceptual hash)
- [ ] Pengelompokan wajah (ML Kit Face Detection)
- [ ] Multi-select + hapus/pindah batch (MediaStore `createDeleteRequest`)
- [ ] Folder terkunci (BiometricPrompt)
- [ ] Halaman kebijakan privasi (wajib Play Store)
