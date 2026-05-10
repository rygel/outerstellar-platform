package io.github.rygel.outerstellar.platform.security

import java.time.LocalDateTime
import java.time.ZoneOffset

class AdminStatsService(private val userRepository: UserRepository) {
    fun newUsersLast30Days(): Long = userRepository.countUsersSince(LocalDateTime.now(ZoneOffset.UTC).minusDays(30))

    fun totalUsers(): Long = userRepository.countAll()
}
