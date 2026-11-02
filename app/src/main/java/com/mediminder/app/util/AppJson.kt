package com.mediminder.app.util

import kotlinx.serialization.json.Json

val AppJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    prettyPrint = true
    coerceInputValues = true
}
