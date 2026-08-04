// The FCGT networking gametest is driven directly via `./gradlew :fabric:<variant>:runClientGametest`
// once a variant pins `fabricapi.semver` (see smoke/README.md and NetworkingSmokeTest). This module is
// a placeholder for a future JUnit matrix that forks that task across variants, as armor-hider does.
plugins { java }
repositories { mavenCentral() }
