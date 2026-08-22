package com.shrekbytes.waqfah.di

import android.content.Context
import androidx.room.Room
import com.shrekbytes.waqfah.data.local.appstate.MonitoredAppDao
import com.shrekbytes.waqfah.data.local.appstate.ReadVerseDao
import com.shrekbytes.waqfah.data.local.appstate.WaqfahAppDatabase
import com.shrekbytes.waqfah.data.local.core.QuranDatabase
import com.shrekbytes.waqfah.data.local.core.SurahDao
import com.shrekbytes.waqfah.data.local.core.VerseDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideQuranDatabase(@ApplicationContext context: Context): QuranDatabase =
        QuranDatabase.build(context)

    @Provides
    fun provideSurahDao(db: QuranDatabase): SurahDao = db.surahDao()

    @Provides
    fun provideVerseDao(db: QuranDatabase): VerseDao = db.verseDao()

    @Provides
    @Singleton
    fun provideWaqfahAppDatabase(@ApplicationContext context: Context): WaqfahAppDatabase =
        // No destructive-migration fallback here on purpose — read_verses is
        // real user progress. Add proper Migration objects if this schema changes.
        Room.databaseBuilder(context, WaqfahAppDatabase::class.java, "waqfah_app.db").build()

    @Provides
    fun provideMonitoredAppDao(db: WaqfahAppDatabase): MonitoredAppDao = db.monitoredAppDao()

    @Provides
    fun provideReadVerseDao(db: WaqfahAppDatabase): ReadVerseDao = db.readVerseDao()
}
