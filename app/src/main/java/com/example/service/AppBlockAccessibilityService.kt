package com.example.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import android.view.WindowManager
import android.view.View
import android.view.Gravity
import android.graphics.PixelFormat
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.os.Build
import com.example.util.AppBlockHelper
import com.example.util.FocusTimerManager

class AppBlockAccessibilityService : AccessibilityService() {

    private var windowManager: WindowManager? = null
    private val activeOverlays = mutableListOf<OverlayInfo>()
    private var fullScreenOverlayView: View? = null
    private var fullScreenYtShortsOverlayView: View? = null

    private class OverlayInfo(
        val view: View,
        var layoutParams: WindowManager.LayoutParams,
        var currentRect: android.graphics.Rect
    )

    private var lastDirectChatTime: Long = 0L
    private var isViewingSharedReelAllowed = false
    private var initialReelText: String? = null
    private var lastFeedScrollTime: Long = 0L
    private var scrollCountOnFeed = 0
    private var lastYtWatchDetectedTime: Long = 0L

    private var lastBlockTime: Long = 0L
    private var consecutiveBlockCount = 0

    private fun applyBlockAction(appName: String, featureName: String) {
        val now = System.currentTimeMillis()
        val timeSinceLastBlock = now - lastBlockTime
        
        if (timeSinceLastBlock < 2500) {
            consecutiveBlockCount++
        } else {
            consecutiveBlockCount = 1
        }
        lastBlockTime = now

        if (consecutiveBlockCount >= 3) {
            Log.w("AppBlocker", "Consecutive block limit reached for $appName ($featureName). Forcing HOME!")
            showToast("Focus Mode: Forcing HOME to break the $featureName loop! 🛑")
            performGlobalAction(GLOBAL_ACTION_HOME)
            consecutiveBlockCount = 0 // reset
        } else {
            Log.w("AppBlocker", "Blocking $appName ($featureName). Going BACK.")
            showToast("$appName $featureName is blocked! Stay focused 🎯")
            performGlobalAction(GLOBAL_ACTION_BACK)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val packageName = event.packageName?.toString() ?: return
        
        // Clear Instagram overlays if we navigate away from Instagram
        if (packageName != "com.instagram.android") {
            clearAllOverlays()
            hideFullScreenReelsOverlay()
        }

        // Clear YouTube overlays if we navigate away from YouTube
        if (packageName != "com.google.android.youtube") {
            hideFullScreenYtShortsOverlay()
        }
        
        // 1. Check general app blocking first
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            AppBlockHelper.checkForegroundAppAndBlockIfNeeded(applicationContext, packageName)
        }

        // 2. Fine-grained Instagram blocking logic
        if (packageName == "com.instagram.android") {
            handleInstagramBlocking(event)
        }

        // 3. Fine-grained YouTube blocking logic
        if (packageName == "com.google.android.youtube") {
            handleYoutubeBlocking(event)
        }

        // 4. Fine-grained Snapchat blocking logic
        if (packageName == "com.snapchat.android") {
            handleSnapchatBlocking(event)
        }

        // 5. Fine-grained Facebook blocking logic
        if (packageName == "com.facebook.katana" || packageName == "com.facebook.lite") {
            handleFacebookBlocking(event)
        }
    }

    private fun handleInstagramBlocking(event: AccessibilityEvent) {
        val context = applicationContext
        
        val useSelective = AppBlockHelper.isIgSelectiveBlockingEnabled(context)
        if (!useSelective) {
            clearAllOverlays()
            hideFullScreenReelsOverlay()
            return
        }

        val rootNode = rootInActiveWindow ?: event.source ?: return

        // Check if we are currently in Direct Messages / Chats
        val inDirectChat = checkInDirectChat(rootNode)
        if (inDirectChat) {
            lastDirectChatTime = System.currentTimeMillis()
            isViewingSharedReelAllowed = AppBlockHelper.isIgAllowSharedReels(context)
            initialReelText = null // Reset so we capture it when we enter Reels
            Log.d("InstagramBlocker", "User is in Direct Chat. Tracking for potential shared reel watch.")
        }

        // Check for Stories screen
        if (AppBlockHelper.isIgStoriesBlocked(context) && checkIsViewingStory(rootNode)) {
            clearAllOverlays()
            hideFullScreenReelsOverlay()
            applyBlockAction("Instagram", "Stories")
            return
        }

        // Check for Explore screen
        if (AppBlockHelper.isIgExploreBlocked(context) && checkIsViewingExplore(rootNode)) {
            clearAllOverlays()
            hideFullScreenReelsOverlay()
            applyBlockAction("Instagram", "Explore")
            return
        }

        // Check for Reels screen
        if (AppBlockHelper.isIgReelsBlocked(context)) {
            val reelsResult = checkIsViewingReels(rootNode)
            val isOnReelsTab = isReelsTabSelected(rootNode)
            val isReelsViewerActive = reelsResult.isReels

            if (isOnReelsTab || isReelsViewerActive) {
                // Check if this is a shared reel from DMs and allowed
                var allowed = false
                if (isReelsViewerActive) {
                    val timeSinceDirectChat = System.currentTimeMillis() - lastDirectChatTime
                    val isRecentDm = timeSinceDirectChat < 8000
                    if (isViewingSharedReelAllowed && isRecentDm) {
                        allowed = true
                        val currentReelText = reelsResult.identifier ?: ""
                        if (initialReelText == null && currentReelText.isNotEmpty()) {
                            initialReelText = currentReelText
                        }
                        val hasScrolled = event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED
                        val idChanged = initialReelText != null && currentReelText.isNotEmpty() && currentReelText != initialReelText
                        
                        if (hasScrolled || idChanged) {
                            allowed = false
                            isViewingSharedReelAllowed = false
                            initialReelText = null
                        }
                    }
                }

                if (!allowed) {
                    // Show full-screen blocked overlay instead of forcing home/back immediately!
                    clearAllOverlays()
                    showFullScreenReelsOverlay()
                    return
                } else {
                    hideFullScreenReelsOverlay()
                }
            } else {
                hideFullScreenReelsOverlay()
            }

            // Next, check if we are on the home feed, find reels carousels/trays/posts to cover individually
            if (checkIsOnHomeFeed(rootNode)) {
                val rects = mutableListOf<android.graphics.Rect>()
                val trays = mutableListOf<AccessibilityNodeInfo>()
                findNodesByCriteria(rootNode, trays) {
                    val id = (it.viewIdResourceName ?: "").lowercase()
                    val text = (it.text?.toString() ?: "").lowercase()
                    val desc = (it.contentDescription?.toString() ?: "").lowercase()
                    
                    id.contains("reels_tray") || id.contains("reels_carousel") || 
                    id.contains("suggested_reels") || id.contains("clips_layout") ||
                    id.contains("reels_video") || id.contains("reel_post") ||
                    text.contains("reels for you") || text.contains("suggested reels") ||
                    desc.contains("reels video") || desc.contains("reels post") ||
                    (text.contains("reels") && !desc.contains("tab") && !id.contains("tab"))
                }
                for (tray in trays) {
                    val rect = android.graphics.Rect()
                    tray.getBoundsInScreen(rect)
                    if (rect.width() > 0 && rect.height() > 0) {
                        rects.add(rect)
                    }
                }
                updateReelsOverlays(rects)
            } else {
                clearAllOverlays()
            }
        } else {
            hideFullScreenReelsOverlay()
            clearAllOverlays()
        }

        // Feed Scroll limiting
        if (AppBlockHelper.isIgFeedScrollLimit(context)) {
            if (event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED && checkIsViewingFeed(rootNode)) {
                val now = System.currentTimeMillis()
                if (now - lastFeedScrollTime > 1500) { // Limit scroll events to once per 1.5 seconds
                    scrollCountOnFeed++
                    lastFeedScrollTime = now
                    Log.d("InstagramBlocker", "Feed scroll count: $scrollCountOnFeed / 5")
                    if (scrollCountOnFeed >= 5) {
                        showToast("Feed scroll quota exceeded! Closing Instagram... 🛑")
                        performGlobalAction(GLOBAL_ACTION_BACK)
                        scrollCountOnFeed = 0
                    }
                }
            }
        }
    }

    private fun checkInDirectChat(node: AccessibilityNodeInfo): Boolean {
        // Find text or class names representing Direct Message chats
        val list = mutableListOf<AccessibilityNodeInfo>()
        findNodesByCriteria(node, list) {
            val txt = it.text?.toString() ?: ""
            val desc = it.contentDescription?.toString() ?: ""
            val id = it.viewIdResourceName ?: ""
            id.contains("direct") || id.contains("message") || id.contains("thread") ||
            txt.contains("Message...") || txt.contains("Active now") || desc.contains("Chat")
        }
        return list.isNotEmpty()
    }

    private fun checkIsViewingStory(node: AccessibilityNodeInfo): Boolean {
        val list = mutableListOf<AccessibilityNodeInfo>()
        findNodesByCriteria(node, list) {
            val id = it.viewIdResourceName ?: ""
            val desc = it.contentDescription?.toString() ?: ""
            id.contains("reel_viewer") || id.contains("story_viewer") || 
            id.contains("viewer_container") || desc.contains("Story") || desc.contains("Stories")
        }
        return list.isNotEmpty()
    }

    private fun checkIsViewingExplore(node: AccessibilityNodeInfo): Boolean {
        val list = mutableListOf<AccessibilityNodeInfo>()
        findNodesByCriteria(node, list) {
            val id = it.viewIdResourceName ?: ""
            val desc = it.contentDescription?.toString() ?: ""
            val txt = it.text?.toString() ?: ""
            id.contains("search_explore") || id.contains("explore_tab") || 
            desc.contains("Search and Explore") || txt.contains("Explore")
        }
        return list.isNotEmpty()
    }

    private fun isHomeTabSelected(node: AccessibilityNodeInfo): Boolean {
        val list = mutableListOf<AccessibilityNodeInfo>()
        findNodesByCriteria(node, list) {
            val desc = (it.contentDescription?.toString() ?: "").lowercase()
            val id = (it.viewIdResourceName ?: "").lowercase()
            (desc.contains("home") || id.contains("home_tab") || id.contains("feed_tab") || desc.contains("feed")) && 
            (desc.contains("tab") || it.isClickable)
        }
        for (tab in list) {
            if (tab.isSelected) {
                return true
            }
        }
        return false
    }

    private fun isNonReelsTabSelected(node: AccessibilityNodeInfo): Boolean {
        val tabs = mutableListOf<AccessibilityNodeInfo>()
        findNodesByCriteria(node, tabs) {
            val desc = (it.contentDescription?.toString() ?: "").lowercase()
            val id = (it.viewIdResourceName ?: "").lowercase()
            (desc.contains("tab") || id.contains("tab") || it.isClickable) &&
            (desc.contains("home") || desc.contains("search") || desc.contains("explore") || 
             desc.contains("create") || desc.contains("post") || desc.contains("profile") ||
             desc.contains("shop") || id.contains("home") || id.contains("search") || 
             id.contains("explore") || id.contains("create") || id.contains("post") || 
             id.contains("profile") || id.contains("shop"))
        }
        for (tab in tabs) {
            if (tab.isSelected) {
                Log.d("InstagramBlocker", "Non-reels tab selected: ${tab.contentDescription ?: tab.viewIdResourceName}")
                return true
            }
        }
        return false
    }

    private fun isReelsTabSelected(node: AccessibilityNodeInfo): Boolean {
        val list = mutableListOf<AccessibilityNodeInfo>()
        findNodesByCriteria(node, list) {
            val desc = (it.contentDescription?.toString() ?: "").lowercase()
            val id = (it.viewIdResourceName ?: "").lowercase()
            (desc.contains("reels") || id.contains("reels_tab") || id.contains("clips_tab") || desc.contains("clips")) && 
            (desc.contains("tab") || it.isClickable)
        }
        for (tab in list) {
            if (tab.isSelected) {
                return true
            }
        }
        return false
    }

    private fun updateReelsOverlays(detectedRects: List<android.graphics.Rect>) {
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        windowManager = wm

        // 1. Remove any overlays that are no longer needed
        while (activeOverlays.size > detectedRects.size) {
            val removed = activeOverlays.removeAt(activeOverlays.lastIndex)
            try {
                wm.removeView(removed.view)
                Log.d("InstagramBlocker", "Removed an old Reels overlay")
            } catch (e: Exception) {
                Log.e("InstagramBlocker", "Error removing Reels overlay", e)
            }
        }

        // 2. Add new overlays if we have more detected rects than active overlays
        while (activeOverlays.size < detectedRects.size) {
            val context = this
            // Create a custom layout for the overlay
            val overlayLayout = FrameLayout(context).apply {
                setBackgroundColor(0xFF000000.toInt()) // Pure black cover-up display
            }

            // Add a nice centered layout with text stating "Reels Blocked"
            val textContainer = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(16, 16, 16, 16)
            }

            val textView = TextView(context).apply {
                text = "Reels Blocked"
                setTextColor(0xFF00ADB5.toInt()) // Accent blue
                textSize = 15f
                gravity = Gravity.CENTER
                setTypeface(android.graphics.Typeface.DEFAULT_BOLD)
            }

            val subTextView = TextView(context).apply {
                text = "Stay focused! 🎯"
                setTextColor(0xFFCCCCCC.toInt())
                textSize = 11f
                gravity = Gravity.CENTER
                setPadding(0, 4, 0, 0)
            }

            textContainer.addView(textView)
            textContainer.addView(subTextView)

            val frameParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.CENTER
            }
            overlayLayout.addView(textContainer, frameParams)

            val lp = WindowManager.LayoutParams().apply {
                type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
                flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or 
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                format = PixelFormat.TRANSLUCENT
                gravity = Gravity.TOP or Gravity.START
            }

            activeOverlays.add(OverlayInfo(overlayLayout, lp, android.graphics.Rect()))
            Log.d("InstagramBlocker", "Created a new Reels overlay")
        }

        // 3. Position and update all active overlays to match the detected rects
        for (i in detectedRects.indices) {
            val rect = detectedRects[i]
            val overlayInfo = activeOverlays[i]
            
            // Only update if the rect has changed significantly to avoid layout stutter
            if (overlayInfo.currentRect != rect) {
                overlayInfo.currentRect = android.graphics.Rect(rect)
                
                overlayInfo.layoutParams.x = rect.left
                overlayInfo.layoutParams.y = rect.top
                overlayInfo.layoutParams.width = rect.width()
                overlayInfo.layoutParams.height = rect.height()

                // If the view has not been added yet, add it!
                if (overlayInfo.view.parent == null) {
                    try {
                        wm.addView(overlayInfo.view, overlayInfo.layoutParams)
                    } catch (e: Exception) {
                        Log.e("InstagramBlocker", "Error adding Reels overlay view to WindowManager", e)
                    }
                } else {
                    try {
                        wm.updateViewLayout(overlayInfo.view, overlayInfo.layoutParams)
                    } catch (e: Exception) {
                        Log.e("InstagramBlocker", "Error updating Reels overlay layout", e)
                    }
                }
            }
        }
    }

    private fun clearAllOverlays() {
        val wm = windowManager ?: getSystemService(Context.WINDOW_SERVICE) as WindowManager
        for (overlay in activeOverlays) {
            try {
                wm.removeView(overlay.view)
            } catch (e: Exception) {
                // Ignore
            }
        }
        activeOverlays.clear()
    }

    private fun showFullScreenReelsOverlay() {
        if (fullScreenOverlayView != null) return
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        windowManager = wm

        val context = this
        val rootLayout = FrameLayout(context).apply {
            setBackgroundColor(0xFF000000.toInt()) // Pure black
        }

        val contentLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(32, 32, 32, 32)
        }

        val iconTextView = TextView(context).apply {
            text = "🛑"
            textSize = 48f
            gravity = Gravity.CENTER
        }

        val titleView = TextView(context).apply {
            text = "Instagram Reels Blocked"
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 22f
            gravity = Gravity.CENTER
            setTypeface(android.graphics.Typeface.DEFAULT_BOLD)
            setPadding(0, 16, 0, 8)
        }

        val subtitleView = TextView(context).apply {
            text = "Reels are blocked. Stay focused! 🎯"
            setTextColor(0xFFCCCCCC.toInt())
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(0, 8, 0, 24)
        }

        val buttonsContainer = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, 16, 0, 0)
        }

        val closeAppButton = android.widget.Button(context).apply {
            text = "Close App"
            setBackgroundColor(0xFFE23E57.toInt()) // Soft Red / Coral
            setTextColor(0xFFFFFFFF.toInt())
            setPadding(24, 12, 24, 12)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 16, 0)
            }
            setOnClickListener {
                performGlobalAction(GLOBAL_ACTION_HOME)
                hideFullScreenReelsOverlay()
            }
        }

        val goToMessagesButton = android.widget.Button(context).apply {
            text = "Go to Messages"
            setBackgroundColor(0xFF00ADB5.toInt()) // WaterBlue
            setTextColor(0xFFFFFFFF.toInt())
            setPadding(24, 12, 24, 12)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setOnClickListener {
                // Go back once to exit current Reels viewer/screen
                performGlobalAction(GLOBAL_ACTION_BACK)
                
                // Then click the direct inbox/chats button in Instagram if visible
                val root = rootInActiveWindow
                if (root != null) {
                    val list = mutableListOf<AccessibilityNodeInfo>()
                    findNodesByCriteria(root, list) {
                        val id = (it.viewIdResourceName ?: "").lowercase()
                        val desc = (it.contentDescription?.toString() ?: "").lowercase()
                        val txt = (it.text?.toString() ?: "").lowercase()
                        id.contains("action_bar_inbox_button") || id.contains("message_button") || 
                        desc.contains("direct") || desc.contains("message") || desc.contains("messenger") || desc.contains("inbox") ||
                        txt.contains("message") || txt.contains("inbox")
                    }
                    for (node in list) {
                        if (node.isClickable) {
                            node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                            break
                        }
                        var p = node.parent
                        var clicked = false
                        while (p != null) {
                            if (p.isClickable) {
                                p.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                                clicked = true
                                break
                            }
                            p = p.parent
                        }
                        if (clicked) break
                    }
                }
                
                hideFullScreenReelsOverlay()
            }
        }

        buttonsContainer.addView(closeAppButton)
        buttonsContainer.addView(goToMessagesButton)

        contentLayout.addView(iconTextView)
        contentLayout.addView(titleView)
        contentLayout.addView(subtitleView)
        contentLayout.addView(buttonsContainer)

        val frameParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.CENTER
        }
        rootLayout.addView(contentLayout, frameParams)

        val lp = WindowManager.LayoutParams().apply {
            width = WindowManager.LayoutParams.MATCH_PARENT
            height = WindowManager.LayoutParams.MATCH_PARENT
            type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or 
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
            format = PixelFormat.TRANSLUCENT
            gravity = Gravity.CENTER
        }

        try {
            wm.addView(rootLayout, lp)
            fullScreenOverlayView = rootLayout
            Log.d("InstagramBlocker", "Full screen Reels overlay shown.")
        } catch (e: Exception) {
            Log.e("InstagramBlocker", "Failed to add full-screen overlay", e)
        }
    }

    private fun hideFullScreenReelsOverlay() {
        val view = fullScreenOverlayView ?: return
        val wm = windowManager ?: getSystemService(Context.WINDOW_SERVICE) as WindowManager
        try {
            wm.removeView(view)
            Log.d("InstagramBlocker", "Full screen Reels overlay removed.")
        } catch (e: Exception) {
            // Ignore
        } finally {
            fullScreenOverlayView = null
        }
    }

    private fun showFullScreenYtShortsOverlay() {
        if (fullScreenYtShortsOverlayView != null) return
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        windowManager = wm

        val context = this
        val rootLayout = FrameLayout(context).apply {
            setBackgroundColor(0xFF000000.toInt()) // Pure black cover-up display
        }

        val contentLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(32, 32, 32, 32)
        }

        val iconTextView = TextView(context).apply {
            text = "🛑"
            textSize = 48f
            gravity = Gravity.CENTER
        }

        val titleView = TextView(context).apply {
            text = "YouTube Shorts Blocked"
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 22f
            gravity = Gravity.CENTER
            setTypeface(android.graphics.Typeface.DEFAULT_BOLD)
            setPadding(0, 16, 0, 8)
        }

        val subtitleView = TextView(context).apply {
            text = "Shorts are blocked. Stay focused! 🎯"
            setTextColor(0xFFCCCCCC.toInt())
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(0, 8, 0, 24)
        }

        val buttonsContainer = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, 16, 0, 0)
        }

        val closeAppButton = android.widget.Button(context).apply {
            text = "Close App"
            setBackgroundColor(0xFFE23E57.toInt()) // Soft Red / Coral
            setTextColor(0xFFFFFFFF.toInt())
            setPadding(24, 12, 24, 12)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 16, 0)
            }
            setOnClickListener {
                performGlobalAction(GLOBAL_ACTION_HOME)
                hideFullScreenYtShortsOverlay()
            }
        }

        val goToHomeButton = android.widget.Button(context).apply {
            text = "Go to YouTube Home"
            setBackgroundColor(0xFF00ADB5.toInt()) // WaterBlue
            setTextColor(0xFFFFFFFF.toInt())
            setPadding(24, 12, 24, 12)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setOnClickListener {
                // Go back once to exit current Shorts player/screen
                performGlobalAction(GLOBAL_ACTION_BACK)
                
                // Then click the YouTube Home tab if visible
                val root = rootInActiveWindow
                if (root != null) {
                    val list = mutableListOf<AccessibilityNodeInfo>()
                    findNodesByCriteria(root, list) {
                        val id = (it.viewIdResourceName ?: "").lowercase()
                        val desc = (it.contentDescription?.toString() ?: "").lowercase()
                        val txt = (it.text?.toString() ?: "").lowercase()
                        id.contains("home_tab") || desc == "home" || txt == "home"
                    }
                    for (node in list) {
                        if (node.isClickable) {
                            node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                            break
                        }
                        var p = node.parent
                        var clicked = false
                        while (p != null) {
                            if (p.isClickable) {
                                p.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                                clicked = true
                                break
                            }
                            p = p.parent
                        }
                        if (clicked) break
                    }
                }
                
                hideFullScreenYtShortsOverlay()
            }
        }

        buttonsContainer.addView(closeAppButton)
        buttonsContainer.addView(goToHomeButton)

        contentLayout.addView(iconTextView)
        contentLayout.addView(titleView)
        contentLayout.addView(subtitleView)
        contentLayout.addView(buttonsContainer)

        val frameParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.CENTER
        }
        rootLayout.addView(contentLayout, frameParams)

        val lp = WindowManager.LayoutParams().apply {
            width = WindowManager.LayoutParams.MATCH_PARENT
            height = WindowManager.LayoutParams.MATCH_PARENT
            type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or 
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
            format = PixelFormat.TRANSLUCENT
            gravity = Gravity.CENTER
        }

        try {
            wm.addView(rootLayout, lp)
            fullScreenYtShortsOverlayView = rootLayout
            Log.d("YouTubeBlocker", "Full screen YouTube Shorts overlay shown.")
        } catch (e: Exception) {
            Log.e("YouTubeBlocker", "Failed to add full-screen YouTube Shorts overlay", e)
        }
    }

    private fun hideFullScreenYtShortsOverlay() {
        val view = fullScreenYtShortsOverlayView ?: return
        val wm = windowManager ?: getSystemService(Context.WINDOW_SERVICE) as WindowManager
        try {
            wm.removeView(view)
            Log.d("YouTubeBlocker", "Full screen YouTube Shorts overlay removed.")
        } catch (e: Exception) {
            // Ignore
        } finally {
            fullScreenYtShortsOverlayView = null
        }
    }

    override fun onDestroy() {
        clearAllOverlays()
        hideFullScreenReelsOverlay()
        hideFullScreenYtShortsOverlay()
        super.onDestroy()
    }

    private fun checkIsOnHomeFeed(node: AccessibilityNodeInfo): Boolean {
        if (isHomeTabSelected(node) || isNonReelsTabSelected(node)) {
            return true
        }
        
        // Check for stories tray or horizontal scroll list at the top of the feed
        val storiesList = mutableListOf<AccessibilityNodeInfo>()
        findNodesByCriteria(node, storiesList) {
            val id = (it.viewIdResourceName ?: "").lowercase()
            val desc = (it.contentDescription?.toString() ?: "").lowercase()
            val text = (it.text?.toString() ?: "").lowercase()
            
            id.contains("stories_tray") || id.contains("story_tray") || id.contains("stories") ||
            text.contains("stories") || desc.contains("stories") || text.contains("your story") || 
            desc.contains("your story") || (id.contains("tray") && !id.contains("reel_viewer_layout"))
        }
        if (storiesList.isNotEmpty()) {
            return true
        }

        val list = mutableListOf<AccessibilityNodeInfo>()
        findNodesByCriteria(node, list) {
            val id = (it.viewIdResourceName ?: "").lowercase()
            val desc = (it.contentDescription?.toString() ?: "").lowercase()
            val text = (it.text?.toString() ?: "").lowercase()
            
            id.contains("reels_tray") || id.contains("instagram_logo") || 
            text == "instagram" || desc == "instagram" ||
            id.contains("action_bar_home") || id.contains("direct_button") || id.contains("activity_button")
        }
        return list.isNotEmpty()
    }

    private data class ReelsCheckResult(val isReels: Boolean, val identifier: String?)

    private fun checkIsViewingReels(node: AccessibilityNodeInfo): ReelsCheckResult {
        // 1. If we are watching stories, do not block as reels
        if (checkIsViewingStory(node)) {
            Log.d("InstagramBlocker", "Bypassing reels check: user is viewing story.")
            return ReelsCheckResult(false, null)
        }

        // 2. If we are on the Home feed, do not block
        if (checkIsOnHomeFeed(node)) {
            Log.d("InstagramBlocker", "Bypassing reels check: user is on home feed.")
            return ReelsCheckResult(false, null)
        }

        val list = mutableListOf<AccessibilityNodeInfo>()
        findNodesByCriteria(node, list) {
            val id = (it.viewIdResourceName ?: "").lowercase()
            val desc = (it.contentDescription?.toString() ?: "").lowercase()
            val text = (it.text?.toString() ?: "").lowercase()
            
            val isBottomTabOrProfile = id.contains("tab") || id.contains("profile") || id.contains("nav_bar") || id.contains("navigation") || id.contains("bottom_sheet") || id.contains("action_bar")
            
            // Exclude stories-related elements from matchesId to make sure we don't accidentally match story viewer elements
            val isStoryElement = id.contains("tray") || id.contains("story") || id.contains("stories") ||
                                 id.contains("viewer") || id.contains("avatar") || id.contains("ring") ||
                                 id.contains("card") || id.contains("grid") || id.contains("cover")

            val matchesId = (
                id.contains("reels") || id.contains("reel") || 
                id.contains("clips") || id.contains("clip")
            ) && !isBottomTabOrProfile && !isStoryElement
            
            val matchesDescOrText = desc.contains("reels player") || desc.contains("clips player") || 
                                    desc.contains("reels video") || desc.contains("clips video") ||
                                    desc.contains("double tap to like") || desc.contains("double_tap_to_like") ||
                                    desc.contains("swipe up for next") || desc.contains("swipe_up_for_next") ||
                                    text.contains("original audio") || text.contains("original sound") ||
                                    desc.contains("original audio") || desc.contains("original sound") ||
                                    text.contains("watch reels") || text.contains("watch clips") ||
                                    text == "reels" || text == "clips" ||
                                    desc == "reels" || desc == "clips"
            
            matchesId || matchesDescOrText
        }
        
        if (list.isEmpty()) return ReelsCheckResult(false, null)

        // Try to find a unique identifier for the current Reel (like caption or uploader's name)
        val textList = mutableListOf<AccessibilityNodeInfo>()
        findNodesByCriteria(node, textList) {
            val id = (it.viewIdResourceName ?: "").lowercase()
            id.contains("reel_caption") || id.contains("username") || id.contains("caption") || id.contains("clips_viewer_")
        }
        
        val identifier = textList.firstOrNull()?.text?.toString() ?: textList.firstOrNull()?.contentDescription?.toString()
        return ReelsCheckResult(true, identifier)
    }

    private fun checkIsViewingFeed(node: AccessibilityNodeInfo): Boolean {
        val list = mutableListOf<AccessibilityNodeInfo>()
        findNodesByCriteria(node, list) {
            val id = it.viewIdResourceName ?: ""
            id.contains("feed") || id.contains("home") || id.contains("root_container")
        }
        return list.isNotEmpty()
    }

    private fun findNodesByCriteria(
        node: AccessibilityNodeInfo?,
        resultList: MutableList<AccessibilityNodeInfo>,
        predicate: (AccessibilityNodeInfo) -> Boolean
    ) {
        if (node == null) return
        if (predicate(node)) {
            resultList.add(node)
        }
        for (i in 0 until node.childCount) {
            findNodesByCriteria(node.getChild(i), resultList, predicate)
        }
    }

    private var lastToastTime = 0L
    private fun showToast(msg: String) {
        val now = System.currentTimeMillis()
        if (now - lastToastTime > 3000) {
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
            lastToastTime = now
        }
    }

    private fun handleYoutubeBlocking(event: AccessibilityEvent) {
        val context = applicationContext
        
        val useSelective = AppBlockHelper.isYtSelectiveBlockingEnabled(context)
        if (!useSelective) {
            hideFullScreenYtShortsOverlay()
            return
        }

        val rootNode = rootInActiveWindow ?: event.source ?: return

        // Check YouTube Shorts
        if (AppBlockHelper.isYtShortsBlocked(context) && checkIsViewingShorts(rootNode)) {
            showFullScreenYtShortsOverlay()
            return
        } else {
            hideFullScreenYtShortsOverlay()
        }

        // Check YouTube Search
        if (AppBlockHelper.isYtSearchBlocked(context) && checkIsViewingSearch(rootNode)) {
            applyBlockAction("YouTube", "Search")
            return
        }

        // Check YouTube Comments
        if (AppBlockHelper.isYtCommentsBlocked(context) && checkIsViewingComments(rootNode)) {
            applyBlockAction("YouTube", "Comments")
            return
        }

        // Check Approved Whitelisted Channels Restriction
        if (AppBlockHelper.isYtOnlyAllowApprovedChannels(context)) {
            val isWatchScreen = checkIsViewingYoutubeWatch(rootNode)
            if (!isWatchScreen) {
                lastYtWatchDetectedTime = 0L
                return
            }

            val allowedChannels = AppBlockHelper.getYtApprovedChannels(context)
                .split(",")
                .map { it.trim().lowercase() }
                .filter { it.isNotEmpty() }

            if (allowedChannels.isEmpty()) {
                if (lastYtWatchDetectedTime == 0L) {
                    lastYtWatchDetectedTime = System.currentTimeMillis()
                } else if (System.currentTimeMillis() - lastYtWatchDetectedTime > 1500) {
                    applyBlockAction("YouTube", "Channels Setup Required")
                    lastYtWatchDetectedTime = 0L
                }
                return
            }

            val detectedNames = findChannelNamesOnScreen(rootNode).map { it.lowercase() }
            var hasMatch = detectedNames.any { detected ->
                allowedChannels.any { allowed ->
                    detected.contains(allowed) || allowed.contains(detected)
                }
            }

            if (!hasMatch) {
                // Global text scan fallback
                val allScreenTexts = mutableListOf<AccessibilityNodeInfo>()
                findNodesByCriteria(rootNode, allScreenTexts) {
                    it.text != null && it.text.toString().trim().isNotEmpty()
                }
                hasMatch = allScreenTexts.any { node ->
                    val text = node.text.toString().lowercase()
                    allowedChannels.any { allowed ->
                        text.contains(allowed) || allowed.contains(text)
                    }
                }
            }

            if (hasMatch) {
                lastYtWatchDetectedTime = 0L
            } else {
                if (lastYtWatchDetectedTime == 0L) {
                    lastYtWatchDetectedTime = System.currentTimeMillis()
                } else if (System.currentTimeMillis() - lastYtWatchDetectedTime > 1500) {
                    applyBlockAction("YouTube", "Unapproved Channel Blocked")
                    lastYtWatchDetectedTime = 0L
                }
            }
        } else {
            lastYtWatchDetectedTime = 0L
        }
    }

    private fun handleSnapchatBlocking(event: AccessibilityEvent) {
        val context = applicationContext
        
        val useSelective = AppBlockHelper.isSnapSelectiveBlockingEnabled(context)
        if (!useSelective) return

        val rootNode = rootInActiveWindow ?: event.source ?: return

        // Check Spotlight
        if (AppBlockHelper.isSnapSpotlightBlocked(context) && checkIsViewingSnapchatSpotlight(rootNode)) {
            applyBlockAction("Snapchat", "Spotlight")
            return
        }

        // Check Map
        if (AppBlockHelper.isSnapMapBlocked(context) && checkIsViewingSnapchatMap(rootNode)) {
            applyBlockAction("Snapchat", "Map")
            return
        }

        // Check Discover / Stories
        if (AppBlockHelper.isSnapDiscoverBlocked(context) && checkIsViewingSnapchatDiscover(rootNode)) {
            applyBlockAction("Snapchat", "Discover & Stories")
            return
        }
    }

    private fun handleFacebookBlocking(event: AccessibilityEvent) {
        val context = applicationContext
        
        val useSelective = AppBlockHelper.isFbSelectiveBlockingEnabled(context)
        if (!useSelective) return

        val rootNode = rootInActiveWindow ?: event.source ?: return

        // Check Facebook Reels
        if (AppBlockHelper.isFbReelsBlocked(context) && checkIsViewingFbReels(rootNode)) {
            applyBlockAction("Facebook", "Reels")
            return
        }

        // Check Facebook Watch / Video
        if (AppBlockHelper.isFbWatchBlocked(context) && checkIsViewingFbWatch(rootNode)) {
            applyBlockAction("Facebook", "Watch")
            return
        }

        // Check Facebook Stories
        if (AppBlockHelper.isFbStoriesBlocked(context) && checkIsViewingFbStories(rootNode)) {
            applyBlockAction("Facebook", "Stories")
            return
        }
    }

    private fun checkIsViewingShorts(node: AccessibilityNodeInfo): Boolean {
        val list = mutableListOf<AccessibilityNodeInfo>()
        findNodesByCriteria(node, list) {
            val id = (it.viewIdResourceName ?: "").lowercase()
            val desc = (it.contentDescription?.toString() ?: "").lowercase()
            val txt = (it.text?.toString() ?: "").lowercase()
            
            val isTabOrNav = id.contains("tab") || id.contains("navigation") || id.contains("nav_bar") || id.contains("action_bar")
            
            val matchesId = (
                id.contains("shorts") || id.contains("reel_container") || 
                id.contains("reel_player") || id.contains("shorts_player") || 
                id.contains("shorts_overlay")
            ) && !isTabOrNav
            
            val matchesDescOrText = desc.contains("shorts player") || desc.contains("shorts_player") ||
                                    desc.contains("double tap to like") || txt == "shorts" || 
                                    desc == "shorts" || txt.contains("shorts video") || 
                                    desc.contains("shorts video") || desc.contains("swipe up for next")
                                    
            matchesId || matchesDescOrText
        }
        return list.isNotEmpty()
    }

    private fun checkIsViewingSearch(node: AccessibilityNodeInfo): Boolean {
        val list = mutableListOf<AccessibilityNodeInfo>()
        findNodesByCriteria(node, list) {
            val id = it.viewIdResourceName ?: ""
            val txt = it.text?.toString() ?: ""
            id.contains("search_box") || id.contains("search_edit_text") || id.contains("search_btn") || txt.contains("Search")
        }
        return list.isNotEmpty()
    }

    private fun checkIsViewingComments(node: AccessibilityNodeInfo): Boolean {
        val list = mutableListOf<AccessibilityNodeInfo>()
        findNodesByCriteria(node, list) {
            val id = it.viewIdResourceName ?: ""
            val txt = it.text?.toString() ?: ""
            id.contains("comment") || id.contains("comments") || txt.contains("Comments") || txt.contains("Add a comment")
        }
        return list.isNotEmpty()
    }

    private fun checkIsViewingYoutubeWatch(node: AccessibilityNodeInfo): Boolean {
        val list = mutableListOf<AccessibilityNodeInfo>()
        findNodesByCriteria(node, list) {
            val id = it.viewIdResourceName ?: ""
            id.contains("watch_next") || id.contains("watch_panel") || id.contains("player_fragment_container") || 
            id.contains("subscribe_button") || id.contains("video_info_fragment") || id.contains("watch_focus_layout")
        }
        return list.isNotEmpty()
    }

    private fun findChannelNamesOnScreen(node: AccessibilityNodeInfo): List<String> {
        val names = mutableListOf<String>()
        val list = mutableListOf<AccessibilityNodeInfo>()
        
        // Find owner/channel/author nodes
        findNodesByCriteria(node, list) {
            val id = it.viewIdResourceName ?: ""
            id.contains("owner_name") || id.contains("channel_name") || id.contains("owner_title") || 
            id.contains("channel_title") || id.contains("author_text") || id.contains("owner_text") || 
            id.contains("uploader_name") || id.contains("channel_title_view") || id.contains("owner_header")
        }
        for (n in list) {
            n.text?.toString()?.trim()?.let { if (it.isNotEmpty()) names.add(it) }
        }
        
        // Also look near the subscribe button
        val subscribeList = mutableListOf<AccessibilityNodeInfo>()
        findNodesByCriteria(node, subscribeList) {
            val id = it.viewIdResourceName ?: ""
            id.contains("subscribe_button") || id.contains("subscribe")
        }
        for (subNode in subscribeList) {
            var parent = subNode.parent
            for (level in 0..1) {
                if (parent != null) {
                    val childTexts = mutableListOf<AccessibilityNodeInfo>()
                    findNodesByCriteria(parent, childTexts) {
                        it.text != null && it.text.toString().trim().isNotEmpty()
                    }
                    for (ct in childTexts) {
                        ct.text?.toString()?.trim()?.let { 
                            val lower = it.lowercase()
                            if (!lower.contains("subscrib") && !lower.matches(Regex("\\d+.*")) && it.length > 2) {
                                names.add(it)
                            }
                        }
                    }
                    parent = parent.parent
                }
            }
        }
        
        return names.distinct()
    }

    private fun checkIsViewingSnapchatSpotlight(node: AccessibilityNodeInfo): Boolean {
        val list = mutableListOf<AccessibilityNodeInfo>()
        findNodesByCriteria(node, list) {
            val id = (it.viewIdResourceName ?: "").lowercase()
            val desc = (it.contentDescription?.toString() ?: "").lowercase()
            val txt = (it.text?.toString() ?: "").lowercase()
            
            val isTabOrNav = id.contains("tab") || id.contains("navigation") || id.contains("nav_bar") || id.contains("action_bar")
            
            val matchesId = (
                id.contains("spotlight_viewer") || id.contains("spotlight_swipe") ||
                id.contains("spotlight_player") || id.contains("spotlight_fragment") ||
                id.contains("spotlight")
            ) && !isTabOrNav
            
            val matchesDescOrText = desc.contains("spotlight player") || desc.contains("spotlight_player") ||
                                    desc.contains("double tap to like") || txt == "spotlight" ||
                                    desc == "spotlight" || txt.contains("spotlight video")
            
            matchesId || matchesDescOrText
        }
        return list.isNotEmpty()
    }

    private fun checkIsViewingSnapchatMap(node: AccessibilityNodeInfo): Boolean {
        val list = mutableListOf<AccessibilityNodeInfo>()
        findNodesByCriteria(node, list) {
            val id = it.viewIdResourceName ?: ""
            val desc = it.contentDescription?.toString() ?: ""
            val txt = it.text?.toString() ?: ""
            id.contains("snap_map") || id.contains("map_view") || id.contains("map") ||
            desc.contains("Map") || desc.contains("map") || desc.contains("Location") ||
            txt.contains("Map") || txt.contains("map")
        }
        return list.isNotEmpty()
    }

    private fun checkIsViewingSnapchatDiscover(node: AccessibilityNodeInfo): Boolean {
        val list = mutableListOf<AccessibilityNodeInfo>()
        findNodesByCriteria(node, list) {
            val id = it.viewIdResourceName ?: ""
            val desc = it.contentDescription?.toString() ?: ""
            val txt = it.text?.toString() ?: ""
            id.contains("discover") || id.contains("Discover") || id.contains("stories") || id.contains("Stories") ||
            desc.contains("Discover") || desc.contains("discover") || desc.contains("Stories") || desc.contains("stories") ||
            txt.contains("Discover") || txt.contains("discover") || txt.contains("Stories") || txt.contains("stories") ||
            id.contains("sub_feed") || id.contains("news_feed")
        }
        return list.isNotEmpty()
    }

    private fun checkIsViewingFbReels(node: AccessibilityNodeInfo): Boolean {
        val list = mutableListOf<AccessibilityNodeInfo>()
        findNodesByCriteria(node, list) {
            val id = (it.viewIdResourceName ?: "").lowercase()
            val desc = (it.contentDescription?.toString() ?: "").lowercase()
            val txt = (it.text?.toString() ?: "").lowercase()
            
            val isTabOrNav = id.contains("tab") || id.contains("navigation") || id.contains("nav_bar") || id.contains("action_bar")
            
            val matchesId = (
                id.contains("reel_viewer") || id.contains("reels_viewer") || 
                id.contains("short_form") || id.contains("reel_video") ||
                id.contains("fb_shorts") || id.contains("reels_tray")
            ) && !isTabOrNav
            
            val matchesDescOrText = desc.contains("reel player") || desc.contains("reels player") ||
                                    desc.contains("double tap to like") || txt == "reels" || 
                                    desc == "reels" || txt == "reel" || desc == "reel"
                                    
            matchesId || matchesDescOrText
        }
        return list.isNotEmpty()
    }

    private fun checkIsViewingFbWatch(node: AccessibilityNodeInfo): Boolean {
        val list = mutableListOf<AccessibilityNodeInfo>()
        findNodesByCriteria(node, list) {
            val id = it.viewIdResourceName ?: ""
            val desc = it.contentDescription?.toString() ?: ""
            val txt = it.text?.toString() ?: ""
            id.contains("watch") || id.contains("Watch") || id.contains("fb_watch") || id.contains("video_tab") ||
            desc.contains("Watch") || desc.contains("watch") || desc.contains("Video") || desc.contains("video") ||
            txt.contains("Watch") || txt.contains("fb_watch") || txt.contains("video")
        }
        return list.isNotEmpty()
    }

    private fun checkIsViewingFbStories(node: AccessibilityNodeInfo): Boolean {
        val list = mutableListOf<AccessibilityNodeInfo>()
        findNodesByCriteria(node, list) {
            val id = it.viewIdResourceName ?: ""
            val desc = it.contentDescription?.toString() ?: ""
            val txt = it.text?.toString() ?: ""
            id.contains("stories") || id.contains("Stories") || id.contains("story_tray") || id.contains("stories_tray") ||
            desc.contains("Story") || desc.contains("Stories") || desc.contains("story") || desc.contains("stories") ||
            txt.contains("Story") || txt.contains("Stories") || txt.contains("story") || txt.contains("stories")
        }
        return list.isNotEmpty()
    }

    override fun onInterrupt() {
        Log.d("AppBlockAccessibility", "Accessibility service interrupted")
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d("AppBlockAccessibility", "Accessibility service connected")
        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or 
                         AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or 
                         AccessibilityEvent.TYPE_VIEW_SCROLLED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            notificationTimeout = 100
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or 
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or 
                    AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
        }
        this.serviceInfo = info
    }
}
