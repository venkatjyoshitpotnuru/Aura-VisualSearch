package com.example.mysearchapp

import android.Manifest
import android.content.ContentUris
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Size
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private lateinit var onnxEngine: OnnxEngine
    private lateinit var tokenizer: ClipTokenizer

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        onnxEngine = OnnxEngine(this)
        tokenizer = ClipTokenizer(this)

        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
            requestPermissionLauncher.launch(permission)
        }

        setContent {
            AuraTheme {
                MainScreen(onnxEngine, tokenizer, this)
            }
        }
    }
}

val AuraBackground = Color(0xFF030303)
val AuraSurface = Color(0xFF0E0F14)
val AuraBorder = Color(0xFF1D2030)
val AuraIcyBlue = Color(0xFF4FA8FF)
val AuraLavender = Color(0xFFB993FF)
val AuraPeach = Color(0xFFFFC59E)
val AuraTextGlow = Color(0xFFFFF5EF)
val EmeraldMatch = Color(0xFF10B981)
val AmberMatch = Color(0xFFF59E0B)

val AuraGradient = Brush.horizontalGradient(
    colors = listOf(AuraIcyBlue, AuraLavender, AuraPeach)
)

@Composable
fun AuraTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = AuraBackground,
            surface = AuraSurface,
            primary = AuraIcyBlue
        ),
        content = content
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(engine: OnnxEngine, tokenizer: ClipTokenizer, activity: ComponentActivity) {
    var searchQuery by remember { mutableStateOf("") }
    var statusText by remember { mutableStateOf("Ready to explore") }
    var isSearching by remember { mutableStateOf(false) }
    var scanProgress by remember { mutableFloatStateOf(0f) }

    val depthPresets = listOf(50, 100, 250, 500, -1)
    var selectedLimit by remember { mutableIntStateOf(100) }
    var expandedMenu by remember { mutableStateOf(false) }
    var showCustomDialog by remember { mutableStateOf(false) }
    var customLimitInput by remember { mutableStateOf("") }

    var fullScreenImage by remember { mutableStateOf<Bitmap?>(null) }
    var searchResults by remember { mutableStateOf<List<Pair<Bitmap, Float>>>(emptyList()) }
    val coroutineScope = rememberCoroutineScope()

    if (showCustomDialog) {
        AlertDialog(
            onDismissRequest = { showCustomDialog = false },
            title = { Text("Custom Scan Depth", fontWeight = FontWeight.Bold, color = AuraTextGlow) },
            text = {
                OutlinedTextField(
                    value = customLimitInput,
                    onValueChange = { customLimitInput = it.filter { c -> c.isDigit() } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    placeholder = { Text("e.g. 750 photos") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AuraIcyBlue,
                        unfocusedBorderColor = AuraBorder,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val parsed = customLimitInput.toIntOrNull()
                        if (parsed != null && parsed > 0) selectedLimit = parsed
                        showCustomDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AuraLavender)
                ) { Text("Apply", color = Color.White) }
            },
            dismissButton = {
                TextButton(onClick = { showCustomDialog = false }) { Text("Cancel", color = Color.Gray) }
            },
            containerColor = AuraSurface
        )
    }

    fullScreenImage?.let { bmp ->
        Dialog(
            onDismissRequest = { fullScreenImage = null },
            properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = true)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.95f))
                    .clickable { fullScreenImage = null },
                contentAlignment = Alignment.Center
            ) {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = "Full Screen Match",
                    modifier = Modifier.fillMaxSize().padding(8.dp),
                    contentScale = ContentScale.Fit
                )

                IconButton(
                    onClick = { fullScreenImage = null },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 32.dp, end = 16.dp)
                        .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                ) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close", tint = Color.White)
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AuraBackground)
            .systemBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 18.dp)
        ) {
            Text(
                text = "A U R A",
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 8.sp
                ),
                color = AuraTextGlow
            )
        }

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Describe a memory (e.g. Alappad beach, rangoli)...", color = Color(0xFF6B7280)) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = AuraIcyBlue) },
            trailingIcon = {
                Box {
                    IconButton(onClick = { expandedMenu = true }) {
                        Icon(Icons.Rounded.MoreVert, contentDescription = "Limit", tint = Color.LightGray)
                    }
                    DropdownMenu(
                        expanded = expandedMenu,
                        onDismissRequest = { expandedMenu = false },
                        modifier = Modifier.background(AuraSurface)
                    ) {
                        depthPresets.forEach { limit ->
                            DropdownMenuItem(
                                text = { Text(if (limit == -1) "All Photos" else "Last $limit", color = AuraTextGlow) },
                                onClick = {
                                    selectedLimit = limit
                                    expandedMenu = false
                                }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Custom...", color = AuraPeach) },
                            onClick = {
                                expandedMenu = false
                                showCustomDialog = true
                            }
                        )
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .border(1.5.dp, AuraGradient, CircleShape),
            shape = CircleShape,
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color.Transparent,
                unfocusedBorderColor = Color.Transparent,
                focusedContainerColor = AuraSurface,
                unfocusedContainerColor = AuraSurface,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            )
        )

        Spacer(modifier = Modifier.height(16.dp))

        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items(depthPresets) { depth ->
                val label = if (depth == -1) "All" else "$depth"
                val isSelected = selectedLimit == depth

                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(if (isSelected) AuraGradient else Brush.linearGradient(listOf(AuraSurface, AuraSurface)))
                        .border(1.dp, if (isSelected) Color.Transparent else AuraBorder, CircleShape)
                        .clickable { selectedLimit = depth }
                        .padding(horizontal = 16.dp, vertical = 7.dp)
                ) {
                    Text(
                        text = "$label Photos",
                        color = if (isSelected) Color.Black else Color(0xFF94A3B8),
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                    )
                }
            }
            item {
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(AuraSurface)
                        .border(1.dp, AuraBorder, CircleShape)
                        .clickable { showCustomDialog = true }
                        .padding(horizontal = 14.dp, vertical = 7.dp)
                ) {
                    Text("Custom...", color = AuraPeach, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Button(
            onClick = {
                if (searchQuery.isNotBlank() && !isSearching) {
                    isSearching = true
                    scanProgress = 0f
                    val limitText = if (selectedLimit == -1) "all gallery photos" else "last $selectedLimit photos"
                    statusText = "Analyzing $limitText..."
                    searchResults = emptyList()

                    coroutineScope.launch {
                        withContext(Dispatchers.IO) {
                            try {
                                val baseQuery = searchQuery.trim()
                                val variations = listOf(
                                    "a photo of $baseQuery",
                                    "a clear picture of $baseQuery",
                                    baseQuery
                                )

                                var textVector: FloatArray? = null
                                for (prompt in variations) {
                                    val tokens = tokenizer.encode(prompt)
                                    val vec = engine.extractTextVector(tokens)
                                    if (vec != null) {
                                        if (textVector == null) {
                                            textVector = vec.clone()
                                        } else {
                                            for (i in textVector.indices) {
                                                textVector[i] += vec[i]
                                            }
                                        }
                                    }
                                }

                                textVector?.let { v ->
                                    var norm = 0f
                                    for (x in v) norm += x * x
                                    norm = Math.sqrt(norm.toDouble()).toFloat()
                                    if (norm != 0f) {
                                        for (i in v.indices) v[i] /= norm
                                    }
                                }

                                val finalQueryVector = textVector ?: throw Exception("Could not encode query.")

                                val projection = arrayOf(MediaStore.Images.Media._ID)
                                val cursor = activity.contentResolver.query(
                                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                                    projection, null, null,
                                    "${MediaStore.Images.Media.DATE_ADDED} DESC"
                                )

                                val allMatches = mutableListOf<Pair<Uri, Float>>()
                                var count = 0
                                val targetLimit = if (selectedLimit == -1) cursor?.count ?: 1 else selectedLimit

                                cursor?.use {
                                    val idCol = it.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                                    while (it.moveToNext() && count < targetLimit) {
                                        val id = it.getLong(idCol)
                                        val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)

                                        val fastBitmap: Bitmap? = try {
                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                                activity.contentResolver.loadThumbnail(uri, Size(224, 224), null)
                                            } else {
                                                activity.contentResolver.openInputStream(uri)?.use { stream ->
                                                    val opts = BitmapFactory.Options().apply { inSampleSize = 4 }
                                                    BitmapFactory.decodeStream(stream, null, opts)
                                                }
                                            }
                                        } catch (e: Exception) { null }

                                        if (fastBitmap != null) {
                                            val pixels = engine.processImage(fastBitmap)
                                            val imgVec = engine.extractImageVector(pixels)
                                            if (imgVec != null) {
                                                val score = engine.cosineSimilarity(finalQueryVector, imgVec)
                                                if (score >= 0.22f) {
                                                    allMatches.add(Pair(uri, score))
                                                }
                                            }
                                        }

                                        count++
                                        if (count % 15 == 0) {
                                            scanProgress = count.toFloat() / targetLimit
                                            statusText = "Processed $count / $targetLimit photos"
                                        }
                                    }
                                }

                                statusText = "Selecting top matches..."
                                val winners = allMatches.sortedByDescending { it.second }.take(10)

                                val finalDisplayList = winners.mapNotNull { m ->
                                    try {
                                        activity.contentResolver.openInputStream(m.first)?.use { stream ->
                                            val opts = BitmapFactory.Options().apply { inSampleSize = 2 }
                                            val bmp = BitmapFactory.decodeStream(stream, null, opts)
                                            if (bmp != null) Pair(bmp, m.second) else null
                                        }
                                    } catch (e: Exception) { null }
                                }

                                searchResults = finalDisplayList
                                statusText = if (finalDisplayList.isNotEmpty()) "Search Complete" else "No matching photos found"
                            } catch (e: Exception) {
                                statusText = "Error: ${e.message}"
                            } finally {
                                isSearching = false
                            }
                        }
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .background(AuraGradient, CircleShape),
            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
            shape = CircleShape
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = Color.Black)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isSearching) "Searching..." else "Discover Photos",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = Color.Black
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        if (isSearching) {
            LinearProgressIndicator(
                progress = { scanProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(CircleShape),
                color = AuraIcyBlue,
                trackColor = AuraSurface
            )
            Spacer(modifier = Modifier.height(6.dp))
        }

        Text(text = statusText, color = Color(0xFF6B7280), fontSize = 12.sp)
        Spacer(modifier = Modifier.height(12.dp))

        LazyVerticalStaggeredGrid(
            columns = StaggeredGridCells.Fixed(2),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalItemSpacing = 10.dp
        ) {
            items(searchResults.size) { index ->
                val (bitmap, score) = searchResults[index]
                val scorePercent = (score * 100).toInt()
                val isHighMatch = score >= 0.28f
                val badgeColor = if (isHighMatch) EmeraldMatch else AmberMatch
                val ratio = if (bitmap.height > 0) bitmap.width.toFloat() / bitmap.height.toFloat() else 1f

                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = AuraSurface),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, AuraBorder, RoundedCornerShape(18.dp))
                        .clickable { fullScreenImage = bitmap }
                ) {
                    Box {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(ratio),
                            contentScale = ContentScale.Crop
                        )

                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .background(
                                    Brush.verticalGradient(
                                        0.65f to Color.Transparent,
                                        1.0f to AuraBackground.copy(alpha = 0.95f) // Fixed: changed '=' to 'to'
                                    )
                                )
                        )

                        Row(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(8.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.Black.copy(alpha = 0.4f))
                                .border(1.dp, badgeColor.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(badgeColor)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "$scorePercent%",
                                color = AuraTextGlow,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        }
    }
}