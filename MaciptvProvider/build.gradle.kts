dependencies {
    implementation("androidx.legacy:legacy-support-v4:1.0.0")
    implementation("com.google.android.material:material:1.4.0")
    implementation("androidx.lifecycle:lifecycle-extensions:2.2.0")
}

// use an integer for version numbers
version = 1


cloudstream {
    language = "fr"
    // All of these properties are optional, you can safely remove them

    description = "IPTV avec Bein Sport RMC Sport ... Je vous laisse découvrir"
    authors = listOf("Eddy")

    /**
     * Status int as the following:
     * 0: Down
     * 1: Ok
     * 2: Slow
     * 3: Beta only
     * */
    status = 1 // will be 3 if unspecified
    tvTypes = listOf("Live")

    iconUrl = "https://www.google.com/s2/favicons?domain=franceiptv.fr/&sz=%size%"
    requiresResources = true
}