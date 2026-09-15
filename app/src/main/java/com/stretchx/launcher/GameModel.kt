package com.stretchx.launcher

import android.graphics.drawable.Drawable

data class GameModel(
    val name: String,
    val packageName: String,
    val icon: Drawable,
    var isSelected: Boolean = false
)
