plugins {
    id("dev.kikugie.stonecutter")
    id("net.neoforged.moddev") version "2.0.140" apply false
}

stonecutter active "fabric-26.2" /* [SC] DO NOT EDIT */

stonecutter parameters {
    // Cross-version source replacements go here (Stonecutter rewrites matching tokens per active
    // MC version at generation time). Empty for the bare library base - a consuming mod adds the
    // rules its own source needs, e.g.:
    //   replacements.string(current.parsed >= "1.21.11") { replace("ResourceLocation", "Identifier") }
}
