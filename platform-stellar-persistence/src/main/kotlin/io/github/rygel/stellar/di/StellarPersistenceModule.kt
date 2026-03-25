package io.github.rygel.stellar.di

import io.github.rygel.stellar.persistence.CommentRepository
import io.github.rygel.stellar.persistence.CommunityMemberRepository
import io.github.rygel.stellar.persistence.CommunityRepository
import io.github.rygel.stellar.persistence.FlairRepository
import io.github.rygel.stellar.persistence.JdbiCommentRepository
import io.github.rygel.stellar.persistence.JdbiCommunityMemberRepository
import io.github.rygel.stellar.persistence.JdbiCommunityRepository
import io.github.rygel.stellar.persistence.JdbiFlairRepository
import io.github.rygel.stellar.persistence.JdbiKarmaRepository
import io.github.rygel.stellar.persistence.JdbiPostRepository
import io.github.rygel.stellar.persistence.JdbiReportRepository
import io.github.rygel.stellar.persistence.JdbiSavedItemRepository
import io.github.rygel.stellar.persistence.JdbiSubscriptionRepository
import io.github.rygel.stellar.persistence.JdbiVoteRepository
import io.github.rygel.stellar.persistence.KarmaRepository
import io.github.rygel.stellar.persistence.PostRepository
import io.github.rygel.stellar.persistence.ReportRepository
import io.github.rygel.stellar.persistence.SavedItemRepository
import io.github.rygel.stellar.persistence.SubscriptionRepository
import io.github.rygel.stellar.persistence.VoteRepository
import org.koin.dsl.module

val stellarPersistenceModule = module {
    single<CommunityRepository> { JdbiCommunityRepository(get()) }
    single<PostRepository> { JdbiPostRepository(get()) }
    single<CommentRepository> { JdbiCommentRepository(get()) }
    single<VoteRepository> { JdbiVoteRepository(get()) }
    single<SubscriptionRepository> { JdbiSubscriptionRepository(get()) }
    single<CommunityMemberRepository> { JdbiCommunityMemberRepository(get()) }
    single<ReportRepository> { JdbiReportRepository(get()) }
    single<SavedItemRepository> { JdbiSavedItemRepository(get()) }
    single<KarmaRepository> { JdbiKarmaRepository(get()) }
    single<FlairRepository> { JdbiFlairRepository(get()) }
}
