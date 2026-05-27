package io.github.rygel.outerstellar.platform.composition

data class PlatformPageSet(val id: String, val description: String) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PlatformPageSet) return false
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
}
