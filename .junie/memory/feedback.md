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

