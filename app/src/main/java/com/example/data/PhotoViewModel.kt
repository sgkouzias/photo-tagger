package com.example.data

import android.app.Application
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PhotoViewModel(application: Application) : AndroidViewModel(application) {
    private val db = PhotoDatabase.getDatabase(application)
    private val dao = db.photoDao()

    // Base database photos flow
    val allPhotos: StateFlow<List<PhotoItem>> = dao.getAllPhotos()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Active filters
    val searchQuery = MutableStateFlow("")
    val selectedCategory = MutableStateFlow<String?>(null) // null = all
    val selectedTag = MutableStateFlow<String?>(null) // null = all

    // Syncing state
    val isSyncing = MutableStateFlow(false)

    // Filtered photos based on search query, active category, and active tags
    val filteredPhotos: StateFlow<List<PhotoItem>> = combine(
        allPhotos,
        searchQuery,
        selectedCategory,
        selectedTag
    ) { photos, query, category, tag ->
        var list = photos

        // 1. Filter by category
        if (!category.isNullOrEmpty()) {
            list = list.filter { it.category.equals(category, ignoreCase = true) }
        }

        // 2. Filter by tag
        if (!tag.isNullOrEmpty()) {
            list = list.filter { photo ->
                photo.tagsString.split(",")
                    .map { it.trim().lowercase() }
                    .contains(tag.lowercase().trim())
            }
        }

        // 3. Search query
        if (query.isNotEmpty()) {
            list = list.filter { photo ->
                photo.title.contains(query, ignoreCase = true) ||
                photo.category.contains(query, ignoreCase = true) ||
                photo.location.contains(query, ignoreCase = true) ||
                photo.tagsString.split(",").any { it.trim().contains(query, ignoreCase = true) }
            }
        }

        list
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Extract all unique tags currently in our database
    val uniqueTags: StateFlow<List<String>> = allPhotos.combine(MutableStateFlow(Unit)) { photos, _ ->
        photos.flatMap { photo ->
            photo.tagsString.split(",")
                .map { it.trim().lowercase() }
                .filter { it.isNotEmpty() }
        }.distinct().sorted()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * Simulates modern Google Photos Sync
     */
    fun syncWithGooglePhotos() {
        viewModelScope.launch {
            isSyncing.value = true
            // Simulate beautiful network loading
            kotlinx.coroutines.delay(2000)

            val samplePictures = listOf(
                PhotoItem(
                    uriString = "https://images.unsplash.com/photo-1470071459604-3b5ec3a7fe05?q=80&w=800",
                    title = "Emerald Lake Sunrise",
                    category = "Places",
                    tagsString = "lake,mountains,sunrise,scenic,nature,travel",
                    location = "Banff, Canada"
                ),
                PhotoItem(
                    uriString = "https://images.unsplash.com/photo-1542037104857-ffbb0b9155fb?q=80&w=800",
                    title = "Family Sunday Picnic",
                    category = "People",
                    tagsString = "family,sunday,picnic,park,gathering,happy",
                    location = "Central Park, NY"
                ),
                PhotoItem(
                    uriString = "https://images.unsplash.com/photo-1494790108377-be9c29b29330?q=80&w=800",
                    title = "Sarah Portrait Session",
                    category = "People",
                    tagsString = "portrait,sarah,smile,studio,curated",
                    location = "Downtown Studio"
                ),
                PhotoItem(
                    uriString = "https://images.unsplash.com/photo-1504280390367-361c6d9f38f4?q=80&w=800",
                    title = "Forest Campfires",
                    category = "Places",
                    tagsString = "camping,campfires,stars,nature,outdoor,travel",
                    location = "Yosemite National Park"
                ),
                PhotoItem(
                    uriString = "https://images.unsplash.com/photo-1600585154340-be6161a56a0c?q=80&w=800",
                    title = "Mid-Century Modern Residence",
                    category = "Places",
                    tagsString = "architecture,minimalist,modern,interior,design",
                    location = "Palm Springs, CA"
                ),
                PhotoItem(
                    uriString = "https://images.unsplash.com/photo-1502602898657-3e91760cbb34?q=80&w=800",
                    title = "Café de Paris Lunch",
                    category = "Places",
                    tagsString = "paris,coffee,cafe,aesthetic,lunch,travel",
                    location = "Paris, France"
                ),
                PhotoItem(
                    uriString = "https://images.unsplash.com/photo-1455390582262-044cdead277a?q=80&w=800",
                    title = "Draft Notebook Page",
                    category = "Docs",
                    tagsString = "notebook,draft,scallop,journal,notes,study",
                    location = "Office Cabin"
                ),
                PhotoItem(
                    uriString = "https://images.unsplash.com/photo-1517842645767-c639042777db?q=80&w=800",
                    title = "Weekly Grocery Purchases",
                    category = "Receipts",
                    tagsString = "receipt,weekly,expense,grocery,finance,spending",
                    location = "Whole Foods Main"
                )
            )

            for (pic in samplePictures) {
                dao.insertPhoto(pic)
            }
            isSyncing.value = false
        }
    }

    /**
     * Adds an user selected local photo
     */
    fun addLocalPhoto(uri: Uri, title: String) {
        viewModelScope.launch {
            val newItem = PhotoItem(
                uriString = uri.toString(),
                title = title,
                category = "Aesthetic",
                tagsString = "added,local",
                location = "Unknown Location"
            )
            dao.insertPhoto(newItem)
        }
    }

    /**
     * Manual modifications
     */
    fun updatePhoto(photo: PhotoItem) {
        viewModelScope.launch {
            dao.updatePhoto(photo)
        }
    }

    fun deletePhoto(photoId: Long) {
        viewModelScope.launch {
            dao.deletePhotoById(photoId)
        }
    }

    fun toggleFavorite(photo: PhotoItem) {
        viewModelScope.launch {
            dao.updatePhoto(photo.copy(isFavorite = !photo.isFavorite))
        }
    }

    fun clearAllPhotos() {
        viewModelScope.launch {
            allPhotos.value.forEach {
                dao.deletePhoto(it)
            }
        }
    }
}
