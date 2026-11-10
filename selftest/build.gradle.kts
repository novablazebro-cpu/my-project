// Test-only plugin: plays dupe scenarios on a real server against AntiDupe. Never ship it.
plugins { java }

repositories { maven("https://repo.papermc.io/repository/maven-public/") }

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.3.build.248-stable")
    compileOnly(project(":plugin"))
    compileOnly(project(":core"))
}

java { toolchain.languageVersion.set(JavaLanguageVersion.of(25)) }

tasks.withType<JavaCompile> { options.encoding = "UTF-8"; options.release.set(25) }
tasks.jar { archiveBaseName.set("AntiDupeSelfTest") }
