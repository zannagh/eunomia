plugins {
    java
    `java-library`
}

// The networking core is deliberately Minecraft-free: plain Java + Gson only. That is what lets
// the Bukkit-family plugin (:paper) depend on it without dragging NMS / loader classes onto its
// classpath, while the loaders (:common) layer their StreamCodec / CustomPacketPayload adapter on
// top. A single artifact covers every game version.
group = "de.zannagh.eunomia"
version = findProperty("semVer")?.toString()?.takeIf { it.isNotEmpty() } ?: "0.0.1-preview.0"

base {
    archivesName.set("eunomia-core")
}

repositories {
    mavenCentral()
}

dependencies {
    // Gson and SLF4J are compileOnly on purpose: every consumer already ships them (Minecraft on the
    // loaders, paper-api on the Paper plugin), and MC pins gson to a strict version - so forcing a
    // version here would clash on the NeoForge classpath. Not declaring a version keeps :core a good
    // citizen on whatever classpath it lands on. Core's own tests pull their own copies below.
    compileOnly("com.google.code.gson:gson:2.11.0")
    compileOnly("org.slf4j:slf4j-api:2.0.16")
    compileOnly("org.jspecify:jspecify:1.0.0")

    testImplementation(platform("org.junit:junit-bom:6.0.1"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("com.google.code.gson:gson:2.11.0")
    testImplementation("org.slf4j:slf4j-api:2.0.16")
    testImplementation("org.jspecify:jspecify:1.0.0")
}

java {
    // Java 17: the lowest baseline across the whole matrix (a 1.20.1 server still runs on 17), so
    // the one core artifact loads everywhere the loaders and Paper do.
    toolchain.languageVersion = JavaLanguageVersion.of(17)
    withSourcesJar()
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}
