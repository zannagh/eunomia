plugins {
    java
    `jvm-test-suite`
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

repositories {
    mavenCentral()
}

// This module holds Tier-3 tests ONLY: each forks `./gradlew` and boots a real Minecraft client to
// drive the FCGT (fabric-client-gametest) networking smoke. They live in their OWN `smokeTest` suite
// (src/smokeTest/java), never the default `test` suite - so `./gradlew test`/`check` cannot pull them
// in and spawn a client. Run them explicitly with `./gradlew smokeTest` (or the root aggregate of the
// same name), or run an individual case from the IDE. See smoke/README.md for enabling FCGT variants.
testing {
    suites {
        // Keep the default `test` suite present (IDE/tooling expect it) but source-less: a green no-op.
        val test by getting(JvmTestSuite::class) {
            useJUnitJupiter("6.0.1")
        }

        val smokeTest by registering(JvmTestSuite::class) {
            useJUnitJupiter("6.0.1")
            dependencies {
                implementation("org.junit.jupiter:junit-jupiter-params")
            }
            targets.configureEach {
                testTask.configure {
                    // A forked runClientGametest can take minutes to boot the client.
                    systemProperty("junit.jupiter.execution.timeout.test.default", "10m")
                    testLogging {
                        events("passed", "skipped", "failed")
                        showStandardStreams = true
                        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
                    }
                    // Repo root -> tests fork ./gradlew from here.
                    systemProperty("eunomia.repo.root", rootProject.projectDir.absolutePath)
                    // Optional explicit target: `-Dsmoke.variant=fabric-26.2`. When absent the test
                    // discovers FCGT-enabled variants from stonecutter.properties.toml and skips if none.
                    System.getProperty("smoke.variant")?.let { systemProperty("smoke.variant", it) }
                }
            }
        }
    }
}
