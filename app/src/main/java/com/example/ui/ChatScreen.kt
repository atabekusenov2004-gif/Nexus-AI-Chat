package com.example.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.ChatMessageEntity
import com.example.data.local.ChatSessionEntity
import com.example.data.local.WebSourceEntity
import com.example.ui.theme.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import androidx.compose.foundation.horizontalScroll
import android.net.Uri
import android.graphics.Bitmap
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.snapshots.SnapshotStateList

sealed class MessageBlock {
    data class Text(val text: String) : MessageBlock()
    data class Code(val language: String, val code: String) : MessageBlock()
    data class Image(val url: String) : MessageBlock()
}

data class AttachedFile(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String,
    val size: String,
    val type: String, // "pdf", "image", "video", "camera", "other"
    val uri: android.net.Uri? = null,
    val bitmap: android.graphics.Bitmap? = null
)

fun getFileName(context: Context, uri: Uri): String {
    var result: String? = null
    if (uri.scheme == "content") {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        try {
            if (cursor != null && cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (index >= 0) {
                    result = cursor.getString(index)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            cursor?.close()
        }
    }
    if (result == null) {
        result = uri.path
        val cut = result?.lastIndexOf('/') ?: -1
        if (cut != -1) {
            result = result?.substring(cut + 1)
        }
    }
    return result ?: "fayl"
}

fun getFileSize(context: Context, uri: Uri): String {
    var size: Long = 0
    if (uri.scheme == "content") {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        try {
            if (cursor != null && cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
                if (index >= 0) {
                    size = cursor.getLong(index)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            cursor?.close()
        }
    }
    if (size <= 0) return "1.2 KB"
    return when {
        size < 1024 -> "$size B"
        size < 1024 * 1024 -> "${String.format("%.1f", size / 1024f)} KB"
        else -> "${String.format("%.1f", size / (1024f * 1024f))} MB"
    }
}

fun parseMessageText(text: String): List<MessageBlock> {
    val blocks = mutableListOf<MessageBlock>()
    try {
        val parts = text.split("```")
        for (i in parts.indices) {
            val part = parts[i]
            if (i % 2 == 1) {
                val lines = part.split("\n")
                val language = lines.firstOrNull()?.trim() ?: ""
                val code = lines.drop(1).joinToString("\n").trim()
                blocks.add(MessageBlock.Code(language, code))
            } else {
                if (part.isNotEmpty()) {
                    // Parse markdown image tags within the text block
                    // e.g., ![Rasm](https://...)
                    val currentText = part
                    val imageRegex = Regex("!\\[.*?\\]\\((.*?)\\)")
                    var match = imageRegex.find(currentText)
                    
                    if (match == null) {
                        blocks.add(MessageBlock.Text(currentText))
                    } else {
                        var lastIndex = 0
                        while (match != null) {
                            val start = match.range.first
                            val end = match.range.last
                            if (lastIndex in 0..start && start <= currentText.length) {
                                val beforeText = currentText.substring(lastIndex, start)
                                if (beforeText.isNotEmpty()) {
                                    blocks.add(MessageBlock.Text(beforeText))
                                }
                            }
                            
                            val imageUrl = match.groupValues[1]
                            blocks.add(MessageBlock.Image(imageUrl))
                            
                            lastIndex = end + 1
                            if (lastIndex > currentText.length) {
                                break
                            }
                            match = imageRegex.find(currentText, lastIndex)
                        }
                        if (lastIndex in 0..currentText.length) {
                            val remainingText = currentText.substring(lastIndex)
                            if (remainingText.isNotEmpty()) {
                                        blocks.add(MessageBlock.Text(remainingText))
                            }
                        }
                    }
                }
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
        blocks.clear()
        blocks.add(MessageBlock.Text(text))
    }
    return blocks
}

fun renderFormattedText(text: String): AnnotatedString {
    try {
        // Clean and replace markdown headings with clean professional headings (no unwanted icons/emojis)
        val cleanedText = text
            .replace(Regex("(?m)^###\\s*(.*?)$"), "**$1**")
            .replace(Regex("(?m)^##\\s*(.*?)$"), "**$1**")
            .replace(Regex("(?m)^#\\s*(.*?)$"), "**$1**")

        return buildAnnotatedString {
            var cursor = 0
            val regex = Regex("\\*\\*(.*?)\\*\\*")
            val matches = regex.findAll(cleanedText)
            for (match in matches) {
                val startIdx = match.range.first
                if (startIdx >= cursor && startIdx <= cleanedText.length) {
                    append(cleanedText.substring(cursor, startIdx))
                }
                val boldText = match.groupValues[1]
                val start = length
                append(boldText)
                addStyle(SpanStyle(fontWeight = FontWeight.Bold, color = Color(0xFF63B3ED)), start, length) // Cyan accent color
                cursor = match.range.last + 1
            }
            if (cursor in 0 until cleanedText.length) {
                append(cleanedText.substring(cursor))
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
        return buildAnnotatedString { append(text) }
    }
}

@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    modifier: Modifier = Modifier
) {
    val sessions by viewModel.sessions.collectAsStateWithLifecycle()
    val selectedSessionId by viewModel.selectedSessionId.collectAsStateWithLifecycle()
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val selectedModel by viewModel.selectedModel.collectAsStateWithLifecycle()
    val isSearchEnabled by viewModel.isSearchEnabled.collectAsStateWithLifecycle()
    val selectedPreset by viewModel.selectedPreset.collectAsStateWithLifecycle()
    val inputText by viewModel.inputText.collectAsStateWithLifecycle()
    val isSending by viewModel.isSending.collectAsStateWithLifecycle()

    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    BackHandler(enabled = selectedSessionId != null) {
        viewModel.selectSession(null)
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        val isWideScreen = maxWidth > 800.dp

        if (isWideScreen) {
            // Tablet & Desktop Layout: Side-by-side panes
            Row(modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                Box(
                    modifier = Modifier
                        .width(300.dp)
                        .fillMaxHeight()
                        .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outline), shape = RoundedCornerShape(0.dp))
                        .background(MaterialTheme.colorScheme.surface)
                ) {
                    SidebarContent(
                        sessions = sessions,
                        selectedSessionId = selectedSessionId,
                        onSelectSession = { viewModel.selectSession(it) },
                        onCreateSession = { viewModel.createSession(it) },
                        onDeleteSession = { viewModel.deleteSession(it) },
                        selectedPreset = selectedPreset,
                        onSelectPreset = { viewModel.selectPreset(it) }
                    )
                }
                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    ChatPane(
                        messages = messages,
                        selectedSessionId = selectedSessionId,
                        selectedModel = selectedModel,
                        isSearchEnabled = isSearchEnabled,
                        selectedPreset = selectedPreset,
                        inputText = inputText,
                        isSending = isSending,
                        totalSessions = sessions.size,
                        onModelSelected = { viewModel.selectModel(it) },
                        onSearchToggled = { viewModel.toggleSearch(it) },
                        onInputTextChanged = { viewModel.updateInputText(it) },
                        onSendMessage = { viewModel.sendMessage() },
                        onStopGeneration = { viewModel.stopGeneration() },
                        onOpenMenu = { scope.launch { drawerState.open() } },
                        isWideScreen = true,
                        onBackClicked = { viewModel.selectSession(null) }
                    )
                }
            }
        } else {
            // Mobile Layout: Modal navigation drawer
            ModalNavigationDrawer(
                drawerState = drawerState,
                drawerContent = {
                    ModalDrawerSheet(
                        modifier = Modifier.width(300.dp),
                        drawerContainerColor = MaterialTheme.colorScheme.surface
                    ) {
                        SidebarContent(
                            sessions = sessions,
                            selectedSessionId = selectedSessionId,
                            onSelectSession = {
                                viewModel.selectSession(it)
                                scope.launch { drawerState.close() }
                            },
                            onCreateSession = {
                                viewModel.createSession(it)
                                scope.launch { drawerState.close() }
                            },
                            onDeleteSession = { viewModel.deleteSession(it) },
                            selectedPreset = selectedPreset,
                            onSelectPreset = { viewModel.selectPreset(it) }
                        )
                    }
                }
            ) {
                ChatPane(
                    messages = messages,
                    selectedSessionId = selectedSessionId,
                    selectedModel = selectedModel,
                    isSearchEnabled = isSearchEnabled,
                    selectedPreset = selectedPreset,
                    inputText = inputText,
                    isSending = isSending,
                    totalSessions = sessions.size,
                    onModelSelected = { viewModel.selectModel(it) },
                    onSearchToggled = { viewModel.toggleSearch(it) },
                    onInputTextChanged = { viewModel.updateInputText(it) },
                    onSendMessage = { viewModel.sendMessage() },
                    onStopGeneration = { viewModel.stopGeneration() },
                    onOpenMenu = { scope.launch { drawerState.open() } },
                    isWideScreen = false,
                    onBackClicked = { viewModel.selectSession(null) }
                )
            }
        }
    }
}

@Composable
fun SidebarContent(
    sessions: List<ChatSessionEntity>,
    selectedSessionId: String?,
    onSelectSession: (String) -> Unit,
    onCreateSession: (String) -> Unit,
    onDeleteSession: (String) -> Unit,
    selectedPreset: SystemPreset,
    onSelectPreset: (SystemPreset) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .navigationBarsPadding()
    ) {
        // App Identity Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(ElegantPrimary, ElegantSecondary)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Hub,
                    contentDescription = "Nexus Logo",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = "Nexus AI",
                    style = TextStyle(
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        letterSpacing = 0.5.sp,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                )
                Text(
                    text = "Muloqot va intellekt tizimi",
                    style = TextStyle(
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }
        }

        // New Session Button
        Button(
            onClick = { onCreateSession("Yangi suhbat") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
                .testTag("new_session_button"),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = "Add Icon")
            Spacer(modifier = Modifier.width(8.dp))
            Text("Yangi suhbat", fontWeight = FontWeight.SemiBold)
        }

        // Sessions Header Label
        Text(
            text = "SUHBATLAR TARIXI",
            style = TextStyle(
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            ),
            modifier = Modifier.padding(bottom = 8.dp)
        )

        // Sessions List
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(sessions) { session ->
                val isSelected = session.id == selectedSessionId
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                            else Color.Transparent
                        )
                        .border(
                            BorderStroke(
                                1.dp,
                                if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                                else Color.Transparent
                            ),
                            shape = RoundedCornerShape(10.dp)
                        )
                        .clickable { onSelectSession(session.id) }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isSelected) Icons.Filled.ChatBubble else Icons.Outlined.ChatBubbleOutline,
                        contentDescription = "Session Icon",
                        tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = session.title,
                        style = TextStyle(
                            fontSize = 14.sp,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (sessions.size > 1) {
                        IconButton(
                            onClick = { onDeleteSession(session.id) },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }

        // Divider
        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outline)

        // System Instruction / Role Presets
        Text(
            text = "YORDAMCHI ROLI",
            style = TextStyle(
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            ),
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SystemPreset.values().forEach { preset ->
                val isSelected = preset == selectedPreset
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f)
                            else Color.Transparent
                        )
                        .clickable { onSelectPreset(preset) }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val icon = when (preset) {
                        SystemPreset.GENERAL -> Icons.Default.SmartToy
                        SystemPreset.CODER -> Icons.Default.Code
                        SystemPreset.LOGIC -> Icons.Default.Psychology
                    }
                    Icon(
                        imageVector = icon,
                        contentDescription = preset.title,
                        tint = if (isSelected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = preset.title,
                            style = TextStyle(
                                fontSize = 13.sp,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                                color = if (isSelected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface
                            )
                        )
                        Text(
                            text = preset.description,
                            style = TextStyle(
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DeepThinkTimeline() {
    val stages = listOf(
        "Tahlil qilinmoqda..." to Icons.Default.Search,
        "Kontekst tekshirilmoqda..." to Icons.Default.List,
        "Maʼlumot qidirilmoqda..." to Icons.Default.Language,
        "Mulohaza yuritilmoqda..." to Icons.Default.Lightbulb,
        "Javob yozilmoqda..." to Icons.Default.AutoAwesome
    )
    
    var currentStageIndex by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        while (currentStageIndex < stages.size - 1) {
            delay(1500)
            currentStageIndex++
        }
    }

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 32.dp, top = 8.dp, bottom = 8.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "🧠 CHUQUR MULOHAZA",
                style = TextStyle(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 1.sp
                )
            )
            Spacer(modifier = Modifier.height(8.dp))
            stages.forEachIndexed { index, stage ->
                val isActive = index == currentStageIndex
                val isCompleted = index < currentStageIndex
                val alpha = if (isActive) 1f else if (isCompleted) 0.5f else 0.25f
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = if (isCompleted) Icons.Default.CheckCircle else stage.second,
                        contentDescription = null,
                        tint = if (isCompleted) Color(0xFF00FF66) else if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stage.first,
                        style = TextStyle(
                            fontSize = 12.sp,
                            fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (isActive) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha)
                        )
                    )
                    if (isActive) {
                        Spacer(modifier = Modifier.weight(1f))
                        CircularProgressIndicator(
                            modifier = Modifier.size(12.dp),
                            strokeWidth = 1.5.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatPane(
    messages: List<ChatMessageEntity>,
    selectedSessionId: String?,
    selectedModel: String,
    isSearchEnabled: Boolean,
    selectedPreset: SystemPreset,
    inputText: String,
    isSending: Boolean,
    totalSessions: Int,
    onModelSelected: (String) -> Unit,
    onSearchToggled: (Boolean) -> Unit,
    onInputTextChanged: (String) -> Unit,
    onSendMessage: () -> Unit,
    onStopGeneration: () -> Unit,
    onOpenMenu: () -> Unit,
    isWideScreen: Boolean,
    onBackClicked: () -> Unit = {}
) {
    var showModelMenu by remember { mutableStateOf(false) }
    val lazyListState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val attachedFiles = remember { mutableStateListOf<AttachedFile>() }
    val customSendMessage = {
        keyboardController?.hide()
        if (attachedFiles.isNotEmpty()) {
            val fileDescriptions = attachedFiles.joinToString(separator = ", ") { "${it.name} (${it.size})" }
            val prefix = "📎 [Biriktirilgan fayllar: $fileDescriptions]\n\n"
            onInputTextChanged(prefix + inputText)
        }
        onSendMessage()
        attachedFiles.clear()
    }

    // Scroll to bottom when new messages arrive
    LaunchedEffect(messages.size, isSending) {
        if (messages.isNotEmpty()) {
            scope.launch {
                lazyListState.animateScrollToItem(messages.size - 1)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Column {
                            Text(
                                "Nexus AI",
                                style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .background(Color(0xFF00FF66), shape = CircleShape)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    selectedPreset.title,
                                    style = TextStyle(fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    if (!isWideScreen) {
                        if (selectedSessionId != null) {
                            IconButton(onClick = onBackClicked) {
                                Icon(Icons.Default.ArrowBack, contentDescription = "Orqaga")
                            }
                        } else {
                            IconButton(onClick = onOpenMenu) {
                                Icon(Icons.Default.Menu, contentDescription = "Menu")
                            }
                        }
                    }
                },
                actions = {},
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (messages.isEmpty()) {
                    OnboardingView(
                        preset = selectedPreset,
                        onQuickPromptClicked = { text ->
                            onInputTextChanged(text)
                            scope.launch {
                                delay(1500)
                                onSendMessage()
                            }
                        },
                        totalSessions = totalSessions,
                        currentSessionMessages = messages.size
                    )
                } else {
                    LazyColumn(
                        state = lazyListState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp)
                    ) {
                        items(messages) { message ->
                            MessageBubble(
                                message = message,
                                onFollowUpClicked = { followUpPrompt ->
                                    onInputTextChanged(followUpPrompt)
                                    scope.launch {
                                        delay(150)
                                        onSendMessage()
                                    }
                                }
                            )
                        }

                        if (isSending) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    Column {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(bottom = 4.dp, start = 4.dp, end = 4.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(20.dp)
                                                    .background(MaterialTheme.colorScheme.primary, shape = CircleShape),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    Icons.Default.AutoAwesome,
                                                    contentDescription = null,
                                                    tint = Color.White,
                                                    modifier = Modifier.size(10.dp)
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "NEXUS AI",
                                                style = TextStyle(
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 11.sp,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    letterSpacing = 0.5.sp
                                                )
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Surface(
                                            shape = RoundedCornerShape(topStart = 0.dp, topEnd = 16.dp, bottomEnd = 16.dp, bottomStart = 16.dp),
                                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
                                            modifier = Modifier.padding(start = 26.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                                            ) {
                                                val transition = rememberInfiniteTransition(label = "typing")
                                                val dotOffsets = (0..2).map { index ->
                                                    transition.animateFloat(
                                                        initialValue = 0f,
                                                        targetValue = -6f,
                                                        animationSpec = infiniteRepeatable(
                                                            animation = tween(durationMillis = 350, delayMillis = index * 120, easing = LinearOutSlowInEasing),
                                                            repeatMode = RepeatMode.Reverse
                                                        ),
                                                        label = "dot_$index"
                                                    )
                                                }
                                                dotOffsets.forEach { offset ->
                                                    Box(
                                                        modifier = Modifier
                                                            .size(6.dp)
                                                            .offset(y = offset.value.dp)
                                                            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f), shape = CircleShape)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Floating Scroll to Bottom button
                    val showScrollToBottom by remember {
                        derivedStateOf {
                            lazyListState.firstVisibleItemIndex > 0 ||
                            lazyListState.firstVisibleItemScrollOffset > 100
                        }
                    }

                    androidx.compose.animation.AnimatedVisibility(
                        visible = showScrollToBottom,
                        enter = fadeIn() + scaleIn(),
                        exit = fadeOut() + scaleOut(),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(16.dp)
                    ) {
                        FloatingActionButton(
                            onClick = {
                                scope.launch {
                                    lazyListState.animateScrollToItem(messages.size - 1)
                                }
                            },
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = Color.White,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowDownward,
                                contentDescription = "Scroll to bottom",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            // Bottom Input Pane
            InputPane(
                inputText = inputText,
                isSending = isSending,
                onInputTextChanged = onInputTextChanged,
                onSendMessage = customSendMessage,
                isSearchEnabled = isSearchEnabled,
                onSearchToggled = onSearchToggled,
                onStopGeneration = onStopGeneration,
                attachedFiles = attachedFiles
            )
        }
    }
}

fun exportToWordFile(context: Context, text: String, title: String) {
    try {
        val cleanTitle = title.replace(Regex("[^a-zA-Z0-9_\\s]"), "").trim().replace(" ", "_")
        val fileName = if (cleanTitle.isNotEmpty()) "Nexus_AI_${cleanTitle}.doc" else "Nexus_AI_Suhbat.doc"
        
        val wordContent = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="utf-8">
                <title>Nexus AI Export</title>
                <style>
                    body { font-family: 'Arial', sans-serif; line-height: 1.6; color: #222222; padding: 25px; }
                    h1 { color: #1e3a8a; font-size: 20pt; border-bottom: 2px solid #3b82f6; padding-bottom: 8px; margin-bottom: 15px; }
                    .header-info { color: #555555; font-size: 10pt; margin-bottom: 20px; }
                    .content { font-size: 11pt; }
                    pre { background-color: #f3f4f6; padding: 12px; border: 1px solid #e5e7eb; font-family: 'Courier New', monospace; font-size: 10pt; white-space: pre-wrap; }
                    code { background-color: #f3f4f6; padding: 2px 4px; font-family: 'Courier New', monospace; font-size: 10pt; }
                    strong { color: #111827; }
                </style>
            </head>
            <body>
                <h1>Nexus AI Muloqotidan Hujjat</h1>
                <div class="header-info">
                    <strong>Mavzu:</strong> ${title}<br/>
                    <strong>Sana:</strong> ${java.text.SimpleDateFormat("dd.MM.yyyy HH:mm", java.util.Locale.getDefault()).format(java.util.Date())}
                </div>
                <hr style="border: 0; border-top: 1px solid #e5e7eb; margin-bottom: 20px;" />
                <div class="content">
                    ${text.replace("\n", "<br/>")}
                </div>
            </body>
            </html>
        """.trimIndent()

        val file = java.io.File(context.cacheDir, fileName)
        file.writeText(wordContent)

        val authority = "${context.packageName}.fileprovider"
        val uri = androidx.core.content.FileProvider.getUriForFile(context, authority, file)

        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "application/msword"
            putExtra(android.content.Intent.EXTRA_STREAM, uri)
            putExtra(android.content.Intent.EXTRA_SUBJECT, title)
            putExtra(android.content.Intent.EXTRA_TEXT, "Nexus AI orqali Word (.doc) formatiga eksport qilingan hujjat.")
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(android.content.Intent.createChooser(intent, "Word hujjatini saqlash/ulashish"))
    } catch (e: Exception) {
        android.widget.Toast.makeText(context, "Word fayl yaratishda xatolik: ${e.localizedMessage}", android.widget.Toast.LENGTH_LONG).show()
    }
}

fun downloadAndSaveImageToGallery(context: Context, imageUrl: String) {
    android.widget.Toast.makeText(context, "Rasm yuklab olinmoqda...", android.widget.Toast.LENGTH_SHORT).show()
    
    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
        try {
            val url = java.net.URL(imageUrl)
            val connection = url.openConnection() as java.net.HttpURLConnection
            connection.doInput = true
            connection.connect()
            
            val inputStream = connection.inputStream
            val bitmap = android.graphics.BitmapFactory.decodeStream(inputStream)
            
            if (bitmap != null) {
                val filename = "Nexus_AI_${System.currentTimeMillis()}.jpg"
                val resolver = context.contentResolver
                val contentValues = android.content.ContentValues().apply {
                    put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, filename)
                    put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                    put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_PICTURES)
                }
                
                val imageUri = resolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                if (imageUri != null) {
                    resolver.openOutputStream(imageUri).use { outputStream ->
                        if (outputStream != null) {
                            bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 100, outputStream)
                            
                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                android.widget.Toast.makeText(context, "Rasm galereyaga saqlandi!", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            throw Exception("Yozib bo'lmadi")
                        }
                    }
                } else {
                    throw Exception("Galereya ulanmadi")
                }
            } else {
                throw Exception("Rasm formati xato")
            }
        } catch (e: Exception) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                android.widget.Toast.makeText(context, "Yuklashda xatolik: ${e.localizedMessage}", android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }
}

fun getFollowUpQuestions(text: String): List<String> {
    val lower = text.lowercase()
    return when {
        lower.contains("kod") || lower.contains("fun ") || lower.contains("class ") || lower.contains("import ") || lower.contains("val ") || lower.contains("def ") || lower.contains("function") -> listOf(
            "💻 Kodni optimallashtirish",
            "❓ Kod qanday ishlaydi?",
            "🔄 Boshqa tilda yozish",
            "🧪 Testlar yozib ber"
        )
        lower.contains("tarix") || lower.contains("shaxs") || lower.contains("sana") || lower.contains("yil") -> listOf(
            "📅 Qoʻshimcha sanalar",
            "📖 Tarixiy kontekst",
            "🔬 Sabab va oqibatlar",
            "💡 Qisqacha xulosa"
        )
        lower.contains("matematika") || lower.contains("formula") || lower.contains("tenglama") || lower.contains("hisob") -> listOf(
            "📐 Formulani isbotlash",
            "✍️ Soddaroq misol",
            "🔬 Amaliy tatbiqi",
            "💡 Muqobil usul"
        )
        else -> listOf(
            "💡 Batafsilroq tushuntir",
            "📝 Qisqacha xulosa ber",
            "🔍 Amaliy misol keltir",
            "❓ Savollarim bor"
        )
    }
}

@Composable
fun MessageBubble(
    message: ChatMessageEntity,
    onFollowUpClicked: (String) -> Unit
) {
    val isUser = message.role == "user"
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(key1 = message.id) {
        visible = true
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(durationMillis = 350)) +
                slideInVertically(
                    initialOffsetY = { if (isUser) 15 else -15 },
                    animationSpec = tween(durationMillis = 350)
                ),
        exit = fadeOut(animationSpec = tween(durationMillis = 150))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
        ) {
        // Sender Name & Role Row
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 4.dp, start = 4.dp, end = 4.dp)
        ) {
            if (!isUser) {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .background(MaterialTheme.colorScheme.primary, shape = CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(10.dp)
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "NEXUS AI",
                    style = TextStyle(
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 0.5.sp
                    )
                )
                Spacer(modifier = Modifier.width(8.dp))
                // Confidence Badge
                val confidenceText = when {
                    message.sources?.isNotEmpty() == true -> "🟢 Oʻta yuqori ishonchlilik"
                    message.text.length > 300 -> "🟢 Yuqori ishonchlilik"
                    message.text.length > 50 -> "🟡 Oʻrtacha ishonchlilik"
                    else -> "🟢 Yuqori ishonchlilik"
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = confidenceText,
                        style = TextStyle(fontSize = 8.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    )
                }
            } else {
                Text(
                    text = "YOU",
                    style = TextStyle(
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary,
                        letterSpacing = 0.5.sp
                    )
                )
                Spacer(modifier = Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .background(MaterialTheme.colorScheme.secondary, shape = CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Person,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(10.dp)
                    )
                }
            }
        }

        // Search Queries Section (if AI made search queries)
        if (!isUser && !message.searchQueries.isNullOrEmpty()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .padding(bottom = 6.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.08f))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.TravelExplore,
                    contentDescription = "Search",
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Google orqali faktlarni tekshirish: " + message.searchQueries.joinToString(", ") { "\"$it\"" },
                    style = TextStyle(fontSize = 11.sp, color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Medium),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Message Bubble Content Card
        val isDark = isSystemInDarkTheme()
        val bubbleBg = if (isUser) {
            if (isDark) ElegantUserBubbleBg else Color(0xFFE0F2FE)
        } else {
            if (isDark) ElegantModelBubbleBg else Color(0xFFF1F5F9)
        }
        val userBubbleText = if (isDark) ElegantUserBubbleText else Color(0xFF0369A1)
        val bubbleBorderColor = if (isDark) ElegantModelBubbleBorder else Color(0xFFE2E8F0)

        val bubbleShape = if (isUser) {
            RoundedCornerShape(topStart = 24.dp, topEnd = 4.dp, bottomStart = 24.dp, bottomEnd = 24.dp)
        } else {
            RoundedCornerShape(topStart = 4.dp, topEnd = 24.dp, bottomStart = 24.dp, bottomEnd = 24.dp)
        }

        Column(
            modifier = Modifier
                .widthIn(max = 320.dp)
                .clip(bubbleShape)
                .background(bubbleBg)
                .then(
                    if (isUser) Modifier
                    else Modifier.border(BorderStroke(1.dp, bubbleBorderColor), bubbleShape)
                )
                .padding(14.dp)
        ) {
            if (message.error != null) {
                // Error State Display
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Warning, contentDescription = "Error", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Soʻrov muvaffaqiyatsiz tugadi", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = message.error ?: "",
                    style = TextStyle(fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                )
            } else {
                val text = message.text
                val hasAttachment = isUser && text.startsWith("📎 [Biriktirilgan fayllar:")
                val fileHeaderEnd = if (hasAttachment) text.indexOf("]\n\n") else -1
                val fileNamesString = if (hasAttachment && fileHeaderEnd != -1) {
                    text.substring(25, fileHeaderEnd)
                } else null
                val cleanText = if (hasAttachment && fileHeaderEnd != -1) {
                    text.substring(fileHeaderEnd + 3)
                } else {
                    text
                }

                if (fileNamesString != null) {
                    val filesList = fileNamesString.split(", ").map { fileStr ->
                        val sizeIndex = fileStr.lastIndexOf(" (")
                        val name = if (sizeIndex != -1) fileStr.substring(0, sizeIndex) else fileStr
                        val size = if (sizeIndex != -1) fileStr.substring(sizeIndex + 2, fileStr.length - 1) else "Nomaʼlum"
                        val type = when {
                            name.endsWith(".pdf", ignoreCase = true) -> "pdf"
                            name.endsWith(".png", ignoreCase = true) || name.endsWith(".jpg", ignoreCase = true) || name.endsWith(".jpeg", ignoreCase = true) -> "image"
                            name.endsWith(".mp4", ignoreCase = true) || name.endsWith(".3gp", ignoreCase = true) -> "video"
                            name.startsWith("Kamera_") -> "camera"
                            else -> "other"
                        }
                        name to (size to type)
                    }

                    Column(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                    ) {
                        filesList.forEach { file ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    val icon = when (file.second.second) {
                                        "pdf" -> Icons.Default.Description
                                        "image" -> Icons.Default.Image
                                        "video" -> Icons.Default.Videocam
                                        "camera" -> Icons.Default.PhotoCamera
                                        else -> Icons.Default.Folder
                                    }
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = file.second.second,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Column {
                                        Text(
                                            text = file.first,
                                            style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = file.second.first,
                                            style = TextStyle(fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Parse and render formatted markdown blocks
                val blocks = parseMessageText(cleanText)
                blocks.forEachIndexed { index, block ->
                    if (index > 0) Spacer(modifier = Modifier.height(8.dp))
                    when (block) {
                        is MessageBlock.Text -> {
                            Text(
                                text = renderFormattedText(block.text),
                                style = TextStyle(
                                    fontSize = 14.sp,
                                    lineHeight = 20.sp,
                                    color = if (isUser) userBubbleText else MaterialTheme.colorScheme.onBackground
                                )
                            )
                        }
                        is MessageBlock.Code -> {
                            // Monospace code block styling
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.Black.copy(alpha = 0.4f))
                                    .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)), RoundedCornerShape(8.dp))
                                    .padding(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = block.language.uppercase(),
                                        style = TextStyle(
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    )
                                    IconButton(
                                        onClick = {
                                            clipboardManager.setText(AnnotatedString(block.code))
                                            Toast.makeText(context, "Kod vaqtinchalik xotiraga nusxalandi", Toast.LENGTH_SHORT).show()
                                        },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ContentCopy,
                                            contentDescription = "Copy code",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = block.code,
                                    style = TextStyle(
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 12.sp,
                                        lineHeight = 16.sp,
                                        color = Color(0xFF81E6D9) // Mint monospace text
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                        is MessageBlock.Image -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color.Black.copy(alpha = 0.05f))
                                    .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)), RoundedCornerShape(12.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    coil.compose.AsyncImage(
                                        model = block.url,
                                        contentDescription = "Generated Image",
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(max = 280.dp)
                                            .clip(RoundedCornerShape(8.dp)),
                                        contentScale = androidx.compose.ui.layout.ContentScale.Fit
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            "Nexus AI Imagen 3",
                                            style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                        )
                                        Row {
                                            IconButton(
                                                onClick = {
                                                    downloadAndSaveImageToGallery(context, block.url)
                                                },
                                                modifier = Modifier.size(24.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Download,
                                                    contentDescription = "Save Image to Gallery",
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(8.dp))
                                            IconButton(
                                                onClick = {
                                                    val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                                        type = "text/plain"
                                                        putExtra(android.content.Intent.EXTRA_TEXT, block.url)
                                                    }
                                                    context.startActivity(android.content.Intent.createChooser(intent, "Rasm havolasini ulashish"))
                                                },
                                                modifier = Modifier.size(24.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Share,
                                                    contentDescription = "Share Image Link",
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Copy, Share, Export Actions Row for AI responses
                if (!isUser && message.text.isNotEmpty() && message.error == null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                clipboardManager.setText(AnnotatedString(cleanText))
                                Toast.makeText(context, "Nusxalandi", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Nusxalash",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        IconButton(
                            onClick = {
                                val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(android.content.Intent.EXTRA_TEXT, cleanText)
                                }
                                context.startActivity(android.content.Intent.createChooser(intent, "Ulashish"))
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Ulashish",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        IconButton(
                            onClick = {
                                exportToWordFile(context, cleanText, "Nexus_AI_Javob")
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Description,
                                contentDescription = "Word fayli qilib yuklab olish",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                }
            }

            // Fact-checking / Grounding Sources Tag chips
            if (!isUser && !message.sources.isNullOrEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "MANBALAR VA IQTIBOSLAR:",
                    style = TextStyle(
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary,
                        letterSpacing = 0.5.sp
                    )
                )
                Spacer(modifier = Modifier.height(4.dp))
                GroundingSection(sources = message.sources)
            }
        }

        // Suggested Follow-up Questions Row
        if (!isUser && message.text.isNotEmpty()) {
            val followUps = getFollowUpQuestions(message.text)
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                followUps.forEach { question ->
                    SuggestionChip(
                        onClick = { onFollowUpClicked(question) },
                        label = {
                            Text(
                                text = question,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        },
                        colors = SuggestionChipDefaults.suggestionChipColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            labelColor = MaterialTheme.colorScheme.primary
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }
        }
    }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GroundingSection(sources: List<WebSourceEntity>) {
    val uriHandler = LocalUriHandler.current

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        sources.take(4).forEach { source ->
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outline), RoundedCornerShape(6.dp))
                    .clickable { source.uri?.let { uriHandler.openUri(it) } }
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Link,
                    contentDescription = "Link",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(12.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = source.title ?: "Manba",
                    style = TextStyle(
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 120.dp)
                )
            }
        }
    }
}

@Composable
fun TypingIndicator() {
    val transition = rememberInfiniteTransition(label = "typing")
    val dotOffsets = (0..2).map { index ->
        transition.animateFloat(
            initialValue = 0f,
            targetValue = -8f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 300, delayMillis = index * 100, easing = LinearOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "dot_$index"
        )
    }

    Row(
        modifier = Modifier.padding(start = 32.dp, top = 8.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        dotOffsets.forEach { offset ->
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .offset(y = offset.value.dp)
                    .background(MaterialTheme.colorScheme.primary, shape = CircleShape)
            )
        }
    }
}

@Composable
fun OnboardingView(
    preset: SystemPreset,
    onQuickPromptClicked: (String) -> Unit,
    totalSessions: Int = 0,
    currentSessionMessages: Int = 0
) {
    val gradientBrush = Brush.verticalGradient(
        colors = listOf(
            MaterialTheme.colorScheme.primary.copy(alpha = 0.05f),
            Color.Transparent
        )
    )

    var showSplash by remember { mutableStateOf(true) }
    val logoScale = remember { Animatable(0.5f) }
    val logoAlpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        // Stage 1: Scale up and Fade in
        launch {
            logoScale.animateTo(
                targetValue = 1.1f,
                animationSpec = tween(durationMillis = 1000, easing = LinearOutSlowInEasing)
            )
        }
        logoAlpha.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 1000, easing = LinearOutSlowInEasing)
        )

        delay(1500)

        // Stage 2: Scale up more and Fade out (o'chiradigan va masshtablaydigan premium animatsiya)
        launch {
            logoScale.animateTo(
                targetValue = 1.6f,
                animationSpec = tween(durationMillis = 800, easing = FastOutLinearInEasing)
            )
        }
        logoAlpha.animateTo(
            targetValue = 0f,
            animationSpec = tween(durationMillis = 800, easing = FastOutLinearInEasing)
        )

        showSplash = false
    }

    if (showSplash) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.background,
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .scale(logoScale.value)
                        .alpha(logoAlpha.value)
                        .clip(RoundedCornerShape(24.dp))
                        .background(
                            Brush.linearGradient(
                                colors = listOf(ElegantPrimary, ElegantSecondary)
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = "Nexus AI",
                        tint = Color.White,
                        modifier = Modifier.size(54.dp)
                    )
                }
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "NEXUS AI",
                    style = TextStyle(
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                        letterSpacing = 4.sp
                    ),
                    modifier = Modifier
                        .scale(logoScale.value)
                        .alpha(logoAlpha.value)
                )
            }
        }
    } else {
        var visible by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            visible = true
        }

        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(animationSpec = tween(durationMillis = 600)) + expandVertically(animationSpec = tween(durationMillis = 600)),
            modifier = Modifier.fillMaxSize()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(gradientBrush)
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Top
            ) {
                Spacer(modifier = Modifier.height(16.dp))

                // AI Hero Icon
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            Brush.linearGradient(
                                colors = listOf(ElegantPrimary, ElegantSecondary)
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = "Hero Icon",
                        tint = Color.White,
                        modifier = Modifier.size(38.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Nexus AI'ga xush kelibsiz",
                    style = TextStyle(
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Sizning professional va ishonchli hamkoringiz",
                    style = TextStyle(
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary
                    )
                )

                Spacer(modifier = Modifier.height(32.dp))

                // Suggestions Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "TEZKOR ANDOZALAR",
                    style = TextStyle(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 2x2 Grid of Beautiful Suggestion Cards
            val suggestions = listOf(
                SuggestionItem("💻 Kod yozish", "Algoritmlar va Kotlin/Java kodlar", "Ikkilik qidiruv universal funksiyasini yozib ber"),
                SuggestionItem("📚 Tarjima qilish", "Ingliz, Rus va boshqa tillarga", "Ushbu xabarni ingliz tiliga tarjima qil: Salom, ahvollar qalay?"),
                SuggestionItem("🧠 Chuqur tahlil", "Mantiqiy yondashuv va tahlil", "Sunʼiy intellekt kelajagi haqida chuqur tahlil yozib ber"),
                SuggestionItem("⚡ Maʼlumot qidirish", "Google Search va qidiruv", "Dunyoning eng baland binolari va ularning balandligi haqida maʼlumot top")
            )

            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                suggestions.forEach { item ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onQuickPromptClicked(item.promptText) },
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.title,
                                    style = TextStyle(
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = item.description,
                                    style = TextStyle(
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.ArrowForward,
                                contentDescription = "Go",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
}

data class SuggestionItem(
    val title: String,
    val description: String,
    val promptText: String
)

@Composable
fun QuickActionsRow(onActionClicked: (String) -> Unit) {
    val actions = listOf(
        Pair("💻 Kod yozish", "Menga ... uchun kod yozib ber"),
        Pair("📝 Konspekt", "Quyidagi matnni qisqacha tushuntirib ber: "),
        Pair("🌐 Tarjima", "Quyidagi matnni ingliz tiliga tarjima qil: "),
        Pair("🎓 Tushuntir", "Menga buni oddiy so'zlar bilan tushuntir: "),
        Pair("✍️ Qayta yozish", "Ushbu matnni professional uslubda qayta yozib ber: "),
        Pair("💡 G'oyalar", "Menga ... mavzusida yangi g'oyalar ber")
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        actions.forEach { action ->
            Surface(
                onClick = { onActionClicked(action.second) },
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.padding(bottom = 2.dp)
            ) {
                Text(
                    text = action.first,
                    style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
    }
}

@Composable
fun InputPane(
    inputText: String,
    isSending: Boolean,
    onInputTextChanged: (String) -> Unit,
    onSendMessage: () -> Unit,
    isSearchEnabled: Boolean,
    onSearchToggled: (Boolean) -> Unit,
    onStopGeneration: () -> Unit,
    attachedFiles: SnapshotStateList<AttachedFile>
) {
    val context = LocalContext.current
    var showAttachmentMenu by remember { mutableStateOf(false) }

    // Launcher for general files (PDF, documents)
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: android.net.Uri? ->
        uri?.let {
            val name = getFileName(context, it)
            val size = getFileSize(context, it)
            attachedFiles.add(AttachedFile(name = name, size = size, type = "pdf", uri = it))
        }
    }

    // Launcher for images
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: android.net.Uri? ->
        uri?.let {
            val name = getFileName(context, it)
            val size = getFileSize(context, it)
            attachedFiles.add(AttachedFile(name = name, size = size, type = "image", uri = it))
        }
    }

    // Launcher for videos
    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: android.net.Uri? ->
        uri?.let {
            val name = getFileName(context, it)
            val size = getFileSize(context, it)
            attachedFiles.add(AttachedFile(name = name, size = size, type = "video", uri = it))
        }
    }

    // Launcher for camera
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap: android.graphics.Bitmap? ->
        bitmap?.let {
            attachedFiles.add(
                AttachedFile(
                    name = "Kamera_Surati_${System.currentTimeMillis() / 1000}.jpg",
                    size = "~150 KB",
                    type = "camera",
                    bitmap = it
                )
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        if (inputText.isEmpty() && !isSending && attachedFiles.isEmpty()) {
            QuickActionsRow(onActionClicked = onInputTextChanged)
        }

        // Attached Files Queue Preview Row
        if (attachedFiles.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                attachedFiles.forEach { file ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val icon = when (file.type) {
                                "pdf" -> Icons.Default.Description
                                "image" -> Icons.Default.Image
                                "video" -> Icons.Default.Videocam
                                "camera" -> Icons.Default.PhotoCamera
                                else -> Icons.Default.Folder
                            }
                            val iconColor = when (file.type) {
                                "pdf" -> MaterialTheme.colorScheme.primary
                                "image" -> Color(0xFF00D2FF)
                                "video" -> Color(0xFFFF5252)
                                "camera" -> Color(0xFF00FF66)
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                            Icon(
                                imageVector = icon,
                                contentDescription = file.type,
                                tint = iconColor,
                                modifier = Modifier.size(16.dp)
                            )
                            Column {
                                Text(
                                    text = if (file.name.length > 15) file.name.take(12) + "..." else file.name,
                                    style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold),
                                    maxLines = 1
                                )
                                Text(
                                    text = file.size,
                                    style = TextStyle(fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                )
                            }
                            Spacer(modifier = Modifier.width(2.dp))
                            IconButton(
                                onClick = { attachedFiles.remove(file) },
                                modifier = Modifier.size(16.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Remove file",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        Surface(
            tonalElevation = 4.dp,
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding(),
            color = MaterialTheme.colorScheme.surface
        ) {
            Row(
                modifier = Modifier
                    .padding(12.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Attach File Menu Button (replaces Globe web search toggle button)
                Box {
                    IconButton(
                        onClick = { showAttachmentMenu = true },
                        colors = IconButtonDefaults.iconButtonColors(
                            contentColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AttachFile,
                            contentDescription = "Fayl biriktirish",
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = showAttachmentMenu,
                        onDismissRequest = { showAttachmentMenu = false },
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.surface)
                            .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)), RoundedCornerShape(12.dp))
                    ) {
                        DropdownMenuItem(
                            text = { Text("📄 PDF / Hujjatlar", style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium)) },
                            onClick = {
                                showAttachmentMenu = false
                                filePickerLauncher.launch("application/pdf")
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Description,
                                    contentDescription = "PDF",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("🖼️ Surat / Galereya", style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium)) },
                            onClick = {
                                showAttachmentMenu = false
                                imagePickerLauncher.launch("image/*")
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Image,
                                    contentDescription = "Surat",
                                    tint = Color(0xFF00D2FF),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("🎥 Videolar", style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium)) },
                            onClick = {
                                showAttachmentMenu = false
                                videoPickerLauncher.launch("video/*")
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Videocam,
                                    contentDescription = "Video",
                                    tint = Color(0xFFFF5252),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("📷 Kamera", style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium)) },
                            onClick = {
                                showAttachmentMenu = false
                                cameraLauncher.launch(null)
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.PhotoCamera,
                                    contentDescription = "Kamera",
                                    tint = Color(0xFF00FF66),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("📁 Boshqa fayllar", style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium)) },
                            onClick = {
                                showAttachmentMenu = false
                                filePickerLauncher.launch("*/*")
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Folder,
                                    contentDescription = "Boshqa",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        )
                    }
                }

                // Outlined message input field
                OutlinedTextField(
                    value = inputText,
                    onValueChange = onInputTextChanged,
                    placeholder = {
                        Text(
                            text = "Xabar yozing yoki fayl biriktiring...",
                            fontSize = 14.sp
                        )
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("message_input"),
                    shape = RoundedCornerShape(20.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    ),
                    maxLines = 4,
                    textStyle = TextStyle(fontSize = 14.sp),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        imeAction = androidx.compose.ui.text.input.ImeAction.Send
                    ),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                        onSend = { if (inputText.trim().isNotEmpty() || attachedFiles.isNotEmpty()) onSendMessage() }
                    )
                )

                // Send or Stop Action Button
                if (isSending) {
                    IconButton(
                        onClick = onStopGeneration,
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Stop,
                            contentDescription = "Stop generation",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                } else {
                    val canSend = inputText.trim().isNotEmpty() || attachedFiles.isNotEmpty()
                    IconButton(
                        onClick = onSendMessage,
                        enabled = canSend,
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = if (canSend) MaterialTheme.colorScheme.primary else Color.Transparent,
                            contentColor = if (canSend) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .testTag("send_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Send,
                            contentDescription = "Send message",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}
