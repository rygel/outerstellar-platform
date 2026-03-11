[2026-03-09 21:54] - Updated by Junie
{
    "TYPE": "preference",
    "CATEGORY": "Scrolling preference",
    "EXPECTATION": "User wants classic pagination/scrolling and explicitly does not want infinite scrolling.",
    "NEW INSTRUCTION": "WHEN designing or modifying list loading UX THEN use classic pagination and avoid infinite scroll"
}

[2026-03-10 07:00] - Updated by Junie
{
    "TYPE": "negative",
    "CATEGORY": "Bug report - NPE",
    "EXPECTATION": "The application should not throw a NullPointerException in JooqMessageRepository.listMessages; repository calls must be null-safe.",
    "NEW INSTRUCTION": "WHEN implementing or modifying listMessages in JooqMessageRepository THEN handle nullable fields safely and add a failing test"
}

[2026-03-10 07:27] - Updated by Junie
{
    "TYPE": "negative",
    "CATEGORY": "Bug report - ClassCastException",
    "EXPECTATION": "The /auth page should render without error and receive a proper ViewModel, not an http4k Request.",
    "NEW INSTRUCTION": "WHEN rendering JTE templates in web routes THEN pass a correct ViewModel, never the Request"
}

[2026-03-10 07:45] - Updated by Junie
{
    "TYPE": "negative",
    "CATEGORY": "Bug report - /auth ClassCastException",
    "EXPECTATION": "The /auth page should render without error and receive a proper ViewModel derived from request context, not an http4k Request.",
    "NEW INSTRUCTION": "WHEN handling /auth route THEN pass AuthViewModel to JTE template, never the Request"
}

[2026-03-11 08:43] - Updated by Junie
{
    "TYPE": "correction",
    "CATEGORY": "Swing tests headless",
    "EXPECTATION": "Swing UI tests must run fully headless and never momentarily display or capture input.",
    "NEW INSTRUCTION": "WHEN running or modifying Swing UI tests THEN set java.awt.headless=true before any UI creation; never display UI"
}

[2026-03-11 08:44] - Updated by Junie
{
    "TYPE": "correction",
    "CATEGORY": "Swing tests headless enforcement",
    "EXPECTATION": "Swing UI tests must run fully headless, never capture input, and must not be executed even once before headless mode is enabled.",
    "NEW INSTRUCTION": "WHEN about to run Swing UI tests THEN verify java.awt.headless=true and avoid any non-headless run"
}

[2026-03-11 08:45] - Updated by Junie
{
    "TYPE": "correction",
    "CATEGORY": "Swing tests headless",
    "EXPECTATION": "Swing UI tests must run fully headless and must not execute even once before headless mode is enabled; they must never capture mouse/keyboard input.",
    "NEW INSTRUCTION": "WHEN running or configuring Swing UI tests THEN set -Djava.awt.headless=true before any UI creation and fail otherwise"
}

[2026-03-11 08:47] - Updated by Junie
{
    "TYPE": "correction",
    "CATEGORY": "Swing tests headless enforcement",
    "EXPECTATION": "Swing UI tests must be fully headless and must not execute even once before headless mode is enabled; they must never capture mouse/keyboard input.",
    "NEW INSTRUCTION": "WHEN running or configuring Swing UI tests THEN enforce headless before any UI creation; abort otherwise"
}

[2026-03-11 08:47] - Updated by Junie
{
    "TYPE": "correction",
    "CATEGORY": "Swing tests headless enforcement",
    "EXPECTATION": "Make Swing UI tests fully headless; never execute even once before headless is enabled; avoid capturing mouse cursor.",
    "NEW INSTRUCTION": "WHEN running or modifying Swing UI tests THEN enforce headless before any UI creation; abort otherwise"
}

[2026-03-11 08:49] - Updated by Junie
{
    "TYPE": "correction",
    "CATEGORY": "Swing headless enforcement",
    "EXPECTATION": "Swing UI tests must be fully headless, must not run even once before headless is enabled, and must not capture mouse/keyboard input.",
    "NEW INSTRUCTION": "WHEN executing or configuring Swing UI tests THEN set -Djava.awt.headless=true before any UI; abort otherwise"
}

