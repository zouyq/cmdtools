plugins {
    java
    id("org.jetbrains.intellij.platform") version "2.18.1"
}

group = "com.github.zouyq"
version = "0.6.5"

providers.gradleProperty("cmdtoolsBuildDir").orNull?.let {
    layout.buildDirectory.set(file(it))
}

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        val localIdePath = providers.gradleProperty("cmdtoolsLocalIdePath").orNull
        if (!localIdePath.isNullOrBlank()) {
            local(localIdePath)
        } else {
            intellijIdeaCommunity(providers.gradleProperty("platformVersion").get())
        }
        testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.Platform)
    }

    testImplementation("junit:junit:4.13.2")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

intellijPlatform {
    instrumentCode = false
    buildSearchableOptions = false

    pluginVerification {
        ides {
            current()
            providers.gradleProperty("cmdtoolsLatestIdePath").orNull?.let {
                local(file(it))
            } ?: latest()
        }
    }

    pluginConfiguration {
        ideaVersion {
            sinceBuild = "242"
        }
    }
}

tasks {
    withType<JavaCompile>().configureEach {
        options.release = 21
        options.encoding = "UTF-8"
    }
}
