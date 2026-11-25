plugins {
    id(ThunderbirdPlugins.Library.androidCompose)
}

android {
    namespace = "net.thunderbird.feature.mailfox.pgp.internal"

    defaultConfig {
        consumerProguardFiles("consumer-rules.pro")
    }
}

dependencies {
    api(projects.feature.mailfox.pgp.api)

    implementation(projects.core.android.account)
    implementation(projects.feature.mail.account.api)
    implementation(projects.core.logging.api)
    implementation(projects.core.ui.theme.api)
    implementation(projects.plugins.openpgpApiLib.openpgpApi)

    implementation(libs.androidx.activity.compose)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)
    implementation(libs.pgpainless.core)

    testImplementation(libs.okhttp.mockwebserver)
}

codeCoverage {
    branchCoverage = 0
    lineCoverage = 0
}
