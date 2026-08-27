package com.danngalann.easymacros.di

import com.danngalann.easymacros.BuildConfig
import com.danngalann.easymacros.data.api.AuthApi
import com.danngalann.easymacros.data.api.AuthHeaderInterceptor
import com.danngalann.easymacros.data.api.DashboardApi
import com.danngalann.easymacros.data.api.IngredientApi
import com.danngalann.easymacros.data.api.NetworkFailureLoggingInterceptor
import com.danngalann.easymacros.data.api.RefreshAuthenticator
import com.danngalann.easymacros.data.api.RecipeApi
import com.danngalann.easymacros.data.api.TrackingApi
import com.danngalann.easymacros.data.repository.AuthRepository
import com.danngalann.easymacros.data.repository.DashboardRepository
import com.danngalann.easymacros.data.repository.DefaultAuthRepository
import com.danngalann.easymacros.data.repository.DefaultDashboardRepository
import com.danngalann.easymacros.data.repository.EncryptedSessionStore
import com.danngalann.easymacros.data.repository.DefaultRecipeRepository
import com.danngalann.easymacros.data.repository.RecipeRepository
import com.danngalann.easymacros.data.repository.SessionStore
import com.danngalann.easymacros.data.repository.DefaultTrackingRepository
import com.danngalann.easymacros.data.repository.TrackingRepository
import com.danngalann.easymacros.data.repository.DefaultIngredientRepository
import com.danngalann.easymacros.data.repository.IngredientRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import javax.inject.Named
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds abstract fun bindSessionStore(store: EncryptedSessionStore): SessionStore
    @Binds abstract fun bindAuthRepository(repository: DefaultAuthRepository): AuthRepository
    @Binds abstract fun bindDashboardRepository(repository: DefaultDashboardRepository): DashboardRepository
    @Binds abstract fun bindRecipeRepository(repository: DefaultRecipeRepository): RecipeRepository
    @Binds abstract fun bindTrackingRepository(repository: DefaultTrackingRepository): TrackingRepository
    @Binds abstract fun bindIngredientRepository(repository: DefaultIngredientRepository): IngredientRepository
}

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides @Singleton @Named("refresh") fun provideRefreshAuthApi(): AuthApi = Retrofit.Builder()
        .baseUrl(BuildConfig.API_BASE_URL)
        .client(networkClient())
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(AuthApi::class.java)

    @Provides @Singleton fun provideAuthApi(
        interceptor: AuthHeaderInterceptor,
        authenticator: RefreshAuthenticator,
    ): AuthApi = Retrofit.Builder()
        .baseUrl(BuildConfig.API_BASE_URL)
        .client(networkClient(interceptor, authenticator))
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(AuthApi::class.java)

    @Provides @Singleton fun provideDashboardApi(
        interceptor: AuthHeaderInterceptor,
        authenticator: RefreshAuthenticator,
    ): DashboardApi = Retrofit.Builder()
        .baseUrl(BuildConfig.API_BASE_URL)
        .client(networkClient(interceptor, authenticator))
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(DashboardApi::class.java)

    @Provides @Singleton fun provideRecipeApi(
        interceptor: AuthHeaderInterceptor,
        authenticator: RefreshAuthenticator,
    ): RecipeApi = protectedRetrofit(interceptor, authenticator).create(RecipeApi::class.java)

    @Provides @Singleton fun provideIngredientApi(
        interceptor: AuthHeaderInterceptor,
        authenticator: RefreshAuthenticator,
    ): IngredientApi = protectedRetrofit(interceptor, authenticator).create(IngredientApi::class.java)

    @Provides @Singleton fun provideTrackingApi(
        interceptor: AuthHeaderInterceptor,
        authenticator: RefreshAuthenticator,
    ): TrackingApi = Retrofit.Builder()
        .baseUrl(BuildConfig.API_BASE_URL)
        .client(
            withRationAnalysisTimeout(
                networkClient(interceptor, authenticator),
            ),
        )
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(TrackingApi::class.java)

    private fun protectedRetrofit(
        interceptor: AuthHeaderInterceptor,
        authenticator: RefreshAuthenticator,
    ): Retrofit = Retrofit.Builder()
        .baseUrl(BuildConfig.API_BASE_URL)
        .client(networkClient(interceptor, authenticator))
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    private fun networkClient(
        interceptor: AuthHeaderInterceptor? = null,
        authenticator: RefreshAuthenticator? = null,
    ): OkHttpClient = OkHttpClient.Builder().apply {
        interceptor?.let(::addInterceptor)
        authenticator?.let(::authenticator)
        if (BuildConfig.DEBUG) addInterceptor(NetworkFailureLoggingInterceptor())
    }.build()
}

internal fun withRationAnalysisTimeout(client: OkHttpClient): OkHttpClient =
    client.newBuilder()
        .callTimeout(120, TimeUnit.SECONDS)
        .readTimeout(100, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()