plugins { java }

repositories {
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.3.build.248-stable")
    implementation(project(":core"))
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.release.set(25)
    options.compilerArgs.addAll(listOf("-Xlint:deprecation", "-Xlint:unchecked"))
}

tasks.processResources {
    inputs.property("version", project.version)
    filesMatching("plugin.yml") { expand("version" to project.version) }
}

tasks.jar {
    archiveBaseName.set("AntiDupe")
    // Put the core classes inside the plugin jar. sqlite-jdbc is not bundled: Paper ships it.
    from(project(":core").sourceSets.main.get().output)
    // Public API only (no server internals), so Paper can skip remapping on startup.
    manifest { attributes("paperweight-mappings-namespace" to "mojang") }
}
