plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "1.9.22"
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = "org.zoobastiks.ztelegram"
version = "1.0"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://oss.sonatype.org/content/repositories/snapshots/")
    maven("https://jitpack.io")
    maven("https://repo.extendedclip.com/content/repositories/placeholderapi/")
}

dependencies {
    implementation("org.telegram:telegrambots:6.8.0")
    compileOnly("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")
    implementation("org.jetbrains.kotlin:kotlin-stdlib:1.9.22")
    implementation("net.kyori:adventure-api:4.15.0")
    implementation("net.kyori:adventure-text-minimessage:4.15.0")
    implementation("net.kyori:adventure-text-serializer-legacy:4.15.0")
    compileOnly("com.github.MilkBowl:VaultAPI:1.7") {
        exclude(group = "org.bukkit", module = "bukkit")
    }
    compileOnly("me.clip:placeholderapi:2.11.5")
}

tasks {
    shadowJar {
        archiveClassifier.set("")
        archiveBaseName.set("Ztelegram")
        mergeServiceFiles()
    }

    build {
        dependsOn(shadowJar)
    }

    compileKotlin {
        kotlinOptions.jvmTarget = "21"
    }

    compileTestKotlin {
        kotlinOptions.jvmTarget = "21"
    }
}