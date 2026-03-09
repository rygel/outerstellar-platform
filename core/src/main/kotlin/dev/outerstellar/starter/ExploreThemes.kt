package dev.outerstellar.starter

import com.outerstellar.theme.ThemeService

fun main() {
    val service = ThemeService.create()
    println(service.javaClass.methods.map { it.name })
}