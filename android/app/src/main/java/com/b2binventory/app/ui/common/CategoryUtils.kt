package com.b2binventory.app.ui.common

import com.b2binventory.app.data.remote.CategoryNode

/** Depth-first flattening of the category tree, preserving order. */
fun List<CategoryNode>.flatten(): List<CategoryNode> = flatMap { listOf(it) + it.children.flatten() }
