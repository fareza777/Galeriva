package com.galeriva.app.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.galeriva.app.data.Photo
import com.galeriva.app.ui.GalleryViewModel
import com.galeriva.app.ui.gallery.PhotoThumbnail
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

private val LOCALE_ID = Locale("id", "ID")

fun Photo.localDate(): LocalDate =
    Instant.ofEpochMilli(dateTakenMillis).atZone(ZoneId.systemDefault()).toLocalDate()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    viewModel: GalleryViewModel,
    onDayClick: (LocalDate) -> Unit,
    onBack: () -> Unit
) {
    val photos by viewModel.photos.collectAsStateWithLifecycle()
    val photosByDay = remember(photos) { photos.groupBy { it.localDate() } }

    var monthValue by rememberSaveable {
        mutableStateOf(YearMonth.now().toString())
    }
    val month = YearMonth.parse(monthValue)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Kalender", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Kembali")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 12.dp)
        ) {
            // Month switcher
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { monthValue = month.minusMonths(1).toString() }) {
                    Icon(Icons.Rounded.ChevronLeft, "Bulan sebelumnya")
                }
                Text(
                    "${month.month.getDisplayName(TextStyle.FULL, LOCALE_ID)} ${month.year}",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.onBackground
                )
                IconButton(onClick = { monthValue = month.plusMonths(1).toString() }) {
                    Icon(Icons.Rounded.ChevronRight, "Bulan berikutnya")
                }
            }

            // Weekday labels (Monday first)
            Row(Modifier.fillMaxWidth()) {
                listOf("Sen", "Sel", "Rab", "Kam", "Jum", "Sab", "Min").forEach { day ->
                    Text(
                        day,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            val firstDay = month.atDay(1)
            val leadingBlanks = (firstDay.dayOfWeek.value + 6) % 7
            val cells: List<LocalDate?> =
                List(leadingBlanks) { null } +
                    (1..month.lengthOfMonth()).map { month.atDay(it) }

            LazyVerticalGrid(
                columns = GridCells.Fixed(7),
                contentPadding = PaddingValues(top = 8.dp, bottom = 40.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(cells.size) { index ->
                    val date = cells[index]
                    if (date == null) {
                        Box(Modifier.aspectRatio(1f))
                    } else {
                        DayCell(
                            date = date,
                            photos = photosByDay[date].orEmpty(),
                            onClick = { onDayClick(date) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(date: LocalDate, photos: List<Photo>, onClick: () -> Unit) {
    val hasPhotos = photos.isNotEmpty()
    Box(
        Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (hasPhotos) MaterialTheme.colorScheme.surfaceContainerHigh
                else Color.Transparent
            )
            .then(if (hasPhotos) Modifier.clickable(onClick = onClick) else Modifier)
    ) {
        if (hasPhotos) {
            AsyncImage(
                model = photos.first().uri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Black.copy(alpha = 0.35f), Color.Black.copy(alpha = 0.55f))
                        )
                    )
            )
            Column(
                Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "${date.dayOfMonth}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    "${photos.size}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.85f)
                )
            }
        } else {
            Text(
                "${date.dayOfMonth}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayScreen(
    viewModel: GalleryViewModel,
    epochDay: Long,
    onPhotoClick: (Photo) -> Unit,
    onBack: () -> Unit
) {
    val photos by viewModel.photos.collectAsStateWithLifecycle()
    val favorites by viewModel.favoriteIds.collectAsStateWithLifecycle()
    val date = LocalDate.ofEpochDay(epochDay)
    val dayPhotos = remember(photos, epochDay) {
        photos.filter { it.localDate() == date }
    }
    val title = "${date.dayOfMonth} ${date.month.getDisplayName(TextStyle.FULL, LOCALE_ID)} ${date.year}"

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Kembali")
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
            items(dayPhotos, key = { it.id }) { photo ->
                PhotoThumbnail(
                    photo = photo,
                    isFavorite = photo.id in favorites,
                    onClick = { onPhotoClick(photo) }
                )
            }
        }
    }
}
