plugins {
    kotlin("jvm") version "2.4.0"
    id("io.github.goooler.shadow") version "8.1.8"
    application
}

group = "com.sdkui"
version = "0.1.0"

repositories {
    mavenCentral()
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_25)
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25
}

dependencies {
    implementation("com.googlecode.lanterna:lanterna:3.1.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")

    testImplementation("org.jetbrains.kotlin:kotlin-test:2.4.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
}

application {
    mainClass.set("com.sdkui.MainKt")
}

tasks.shadowJar {
    archiveClassifier.set("")
    archiveBaseName.set("sdkui")
    archiveVersion.set("")
    manifest {
        attributes["Main-Class"] = "com.sdkui.MainKt"
    }
}

tasks.test {
    useJUnitPlatform()
}
