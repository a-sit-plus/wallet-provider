package at.asitplus.walletprovider.injection

import kotlin.reflect.KClass
import kotlin.reflect.full.primaryConstructor

/**
 * Code generated with Copilot AI
 */
object DependencyInjector {
    val singletonProviders = mutableMapOf<KClass<*>, () -> Any>()
    val factoryProviders = mutableMapOf<KClass<*>, () -> Any>()
    private val singletons = mutableMapOf<KClass<*>, Any>()

    inline fun <reified T : Any> single(noinline provider: () -> T) {
        singletonProviders[T::class] = provider
    }

    inline fun <reified T : Any> factory(noinline provider: () -> T) {
        factoryProviders[T::class] = provider
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> resolve(kClass: KClass<T>): T {
        singletons[kClass]?.let { return it as T }

        singletonProviders[kClass]?.let { provider ->
            val instance = provider() as T
            singletons[kClass] = instance
            return instance
        }

        factoryProviders[kClass]?.let { provider ->
            return provider() as T
        }

        return construct(kClass)
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T : Any> construct(kClass: KClass<T>): T {
        val constructor = kClass.primaryConstructor
            ?: error("Class ${kClass.simpleName} has no primary constructor")

        val params = constructor.parameters.map { param ->
            val type = param.type.classifier as? KClass<*>
                ?: error("Unsupported parameter type: ${param.type}")

            resolve(type)
        }

        return constructor.call(*params.toTypedArray())
    }
}

inline fun <reified T : Any> inject(): T =
    DependencyInjector.resolve(T::class)


