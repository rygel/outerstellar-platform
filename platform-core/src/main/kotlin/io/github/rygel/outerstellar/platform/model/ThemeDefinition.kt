package io.github.rygel.outerstellar.platform.model

import kotlinx.serialization.Serializable

@Serializable
data class ThemeDefinition(val id: String, val name: String, val type: String, val colors: Map<String, String>)
