package com.autoglm.autoagent.config

/**
 * Timing configuration for Android Agent.
 * 
 * Defines all configurable waiting times used throughout the application.
 * Based on Python implementation in phone_agent/config/timing.py
 */
object TimingConfig {
    
    /**
     * Action timing delays (in milliseconds)
     */
    object Action {
        // Tap action delays
        const val TAP_DELAY = 1000L
        const val DOUBLE_TAP_DELAY = 1000L
        const val LONG_PRESS_DELAY = 1000L
        
        // Swipe/scroll delays
        const val SWIPE_DELAY = 1000L
        
        // Navigation delays
        const val BACK_DELAY = 1000L
        const val HOME_DELAY = 1000L
        const val LAUNCH_DELAY = 1000L
        
        // Wait action
        const val WAIT_DELAY = 1000L
        
        // Text input delays
        const val TYPE_DELAY = 1000L
    }
    
    /**
     * Task execution delays
     */
    object Task {
        // Delay after task completion before stopping agent
        const val FINISH_DELAY = 2000L
        
        // Delay when checking if agent is paused
        const val PAUSE_CHECK_DELAY = 500L
        
        // Delay after network error before stopping
        const val ERROR_DELAY = 3000L
    }
    
    /**
     * Get delay for specific action type
     * @param actionType The type of action (e.g., "Tap", "Swipe", "Back")
     * @return Delay in milliseconds
     */
    fun getActionDelay(actionType: String): Long {
        return when (actionType) {
            "Tap" -> Action.TAP_DELAY
            "Double Tap" -> Action.DOUBLE_TAP_DELAY
            "Long Press" -> Action.LONG_PRESS_DELAY
            "Swipe" -> Action.SWIPE_DELAY
            "Back" -> Action.BACK_DELAY
            "Home" -> Action.HOME_DELAY
            "Launch" -> Action.LAUNCH_DELAY
            "Wait" -> Action.WAIT_DELAY
            "Type", "Type_Name" -> Action.TYPE_DELAY
            else -> 1000L // Default delay
        }
    }
}
