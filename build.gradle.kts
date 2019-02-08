import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.3.21"
}

group = "clockvapor"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven("https://jitpack.io")
}

dependencies {
    val jacksonVersion = property("jackson.version") as String

    implementation(kotlin("stdlib-jdk8"))
    compile("com.fasterxml.jackson.core", "jackson-core", jacksonVersion)
    compile("com.fasterxml.jackson.core", "jackson-databind", jacksonVersion)
    compile("com.xenomachina", "kotlin-argparser", "2.0.7")
    compile("io.github.seik", "kotlin-telegram-bot", "0.3.5") {
        exclude("io.github.seik.kotlin-telegram-bot", "echo")
        exclude("io.github.seik.kotlin-telegram-bot", "dispatcher")
    }
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
}

task<Jar>("fatJar") {
    manifest {
        attributes(mapOf("Main-Class" to "clockvapor.telegram.eightball.EightBallTelegramBot"))
    }
    from(configurations.runtimeClasspath
        .filter { it.exists() }
        .map { if (it.isDirectory) it else zipTree(it) }
    )
    with(tasks["jar"] as CopySpec)
}

task("stage") {
    dependsOn("clean", "fatJar")
}
