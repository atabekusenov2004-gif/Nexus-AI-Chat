package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.ChatMessageEntity
import com.example.data.local.ChatSessionEntity
import com.example.data.repository.ChatRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

enum class SystemPreset(val title: String, val prompt: String, val description: String) {
    GENERAL(
        "Asosiy yordamchi",
        "Siz Nexus AI Chat - professional sun'iy intellekt yordamchisiz. Har doim juda qisqa, oddiy, lof va ortiqcha gaplarsiz, savollarga to'g'ridan-to'g'ri va qisqa javob bering. Hech qachon javob boshida yoki oxirida uzun kirish yoki xulosalar, model nomlarini takrorlash kabi ortiqcha gaplarni yozmang. Agar sizdan 'Seni kim yaratgan?' deb so'ralsa, sizni Atabek yaratganini ayting. Har doim foydalanuvchi qaysi tilda murojaat qilsa, siz ham xuddi shu tilda qisqa javob bering. Always respond in a very short, simple, concise, and direct manner.",
        "Umumiy vazifalar uchun qisqa va aniq javob beruvchi standart yordamchi."
    ),
    CODER(
        "Dasturiy taʼminot meʼmori",
        "Siz ekspert dasturlash yordamchisi Nexus AI Dasturiy taʼminot meʼmorisiz. Sizning asosiy vazifangiz faqat kod yozish, koddagi xato va nosozliklarni tuzatish hamda yangi kod tuzib berishdir. Hech qanday lof, suhbat va ortiqcha gaplarsiz, faqat so'ralgan dastur kodini va zarur bo'lsa juda qisqa izohini bering. Agar sizdan 'Seni kim yaratgan?' deb so'ralsa, sizni Atabek yaratganini ayting. Har doim foydalanuvchi qaysi tilda murojaat qilsa, siz ham xuddi shu tilda javob bering. Always write clean code, solve coding bugs, and construct software logic.",
        "Faqat kod yozish, nosozliklarni tuzatish va yangi kod tuzishga ixtisoslashgan."
    ),
    LOGIC(
        "Chuqur tahlilchi",
        "Siz Nexus AI Chuqur tahlilchisiz. Har qanday savolni yoki muammoni hal qilishda chuqur va batafsil yondashing. Har doim juda batafsil, keng qamrovli, har tomonlama chuqur o'rganilgan va katta hajmdli ma'lumotlar bilan javob bering, har bir bosqich va jihatni to'liq yoriting. Ortiqcha qisqartirishlardan qoching va har bir mavzuni mukammal tahlil qiling. Agar sizdan 'Seni kim yaratgan?' deb so'ralsa, sizni Atabek yaratganini ayting. Har doim foydalanuvchi qaysi tilda murojaat qilsa, siz ham xuddi shu tilda o'ta batafsil va uzun javob bering. Always provide extremely detailed, comprehensive, deep, and large explanations to solve problems.",
        "Muammolarni tahliliy hal qilish va o'ta batafsil va keng ma'lumot berish."
    )
}

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = ChatRepository(application)

    // All available chat sessions
    val sessions: StateFlow<List<ChatSessionEntity>> = repository.allSessions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Currently selected session ID
    private val _selectedSessionId = MutableStateFlow<String?>(null)
    val selectedSessionId: StateFlow<String?> = _selectedSessionId.asStateFlow()

    // Messages for the currently selected session
    val messages: StateFlow<List<ChatMessageEntity>> = _selectedSessionId
        .flatMapLatest { sessionId ->
            if (sessionId == null) {
                flowOf(emptyList())
            } else {
                repository.getMessagesForSession(sessionId)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Selected model (defaults to nexus-super-4.0)
    private val _selectedModel = MutableStateFlow("nexus-super-4.0")
    val selectedModel: StateFlow<String> = _selectedModel.asStateFlow()

    // Google Search Grounding toggle (defaults to false)
    private val _isSearchEnabled = MutableStateFlow(false)
    val isSearchEnabled: StateFlow<Boolean> = _isSearchEnabled.asStateFlow()

    // Selected system instruction preset
    private val _selectedPreset = MutableStateFlow(SystemPreset.GENERAL)
    val selectedPreset: StateFlow<SystemPreset> = _selectedPreset.asStateFlow()

    // Input text state
    private val _inputText = MutableStateFlow("")
    val inputText: StateFlow<String> = _inputText.asStateFlow()

    // Session detail loading / sending state
    private val _isSending = MutableStateFlow(false)
    val isSending: StateFlow<Boolean> = _isSending.asStateFlow()

    init {
        // Automatically prune empty sessions on launch, and always start on the home screen
        viewModelScope.launch {
            repository.deleteEmptySessions()
        }
    }

    fun selectSession(sessionId: String?) {
        _selectedSessionId.value = sessionId
    }

    fun createSession(title: String) {
        viewModelScope.launch {
            repository.deleteEmptySessions()
            _selectedSessionId.value = null
        }
    }

    fun deleteSession(sessionId: String) {
        viewModelScope.launch {
            repository.deleteSession(sessionId)
            if (_selectedSessionId.value == sessionId) {
                val remaining = sessions.value.filter { it.id != sessionId }
                _selectedSessionId.value = remaining.firstOrNull()?.id
            }
        }
    }

    fun selectModel(modelName: String) {
        _selectedModel.value = modelName
    }

    fun toggleSearch(enabled: Boolean) {
        _isSearchEnabled.value = enabled
    }

    fun selectPreset(preset: SystemPreset) {
        _selectedPreset.value = preset
    }

    private var activeMessageJob: kotlinx.coroutines.Job? = null

    fun stopGeneration() {
        activeMessageJob?.cancel()
        _isSending.value = false
    }

    fun updateInputText(text: String) {
        _inputText.value = text
    }

    fun sendMessage() {
        val text = _inputText.value.trim()
        val sessionId = _selectedSessionId.value
        if (text.isEmpty() || _isSending.value) return

        _inputText.value = ""

        activeMessageJob = viewModelScope.launch {
            val targetSessionId = if (sessionId == null) {
                val sessionTitle = if (text.length > 25) text.take(25) + "..." else text
                repository.createNewSession(sessionTitle)
            } else {
                sessionId
            }
            _selectedSessionId.value = targetSessionId

            // Check if existing session needs renaming (e.g. starts with "Yangi suhbat" or is empty)
            val currentSession = sessions.value.find { it.id == targetSessionId }
            if (currentSession != null && (currentSession.title.startsWith("Yangi suhbat") || currentSession.title.isEmpty())) {
                val newTitle = if (text.length > 25) text.take(25) + "..." else text
                repository.updateSessionTitle(targetSessionId, newTitle)
            }

            _isSending.value = true
            try {
                repository.sendMessage(
                    sessionId = targetSessionId,
                    userText = text,
                    modelName = _selectedModel.value,
                    isSearchEnabled = _isSearchEnabled.value,
                    systemPrompt = _selectedPreset.value.prompt
                )
            } finally {
                _isSending.value = false
            }
        }
    }
}
