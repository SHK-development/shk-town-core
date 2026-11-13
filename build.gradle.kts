plugins {
    id("java")
    id("com.gradleup.shadow") version "9.4.1"
}

group = "kr.shkworld"
version = "1.2"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://jitpack.io")
    maven("https://maven.enginehub.org/repo/")
    maven("https://repo.codemc.io/repository/creatorfromhell/")
}

dependencies {
    implementation("com.zaxxer:HikariCP:7.0.2")
    implementation("com.mysql:mysql-connector-j:9.6.0")
    compileOnly("io.papermc.paper:paper-api:26.3.build.242-stable")
    compileOnly("net.milkbowl.vault:VaultUnlockedAPI:2.20")
    compileOnly("net.luckperms:api:5.5")
    compileOnly("com.sk89q.worldguard:worldguard-bukkit:7.0.20")
    compileOnly("io.github.toxicity188:BetterHud-bukkit-api:2.0.0")

    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("io.papermc.paper:paper-api:26.3.build.242-stable")
}

tasks {
    shadowJar {
        archiveFileName.set("SHKTown.jar")
        destinationDirectory.set(file("../test_server/plugins"))

        relocate("com.zaxxer.hikari", "kr.shkworld.shktown.libs.hikari")
        relocate("com.mysql", "kr.shkworld.shktown.libs.mysql")
    }

    build {
        dependsOn(shadowJar)
    }
}

tasks.test {
    useJUnitPlatform()
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}
