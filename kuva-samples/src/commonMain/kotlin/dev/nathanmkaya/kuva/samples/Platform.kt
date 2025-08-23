package dev.nathanmkaya.kuva.samples

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform
