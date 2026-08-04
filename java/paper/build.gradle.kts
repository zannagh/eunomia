plugins {
    java
    id("com.gradleup.shadow") version "9.2.2"
}

// The Paper plugin talks nothing but the wire protocol, so a single jar covers every supported game
// version. It reuses :core for the packet definitions and JSON resolution - it is NOT a blind byte
// relay and hand-writes no schema: the same PacketType/PayloadCodec the loaders use resolve payloads
// here too. paperweight-userdev is deliberately not used; nothing touches NMS.
group = "de.zannagh.eunomia"
version = findProperty("semVer")?.toString()?.takeIf { it.isNotEmpty() } ?: "0.0.1-preview.0"

base {
    archivesName.set("eunomia-paper")
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    // The shared, Minecraft-free networking core - shaded into the plugin jar below.
    implementation(project(":core"))
    // Old API level on purpose: everything used here has been stable Bukkit API since 1.13, and
    // compiling low keeps the one jar loadable on 1.20.1 through 26.x. 1.20.4 (not 1.20.6) because
    // Paper moved to a Java 21 baseline at 1.20.5, but a 1.20.1 server still runs on Java 17.
    compileOnly("io.papermc.paper:paper-api:1.20.4-R0.1-SNAPSHOT")

    testImplementation(platform("org.junit:junit-bom:6.0.1"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation(project(":core"))
    testImplementation("com.google.code.gson:gson:2.11.0")
}

java {
    // Java 17 baseline: a 1.20.1 Paper server runs on 17, and :core is compiled at 17 too.
    toolchain.languageVersion = JavaLanguageVersion.of(17)
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

tasks.shadowJar {
    archiveClassifier.set("")
    // Paper already ships Gson and SLF4J; bundling ours would risk a version clash. Only the core's
    // own classes are shaded in.
    dependencies {
        exclude(dependency("com.google.code.gson:.*:.*"))
        exclude(dependency("org.slf4j:.*:.*"))
    }
}

// `build` should produce the shaded jar as the artifact.
tasks.named("build") {
    dependsOn(tasks.shadowJar)
}
