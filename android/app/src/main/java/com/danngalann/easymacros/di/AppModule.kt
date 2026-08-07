package com.danngalann.easymacros.di

import com.danngalann.easymacros.BuildConfig
import com.danngalann.easymacros.data.api.AuthApi
import com.danngalann.easymacros.data.api.AuthHeaderInterceptor
import com.danngalann.easymacros.data.api.DashboardApi
import com.danngalann.easymacros.data.api.IngredientApi
import com.danngalann.easymacros.data.api.RefreshAuthenticator
import com.danngalann.easymacros.data.api.RecipeApi
import com.danngalann.easymacros.data.repository.AuthRepository
import com.danngalann.easymacros.data.repository.DashboardRepository
import com.danngalann.easymacros.data.repository.DefaultAuthRepository
import com.danngalann.easymacros.data.repository.DefaultDashboardRepository
import com.danngalann.easymacros.data.repository.EncryptedSessionStore
import com.danngalann.easymacros.data.repository.DefaultRecipeRepository
import com.danngalann.easymacros.data.repository.RecipeRepository
import com.danngalann.easymacros.data.repository.SessionStore
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

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds abstract fun bindSessionStore(store: EncryptedSessionStore): SessionStore
    @Binds abstract fun bindAuthRepository(repository: DefaultAuthRepository): AuthRepository
    @Binds abstract fun bindDashboardRepository(repository: DefaultDashboardRepository): DashboardRepository
    @Binds abstract fun bindRecipeRepository(repository: DefaultRecipeRepository): RecipeRepository
}

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides @Singleton @Named("refresh") fun provideRefreshAuthApi(): AuthApi = Retrofit.Builder()
        .baseUrl(BuildConfig.API_BASE_URL)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(AuthApi::class.java)

    @Provides @Singleton fun provideAuthApi(
        interceptor: AuthHeaderInterceptor,
        authenticator: RefreshAuthenticator,
    ): AuthApi = Retrofit.Builder()
        .baseUrl(BuildConfig.API_BASE_URL)
        .client(OkHttpClient.Builder().addInterceptor(interceptor).authenticator(authenticator).build())
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(AuthApi::class.java)

    @Provides @Singleton fun provideDashboardApi(
        interceptor: AuthHeaderInterceptor,
        authenticator: RefreshAuthenticator,
    ): DashboardApi = Retrofit.Builder()
        .baseUrl(BuildConfig.API_BASE_URL)
        .client(OkHttpClient.Builder().addInterceptor(interceptor).authenticator(authenticator).build())
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

    private fun protectedRetrofit(
        interceptor: AuthHeaderInterceptor,
        authenticator: RefreshAuthenticator,
    ): Retrofit = Retrofit.Builder()
        .baseUrl(BuildConfig.API_BASE_URL)
        .client(OkHttpClient.Builder().addInterceptor(interceptor).authenticator(authenticator).build())
        .addConverterFactory(GsonConverterFactory.create())
        .build()
}