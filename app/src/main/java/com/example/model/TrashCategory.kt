package com.example.model

data class TrashCategory(
    val id: String,
    val name: String,
    val description: String,
    val sizeBytes: Long,
    val sizeStr: String,
    val isSelected: Boolean = true,
    val isCleaned: Boolean = false
)
