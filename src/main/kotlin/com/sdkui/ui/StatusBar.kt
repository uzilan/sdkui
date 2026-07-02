package com.sdkui.ui

import com.googlecode.lanterna.gui2.Label
import com.googlecode.lanterna.gui2.Panel

class StatusBar : Panel() {
    private val label = Label(" ")

    init {
        addComponent(label)
    }

    fun setText(text: String) {
        label.text = if (text.isBlank()) " " else " $text"
    }
}
