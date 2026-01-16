plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "1.9.22"
    id("com.github.johnrengelman.shadow") version "8.1.1"
}


java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

group = "org.zoobastiks.ztelegram"
version = "1.7"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://oss.sonatype.org/content/repositories/snapshots/")
    maven("https://jitpack.io")
    maven("https://repo.extendedclip.com/content/repositories/placeholderapi/")
}

dependencies {
    implementation("org.telegram:telegrambots:6.9.7.1")
    compileOnly("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT") // Совместимо с 1.21.11 (Paper API обратно совместим)
    implementation("org.jetbrains.kotlin:kotlin-stdlib:1.9.22")
    implementation("net.kyori:adventure-api:4.17.0")
    implementation("net.kyori:adventure-text-minimessage:4.17.0")
    implementation("net.kyori:adventure-text-serializer-legacy:4.17.0")
    // SQLite для универсальной базы данных
    implementation("org.xerial:sqlite-jdbc:3.44.1.0")
    // bStats для статистики использования плагина
    implementation("org.bstats:bstats-bukkit:3.1.0")
    compileOnly("com.github.MilkBowl:VaultAPI:1.7") {
        exclude(group = "org.bukkit", module = "bukkit")
    }
    compileOnly("me.clip:placeholderapi:2.11.6")
}

tasks {
    shadowJar {
        archiveClassifier.set("")
        archiveBaseName.set("Ztelegram")
        mergeServiceFiles()
        
        // Релокация bstats не требуется - библиотека не конфликтует с другими плагинами
        // Если нужна релокация, можно добавить: relocate("org.bstats", "${project.group}.bstats")
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