package io.github.rygel.outerstellar.platform.web.composition

import io.github.rygel.outerstellar.platform.composition.PlatformPageSet as PageSet

enum class PlatformPageSets(val pageSet: PageSet) {
    HOME(PageSet("home", "Default home page with message list")),
    CONTACTS(PageSet("contacts", "Contact management with trash/restore")),
    SETTINGS(PageSet("settings", "User settings with 6 tabs")),
    SEARCH(PageSet("search", "Full-text search across messages and contacts")),
    NOTIFICATIONS(PageSet("notifications", "Notification list and management")),
    PROFILE(PageSet("profile", "User profile viewing and editing")),
    ADMIN(PageSet("admin", "Admin dashboard, user management, audit log")),
    DEV_DASHBOARD(PageSet("dev-dashboard", "Developer debug dashboard")),
}
