package com.maloy.muzza.ui.utils

fun String.resize(
    width: Int? = null,
    height: Int? = null,
): String {
    if (width == null && height == null) return this
    
    // Mejorar el regex para capturar cualquier tamaño en URLs de Google/YT
    val regex = "([\\s\\S]*)(=w\\d+-h\\d+.*)".toRegex()
    val match = regex.find(this)
    if (match != null) {
        val baseUrl = match.groupValues[1]
        return "$baseUrl=w${width ?: 544}-h${height ?: 544}-p-l90-rj"
    }

    // Para avatares de YouTube
    if (this.contains("yt3.ggpht.com") && this.contains("=s")) {
        return this.substringBeforeLast("=s") + "=s${width ?: 544}-c-k-c0x00ffffff-no-rj"
    }

    // Caso general de reemplazo de dimensiones
    return this.replace(Regex("w\\d+-h\\d+"), "w${width ?: 544}-h${height ?: 544}")
}

fun String.toHighResThumbnail(): String = this.resize(544, 544)
