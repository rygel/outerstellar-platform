package dev.outerstellar.starter.model

data class ThemeDefinition(
  val id: String,
  val name: String,
  val type: String,
  val colors: Map<String, String>
)
