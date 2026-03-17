package vegabobo.dsusideloader.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import vegabobo.dsusideloader.core.MaxRegnerCore
import vegabobo.dsusideloader.core.SystemImagePorter
import vegabobo.dsusideloader.util.PrivilegeManager

@Module
@InstallIn(SingletonComponent::class)
object MaxRegnerModule {

    @Provides
    @Singleton
    fun provideMaxRegnerCore(): MaxRegnerCore {
        return MaxRegnerCore()
    }

    @Provides
    @Singleton
    fun provideSystemImagePorter(): SystemImagePorter {
        return SystemImagePorter()
    }

    @Provides
    @Singleton
    fun providePrivilegeManager(): PrivilegeManager {
        return PrivilegeManager()
    }
}
