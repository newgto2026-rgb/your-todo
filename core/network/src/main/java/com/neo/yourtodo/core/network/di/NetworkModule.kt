package com.neo.yourtodo.core.network.di

import com.neo.yourtodo.core.network.BuildConfig
import com.neo.yourtodo.core.network.aitodo.AiTodoDraftApi
import com.neo.yourtodo.core.network.aitodo.AiTodoDraftNetworkDataSource
import com.neo.yourtodo.core.network.aitodo.RetrofitAiTodoDraftNetworkDataSource
import com.neo.yourtodo.core.network.assignments.AssignmentApi
import com.neo.yourtodo.core.network.assignments.AssignmentNetworkDataSource
import com.neo.yourtodo.core.network.assignments.RetrofitAssignmentNetworkDataSource
import com.neo.yourtodo.core.network.auth.AuthApi
import com.neo.yourtodo.core.network.auth.AuthNetworkDataSource
import com.neo.yourtodo.core.network.auth.RetrofitAuthNetworkDataSource
import com.neo.yourtodo.core.network.friends.FriendApi
import com.neo.yourtodo.core.network.friends.FriendNetworkDataSource
import com.neo.yourtodo.core.network.friends.RetrofitFriendNetworkDataSource
import com.neo.yourtodo.core.network.push.PushApi
import com.neo.yourtodo.core.network.push.PushNetworkDataSource
import com.neo.yourtodo.core.network.push.RetrofitPushNetworkDataSource
import com.neo.yourtodo.core.network.sync.RetrofitTodoSyncNetworkDataSource
import com.neo.yourtodo.core.network.sync.TodoSyncApi
import com.neo.yourtodo.core.network.sync.TodoSyncNetworkDataSource
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import javax.inject.Qualifier

@Qualifier
@Retention(AnnotationRetention.BINARY)
internal annotation class AiServerRetrofit

@Module
@InstallIn(SingletonComponent::class)
internal object NetworkProvidesModule {
    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = true
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BASIC
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }
        return OkHttpClient.Builder()
            .addInterceptor { chain ->
                chain.proceed(
                    chain.request()
                        .newBuilder()
                        .header("ngrok-skip-browser-warning", "true")
                        .build()
                )
            }
            .addInterceptor(logging)
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(
        json: Json,
        okHttpClient: OkHttpClient
    ): Retrofit =
        Retrofit.Builder()
            .baseUrl(BuildConfig.YOURTODO_SERVER_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

    @Provides
    @Singleton
    @AiServerRetrofit
    fun provideAiServerRetrofit(
        json: Json,
        okHttpClient: OkHttpClient
    ): Retrofit =
        Retrofit.Builder()
            .baseUrl(BuildConfig.YOURTODO_AI_SERVER_BASE_URL)
            .client(
                okHttpClient.newBuilder()
                    .connectTimeout(15, TimeUnit.SECONDS)
                    .readTimeout(60, TimeUnit.SECONDS)
                    .writeTimeout(30, TimeUnit.SECONDS)
                    .callTimeout(75, TimeUnit.SECONDS)
                    .build()
            )
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

    @Provides
    @Singleton
    fun provideAuthApi(retrofit: Retrofit): AuthApi = retrofit.create(AuthApi::class.java)

    @Provides
    @Singleton
    fun provideTodoSyncApi(retrofit: Retrofit): TodoSyncApi = retrofit.create(TodoSyncApi::class.java)

    @Provides
    @Singleton
    fun provideFriendApi(retrofit: Retrofit): FriendApi = retrofit.create(FriendApi::class.java)

    @Provides
    @Singleton
    fun provideAssignmentApi(retrofit: Retrofit): AssignmentApi =
        retrofit.create(AssignmentApi::class.java)

    @Provides
    @Singleton
    fun providePushApi(retrofit: Retrofit): PushApi = retrofit.create(PushApi::class.java)

    @Provides
    @Singleton
    fun provideAiTodoDraftApi(@AiServerRetrofit retrofit: Retrofit): AiTodoDraftApi =
        retrofit.create(AiTodoDraftApi::class.java)
}

@Module
@InstallIn(SingletonComponent::class)
internal abstract class NetworkBindsModule {
    @Binds
    @Singleton
    abstract fun bindAuthNetworkDataSource(
        impl: RetrofitAuthNetworkDataSource
    ): AuthNetworkDataSource

    @Binds
    @Singleton
    abstract fun bindTodoSyncNetworkDataSource(
        impl: RetrofitTodoSyncNetworkDataSource
    ): TodoSyncNetworkDataSource

    @Binds
    @Singleton
    abstract fun bindFriendNetworkDataSource(
        impl: RetrofitFriendNetworkDataSource
    ): FriendNetworkDataSource

    @Binds
    @Singleton
    abstract fun bindAssignmentNetworkDataSource(
        impl: RetrofitAssignmentNetworkDataSource
    ): AssignmentNetworkDataSource

    @Binds
    @Singleton
    abstract fun bindPushNetworkDataSource(
        impl: RetrofitPushNetworkDataSource
    ): PushNetworkDataSource

    @Binds
    @Singleton
    abstract fun bindAiTodoDraftNetworkDataSource(
        impl: RetrofitAiTodoDraftNetworkDataSource
    ): AiTodoDraftNetworkDataSource
}
