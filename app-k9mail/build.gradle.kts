plugins {
    id(ThunderbirdPlugins.App.androidCompose)
    alias(libs.plugins.dependency.guard)
    alias(libs.plugins.tb.app.badging)
    alias(libs.plugins.tb.app.versioning)
}

// MailFox: version handling, see file VERSION in the project root
data class MailFoxVersion(val name: String, val code: Int)

val mailFoxVersion: MailFoxVersion = providers
    .fileContents(isolated.rootProject.projectDirectory.file("VERSION"))
    .asText.get().trim()
    .let { name ->
        val match = requireNotNull(Regex("""^(\d+)\.(\d+)\.(\d+)$""").matchEntire(name)) {
            "VERSION must have the format X.Y.Z, but is '$name'"
        }
        val (major, minor, patch) = match.destructured
        MailFoxVersion(name = name, code = major.toInt() * 10000 + minor.toInt() * 100 + patch.toInt())
    }

val testCoverageEnabled = providers
    .gradleProperty("testCoverageEnabled")
    .isPresent

android {
    namespace = "com.fsck.k9"

    defaultConfig {
        // MailFox: own application ID so MailFox can be installed next to K-9 Mail
        applicationId = "io.github.haigerman.mailfox"
        testApplicationId = "io.github.haigerman.mailfox.tests"

        // MailFox: version is read from the file VERSION (format X.Y.Z, versionCode = X*10000 + Y*100 + Z)
        versionCode = mailFoxVersion.code
        versionName = mailFoxVersion.name

        buildConfigField("String", "CLIENT_INFO_APP_NAME", "\"MailFox\"")
    }

    androidResources {
        // Keep in sync with the resource string array "supported_languages"
        localeFilters += listOf(
            "ar",
            "be",
            "bg",
            "br",
            "ca",
            "co",
            "cs",
            "cy",
            "da",
            "de",
            "el",
            "en",
            "en-rGB",
            "eo",
            "es",
            "et",
            "eu",
            "fa",
            "fi",
            "fr",
            "fy",
            "ga",
            "gd",
            "gl",
            "hr",
            "hu",
            "in",
            "is",
            "it",
            "iw",
            "ja",
            "ko",
            "lt",
            "lv",
            "nb",
            "nl",
            "nn",
            "pl",
            "pt-rBR",
            "pt-rPT",
            "ro",
            "ru",
            "sk",
            "sl",
            "sq",
            "sr",
            "sv",
            "ta-rIN",
            "tr",
            "uk",
            "vi",
            "zh-rCN",
            "zh-rTW",
        )
    }

    signingConfigs {
        createSigningConfig(project, SigningType.K9_RELEASE, isUpload = false)
    }

    buildTypes {
        val isCI = providers.gradleProperty("ci")
            .map(String::toBoolean)
            .orElse(false)
        release {
            signingConfig = signingConfigs.getByType(SigningType.K9_RELEASE)

            isMinifyEnabled = !isCI.get()
            isShrinkResources = !isCI.get()

            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }

        debug {
            applicationIdSuffix = ".debug"

            enableUnitTestCoverage = testCoverageEnabled
            enableAndroidTestCoverage = testCoverageEnabled

            isMinifyEnabled = false
        }
    }

    flavorDimensions += listOf("app")
    productFlavors {
        create("foss") {
            dimension = "app"
            buildConfigField("String", "PRODUCT_FLAVOR_APP", "\"foss\"")
        }

        create("full") {
            dimension = "app"
            buildConfigField("String", "PRODUCT_FLAVOR_APP", "\"full\"")
        }
    }

    packaging {
        jniLibs {
            excludes += listOf("kotlin/**")
        }

        resources {
            excludes += listOf(
                "META-INF/*.kotlin_module",
                "META-INF/*.version",
                "kotlin/**",
                "DebugProbesKt.bin",
            )
        }
    }
}

dependencies {
    implementation(projects.appCommon)
    implementation(projects.core.ui.compose.common)
    implementation(projects.core.ui.legacy.theme2.k9mail)
    implementation(projects.feature.launcher)
    implementation(projects.feature.mail.message.list.api)
    implementation(projects.feature.mail.message.list.internal)
    implementation(projects.feature.mail.message.reader.api)

    implementation(projects.legacy.core)
    implementation(projects.legacy.ui.legacy)

    implementation(projects.core.featureflag)

    implementation(projects.feature.autodiscovery.api)
    implementation(projects.feature.account.settings.impl)

    "fossImplementation"(projects.feature.funding.noop)
    "fullImplementation"(projects.feature.funding.googleplay)
    implementation(projects.feature.migration.launcher.noop)
    implementation(projects.feature.onboarding.migration.noop)
    implementation(projects.feature.thundermail.api)
    implementation(projects.feature.thundermail.k9mail)
    implementation(projects.feature.thundermail.api)
    implementation(projects.feature.telemetry.noop)
    implementation(projects.feature.widget.messageList)
    implementation(projects.feature.widget.messageListGlance)
    implementation(projects.feature.widget.shortcut)
    implementation(projects.feature.widget.unread)

    implementation(libs.androidx.work.runtime)

    debugImplementation(projects.backend.demo)
    debugImplementation(projects.feature.autodiscovery.demo)

    // Required for DependencyInjectionTest
    testImplementation(projects.feature.account.api)
    testImplementation(projects.feature.account.common)
    testImplementation(projects.feature.thundermail.internal.common)
    testImplementation(projects.plugins.openpgpApiLib.openpgpApi)
    testImplementation(projects.feature.changelog.api)
    testImplementation(projects.feature.changelog.internal)

    testImplementation(libs.appauth)
}

dependencyGuard {
    configuration("fossReleaseRuntimeClasspath")
    configuration("fullReleaseRuntimeClasspath")
}

codeCoverage {
    branchCoverage = 0
    lineCoverage = 24
}
