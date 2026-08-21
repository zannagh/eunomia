import org.gradle.api.Project

/**
 * The mod version for this build.
 *
 * CI injects the GitVersion-derived `-PsemVer`; local builds fall back to the `localSemVer` property in
 * `gradle.properties`. That fallback is the single source of truth for every module - its `major.minor` is
 * truncated into the upstream API path segment (`/api/v<major>.<minor>/...`), so a stale value produces URLs
 * no relay route serves.
 */
fun Project.resolveSemVer(): String =
    findProperty("semVer")?.toString()?.takeIf { it.isNotEmpty() }
        ?: findProperty("localSemVer")?.toString()?.takeIf { it.isNotEmpty() }
        ?: error("Neither -PsemVer nor the `localSemVer` gradle property is set; cannot determine the mod version.")
