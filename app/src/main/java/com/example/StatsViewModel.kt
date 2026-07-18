package com.example

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*

class StatsViewModel(application: Application) : AndroidViewModel(application) {
    private val db = MuseFlowDatabase.getInstance(application)
    private val statsDao = db.statsDao()

    private val _selectedDropdown = MutableStateFlow("Continuous")
    val selectedDropdown: StateFlow<String> = _selectedDropdown.asStateFlow()

    private val _selectedChip = MutableStateFlow("1 week")
    val selectedChip: StateFlow<String> = _selectedChip.asStateFlow()

    fun selectDropdown(option: String) {
        _selectedDropdown.value = option
        // Reset chip to a sensible default based on dropdown
        when (option) {
            "Continuous" -> _selectedChip.value = "1 week"
            "Weeks" -> _selectedChip.value = "This week"
            "Months" -> _selectedChip.value = "This month"
            "Years" -> _selectedChip.value = "This year"
        }
    }

    fun selectChip(chip: String) {
        _selectedChip.value = chip
    }

    // Determine the 'since' timestamp based on current selection
    @OptIn(ExperimentalCoroutinesApi::class)
    private val sinceTimestamp: Flow<Long> = combine(_selectedDropdown, _selectedChip) { dropdown, chip ->
        val now = System.currentTimeMillis()
        val oneDay = 24L * 60 * 60 * 1000
        val oneWeek = 7L * oneDay
        val oneMonth = 30L * oneDay
        val oneYear = 365L * oneDay

        if (dropdown == "Continuous") {
            when (chip) {
                "1 week" -> now - oneWeek
                "1 month" -> now - oneMonth
                "3 months" -> now - (3 * oneMonth)
                "6 months" -> now - (6 * oneMonth)
                "1 year" -> now - oneYear
                else -> 0L
            }
        } else if (dropdown == "Weeks") {
            when (chip) {
                "This week" -> now - oneWeek
                "Last week" -> now - (2 * oneWeek)
                "2 weeks ago" -> now - (3 * oneWeek)
                else -> 0L
            }
        } else if (dropdown == "Months") {
             when (chip) {
                "This month" -> now - oneMonth
                "Last month" -> now - (2 * oneMonth)
                else -> 0L
            }
        } else if (dropdown == "Years") {
             when (chip) {
                "This year" -> now - oneYear
                "Last year" -> now - (2 * oneYear)
                else -> 0L
            }
        } else {
            0L // All time
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val topSongs: StateFlow<List<TopItem>> = sinceTimestamp
        .flatMapLatest { since -> statsDao.getTopSongs(since, 10) }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val topArtists: StateFlow<List<TopItem>> = sinceTimestamp
        .flatMapLatest { since -> statsDao.getTopArtists(since, 10) }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val topAlbums: StateFlow<List<TopItem>> = sinceTimestamp
        .flatMapLatest { since -> statsDao.getTopAlbums(since, 10) }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val chipOptions = _selectedDropdown.map { dropdown ->
        when (dropdown) {
            "Continuous" -> listOf("1 week", "1 month", "3 months", "6 months", "1 year")
            "Weeks" -> listOf("This week", "Last week", "2 weeks ago")
            "Months" -> listOf("This month", "Last month", "2 months ago")
            "Years" -> listOf("This year", "Last year")
            else -> emptyList()
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, listOf("1 week", "1 month", "3 months", "6 months", "1 year"))
}
