package iffan.budhi.uts2

internal object FirebaseMessageGuard {
    fun orderCode(data: Map<String, String>, uid: String, server: String): String? {
        if (data["uid"] != uid || data["server"] != server) return null
        if (!Regex("^[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}$").matches(data["event_id"].orEmpty())) return null
        return data["code"]?.takeIf { Regex("^TF-[A-Z0-9]{12}$").matches(it) }
    }
}
