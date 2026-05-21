package com.example

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.data.*
import com.example.ui.theme.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    PhotoOrganizerApp()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoOrganizerApp() {
    val context = LocalContext.current
    val viewModel: PhotoViewModel = viewModel()
    
    val photos by viewModel.filteredPhotos.collectAsState()
    val allPhotosCount by viewModel.allPhotos.collectAsState()
    val tags by viewModel.uniqueTags.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val selectedTag by viewModel.selectedTag.collectAsState()
    val isSyncing by viewModel.isSyncing.collectAsState()

    // Active bottom navigation tab
    var activeTab by remember { mutableStateOf("Photos") } // "Photos", "Tags", "Settings"

    // Dialog & detail view state
    var selectedPhotoDetail by remember { mutableStateOf<PhotoItem?>(null) }
    var isEditingPhoto by remember { mutableStateOf(false) }

    // Media Picker Launcher
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.addLocalPhoto(uri, "Local Import #${allPhotosCount.size + 1}")
            Toast.makeText(context, "Photo imported locally", Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        bottomBar = {
            // Elegant M3 Navigation Bar aligned with Clean Minimalism spec
            NavigationBar(
                containerColor = MinimalInputBg,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MinimalBorder.copy(alpha = 0.2f))
                    .testTag("app_bottom_nav"),
                windowInsets = WindowInsets.navigationBars
            ) {
                listOf(
                    Triple("Photos", Icons.Default.Home, "Photos"),
                    Triple("Tags", Icons.Default.List, "Tags"),
                    Triple("Settings", Icons.Default.Settings, "Config")
                ).forEach { (tabName, icon, label) ->
                    val isActive = activeTab == tabName
                    NavigationBarItem(
                        selected = isActive,
                        onClick = { activeTab = tabName },
                        icon = {
                            Icon(
                                imageVector = icon,
                                contentDescription = label,
                                tint = if (isActive) MinimalAccentText else MinimalSubText
                            )
                        },
                        label = {
                            Text(
                                text = label,
                                fontSize = 11.sp,
                                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                                color = if (isActive) MinimalText else MinimalSubText
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = MinimalHighlight
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MinimalBg)
        ) {
            // Primary layout branching based on bottom navigation tabs
            Column(modifier = Modifier.fillMaxSize()) {
                // Shared header for Clean Minimalism aesthetic
                HeaderSection(
                    photoCount = allPhotosCount.size,
                    onSyncClick = { viewModel.syncWithGooglePhotos() },
                    onAddPhotoClick = {
                        imagePickerLauncher.launch(
                            PickVisualMediaRequest(
                                ActivityResultContracts.PickVisualMedia.ImageOnly
                            )
                        )
                    },
                    isSyncing = isSyncing
                )

                Spacer(modifier = Modifier.height(12.dp))

                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    when (activeTab) {
                        "Photos" -> {
                            PhotosGalleryTab(
                                photos = photos,
                                tags = tags,
                                searchQuery = searchQuery,
                                onQueryChange = { viewModel.searchQuery.value = it },
                                selectedCategory = selectedCategory,
                                onCategoryChange = {
                                    viewModel.selectedCategory.value = if (it.isEmpty() || it.equals(selectedCategory, ignoreCase = true)) null else it
                                },
                                selectedTag = selectedTag,
                                onTagChange = {
                                    viewModel.selectedTag.value = if (it.isEmpty() || it.equals(selectedTag, ignoreCase = true)) null else it
                                },
                                onPhotoSelect = { selectedPhotoDetail = it },
                                onToggleFavorite = { viewModel.toggleFavorite(it) }
                            )
                        }
                        "Tags" -> {
                            TagsManagementTab(
                                tags = tags,
                                allPhotos = allPhotosCount,
                                onTagSelect = {
                                    viewModel.selectedTag.value = it
                                    activeTab = "Photos"
                                }
                            )
                        }
                        "Settings" -> {
                            SettingsTab(
                                allCount = allPhotosCount.size,
                                onClearAll = { viewModel.clearAllPhotos() },
                                onResetSync = { viewModel.syncWithGooglePhotos() }
                            )
                        }
                    }
                }
            }

            // Syncing overlay HUD (Progress indicators)
            if (isSyncing) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f))
                        .clickable(enabled = false) {},
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MinimalSurface),
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier
                            .padding(24.dp)
                            .border(1.dp, MinimalBorder.copy(alpha = 0.3f))
                    ) {
                        Column(
                            modifier = Modifier.padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(color = MinimalActionPurple)
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                "Google Photos Sync",
                                fontWeight = FontWeight.SemiBold,
                                color = MinimalText,
                                fontSize = 16.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "Downloading album metadata...",
                                color = MinimalSubText,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }

            // Detailed photo bottom modal sheet overlay
            selectedPhotoDetail?.let { photo ->
                val currentPhoto = allPhotosCount.find { it.id == photo.id } ?: photo

                DetailPhotoModal(
                    photo = currentPhoto,
                    onDismiss = {
                        selectedPhotoDetail = null
                        isEditingPhoto = false
                    },
                    onDelete = {
                        viewModel.deletePhoto(currentPhoto.id)
                        selectedPhotoDetail = null
                    },
                    onToggleFavorite = { viewModel.toggleFavorite(currentPhoto) },
                    onUpdatePhoto = { viewModel.updatePhoto(it) },
                    isEditing = isEditingPhoto,
                    toggleEdit = { isEditingPhoto = !isEditingPhoto }
                )
            }
        }
    }
}

@Composable
fun HeaderSection(
    photoCount: Int,
    onSyncClick: () -> Unit,
    onAddPhotoClick: () -> Unit,
    isSyncing: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Photos",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = (-0.5).sp,
                    color = MinimalText
                )
                Text(
                    text = "$photoCount organized items",
                    fontSize = 13.sp,
                    color = MinimalSubText,
                    fontWeight = FontWeight.Normal
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Simulated Google Sync button
                Button(
                    onClick = onSyncClick,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MinimalHighlight,
                        contentColor = MinimalAccentText
                    ),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    modifier = Modifier
                        .height(38.dp)
                        .testTag("google_sync_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Sync",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Sync Sync", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                // Add photo FAB/button
                IconButton(
                    onClick = onAddPhotoClick,
                    modifier = Modifier
                        .size(38.dp)
                        .background(MinimalInputBg, CircleShape)
                        .border(1.dp, MinimalBorder.copy(alpha = 0.3f), CircleShape)
                        .testTag("add_photo_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add local photo",
                        tint = MinimalActionPurple,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun PhotosGalleryTab(
    photos: List<PhotoItem>,
    tags: List<String>,
    searchQuery: String,
    onQueryChange: (String) -> Unit,
    selectedCategory: String?,
    onCategoryChange: (String) -> Unit,
    selectedTag: String?,
    onTagChange: (String) -> Unit,
    onPhotoSelect: (PhotoItem) -> Unit,
    onToggleFavorite: (PhotoItem) -> Unit
) {
    val keyboardController = LocalSoftwareKeyboardController.current

    Column(modifier = Modifier.fillMaxSize()) {
        // Search bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onQueryChange,
            placeholder = { Text("Search photos, locations or tags...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", tint = MinimalSubText) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(24.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MinimalInputBg,
                unfocusedContainerColor = MinimalInputBg,
                focusedBorderColor = MinimalBorder,
                unfocusedBorderColor = Color.Transparent
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { keyboardController?.hide() }),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .testTag("search_bar")
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Smart Tags Carousel
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Smart Tags",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MinimalSubText.copy(alpha = 0.8f),
                    letterSpacing = 1.sp
                )
                if (selectedTag != null) {
                    Text(
                        text = "Reset tag view",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = MinimalActionPurple,
                        modifier = Modifier.clickable { onTagChange("") }
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // Static recent list tag resets selection
                item {
                    val isNoneSelected = selectedTag == null
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (isNoneSelected) MinimalHighlight else MinimalInputBg
                        ),
                        shape = RoundedCornerShape(12.dp),
                        onClick = { onTagChange("") },
                        modifier = Modifier.border(
                            1.dp,
                            if (isNoneSelected) MinimalActionPurple.copy(alpha = 0.3f) else Color.Transparent,
                            RoundedCornerShape(12.dp)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.List,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = if (isNoneSelected) MinimalAccentText else MinimalSubText
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "All Tags",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (isNoneSelected) MinimalAccentText else MinimalText
                            )
                        }
                    }
                }

                items(tags) { tag ->
                    val isSelected = selectedTag?.equals(tag, ignoreCase = true) == true
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) MinimalHighlight else MinimalInputBg
                        ),
                        shape = RoundedCornerShape(12.dp),
                        onClick = { onTagChange(tag) },
                        modifier = Modifier.border(
                            1.dp,
                            if (isSelected) MinimalActionPurple.copy(alpha = 0.3f) else MinimalBorder.copy(alpha = 0.4f),
                            RoundedCornerShape(12.dp)
                        )
                    ) {
                        Text(
                            text = tag,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (isSelected) MinimalAccentText else MinimalText,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Categories Header Label
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            Text(
                text = "Categories",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MinimalSubText.copy(alpha = 0.8f),
                letterSpacing = 1.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Beautiful grid of standard Material Cards for categories
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                listOf(
                    Triple("People", CategoryPeopleBg, Icons.Default.Face),
                    Triple("Places", CategoryPlacesBg, Icons.Default.LocationOn),
                    Triple("Docs", CategoryDocsBg, Icons.Default.List),
                    Triple("Receipts", CategoryReceiptsBg, Icons.Default.ShoppingCart)
                ).forEach { (catName, badgeBg, icon) ->
                    val isSelected = selectedCategory?.equals(catName, ignoreCase = true) == true
                    
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(0.95f)
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (isSelected) MinimalHighlight else MinimalSurface)
                            .border(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = if (isSelected) MinimalActionPurple else MinimalBorder.copy(alpha = 0.3f),
                                shape = RoundedCornerShape(16.dp)
                            )
                            .clickable { onCategoryChange(catName) }
                            .padding(10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            verticalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(badgeBg),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = catName,
                                    modifier = Modifier.size(18.dp),
                                    tint = when(catName) {
                                        "People" -> CategoryPeopleText
                                        "Places" -> CategoryPlacesText
                                        "Docs" -> CategoryDocsText
                                        else -> CategoryReceiptsText
                                    }
                                )
                            }
                            Column {
                                Text(
                                    text = catName,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MinimalText
                                )
                                Text(
                                    text = if (isSelected) "Active" else "Filter",
                                    fontSize = 10.sp,
                                    color = MinimalSubText,
                                    fontWeight = FontWeight.Normal
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Photo Grid gallery title
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "My Gallery",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MinimalText
            )
            if (selectedCategory != null || selectedTag != null || searchQuery.isNotEmpty()) {
                Text(
                    text = "Clear All Filters",
                    fontSize = 12.sp,
                    color = MinimalActionPurple,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable {
                        onCategoryChange("")
                        onTagChange("")
                        onQueryChange("")
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Empty State Handler
        if (photos.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Home,
                        contentDescription = null,
                        tint = MinimalBorder,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "No photos matching filters found.",
                        fontWeight = FontWeight.Medium,
                        color = MinimalSubText,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Click 'Sync' at top to load royalty-free sample photos, or tap '+' to import image catalog files.",
                        textAlign = TextAlign.Center,
                        fontSize = 12.sp,
                        color = MinimalSubText.copy(alpha = 0.7f),
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                }
            }
        } else {
            // High fidelity image grid
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .testTag("photo_gallery_grid")
            ) {
                items(photos) { photo ->
                    PhotoCardItem(
                        photo = photo,
                        onClick = { onPhotoSelect(photo) },
                        onToggleFavorite = { onToggleFavorite(photo) }
                    )
                }
            }
        }
    }
}



@Composable
fun PhotoCardItem(
    photo: PhotoItem,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MinimalSurface),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .border(1.dp, MinimalBorder.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            // Photo view
            AsyncImage(
                model = photo.uriString,
                contentDescription = photo.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
            )

            // Category badge overlay
            Box(
                modifier = Modifier
                    .padding(8.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .align(Alignment.TopStart)
            ) {
                Text(
                    text = photo.category,
                    fontSize = 9.sp,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }

            // Favorite button overlay
            IconButton(
                onClick = onToggleFavorite,
                modifier = Modifier
                    .padding(4.dp)
                    .size(28.dp)
                    .background(Color.Black.copy(alpha = 0.4f), CircleShape)
                    .align(Alignment.TopEnd)
            ) {
                Icon(
                    imageVector = if (photo.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = "Favorite",
                    tint = if (photo.isFavorite) Color.Red else Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        Column(modifier = Modifier.padding(8.dp)) {
            Text(
                text = photo.title,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MinimalText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (photo.location.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Place,
                        contentDescription = null,
                        tint = MinimalSubText,
                        modifier = Modifier.size(10.dp)
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(
                        text = photo.location,
                        fontSize = 10.sp,
                        color = MinimalSubText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
fun TagsManagementTab(
    tags: List<String>,
    allPhotos: List<PhotoItem>,
    onTagSelect: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = "Photo Tags Organizer",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = MinimalText
        )
        Text(
            text = "Manage and explore metadata tags associated with your synced media.",
            fontSize = 13.sp,
            color = MinimalSubText,
            modifier = Modifier.padding(vertical = 4.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (tags.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No active metadata tags. Open a photo detail card and click Edit Info to add tags manually.",
                    textAlign = TextAlign.Center,
                    color = MinimalSubText,
                    fontSize = 13.sp
                )
            }
        } else {
            tags.forEach { tag ->
                val matchingCount = allPhotos.count {
                    it.tagsString.split(",").map { t -> t.trim().lowercase() }.contains(tag.lowercase().trim())
                }
                
                Card(
                    colors = CardDefaults.cardColors(containerColor = MinimalSurface),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clickable { onTagSelect(tag) }
                        .border(1.dp, MinimalBorder.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.List, contentDescription = null, tint = MinimalActionPurple, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                  text = tag,
                                  fontSize = 14.sp,
                                  fontWeight = FontWeight.SemiBold,
                                  color = MinimalText
                            )
                        }
                        Badge(
                            containerColor = MinimalHighlight,
                            contentColor = MinimalAccentText
                        ) {
                            Text("$matchingCount photos", modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsTab(
    allCount: Int,
    onClearAll: () -> Unit,
    onResetSync: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = "App settings & Config",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = MinimalText
        )
        Text(
            text = "System management dashboard for photo indexers.",
            fontSize = 13.sp,
            color = MinimalSubText,
            modifier = Modifier.padding(vertical = 4.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Data Management",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = MinimalText
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Reset database / Sync again button
        Card(
            colors = CardDefaults.cardColors(containerColor = MinimalSurface),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, MinimalBorder.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "Re-Sync Photo Catalog",
                    fontWeight = FontWeight.SemiBold,
                    color = MinimalText,
                    fontSize = 14.sp
                )
                Text(
                    "Downloads latest shared Google Album photo indexes. Existing tags of synced photos will be reset in local storage.",
                    fontSize = 12.sp,
                    color = MinimalSubText,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
                Button(
                    onClick = onResetSync,
                    colors = ButtonDefaults.buttonColors(containerColor = MinimalActionPurple),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    Text("Re-Sync Now", fontSize = 12.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Clear local storage card
        Card(
            colors = CardDefaults.cardColors(containerColor = MinimalSurface),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, MinimalBorder.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "Clear Database Storage",
                    fontWeight = FontWeight.SemiBold,
                    color = Color.Red,
                    fontSize = 14.sp
                )
                Text(
                    "Removes all indexed photo files and manually added tags from local device memory. $allCount items currently indexed.",
                    fontSize = 12.sp,
                    color = MinimalSubText,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
                Button(
                    onClick = onClearAll,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red.copy(alpha = 0.1f), contentColor = Color.Red),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    Text("Clear All Data", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DetailPhotoModal(
    photo: PhotoItem,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
    onToggleFavorite: () -> Unit,
    onUpdatePhoto: (PhotoItem) -> Unit,
    isEditing: Boolean,
    toggleEdit: () -> Unit
) {
    // Local form states
    var editTitle by remember(photo.title) { mutableStateOf(photo.title) }
    var editCategory by remember(photo.category) { mutableStateOf(photo.category) }
    var editTags by remember(photo.tagsString) { mutableStateOf(photo.tagsString) }
    var editLocation by remember(photo.location) { mutableStateOf(photo.location) }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            if (isEditing) {
                Button(
                    onClick = {
                        onUpdatePhoto(
                            photo.copy(
                                title = editTitle,
                                category = editCategory,
                                tagsString = editTags,
                                location = editLocation
                            )
                        )
                        toggleEdit()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MinimalActionPurple)
                ) {
                    Text("Save")
                }
            } else {
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = MinimalInputBg, contentColor = MinimalText)
                ) {
                    Text("Done")
                }
            }
        },
        dismissButton = {
            if (isEditing) {
                TextButton(onClick = toggleEdit) {
                    Text("Cancel", color = MinimalSubText)
                }
            } else {
                TextButton(onClick = toggleEdit) {
                    Text("Edit Info", color = MinimalActionPurple, fontWeight = FontWeight.Bold)
                }
            }
        },
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isEditing) "Edit Metadata" else "Photo Details",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MinimalText
                )
                Row {
                    // Favorite button
                    IconButton(onClick = onToggleFavorite) {
                        Icon(
                            imageVector = if (photo.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "Favorite",
                            tint = if (photo.isFavorite) Color.Red else MinimalText
                        )
                    }
                    // Delete button
                    IconButton(onClick = onDelete) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = Color.Red
                        )
                    }
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                // High-res preview image
                Card(
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                ) {
                    AsyncImage(
                        model = photo.uriString,
                        contentDescription = photo.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (isEditing) {
                    // Edit Form
                    Text("Title", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MinimalSubText)
                    OutlinedTextField(
                        value = editTitle,
                        onValueChange = { editTitle = it },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text("Category", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MinimalSubText)
                    // Simple select list mapping for category editing
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        listOf("People", "Places", "Docs", "Receipts").forEach { cat ->
                            val isSelected = editCategory.equals(cat, ignoreCase = true)
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isSelected) MinimalHighlight else MinimalInputBg
                                ),
                                shape = RoundedCornerShape(8.dp),
                                onClick = { editCategory = cat },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    cat,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) MinimalAccentText else MinimalText,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text("Location", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MinimalSubText)
                    OutlinedTextField(
                        value = editLocation,
                        onValueChange = { editLocation = it },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text("Tags (comma separated)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MinimalSubText)
                    OutlinedTextField(
                        value = editTags,
                        onValueChange = { editTags = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    )
                } else {
                    // Display View
                    Text(
                        text = photo.title,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MinimalText
                    )

                    // Details block
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Place, contentDescription = null, tint = MinimalSubText, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (photo.location.isNotEmpty()) photo.location else "No location recorded",
                            fontSize = 12.sp,
                            color = MinimalSubText
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.List, contentDescription = null, tint = MinimalSubText, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Category: ${photo.category}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = MinimalActionPurple
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Tags flow list
                    Text("Associated Tags", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MinimalSubText)
                    Spacer(modifier = Modifier.height(4.dp))

                    if (photo.tagsString.isEmpty()) {
                        Text("No metadata tags found. Click 'Edit Info' to add tags manually.", fontSize = 12.sp, color = MinimalSubText)
                    } else {
                        ContextFlowRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            photo.tagsString.split(",").forEach { tag ->
                                if (tag.trim().isNotEmpty()) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(MinimalInputBg)
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = tag.trim().lowercase(),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = MinimalText
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ContextFlowRow(
    modifier: Modifier = Modifier,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    content: @Composable FlowRowScope.() -> Unit
) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = horizontalArrangement,
        verticalArrangement = verticalArrangement,
        content = content
    )
}
