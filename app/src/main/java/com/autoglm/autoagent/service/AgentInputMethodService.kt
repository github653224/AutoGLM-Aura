package com.autoglm.autoagent.service

import android.inputmethodservice.InputMethodService
import android.util.Log
import android.view.inputmethod.InputConnection

class AgentInputMethodService : InputMethodService() {

    companion object {
        var instance: AgentInputMethodService? = null
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        Log.d("AgentIME", "Service Created")
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        Log.d("AgentIME", "Service Destroyed")
    }

    override fun onStartInput(attribute: android.view.inputmethod.EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        Log.d("AgentIME", "onStartInput")
    }

    /**
     * Input text directly using InputConnection
     */
    fun inputText(text: String): Boolean {
        val inputConnection = currentInputConnection
        if (inputConnection != null) {
            val result = inputConnection.commitText(text, 1)
            Log.d("AgentIME", "commitText('$text') result: $result")
            return result
        }
        Log.w("AgentIME", "InputConnection is null")
        return false
    }
}
