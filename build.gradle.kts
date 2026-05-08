plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "1.9.24"
    id("org.jetbrains.intellij.platform") version "2.1.0"
}

group = providers.gradleProperty("pluginGroup").get()
version = providers.gradleProperty("pluginVersion").get()

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        intellijIdeaCommunity(providers.gradleProperty("platformVersion"))
        instrumentationTools()
        // Pull the Plugin Verifier CLI so `gradle verifyPlugin` has an
        // executable to invoke. Without this the task fails with:
        //   "No IntelliJ Plugin Verifier executable found".
        pluginVerifier()
    }
    // Plain JUnit5 only — these are pure-Kotlin unit tests, no IntelliJ test framework.
    testImplementation("org.junit.jupiter:junit-jupiter:5.14.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}

intellijPlatform {
    pluginConfiguration {
        version = providers.gradleProperty("pluginVersion")
        ideaVersion {
            sinceBuild = providers.gradleProperty("pluginSinceBuild")
            untilBuild = providers.gradleProperty("pluginUntilBuild")
        }
    }

    // Plugin Verifier configuration. Run via `gradle verifyPlugin`.
    pluginVerification {
        ides {
            // Verify against IDEA Community at the lower bound (since-build
            // 241 = 2024.1) plus a recent release. Keep the set small —
            // each IDE adds ~500 MB download to CI runtime.
            ide("IC", "2024.1")
            ide("IC", "2024.3")
        }
    }

    signing {
        certificateChain = providers.environmentVariable("CERTIFICATE_CHAIN")
        privateKey = providers.environmentVariable("PRIVATE_KEY")
        password = providers.environmentVariable("PRIVATE_KEY_PASSWORD")
    }

    publishing {
        token = providers.environmentVariable("PUBLISH_TOKEN")
    }
}

kotlin {
    jvmToolchain(providers.gradleProperty("javaVersion").get().toInt())
}
