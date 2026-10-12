package iffan.budhi.uts2

import java.net.URI

object StoreMapNavigation {
    fun allowed(url: String): Boolean = runCatching {
        val uri = URI(url)
        uri.scheme == "https" && uri.host in setOf("www.google.com", "maps.google.com", "www.google.co.id", "maps.google.co.id") &&
            (uri.path == "/maps" || uri.path.startsWith("/maps/"))
    }.getOrDefault(false)
}
