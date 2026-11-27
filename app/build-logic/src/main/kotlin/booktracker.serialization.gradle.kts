// kotlinx.serialization for a module. A plugin from build-logic's classpath can only be applied
// through a convention plugin, not by its id in a module's build file.
plugins {
    id("org.jetbrains.kotlin.plugin.serialization")
}
