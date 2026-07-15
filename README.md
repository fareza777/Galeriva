# Galeriva

Aplikasi galeri foto Android premium dengan pengelolaan pintar **100% on-device** — foto tidak pernah meninggalkan perangkat. Cocok untuk model penjualan sekali-bayar (tidak ada biaya server).

## Fitur

- **Galeri** — grid foto dikelompokkan per hari (bahasa Indonesia), tampilan Material 3 dengan dynamic color (Android 12+) dan dark mode.
- **Pengindeksan otomatis** — ML Kit Image Labeling berjalan di latar belakang (WorkManager) dan menandai setiap foto (meeting, food, beach, document, dll). Hasil disimpan lokal di Room.
- **Pencarian bahasa Indonesia** — ketik "rapat" dan semua foto rapat/whiteboard/presentasi muncul. Kamus kata kunci ID→EN ada di `SearchKeywords.kt`.
- **Album pintar** — kategori otomatis: Rapat & Kerja, Orang, Makanan, Alam, Hewan, Kendaraan, Dokumen, Kota.
- **Album folder** — folder asli perangkat dari MediaStore.
- **Viewer** — swipe antar foto, pinch-to-zoom, favorit, bagikan, hapus.
- **Multi-select** — tekan-lama foto di galeri untuk memilih banyak, lalu hapus atau bagikan sekaligus (dialog konfirmasi sistem Android).
- **Deteksi duplikat** — pindai foto yang persis sama (pre-filter ukuran+dimensi, konfirmasi MD5) dan hapus untuk membebaskan ruang.
- **Foto mirip** — perceptual hash (dHash 64-bit, dihitung saat pengindeksan) mengelompokkan jepretan beruntun/foto hampir sama.
- **Brankas** — sembunyikan foto dari galeri/album/pencarian, dilindungi sidik jari atau kunci layar (BiometricPrompt). Catatan: foto tetap ada di penyimpanan dan masih terlihat di aplikasi galeri lain.

## Teknologi

Kotlin • Jetpack Compose (Material 3) • Room • WorkManager • ML Kit Image Labeling (offline) • Coil • Navigation Compose

- `minSdk 26` (Android 8.0) • `targetSdk 35`

## Build

### Via GitHub Actions (tanpa install apa pun)
Setiap push ke `main` otomatis mem-build APK debug. Unduh dari tab **Actions → Build APK → Artifacts**.

### Lokal (Android Studio)
Buka folder proyek di Android Studio (Ladybug atau lebih baru) dan jalankan konfigurasi `app`.

## Rilis ke Play Store

CI sudah mem-build **AAB release** (`galeriva-release-aab` di Artifacts). Tanpa keystore, AAB ditandatangani dengan debug key (cukup untuk uji coba, belum bisa diunggah ke Play Store).

Untuk signing resmi, buat keystore lalu tambahkan GitHub Secrets:

| Secret | Isi |
|---|---|
| `KEYSTORE_BASE64` | file `.jks` di-encode base64 |
| `KEYSTORE_PASSWORD` | password keystore |
| `KEY_ALIAS` | alias key |
| `KEY_PASSWORD` | password key |

Buat keystore: `keytool -genkeypair -v -keystore galeriva.jks -keyalg RSA -keysize 2048 -validity 10000 -alias galeriva`

Kebijakan privasi (wajib Play Store): [PRIVACY.md](PRIVACY.md)

## Roadmap

- [x] Multi-select + hapus/bagikan batch
- [x] Deteksi foto duplikat (MD5)
- [x] Build release AAB via CI + dukungan signing
- [x] Kebijakan privasi
- [x] Foto mirip / hampir duplikat (perceptual hash)
- [x] Brankas terkunci (BiometricPrompt)
- [ ] Pengelompokan wajah (ML Kit Face Detection)
- [ ] Pindah/salin ke album (MediaStore RELATIVE_PATH)
- [ ] Enkripsi file Brankas (saat ini hanya disembunyikan dari Galeriva)
