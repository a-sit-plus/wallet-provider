pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

include(":server")

val vckDir = file("../vck")
if (vckDir.isDirectory) {
    logger.warn("Detected VC-K in ${vckDir.absolutePath}.")
    logger.warn("Including VC-K and Signum as composite build.")
    logger.warn("If you do not want this, move the VC-K to another location!")
    includeBuild("../vck")
}