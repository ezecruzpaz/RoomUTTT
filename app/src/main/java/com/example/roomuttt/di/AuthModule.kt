package com.example.roomuttt.di

import com.example.roomuttt.data.remote.AuthRemoteDataSource
import com.example.roomuttt.data.repository.AuthRepository
import com.example.roomuttt.domain.usecase.LoginWithEmailPasswordUseCase
import com.example.roomuttt.domain.usecase.LoginWithGoogleUseCase
import com.google.firebase.auth.FirebaseAuth
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import dagger.hilt.android.scopes.ViewModelScoped

@Module
@InstallIn(ViewModelComponent::class)
object AuthModule {
    @Provides
    @ViewModelScoped
    fun provideAuthRemoteDataSource(auth: FirebaseAuth): AuthRemoteDataSource = AuthRemoteDataSource(auth)

    @Provides
    @ViewModelScoped
    fun provideAuthRepository(remote: AuthRemoteDataSource): AuthRepository = AuthRepository(remote)

    @Provides
    @ViewModelScoped
    fun provideLoginWithGoogleUseCase(repo: AuthRepository): LoginWithGoogleUseCase = LoginWithGoogleUseCase(repo)

    @Provides
    @ViewModelScoped
    fun provideLoginWithEmailPasswordUseCase(repo: AuthRepository): LoginWithEmailPasswordUseCase = LoginWithEmailPasswordUseCase(repo)
}