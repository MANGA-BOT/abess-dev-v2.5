package com.abess.enspy

import android.widget.LinearLayout

fun LinearLayout.vertical(): LinearLayout {
    orientation = LinearLayout.VERTICAL
    return this
}

fun LinearLayout.horizontal(): LinearLayout {
    orientation = LinearLayout.HORIZONTAL
    return this
}