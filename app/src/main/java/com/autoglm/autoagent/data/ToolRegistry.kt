package com.autoglm.autoagent.data

import com.google.gson.JsonObject
import com.google.gson.JsonParser

object ToolRegistry {
    val tools: List<JsonObject> = listOf(
        // Tool: plan_task
        JsonParser.parseString("""
        {
            "type": "function",
            "function": {
                "name": "plan_task",
                "description": "Decompose the user goal into a step-by-step plan.",
                "parameters": {
                    "type": "object",
                    "properties": {
                        "steps": {
                            "type": "array",
                            "items": { "type": "string" },
                            "description": "List of steps to execute."
                        }
                    },
                    "required": ["steps"]
                }
            }
        }
        """).asJsonObject,

        // Tool: click_element
        JsonParser.parseString("""
        {
            "type": "function",
            "function": {
                "name": "click_element",
                "description": "Click on a UI element at specific coordinates.",
                "parameters": {
                    "type": "object",
                    "properties": {
                        "x": { "type": "integer", "description": "X coordinate" },
                        "y": { "type": "integer", "description": "Y coordinate" },
                        "description": { "type": "string", "description": "Description of the element being clicked" }
                    },
                    "required": ["x", "y"]
                }
            }
        }
        """).asJsonObject,
        
        // Tool: launch_app
        JsonParser.parseString("""
        {
            "type": "function",
            "function": {
                "name": "launch_app",
                "description": "Launch an Android application by name.",
                "parameters": {
                    "type": "object",
                    "properties": {
                        "app_name": { "type": "string", "description": "Name of the app (e.g. 'WeChat', 'Settings')" }
                    },
                    "required": ["app_name"]
                }
            }
        }
        """).asJsonObject,

        // Tool: finish_task
        JsonParser.parseString("""
        {
            "type": "function",
            "function": {
                "name": "finish_task",
                "description": "Signal that the task is completed.",
                "parameters": {
                    "type": "object",
                    "properties": {
                        "summary": { "type": "string", "description": "Summary of what was done." }
                    },
                    "required": []
                }
            }
        }
        """).asJsonObject
    )
}
