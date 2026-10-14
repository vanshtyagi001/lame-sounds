package com.lamesounds.mobile.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lamesounds.mobile.models.*
import com.lamesounds.mobile.network.DiscoveryManager
import com.lamesounds.mobile.network.PhoneHttpServer
import com.lamesounds.mobile.network.SoundboardApiClient
import com.lamesounds.mobile.network.UdpTriggerClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

data class StorageSummary(
    val totalSets: Int,
    val totalButtons: Int,
    val customSoundsCount: Int,
    val customSoundsSizeBytes: Long
)

class SoundboardViewModel(private val application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("lamesounds_prefs", Context.MODE_PRIVATE)
    private val udpClient = UdpTriggerClient()
    private val apiClient = SoundboardApiClient()
    private val discoveryManager = DiscoveryManager(application.applicationContext)
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }
    private val phoneHttpServer = PhoneHttpServer { _librarySounds.value }

    // Persistent storage files and directories on phone
    private val soundSetsConfigFile = File(application.filesDir, "sound_sets_v1.json")
    private val customSoundsConfigFile = File(application.filesDir, "custom_sounds_v1.json")
    private val soundsDirectory = File(application.filesDir, "custom_sounds").apply {
        if (!exists()) mkdirs()
    }
    private val imagesDirectory = File(application.filesDir, "custom_images").apply {
        if (!exists()) mkdirs()
    }



    private val _syncMessage = MutableStateFlow<String?>(null)
    val syncMessage: StateFlow<String?> = _syncMessage.asStateFlow()

    fun clearSyncMessage() {
        _syncMessage.value = null
    }

    private val _connectionState = MutableStateFlow(ConnectionState())
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _soundSets = MutableStateFlow<List<SoundSet>>(emptyList())
    val soundSets: StateFlow<List<SoundSet>> = _soundSets.asStateFlow()

    private val _librarySounds = MutableStateFlow<List<SoundLibraryItem>>(emptyList())
    val librarySounds: StateFlow<List<SoundLibraryItem>> = _librarySounds.asStateFlow()

    init {
        // Load persistent settings from phone preferences
        val savedHost = prefs.getString("saved_host", "192.168.1.100") ?: "192.168.1.100"
        val savedHttpPort = prefs.getInt("http_port", 50050)
        val savedUdpPort = prefs.getInt("udp_port", 50051)
        val autoConnect = prefs.getBoolean("auto_connect", true)
        val speakerVol = prefs.getFloat("saved_speaker_volume", 1.0f)
        val micVol = prefs.getFloat("saved_mic_volume", 1.0f)
        val hapticEnabled = prefs.getBoolean("haptic_feedback_enabled", true)
        val keepScreenOn = prefs.getBoolean("keep_screen_awake", true)

        _connectionState.update {
            it.copy(
                host = savedHost,
                httpPort = savedHttpPort,
                udpPort = savedUdpPort,
                autoConnectEnabled = autoConnect,
                speakerVolume = speakerVol,
                micVolume = micVol,
                hapticFeedbackEnabled = hapticEnabled,
                keepScreenAwake = keepScreenOn
            )
        }

        // Load sound sets from phone internal storage or initialize defaults
        loadOrInitSoundSets()

        // Load audio library (built-in + persistent imported phone files)
        initLibraryWithStorage()

        // Start embedded phone HTTP server so PC can import phone audio
        phoneHttpServer.start(50055)

        // Start background connection status heartbeat
        startConnectionHeartbeat()

        if (autoConnect) {
            triggerAutoDiscovery()
        }
    }

    // ==========================================
    // 1. SOUND SETS & GRID PERSISTENCE
    // ==========================================

    private fun loadOrInitSoundSets() {
        val loadedSets = try {
            if (soundSetsConfigFile.exists()) {
                val content = soundSetsConfigFile.readText()
                if (content.isNotBlank()) {
                    val decoded = json.decodeFromString<List<SoundSet>>(content)
                    if (decoded.isNotEmpty()) decoded else null
                } else null
            } else null
        } catch (e: Exception) {
            null
        }

        if (loadedSets != null) {
            // Strip any legacy empty placeholder slots (+ Slot 1, + Slot 2, etc.)
            val cleaned = loadedSets.map { set ->
                set.copy(
                    buttons = set.buttons.filter { it.soundId.isNotBlank() && !it.label.startsWith("+ Slot") }
                )
            }
            _soundSets.value = cleaned
        } else {
            initDefaultSoundSets()
            persistSoundSetsToDisk(_soundSets.value)
        }
    }

    private fun persistSoundSetsToDisk(sets: List<SoundSet>) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val serialized = json.encodeToString(sets)
                soundSetsConfigFile.writeText(serialized)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun initDefaultSoundSets() {
        _soundSets.value = listOf(
            SoundSet(name = "SOUND SET 1", rows = 1, columns = 3, buttons = emptyList())
        )
    }

    fun updateButton(setId: String, updated: SoundButtonConfig) {
        _soundSets.update { sets ->
            val modified = sets.map { set ->
                if (set.id == setId) {
                    val existingIdx = set.buttons.indexOfFirst { it.id == updated.id }
                    val updatedButtons = set.buttons.toMutableList()
                    if (existingIdx >= 0) {
                        updatedButtons[existingIdx] = updated
                    } else {
                        updatedButtons.add(updated)
                    }
                    set.copy(buttons = updatedButtons)
                } else set
            }
            persistSoundSetsToDisk(modified)
            modified
        }
    }

    fun deleteButton(setId: String, buttonId: String) {
        _soundSets.update { sets ->
            val modified = sets.map { set ->
                if (set.id == setId) {
                    set.copy(buttons = set.buttons.filterNot { it.id == buttonId })
                } else set
            }
            persistSoundSetsToDisk(modified)
            modified
        }
    }

    fun updateGridDimensions(setId: String, rows: Int, columns: Int) {
        _soundSets.update { sets ->
            val modified = sets.map { set ->
                if (set.id == setId) set.copy(rows = rows, columns = columns) else set
            }
            persistSoundSetsToDisk(modified)
            modified
        }
    }

    fun assignSoundToSlot(setId: String, slotButton: SoundButtonConfig, item: SoundLibraryItem) {
        val isNewSlot = slotButton.id.startsWith("add_slot_") || slotButton.soundId.isBlank()
        val buttonId = if (isNewSlot) java.util.UUID.randomUUID().toString() else slotButton.id
        val updated = slotButton.copy(
            id = buttonId,
            label = item.title,
            soundId = item.id,
            colorHex = item.colorHex,
            gradientHex = item.gradientHex ?: item.colorHex,
            imagePath = item.imagePath
        )
        updateButton(setId, updated)
    }

    /**
     * Appends multiple sounds from the library into a sound set, automatically assigning slots.
     * If the set does not exist yet, creates it.
     */
    fun importMultipleSoundsToSet(setId: String, items: List<SoundLibraryItem>) {
        if (items.isEmpty()) return
        _soundSets.update { sets ->
            val setExists = sets.any { it.id == setId }
            val baseSets = if (!setExists) {
                val nextNum = sets.size + 1
                sets + SoundSet(
                    id = setId,
                    name = "SOUND SET $nextNum",
                    rows = 1,
                    columns = 3,
                    buttons = emptyList()
                )
            } else sets

            val modified = baseSets.map { set ->
                if (set.id == setId) {
                    val currentButtons = set.buttons.filter { it.soundId.isNotBlank() && !it.label.startsWith("+ Slot") }.toMutableList()
                    val cols = set.columns.coerceIn(1, 6)

                    for (item in items) {
                        val row = currentButtons.size / cols
                        val col = currentButtons.size % cols
                        currentButtons.add(
                            SoundButtonConfig(
                                id = java.util.UUID.randomUUID().toString(),
                                soundId = item.id,
                                label = item.title,
                                colorHex = item.colorHex,
                                gradientHex = item.gradientHex ?: item.colorHex,
                                imagePath = item.imagePath,
                                row = row,
                                column = col
                            )
                        )
                    }

                    val neededRows = kotlin.math.max(set.rows, (currentButtons.size + cols - 1) / cols)
                    set.copy(rows = neededRows, buttons = currentButtons)
                } else set
            }
            persistSoundSetsToDisk(modified)
            modified
        }
    }

    /**
     * Creates a new empty sound set and appends it to the soundboard.
     */
    fun createNewSoundSet(name: String? = null): SoundSet {
        val nextNum = _soundSets.value.size + 1
        val newSet = SoundSet(
            id = java.util.UUID.randomUUID().toString(),
            name = name ?: "SOUND SET $nextNum",
            rows = 1,
            columns = 3,
            buttons = emptyList()
        )
        _soundSets.update { sets ->
            val modified = sets + newSet
            persistSoundSetsToDisk(modified)
            modified
        }
        return newSet
    }

    /**
     * Renames an existing sound set.
     */
    fun updateSetName(setId: String, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isBlank()) return
        _soundSets.update { sets ->
            val modified = sets.map { set ->
                if (set.id == setId) set.copy(name = trimmed) else set
            }
            persistSoundSetsToDisk(modified)
            modified
        }
    }

    /**
     * Reorders buttons in a set and recalculates their row and column coordinates.
     */
    fun reorderButtonsInSet(setId: String, reorderedButtons: List<SoundButtonConfig>) {
        _soundSets.update { sets ->
            val modified = sets.map { set ->
                if (set.id == setId) {
                    val cols = set.columns.coerceIn(1, 6)
                    val updated = reorderedButtons.mapIndexed { index, button ->
                        button.copy(
                            row = index / cols,
                            column = index % cols
                        )
                    }
                    val neededRows = kotlin.math.max(1, (updated.size + cols - 1) / cols)
                    set.copy(rows = neededRows, buttons = updated)
                } else set
            }
            persistSoundSetsToDisk(modified)
            modified
        }
    }

    /**
     * Deletes a sound set (as long as at least one set remains).
     */
    fun deleteSoundSet(setId: String) {
        _soundSets.update { sets ->
            if (sets.size <= 1) sets
            else {
                val modified = sets.filterNot { it.id == setId }
                persistSoundSetsToDisk(modified)
                modified
            }
        }
    }

    fun resetSoundSetsToDefault() {
        initDefaultSoundSets()
        persistSoundSetsToDisk(_soundSets.value)
    }

    // ==========================================
    // 2. AUDIO LIBRARY & PHONE FILE STORAGE
    // ==========================================

    private fun initLibraryWithStorage() {
        val builtInSounds = emptyList<SoundLibraryItem>()

        // Load custom imported sounds from phone storage
        val customSounds: List<SoundLibraryItem> = try {
            if (customSoundsConfigFile.exists()) {
                val content = customSoundsConfigFile.readText()
                if (content.isNotBlank()) {
                    val decoded = json.decodeFromString<List<SoundLibraryItem>>(content)
                    // Keep items whose file exists on phone storage
                    decoded.filter { it.filePath == null || File(it.filePath).exists() }
                } else emptyList()
            } else {
                val legacy = prefs.getString("custom_sounds", null)
                if (!legacy.isNullOrBlank()) {
                    json.decodeFromString<List<SoundLibraryItem>>(legacy)
                } else emptyList()
            }
        } catch (e: Exception) {
            emptyList()
        }

        _librarySounds.value = customSounds + builtInSounds
    }

    private fun persistCustomSoundsToDisk(sounds: List<SoundLibraryItem>) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val customs = sounds.filter { it.isCustom }
                val serialized = json.encodeToString(customs)
                customSoundsConfigFile.writeText(serialized)
                prefs.edit().putString("custom_sounds", serialized).apply()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * Imports an audio file picked from phone storage, copying it into internal private app storage.
     */
    fun importCustomAudio(sourceUri: Uri, originalTitle: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val cleanTitle = originalTitle.trim().ifBlank { "Custom Sound" }
                val soundId = "custom_" + System.currentTimeMillis()

                val mimeType = application.contentResolver.getType(sourceUri)
                val ext = when {
                    mimeType?.contains("wav") == true -> "wav"
                    mimeType?.contains("ogg") == true -> "ogg"
                    mimeType?.contains("flac") == true -> "flac"
                    mimeType?.contains("aac") == true -> "aac"
                    mimeType?.contains("m4a") == true || mimeType?.contains("mp4") == true -> "m4a"
                    else -> "mp3"
                }

                val destFile = File(soundsDirectory, "$soundId.$ext")
                application.contentResolver.openInputStream(sourceUri)?.use { input ->
                    destFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }

                val fileSize = if (destFile.exists()) destFile.length() else 0L

                val newItem = SoundLibraryItem(
                    id = soundId,
                    title = cleanTitle,
                    category = "Imported",
                    durationSeconds = 0.0,
                    colorHex = "#10B981",
                    isCustom = true,
                    source = "Phone Storage",
                    filePath = destFile.absolutePath,
                    fileSizeBytes = fileSize,
                    fileName = destFile.name
                )

                _librarySounds.update { listOf(newItem) + it }
                persistCustomSoundsToDisk(_librarySounds.value)

                // Also background-upload to PC if reachable so PC can play it when triggered
                val state = _connectionState.value
                if (state.host.isNotBlank()) {
                    viewModelScope.launch(Dispatchers.IO) {
                        try {
                            apiClient.uploadAudioFileToPc(state.host, state.httpPort, destFile, cleanTitle, "Imported")
                        } catch (e: Exception) { }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * Deletes a custom audio file from phone disk and updates the library list.
     */
    fun deleteCustomAudio(soundId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val item = _librarySounds.value.firstOrNull { it.id == soundId }
            if (item != null && item.isCustom) {
                item.filePath?.let { path ->
                    val file = File(path)
                    if (file.exists()) file.delete()
                }
                _librarySounds.update { list -> list.filterNot { it.id == soundId } }
                persistCustomSoundsToDisk(_librarySounds.value)
            }
        }
    }

    /**
     * Imports all sounds from PC into phone storage, skipping duplicates by name and size.
     */
    fun importAllSoundsFromPc() {
        viewModelScope.launch(Dispatchers.IO) {
            val state = _connectionState.value
            _syncMessage.value = "Fetching sounds from PC..."
            val pcClips = apiClient.fetchPcSounds(state.host, state.httpPort)
            if (pcClips.isEmpty()) {
                _syncMessage.value = "No sounds found on PC or PC is unreachable."
                return@launch
            }

            var importedCount = 0
            var duplicateCount = 0
            val newlyImportedItems = mutableListOf<SoundLibraryItem>()

            val currentSounds = _librarySounds.value.toMutableList()

            for (clip in pcClips) {
                // Deduplication check:
                // 1) Match by ID
                // 2) Match by Title (case-insensitive) AND file size
                // 3) Match by File Name (case-insensitive) AND file size
                val isDuplicate = currentSounds.any { local ->
                    local.id == clip.id ||
                    (local.title.equals(clip.title, ignoreCase = true) && clip.fileSizeBytes > 0 && local.fileSizeBytes == clip.fileSizeBytes) ||
                    (clip.fileName.isNotBlank() && local.fileName.equals(clip.fileName, ignoreCase = true) && clip.fileSizeBytes > 0 && local.fileSizeBytes == clip.fileSizeBytes) ||
                    (local.title.equals(clip.title, ignoreCase = true) && clip.fileSizeBytes == 0L && local.fileSizeBytes == 0L)
                }

                if (isDuplicate) {
                    duplicateCount++
                    continue
                }

                // Download audio file to phone storage
                val ext = if (clip.fileName.contains(".")) clip.fileName.substringAfterLast(".") else "mp3"
                val destFile = File(soundsDirectory, "${clip.id}.$ext")

                val success = apiClient.downloadAudioFile(state.host, state.httpPort, clip.id, destFile)
                if (success) {
                    val fileSize = destFile.length()

                    // Download artwork/image from PC if available
                    var localImagePath: String? = null
                    if (clip.hasImage || !clip.imagePath.isNullOrBlank()) {
                        val imgFile = File(imagesDirectory, "img_${clip.id}.png")
                        val imgSuccess = apiClient.downloadImageFile(state.host, state.httpPort, clip.id, imgFile)
                        if (imgSuccess) {
                            localImagePath = imgFile.absolutePath
                        }
                    }

                    val newItem = SoundLibraryItem(
                        id = clip.id,
                        title = clip.title,
                        category = if (clip.category.isNotBlank()) clip.category else "PC",
                        durationSeconds = clip.durationSeconds,
                        colorHex = clip.colorHex,
                        gradientHex = clip.gradientHex,
                        imagePath = localImagePath,
                        isCustom = true,
                        source = "Phone Storage",
                        filePath = destFile.absolutePath,
                        fileSizeBytes = fileSize,
                        fileName = clip.fileName.ifBlank { destFile.name }
                    )
                    currentSounds.add(0, newItem)
                    newlyImportedItems.add(newItem)
                    importedCount++
                }
            }

            _librarySounds.value = currentSounds
            persistCustomSoundsToDisk(currentSounds)

            // Register updated phone count with PC
            try {
                apiClient.registerPhoneWithPc(state.host, state.httpPort, getLocalIpAddress(), phoneHttpServer.port, currentSounds.count { it.isCustom })
            } catch (e: Exception) { }

            _syncMessage.value = if (importedCount > 0) {
                "Imported $importedCount sound(s) to library ($duplicateCount duplicate(s) skipped)."
            } else {
                "All $duplicateCount sounds already in library."
            }
        }
    }

    /**
     * Sends/uploads all phone custom audio clips to the PC, skipping existing duplicates on PC.
     */
    fun sendAllSoundsToPc() {
        viewModelScope.launch(Dispatchers.IO) {
            val state = _connectionState.value
            val customSounds = _librarySounds.value.filter { it.isCustom && it.filePath != null && File(it.filePath).exists() }
            if (customSounds.isEmpty()) {
                _syncMessage.value = "No custom phone sounds to send."
                return@launch
            }

            _syncMessage.value = "Sending ${customSounds.size} sound(s) to PC..."
            var sentCount = 0
            var skippedCount = 0

            for (sound in customSounds) {
                val file = File(sound.filePath!!)
                val (ok, resp) = apiClient.uploadAudioFileToPc(state.host, state.httpPort, file, sound.title, sound.category)
                if (ok) {
                    if (resp.contains("duplicate_skipped")) {
                        skippedCount++
                    } else {
                        sentCount++
                    }
                }
            }

            _syncMessage.value = "Sync to PC done: $sentCount uploaded, $skippedCount duplicate(s) skipped."
        }
    }

    // ==========================================
    // 3. AUDIO PLAYBACK & PC REMOTE CONTROL
    // ==========================================

    fun playSound(button: SoundButtonConfig) {
        viewModelScope.launch {
            val state = _connectionState.value
            // Send ultra low-latency UDP datagram (< 2ms) to PC with HTTP fallback.
            udpClient.play(state.host, state.udpPort, button.soundId)
            apiClient.playSound(state.host, state.httpPort, button.soundId)
        }
    }

    fun previewSound(item: SoundLibraryItem) {
        viewModelScope.launch {
            val state = _connectionState.value
            udpClient.play(state.host, state.udpPort, item.id)
            apiClient.playSound(state.host, state.httpPort, item.id)
        }
    }

    // ==========================================
    // 4. SETTINGS & PREFERENCES PERSISTENCE
    // ==========================================

    fun updateNetworkSettings(host: String, httpPort: Int = 50050, udpPort: Int = 50051) {
        prefs.edit()
            .putString("saved_host", host)
            .putInt("http_port", httpPort)
            .putInt("udp_port", udpPort)
            .apply()

        _connectionState.update {
            it.copy(host = host, httpPort = httpPort, udpPort = udpPort)
        }
    }

    fun setHostAddress(host: String) {
        prefs.edit().putString("saved_host", host).apply()
        _connectionState.update { it.copy(host = host) }
    }

    fun setAutoConnectEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("auto_connect", enabled).apply()
        _connectionState.update { it.copy(autoConnectEnabled = enabled) }
        if (enabled && !_connectionState.value.isConnected) {
            triggerAutoDiscovery()
        }
    }

    fun setHapticFeedbackEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("haptic_feedback_enabled", enabled).apply()
        _connectionState.update { it.copy(hapticFeedbackEnabled = enabled) }
    }

    fun setKeepScreenAwake(enabled: Boolean) {
        prefs.edit().putBoolean("keep_screen_awake", enabled).apply()
        _connectionState.update { it.copy(keepScreenAwake = enabled) }
    }

    fun updateSpeakerVolume(volume: Float) {
        val clamped = volume.coerceIn(0f, 1f)
        prefs.edit().putFloat("saved_speaker_volume", clamped).apply()
        _connectionState.update { it.copy(speakerVolume = clamped) }

        val state = _connectionState.value
        viewModelScope.launch {
            // Send instant UDP datagram
            udpClient.setVolume(state.host, state.udpPort, clamped, state.micVolume)
            // Send reliable HTTP POST in background
            apiClient.setVolume(state.host, state.httpPort, clamped, state.micVolume)
        }
    }

    fun updateMicVolume(volume: Float) {
        val clamped = volume.coerceIn(0f, 1f)
        prefs.edit().putFloat("saved_mic_volume", clamped).apply()
        _connectionState.update { it.copy(micVolume = clamped) }

        val state = _connectionState.value
        viewModelScope.launch {
            // Send instant UDP datagram
            udpClient.setVolume(state.host, state.udpPort, state.speakerVolume, clamped)
            // Send reliable HTTP POST in background
            apiClient.setVolume(state.host, state.httpPort, state.speakerVolume, clamped)
        }
    }

    fun stopAllSounds() {
        val state = _connectionState.value
        viewModelScope.launch {
            udpClient.stopAll(state.host, state.udpPort)
            apiClient.stopAll(state.host, state.httpPort)
        }
    }

    fun getStorageSummary(): StorageSummary {
        val sets = _soundSets.value
        val totalBtns = sets.sumOf { it.buttons.size }
        val customSounds = _librarySounds.value.filter { it.isCustom }
        val totalBytes = customSounds.sumOf { it.fileSizeBytes }
        return StorageSummary(
            totalSets = sets.size,
            totalButtons = totalBtns,
            customSoundsCount = customSounds.size,
            customSoundsSizeBytes = totalBytes
        )
    }

    // ==========================================
    // 5. CONNECTION HEARTBEAT & AUTO DISCOVERY
    // ==========================================

    fun triggerAutoDiscovery() {
        if (_connectionState.value.isAutoDiscovering) return

        viewModelScope.launch {
            _connectionState.update { it.copy(isAutoDiscovering = true) }
            val currentSavedHost = prefs.getString("saved_host", null)
            val discovered = discoveryManager.discoverServer(currentSavedHost)

            if (discovered != null && discovered.ip.isNotBlank()) {
                setHostAddress(discovered.ip)
                val (status, ping) = apiClient.checkStatusAndPing(discovered.ip, discovered.httpPort)
                val isConn = status != null
                _connectionState.update {
                    it.copy(
                        isConnected = isConn,
                        latencyMs = if (isConn) ping else 0L,
                        isAutoDiscovering = false
                    )
                }
                if (isConn) {
                    try {
                        apiClient.registerPhoneWithPc(
                            discovered.ip,
                            discovered.httpPort,
                            getLocalIpAddress(),
                            phoneHttpServer.port,
                            _librarySounds.value.count { it.isCustom }
                        )
                    } catch (e: Exception) { }
                }
            } else {
                _connectionState.update { it.copy(isAutoDiscovering = false) }
            }
        }
    }

    private fun startConnectionHeartbeat() {
        viewModelScope.launch {
            var consecutiveFailures = 0
            var pcSoundsFetched = false

            while (isActive) {
                val state = _connectionState.value
                val (status, ping) = apiClient.checkStatusAndPing(state.host, state.httpPort)
                val connected = status != null

                _connectionState.update {
                    it.copy(
                        isConnected = connected,
                        latencyMs = if (connected) ping else 0L
                    )
                }

                if (connected) {
                    consecutiveFailures = 0
                    if (!pcSoundsFetched) {
                        pcSoundsFetched = true
                        // Fetch PC's actual volume state on connect
                        val pcVolume = apiClient.fetchVolume(state.host, state.httpPort)
                        if (pcVolume != null) {
                            _connectionState.update {
                                it.copy(
                                    speakerVolume = pcVolume.first,
                                    micVolume = pcVolume.second
                                )
                            }
                        }

                        // Register phone server with PC on initial connect
                        try {
                            apiClient.registerPhoneWithPc(
                                state.host,
                                state.httpPort,
                                getLocalIpAddress(),
                                phoneHttpServer.port,
                                _librarySounds.value.count { it.isCustom }
                            )
                        } catch (e: Exception) { }
                    }
                } else {
                    consecutiveFailures++
                    pcSoundsFetched = false
                    if (state.autoConnectEnabled && !state.isAutoDiscovering && consecutiveFailures >= 5) {
                        consecutiveFailures = 0
                        triggerAutoDiscovery()
                    }
                }

                delay(2000)
            }
        }
    }

    // ==========================================
    // 6. PROFILES BACKUP & RESTORE
    // ==========================================

    fun exportProfileJson(): String {
        val profile = SoundboardProfile(
            profileName = "My Soundboard Profile",
            soundSets = _soundSets.value
        )
        return json.encodeToString(profile)
    }

    fun importProfileJson(jsonStr: String): Boolean {
        return try {
            val profile = json.decodeFromString<SoundboardProfile>(jsonStr)
            if (profile.soundSets.isNotEmpty()) {
                _soundSets.value = profile.soundSets
                persistSoundSetsToDisk(profile.soundSets)
                true
            } else false
        } catch (e: Exception) {
            false
        }
    }

    private fun getLocalIpAddress(): String {
        try {
            val interfaces = java.net.NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val intf = interfaces.nextElement()
                val addrs = intf.inetAddresses
                while (addrs.hasMoreElements()) {
                    val addr = addrs.nextElement()
                    if (!addr.isLoopbackAddress && addr is java.net.Inet4Address) {
                        return addr.hostAddress ?: "127.0.0.1"
                    }
                }
            }
        } catch (e: Exception) { }
        return "127.0.0.1"
    }

    override fun onCleared() {
        super.onCleared()
        phoneHttpServer.stop()
        udpClient.close()
    }
}
