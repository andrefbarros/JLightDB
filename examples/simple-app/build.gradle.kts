import org.gradle.kotlin.dsl.application
import org.gradle.kotlin.dsl.dependencies

plugins {
    application
}

dependencies {
    implementation(project(":lib"))
}

application {
    mainClass.set("io.jlightdb.examples.Main")
}