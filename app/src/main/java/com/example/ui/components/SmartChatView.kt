package com.example.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.AppViewModel
import com.example.ui.AiHandshakeState
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.CheckCircle
import com.example.ui.theme.Charcoal
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.WaterBlue

import androidx.compose.ui.graphics.asImageBitmap
import android.graphics.BitmapFactory
import android.util.Base64

// Helper function to parse markdown bold "**text**", italic "*text*", triple "***text***", and headers "# Headers" into Styled AnnotatedString
fun parseMarkdown(text: String): AnnotatedString {
    return buildAnnotatedString {
        val lines = text.split("\n")
        for (i in lines.indices) {
            val line = lines[i]
            var currentLine = line
            var isHeader = false
            var headerLevel = 0

            // Check for headers starting with #, up to 6 levels, space between # and text is optional but standard
            val headerMatch = Regex("""^(#{1,6})\s*(.*)$""").matchEntire(currentLine)
            if (headerMatch != null) {
                isHeader = true
                headerLevel = headerMatch.groupValues[1].length
                currentLine = headerMatch.groupValues[2]
            }

            // Convert raw bullet symbols to bullet characters
            if (!isHeader) {
                if (currentLine.startsWith("* ")) {
                    currentLine = "• " + currentLine.substring(2)
                } else if (currentLine.startsWith("- ")) {
                    currentLine = "• " + currentLine.substring(2)
                }
            }

            // Replace checkbox patterns anywhere in the string
            currentLine = currentLine.replace("[ ]", "☐").replace("[x]", "☑").replace("[X]", "☑")

            // Horizontal dividers using dashes or dots
            if (currentLine.trim() == "---" || currentLine.trim() == "----" || currentLine.trim() == "...." || currentLine.trim() == "...") {
                currentLine = "────────────────────────"
            }

            if (isHeader) {
                // Apply a larger/bolder style for headers
                val scale = when (headerLevel) {
                    1 -> 1.35f
                    2 -> 1.25f
                    3 -> 1.15f
                    else -> 1.05f
                }
                pushStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = (14 * scale).sp, color = WaterBlue))
            }

            // Now parse ***bold italic***, **bold**, and *italic* within the current line
            val tripleParts = currentLine.split("***")
            for (bi in tripleParts.indices) {
                val biPart = tripleParts[bi]
                if (bi % 2 == 1) {
                    withStyle(style = SpanStyle(fontWeight = FontWeight.Bold, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)) {
                        append(biPart)
                    }
                } else {
                    val boldParts = biPart.split("**")
                    for (b in boldParts.indices) {
                        val boldPart = boldParts[b]
                        if (b % 2 == 1) {
                            withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                                append(boldPart)
                            }
                        } else {
                            val italicParts = boldPart.split("*")
                            for (j in italicParts.indices) {
                                val italicPart = italicParts[j]
                                if (j % 2 == 1) {
                                    withStyle(style = SpanStyle(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)) {
                                        append(italicPart)
                                    }
                                } else {
                                    append(italicPart)
                                }
                            }
                        }
                    }
                }
            }

            if (isHeader) {
                // pop the header style
                pop()
            }
            if (i < lines.size - 1) {
                append("\n")
            }
        }
    }
}

@Composable
fun SmartChatView(viewModel: AppViewModel, modifier: Modifier = Modifier) {
    val messages by viewModel.chatbotMessages.collectAsState()
    val isLoading by viewModel.chatbotLoading.collectAsState()
    val handshakeState by viewModel.aiHandshakeStatus.collectAsState()

    var inputMessage by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    val welcomeGreeting by viewModel.welcomeGreeting.collectAsState()

    // Automatically trigger handshake check when opening the view
    LaunchedEffect(Unit) {
        if (handshakeState is AiHandshakeState.NotTested) {
            viewModel.performAiHandshake()
        }
        viewModel.generateWelcomeGreeting()
    }

    // Automatically scroll to the bottom when new messages arrive
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
    val isKeyboardVisible = androidx.compose.foundation.layout.WindowInsets.isImeVisible
    val bottomPadding = if (isKeyboardVisible) 4.dp else 12.dp

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = bottomPadding)
        ) {
            // Conversation history thread window
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth().padding(bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (messages.isEmpty() && !isLoading) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillParentMaxSize()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = welcomeGreeting ?: "Hi, welcome! Where can we start today?",
                                color = Color.Gray,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                modifier = Modifier.padding(bottom = 32.dp)
                            )
                        }
                    }
                }

                items(messages) { msg ->
                val isUser = msg.isUser
                val text = msg.text

                val alignment = if (isUser) Alignment.End else Alignment.Start

                Column(
                    modifier = Modifier.animateItem().fillMaxWidth(),
                    horizontalAlignment = alignment
                ) {
                    val bubbleShape = RoundedCornerShape(24.dp)
                    val bubbleBackground = Color.White.copy(alpha = 0.1f)
                    val bubbleBorderColor = Color.White.copy(alpha = 0.2f)
                    val bubbleTextColor = Color.White

                    Box(
                        modifier = Modifier
                            .clip(bubbleShape)
                            .background(bubbleBackground)
                            .border(width = 1.dp, color = bubbleBorderColor, shape = bubbleShape)
                            .padding(16.dp)
                            .widthIn(max = 780.dp) // Optimized maximum width for tablet/tab screens
                    ) {
                        Column {
                            Text(
                                text = parseMarkdown(text),
                                color = bubbleTextColor,
                                fontSize = 14.sp
                            )
                            
                            if (msg.base64Image != null) {
                                val bitmap = remember(msg.base64Image) {
                                    try {
                                        val decodedString = Base64.decode(msg.base64Image, Base64.DEFAULT)
                                        BitmapFactory.decodeByteArray(decodedString, 0, decodedString.size)
                                    } catch (e: Exception) {
                                        null
                                    }
                                }
                                if (bitmap != null) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    androidx.compose.foundation.Image(
                                        bitmap = bitmap.asImageBitmap(),
                                        contentDescription = "AI Generated Image",
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(max = 300.dp)
                                            .clip(RoundedCornerShape(8.dp)),
                                        contentScale = androidx.compose.ui.layout.ContentScale.Fit
                                    )
                                }
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(2.dp))
                    
                    val badgeInfo = if (msg.modelUsed != null) " (Model: ${msg.modelUsed})" else ""
                    Text(
                        text = (if (isUser) "You" else "Core Intel AI") + badgeInfo,
                        fontSize = 9.sp,
                        color = Color.Gray,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }
            }

            if (isLoading) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        CircularProgressIndicator(
                            color = WaterBlue,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }

        // Input entry box
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TextField(
                value = inputMessage,
                onValueChange = { inputMessage = it },
                placeholder = { Text("Chat with local offline Gemma or enter commands...", color = Color.Gray, fontSize = 13.sp) },
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.LightGray,
                    focusedContainerColor = Color.White.copy(alpha = 0.1f),
                    unfocusedContainerColor = Color.White.copy(alpha = 0.05f),
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp),
                modifier = Modifier
                    .weight(1f)
                    .height(64.dp)
                    .clip(RoundedCornerShape(32.dp))
                    .border(width = 1.dp, color = Color.White.copy(alpha = 0.2f), shape = RoundedCornerShape(32.dp))
                    .testTag("ai_chat_input")
            )

            IconButton(
                onClick = {
                    if (inputMessage.trim().isNotEmpty()) {
                        viewModel.sendMessageToAI(inputMessage)
                        inputMessage = ""
                    }
                },
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(androidx.compose.ui.graphics.Brush.horizontalGradient(
                        colors = listOf(WaterBlue, com.example.ui.theme.WaterBlueAccent)
                    ))
                    .border(width = 1.dp, color = Color(0x40FFFFFF), shape = RoundedCornerShape(24.dp))
                    .testTag("send_chat_btn")
            ) {
                Icon(Icons.Default.Send, contentDescription = "Send", tint = Color.White)
            }
        }
    }
        
    // Connection status indicator moved to MainActivity top bar for proper placement
}
}


