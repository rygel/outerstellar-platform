package io.github.rygel.outerstellar.platform.web

import com.natpryce.hamkrest.Matcher
import com.natpryce.hamkrest.and
import com.natpryce.hamkrest.containsSubstring
import com.natpryce.hamkrest.present
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.hamkrest.hasBody
import org.http4k.hamkrest.hasHeader
import org.http4k.hamkrest.hasStatus

fun hasOkStatus(): Matcher<Response> = hasStatus(Status.OK)

fun bodyContains(text: String): Matcher<Response> = hasBody(containsSubstring(text))

fun hasContentType(type: String): Matcher<Response> =
    hasHeader("content-type", present(containsSubstring(type))) as Matcher<Response>

fun hasLocation(path: String): Matcher<Response> = hasHeader("Location", path) as Matcher<Response>

fun hasRedirect(path: String): Matcher<Response> = (hasStatus(Status.FOUND) as Matcher<Response>).and(hasLocation(path))

fun hasSessionCookie(): Matcher<Response> =
    hasHeader("Set-Cookie", present(containsSubstring("SESSION"))) as Matcher<Response>
