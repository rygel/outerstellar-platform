package com.example.outerstellar.starter.extension

import org.http4k.template.ViewModel

data class StarterIndexPage(
    val platformVersion: String,
) : ViewModel {
    override fun template() = "com/example/outerstellar/starter/extension/StarterIndexPage"
}
