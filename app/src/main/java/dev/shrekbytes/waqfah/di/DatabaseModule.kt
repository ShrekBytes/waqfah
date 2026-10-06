package dev.shrekbytes.waqfah.di

import android.content.Context
import androidx.room.Room
import dev.shrekbytes.waqfah.data.local.appstate.AppStateMigrations
import dev.shrekbytes.waqfah.data.local.appstate.WaqfahAppDatabase
import dev.shrekbytes.waqfah.data.local.core.QuranDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

// Databases only — the graph binds no DAOs. Repositories fetch their DAOs
// from the injected database handle directly (QuranRepository, the appstate
// repositories); no consumer has ever requested a bound one.
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideQuranDatabase(@ApplicationContext context: Context): QuranDatabase =
        QuranDatabase.build(context)

    @Provides
    @Singleton
    fun provideWaqfahAppDatabase(@ApplicationContext context: Context): WaqfahAppDatabase =
        // No destructive-migration fallback — read_verses is real user progress
        // and bookmark_verses is the user's own collection; add proper Migration
        // objects if this schema ever changes.
        Room.databaseBuilder(context, WaqfahAppDatabase::class.java, "waqfah_app.db")
            .addMigrations(AppStateMigrations.MIGRATION_1_2, AppStateMigrations.MIGRATION_2_3)
            .build()
}
