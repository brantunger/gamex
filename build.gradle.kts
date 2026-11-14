import godot.annotation.processor.classgraph.AnnotationProcessingMode
import godot.gradle.GodotLanguage

plugins {
    id("com.utopia-rise.godot-jvm") version "1.0.1"
}

repositories {
    mavenCentral()
    google()
}

godot {
    languages.set(setOf(GodotLanguage.JAVA))
    registration.annotationProcessingMode.set(AnnotationProcessingMode.Inferred)
    toolchain.javaVersion.set(21)
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.14.1"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
    }
}
