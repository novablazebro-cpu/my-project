allprojects {
    group = "dev.novablaze.antidupe"
    version = "0.1.0"
    repositories {
        // Google's copy of Maven Central first: the main host rate-limits busy build machines.
        maven("https://maven-central.storage-download.googleapis.com/maven2/")
        mavenCentral()
    }
}
