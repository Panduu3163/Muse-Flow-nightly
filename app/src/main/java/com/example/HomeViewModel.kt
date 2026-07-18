package com.example

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** A Home shelf backed by a canned search query, standing in for a real recommendation system. */
private data class MoodShelfQuery(val title: String, val query: String)

private val moodShelfQueries = listOf(
    MoodShelfQuery("Chill Vibes", "lofi chill beats"),
    MoodShelfQuery("Workout Energy", "workout gym hype"),
    MoodShelfQuery("Party Hits", "party dance hits"),
    MoodShelfQuery("Focus Flow", "instrumental focus study")
)

/**
 * Real data for Home, in place of the static [MusicData] shelves: "Recently Played" comes from
 * actual playback history in Room ([PlaybackHistoryRepository]), and every other shelf is a
 * canned genre/mood search against JioSaavn via [Provider] - a stand-in for a real
 * recommendation system, which doesn't exist yet. Each shelf loads independently (its entry in
 * [moodShelves] is null until that one search resolves) so a slow or failed shelf doesn't block
 * the others from appearing.
 *
 * If JioSaavn returns no results or fails/times out for a shelf, YouTube Music is tried as a
 * fallback - same resilience approach Search uses, but sequential (JioSaavn first, YouTube only
 * when needed) to avoid hitting YouTube's API unnecessarily on every launch.
 */
class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val playbackHistoryRepository = PlaybackHistoryRepository.getInstance(application)
    private val jioProvider: Provider<TrackResult> = JioSaavnProvider()
    private val ytProvider: Provider<TrackResult> = YouTubeMusicProvider(application)
    
    private val homeShelfDao = MuseFlowDatabase.getInstance(application).homeShelfDao()
    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
    private val trackListAdapter = moshi.adapter<List<Track>>(Types.newParameterizedType(List::class.java, Track::class.java))

    /** Null while the first read from Room is still in flight; empty once loaded with no history. */
    val recentlyPlayed: StateFlow<List<Track>?> = playbackHistoryRepository.observeRecent(10)
        .map { entities -> entities.map { it.toTrack() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val shelfTitles: List<String> = moodShelfQueries.map { it.title }

    /** Shelf title -> [UiState]. A missing entry (or [UiState.Loading]) means that shelf's search
     * hasn't resolved yet; [UiState.Error] means a genuine failure or timeout, distinct from
     * [UiState.Success] with an empty list (resolved, just nothing playable found). */
    private val _moodShelves = MutableStateFlow<Map<String, UiState<List<Track>>>>(emptyMap())
    val moodShelves: StateFlow<Map<String, UiState<List<Track>>>> = _moodShelves

    private val _isOfflineMode = MutableStateFlow(false)
    val isOfflineMode = _isOfflineMode.asStateFlow()

    private val connectivityManager = application.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    init {
        setupNetworkCallback()
        loadShelves(isRefresh = false)
    }

    private fun setupNetworkCallback() {
        // Initial state check
        val activeNetwork = connectivityManager.activeNetwork
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
        val isConnected = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        _isOfflineMode.value = !isConnected

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                // Network regained, re-fetch shelves if we were offline
                if (_isOfflineMode.value) {
                    _isOfflineMode.value = false
                    loadShelves(isRefresh = true)
                }
            }

            override fun onLost(network: Network) {
                _isOfflineMode.value = true
            }
        }
        connectivityManager.registerNetworkCallback(request, networkCallback!!)
    }
    
    override fun onCleared() {
        super.onCleared()
        networkCallback?.let { connectivityManager.unregisterNetworkCallback(it) }
    }

    private fun loadShelves(isRefresh: Boolean) {
        if (isRefresh) {
            // Reset state to loading on refresh
            _moodShelves.value = emptyMap()
        }

        moodShelfQueries.forEach { shelf ->
            viewModelScope.launch {
                // If offline, try to load from cache immediately
                if (_isOfflineMode.value) {
                    val cachedEntity = homeShelfDao.getShelf(shelf.title)
                    if (cachedEntity != null) {
                        try {
                            val tracks = trackListAdapter.fromJson(cachedEntity.tracksJson) ?: emptyList()
                            _moodShelves.update { it + (shelf.title to UiState.Success(tracks)) }
                            return@launch
                        } catch (e: Exception) {
                            // JSON parsing failed, fallback to error/empty
                        }
                    }
                }

                // Normal online load
                val state = loadAsUiState(
                    errorMessage = "Couldn't load \"${shelf.title}\" right now.",
                    timeoutMs = DEFAULT_LOAD_TIMEOUT_MS * 2
                ) {
                    val jioResults = try {
                        withTimeoutOrNull(DEFAULT_LOAD_TIMEOUT_MS) {
                            jioProvider.search(shelf.query).filter { it.directStreamUrl != null }
                        }
                    } catch (_: Exception) { null }

                    val results = if (!jioResults.isNullOrEmpty()) {
                        jioResults
                    } else {
                        try {
                            withTimeoutOrNull(DEFAULT_LOAD_TIMEOUT_MS) {
                                ytProvider.search(shelf.query)
                            } ?: emptyList()
                        } catch (_: Exception) { emptyList() }
                    }

                    results
                        .take(10)
                        .mapIndexed { index, result -> result.toPlayableTrack(gradientIndex = index) }
                }
                
                // Cache the result if successful
                if (state is UiState.Success) {
                    try {
                        val json = trackListAdapter.toJson(state.data)
                        homeShelfDao.saveShelf(HomeShelfEntity(title = shelf.title, tracksJson = json, updatedAt = System.currentTimeMillis()))
                    } catch (e: Exception) {
                        // Ignore cache write error
                    }
                }
                
                _moodShelves.update { it + (shelf.title to state) }
            }
        }
    }
}
