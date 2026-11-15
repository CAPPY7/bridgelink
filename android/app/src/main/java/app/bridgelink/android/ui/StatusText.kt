package app.bridgelink.android.ui

/** Plain words for the home screen. No Android types, so the unit test can run it. */
object StatusText {
    fun dot(status: String): String {
        val text = status.trim()
        if (text.startsWith("Paired")) return "on"
        if (text.isEmpty() || text.startsWith("Not connected") || text.startsWith("Forgot")) return "off"
        return "wait"
    }

    fun title(status: String): String {
        val prefix = "Paired with "
        return if (status.startsWith(prefix)) status.removePrefix(prefix) else "Not connected"
    }

    /** Newest matching line wins. History is stored newest first. */
    fun macBattery(lines: List<String>): String {
        val line = lines.firstOrNull { it.startsWith("Mac battery ") } ?: return "—"
        return line.removePrefix("Mac battery ").trim().ifEmpty { "—" }
    }

    fun phoneBattery(level: Int, charging: Boolean): String {
        if (level < 0 || level > 100) return "—"
        return if (charging) "$level% charging" else "$level%"
    }
}
