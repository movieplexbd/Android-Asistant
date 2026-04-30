package com.jarvis.ceotitan.core.di

import android.content.Context
import androidx.room.Room
import com.jarvis.ceotitan.database.JarvisDatabase
import com.jarvis.ceotitan.database.dao.*
import com.jarvis.ceotitan.network.GeminiApiService
import com.jarvis.ceotitan.network.GroqApiService
import com.jarvis.ceotitan.network.OpenRouterApiService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Singleton
    @Provides
    fun provideDatabase(@ApplicationContext context: Context): JarvisDatabase {
        return Room.databaseBuilder(
            context,
            JarvisDatabase::class.java,
            "jarvis_database"
        ).fallbackToDestructiveMigration().build()
    }

    @Provides fun provideCacheDao(db: JarvisDatabase): CacheDao = db.cacheDao()
    @Provides fun provideMemoryDao(db: JarvisDatabase): MemoryDao = db.memoryDao()
    @Provides fun provideInteractionDao(db: JarvisDatabase): InteractionDao = db.interactionDao()
    @Provides fun provideBusinessNoteDao(db: JarvisDatabase): BusinessNoteDao = db.businessNoteDao()
    @Provides fun provideUserCustomDao(db: JarvisDatabase): UserCustomDao = db.userCustomDao()

    @Singleton
    @Provides
    fun provideOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            })
            .build()
    }

    @Singleton
    @Provides
    @Named("gemini")
    fun provideGeminiRetrofit(okHttpClient: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl("https://generativelanguage.googleapis.com/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    @Singleton
    @Provides
    @Named("groq")
    fun provideGroqRetrofit(okHttpClient: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl("https://api.groq.com/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    @Singleton
    @Provides
    @Named("openrouter")
    fun provideOpenRouterRetrofit(okHttpClient: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl("https://openrouter.ai/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    @Singleton
    @Provides
    fun provideGeminiService(@Named("gemini") retrofit: Retrofit): GeminiApiService =
        retrofit.create(GeminiApiService::class.java)

    @Singleton
    @Provides
    fun provideGroqService(@Named("groq") retrofit: Retrofit): GroqApiService =
        retrofit.create(GroqApiService::class.java)

    @Singleton
    @Provides
    fun provideOpenRouterService(@Named("openrouter") retrofit: Retrofit): OpenRouterApiService =
        retrofit.create(OpenRouterApiService::class.java)
}
