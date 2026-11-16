package com.shashank.applocker.di

import com.shashank.applocker.domain.repository.LockoutRepository
import com.shashank.applocker.domain.repository.PinRepository
import com.shashank.applocker.security.AndroidBiometricKeyStore
import com.shashank.applocker.security.BiometricKeyManager
import com.shashank.applocker.security.BiometricKeyStore
import com.shashank.applocker.security.CalibratedIterationPolicy
import com.shashank.applocker.security.DefaultBiometricKeyManager
import com.shashank.applocker.security.DeviceCredentialAvailability
import com.shashank.applocker.security.ElapsedClock
import com.shashank.applocker.security.IterationPolicy
import com.shashank.applocker.security.KeyguardCredentialAvailability
import com.shashank.applocker.security.KeystoreSecretStore
import com.shashank.applocker.security.Pbkdf2PinHasher
import com.shashank.applocker.security.PinHasher
import com.shashank.applocker.security.SecretStore
import com.shashank.applocker.security.SecureRandomTokenGenerator
import com.shashank.applocker.security.TokenGenerator
import com.shashank.applocker.security.SecureLockoutRepository
import com.shashank.applocker.security.SecurePinRepository
import com.shashank.applocker.security.SystemElapsedClock
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.security.SecureRandom
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DefaultDispatcher

@Module
@InstallIn(SingletonComponent::class)
abstract class SecurityBindingsModule {
    @Binds abstract fun bindSecretStore(impl: KeystoreSecretStore): SecretStore
    @Binds abstract fun bindPinHasher(impl: Pbkdf2PinHasher): PinHasher
    @Binds abstract fun bindIterationPolicy(impl: CalibratedIterationPolicy): IterationPolicy
    @Binds abstract fun bindPinRepository(impl: SecurePinRepository): PinRepository
    @Binds abstract fun bindLockoutRepository(impl: SecureLockoutRepository): LockoutRepository
    @Binds abstract fun bindBiometricKeyStore(impl: AndroidBiometricKeyStore): BiometricKeyStore
    @Binds abstract fun bindBiometricKeyManager(impl: DefaultBiometricKeyManager): BiometricKeyManager
    @Binds abstract fun bindTokenGenerator(impl: SecureRandomTokenGenerator): TokenGenerator
    @Binds abstract fun bindElapsedClock(impl: SystemElapsedClock): ElapsedClock
    @Binds abstract fun bindCredentialAvailability(impl: KeyguardCredentialAvailability): DeviceCredentialAvailability
}

@Module
@InstallIn(SingletonComponent::class)
object SecurityProvidesModule {
    @Provides @Singleton fun provideSecureRandom(): SecureRandom = SecureRandom()

    @Provides @DefaultDispatcher fun provideDefaultDispatcher(): CoroutineDispatcher = Dispatchers.Default
}
