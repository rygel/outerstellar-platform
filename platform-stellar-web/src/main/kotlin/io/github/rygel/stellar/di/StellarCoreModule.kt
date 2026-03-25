package io.github.rygel.stellar.di

import io.github.rygel.stellar.seed.StellarSeeder
import io.github.rygel.stellar.service.CaffeineStellarCache
import io.github.rygel.stellar.service.CommentService
import io.github.rygel.stellar.service.CommunityService
import io.github.rygel.stellar.service.FeedService
import io.github.rygel.stellar.service.FlairService
import io.github.rygel.stellar.service.ModerationService
import io.github.rygel.stellar.service.PostService
import io.github.rygel.stellar.service.StellarCache
import io.github.rygel.stellar.service.StellarRateLimiter
import io.github.rygel.stellar.service.VoteService
import org.koin.dsl.module

val stellarCoreModule = module {
    single<StellarCache> { CaffeineStellarCache() }
    single { StellarRateLimiter(maxRequests = 10, windowSeconds = 60) }
    single { CommunityService(get(), get(), get(), getOrNull()) }
    single { PostService(get(), get(), getOrNull()) }
    single { CommentService(get(), get(), getOrNull()) }
    single { VoteService(get(), get(), get(), get()) }
    single { FeedService(get(), get(), get(), get(), get(), getOrNull()) }
    single { FlairService(get()) }
    single { ModerationService(get(), get(), get(), get()) }
    single { StellarSeeder(get(), get(), get(), get(), get()) }
}
