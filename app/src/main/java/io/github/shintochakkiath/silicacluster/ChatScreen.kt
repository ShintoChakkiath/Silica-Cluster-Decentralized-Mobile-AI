/*
 * Silica Cluster - Decentralized Mobile AI
 * Copyright (C) 2026 Shinto Chakkiath
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 */
package io.github.shintochakkiath.silicacluster

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.Link
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material.icons.filled.ArrowDropDown
import io.github.shintochakkiath.silicacluster.ui.theme.AlertRed
import androidx.compose.foundation.lazy.itemsIndexed

fun formatMarkdown(text: String): AnnotatedString {
    return buildAnnotatedString {
        val regex = Regex("""\*\*(.*?)\*\*|\*(.*?)\*""")
        var lastIndex = 0
        regex.findAll(text).forEach { matchResult ->
            append(text.substring(lastIndex, matchResult.range.first))
            if (matchResult.groupValues[1].isNotEmpty()) {
                withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                    append(matchResult.groupValues[1])
                }
            } else if (matchResult.groupValues[2].isNotEmpty()) {
                withStyle(style = SpanStyle(fontStyle = FontStyle.Italic)) {
                    append(matchResult.groupValues[2])
                }
            }
            lastIndex = matchResult.range.last + 1
        }
        append(text.substring(lastIndex))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    currentBridgeUrl: String?,
    activeApiKey: String,
    selectedModel: String?,
    isRealTimeAccess: Boolean = false,
    isServerRunning: Boolean = false,
    onNavigateToDownloads: () -> Unit = {},
    onStartEngine: (String) -> Unit = {},
    onStopEngine: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    
    val sessions by ChatRepository.sessions.collectAsState()
    val activeId by ChatRepository.activeSessionId.collectAsState()
    val activeSession = sessions.find { it.id == activeId }
    
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
    var inputText by remember { mutableStateOf("") }
    var isSending by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf("User message received...") }

    Column(modifier = Modifier.fillMaxSize()) {
        // Chat History Scroll View
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
            contentPadding = PaddingValues(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val messages = activeSession?.messages ?: emptyList()
            
            if (messages.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            "What can I compute for you?",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.padding(top = 100.dp)
                        )
                    }
                }
            }
            
            itemsIndexed(messages) { index, msg ->
                val isUser = msg.role == "user"
                val isLastUserMessage = isUser && index == messages.indexOfLast { it.role == "user" }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
                ) {
                    Box(
                        modifier = Modifier
                            .background(
                                color = if (isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(
                                    topStart = 16.dp,
                                    topEnd = 16.dp,
                                    bottomStart = if (isUser) 16.dp else 4.dp,
                                    bottomEnd = if (isUser) 4.dp else 16.dp
                                )
                            )
                            .padding(16.dp)
                            .widthIn(max = 280.dp)
                    ) {
                        Column {
                            Text(
                                text = formatMarkdown(msg.content),
                                color = if (isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                style = MaterialTheme.typography.bodyLarge
                            )
                            if (!isUser && msg.sourceUrls.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    msg.sourceUrls.forEachIndexed { index, url ->
                                            val parsedUri = Uri.parse(url)
                                            val overrideDomain = parsedUri.fragment?.let { if (it.startsWith("silica_domain=")) it.removePrefix("silica_domain=") else null }
                                            val host = overrideDomain ?: try { parsedUri.host?.removePrefix("www.") } catch (e: Exception) { null }
                                            
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier
                                                    .clickable {
                                                        val cleanUrl = if (overrideDomain != null) url.substringBefore("#silica_domain=") else url
                                                        val i = Intent(Intent.ACTION_VIEW, Uri.parse(cleanUrl))
                                                        context.startActivity(i)
                                                    }
                                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                                            ) {
                                                Icon(Icons.Default.Link, contentDescription = "Source", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                                                Spacer(modifier = Modifier.width(4.dp))
                                                val labelText = if (host.isNullOrBlank()) "Source ${index + 1}" else "Source: $host"
                                                Text(labelText, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                            }
                                    }
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(4.dp))
                            val iconTint = if (isUser) MaterialTheme.colorScheme.onPrimary.copy(alpha=0.7f) else MaterialTheme.colorScheme.outline.copy(alpha=0.7f)
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start) {
                                if (isLastUserMessage) {
                                    IconButton(
                                        onClick = { 
                                            inputText = msg.content
                                            ChatRepository.revertToMessage(context, ChatRepository.activeSessionId.value ?: "", index)
                                        }, 
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(Icons.Default.Edit, contentDescription = "Edit", tint = iconTint, modifier = Modifier.size(16.dp))
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                }
                                IconButton(
                                    onClick = { clipboardManager.setText(AnnotatedString(msg.content)) }, 
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = iconTint, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            }
            
            if (isSending) {
                item {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                        Box(
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(16.dp))
                                .padding(16.dp)
                        ) {
                            Text(statusText, color = MaterialTheme.colorScheme.outline, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }

        // Bottom Input Pane
        Surface(
            tonalElevation = 8.dp,
            shadowElevation = 8.dp,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(end = 16.dp, top = 4.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    var expanded by remember { mutableStateOf(false) }
                    var currentModel by remember { mutableStateOf(selectedModel ?: "LLM Models") }
                    val downloadedModels = ModelDirectory.getModels(context).filter { java.io.File(context.filesDir.absolutePath + "/models/${it.name.replace(" ", "_").lowercase()}.gguf.completed").exists() || java.io.File(context.getExternalFilesDir(null)?.absolutePath + "/models/${it.name.replace(" ", "_").lowercase()}.gguf.completed").exists() }

                    Box {
                        TextButton(onClick = { expanded = true }) {
                            Text(currentModel, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        }
                        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            downloadedModels.forEach { model ->
                                DropdownMenuItem(
                                    text = { Text(model.name) },
                                    onClick = {
                                        currentModel = model.name
                                        expanded = false
                                    }
                                )
                            }
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("Download a New LLM Model", color = MaterialTheme.colorScheme.tertiary) },
                                onClick = {
                                    expanded = false
                                    onNavigateToDownloads()
                                }
                            )
                        }
                    }

                    if (currentModel != "LLM Models") {
                        Spacer(Modifier.width(8.dp))
                        if (isServerRunning && selectedModel == currentModel) {
                            val activeNodes by NodeManager.activeNodes.collectAsState()
                            val bridgeUrl by BridgeStateManager.currentBridgeUrl.collectAsState()
                            val isOnline = activeNodes.any { it.status == NodeStatus.ONLINE } || (!bridgeUrl.isNullOrEmpty() && bridgeUrl != "Initializing Tunnel...")
                            
                            if (isOnline) {
                                IconButton(onClick = onStopEngine, modifier = Modifier.size(32.dp)) {
                                    Icon(Icons.Default.StopCircle, contentDescription = "Stop", tint = AlertRed)
                                }
                            } else {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(32.dp)) {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary)
                                    IconButton(onClick = onStopEngine) {
                                        Icon(Icons.Default.StopCircle, contentDescription = "Stop", tint = AlertRed, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        } else {
                            IconButton(onClick = { onStartEngine(currentModel) }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.PlayArrow, contentDescription = "Play", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 8.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { /* Implement later */ }) {
                        Icon(Icons.Default.Add, contentDescription = "Add Attachment", tint = MaterialTheme.colorScheme.primary)
                    }
                
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Message Silica...") },
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha=0.5f),
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha=0.5f)
                    ),
                    maxLines = 4
                )
                
                IconButton(
                    onClick = {
                        if (inputText.isNotBlank() && !isSending) {
                            val userText = inputText
                            inputText = ""
                            
                            if (activeSession == null) {
                                ChatRepository.createNewChat(context)
                            }
                            
                            ChatRepository.addMessageToActive(context, ChatMessage("user", userText))
                            isSending = true
                            
                            scope.launch {
                                // Auto-scroll down
                                listState.animateScrollToItem(ChatRepository.sessions.value.find { it.id == ChatRepository.activeSessionId.value }?.messages?.size ?: 0)
                                
                                statusText = "User message received..."
                                kotlinx.coroutines.delay(400)
                                
                                var searchResultsText = ""
                                var sourceUrls: List<String> = emptyList()
                                if (isRealTimeAccess) {
                                    val q = userText.lowercase().trim().replace(Regex("[^a-z0-9 ]"), "")
                                    val slangs = setOf("hi", "hello", "hey", "yo", "sup", "wassup", "whatsup", "whats up", "what up", "hola", "good morning", "good afternoon", "good evening", "good night", "bye", "goodbye", "thanks", "thank you", "ok", "okay", "cool", "awesome", "lol", "lmao", "haha", "yes", "no", "yup", "nope", "yeah", "nah")
                                    val isBasicConvo = slangs.contains(q) || q.length < 3
                                    
                                    val wantsSearch = if (isBasicConvo) {
                                        false
                                    } else {
                                        statusText = "Analyzing intent..."
                                        val wordCount = userText.trim().split("\\s+".toRegex()).size
                                        val shortContext = if (wordCount <= 4) "The query is $wordCount words. If it is slang, a basic question, a greeting, or a simple dictionary word, reply NO. " else ""
                                        val routePrompt = "User query: \"$userText\"\n${shortContext}Does this query explicitly require looking up extremely recent real-time news, current local weather, or live internet statistics to answer accurately? Reply with ONLY the word YES or NO. Do not explain, do not converse."
                                        val routeResponse = ChatClient.sendMessage(currentBridgeUrl ?: "", activeApiKey, selectedModel ?: "silica-network", listOf(ChatMessage("user", routePrompt)))
                                        routeResponse?.content?.contains("YES", ignoreCase = true) == true
                                    }
                                    
                                    if (wantsSearch) {
                                        statusText = "Looking up internet about the subject..."
                                        val (contextData, links) = WebSearcher.getRealTimeContext(userText)
                                        sourceUrls = links
                                        if (contextData.isNotBlank()) {
                                            searchResultsText = "\n\nCRITICAL SYSTEM INSTRUCTION: Use the following real-time data to answer the user's latest query accurately. Do not refuse to answer if the information is here.\n\n$contextData"
                                        }
                                    }
                                }

                                statusText = "LLM model processing the context..."
                                kotlinx.coroutines.delay(400)
                                val fullSession = ChatRepository.sessions.value.find { it.id == ChatRepository.activeSessionId.value }
                                val allMessages = fullSession?.messages?.toList() ?: emptyList()
                                
                                val apiMessages = if (allMessages.size > 1) {
                                    val oldMessages = allMessages.dropLast(1)
                                    val newMessage = allMessages.last()
                                    val historyText = oldMessages.joinToString("\n") { 
                                        "${if (it.role == "user") "User" else "Assistant"}: ${it.content}" 
                                    }
                                    val compiledContent = "chats so far [\n$historyText\n]\nthen the new message: ${newMessage.content}$searchResultsText"
                                    listOf(ChatMessage("user", compiledContent, newMessage.timestamp))
                                } else {
                                    if (searchResultsText.isNotEmpty()) {
                                        val newMessage = allMessages.last()
                                        listOf(ChatMessage("user", "${newMessage.content}$searchResultsText", newMessage.timestamp))
                                    } else {
                                        allMessages
                                    }
                                }

                                statusText = "Preparing response..."
                                val response = ChatClient.sendMessage(
                                    hostUrl = currentBridgeUrl ?: "",
                                    apiKey = activeApiKey,
                                    model = selectedModel ?: "silica-network",
                                    messages = apiMessages
                                )
                                
                                if (response != null) {
                                    val finalResponse = if (sourceUrls.isNotEmpty()) response.copy(sourceUrls = sourceUrls) else response
                                    ChatRepository.addMessageToActive(context, finalResponse)
                                }
                                isSending = false
                                listState.animateScrollToItem(ChatRepository.sessions.value.find { it.id == ChatRepository.activeSessionId.value }?.messages?.size ?: 0)
                            }
                        }
                    },
                    enabled = inputText.isNotBlank() && !isSending
                ) {
                    Icon(
                        Icons.Default.Send,
                        contentDescription = "Send",
                        tint = if (inputText.isNotBlank() && !isSending) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                    )
                }
            }
        }
        }
    }
}
