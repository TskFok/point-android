import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.net.URI

fun releaseApiBaseUrlValidationError(rawApiBaseUrl: String?): String? {
    if (rawApiBaseUrl.isNullOrBlank()) return "pointApiBaseUrl is required for release builds."
    val uri = try {
        URI(rawApiBaseUrl)
    } catch (_: Exception) {
        return "pointApiBaseUrl must be a valid HTTPS root URL."
    }
    return when {
        !uri.scheme.equals("https", ignoreCase = true) || uri.host.isNullOrBlank() ->
            "pointApiBaseUrl must use HTTPS and include a host."
        uri.rawUserInfo != null -> "pointApiBaseUrl must not include user info."
        uri.rawQuery != null -> "pointApiBaseUrl must not include a query."
        uri.rawFragment != null -> "pointApiBaseUrl must not include a fragment."
        uri.rawPath != "/" -> "pointApiBaseUrl must be the service root path '/'."
        !rawApiBaseUrl.endsWith("/") -> "pointApiBaseUrl must end with '/'."
        else -> null
    }
}

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.openapi.generator)
}

android {
    namespace = "com.pointquest.android"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.pointquest.android"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    val releaseApiBaseUrl = providers.gradleProperty("pointApiBaseUrl").orElse("").get()
    val releaseImageBaseUrl = runCatching {
        URI(releaseApiBaseUrl).let { uri ->
            if (uri.scheme == null || uri.authority == null) "" else "${uri.scheme}://${uri.authority}/"
        }
    }.getOrDefault("")

    buildTypes {
        getByName("debug") {
            buildConfigField("String", "API_BASE_URL", "\"http://10.0.2.2:3000/\"")
            buildConfigField("String", "IMAGE_BASE_URL", "\"http://10.0.2.2:3000/\"")
        }
        getByName("release") {
            buildConfigField("String", "API_BASE_URL", "\"$releaseApiBaseUrl\"")
            buildConfigField("String", "IMAGE_BASE_URL", "\"$releaseImageBaseUrl\"")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    sourceSets {
        getByName("main").java.srcDir(layout.buildDirectory.dir("generated/openapi/src/main/kotlin"))
    }
}

val validateReleaseApiBaseUrl by tasks.registering {
    doLast {
        releaseApiBaseUrlValidationError(providers.gradleProperty("pointApiBaseUrl").orNull)
            ?.let { throw GradleException(it) }
    }
}

val verifyReleaseApiBaseUrlValidation by tasks.registering {
    doLast {
        val accepted = listOf(
            "https://api.example.invalid/",
            "https://api.example.invalid:8443/",
        )
        accepted.forEach { candidate ->
            check(releaseApiBaseUrlValidationError(candidate) == null) {
                "Expected valid release root URL: $candidate"
            }
        }
        val rejected = listOf(
            null,
            "",
            "http://api.example.invalid/",
            "https://api.example.invalid",
            "https://api.example.invalid/prefix/",
            "https://api.example.invalid/api/v1/",
            "https://user:secret@api.example.invalid/",
            "https://api.example.invalid/?token=secret",
            "https://api.example.invalid/#fragment",
        )
        rejected.forEach { candidate ->
            check(releaseApiBaseUrlValidationError(candidate) != null) {
                "Expected invalid release root URL: $candidate"
            }
        }
    }
}

tasks.configureEach {
    if (name == "preReleaseBuild") {
        dependsOn(validateReleaseApiBaseUrl)
    }
}

val verifyNetworkSecurityConfig by tasks.registering {
    dependsOn(
        "packageDebugResources",
        "packageReleaseResources",
        "processDebugMainManifest",
        "processReleaseMainManifest",
    )
    doLast {
        fun parse(resourceFile: File) = javax.xml.parsers.DocumentBuilderFactory.newInstance()
            .newDocumentBuilder()
            .parse(resourceFile)

        fun cleartextTrafficPermission(resourceFile: File): String? {
            val document = parse(resourceFile)
            return document.getElementsByTagName("base-config")
                .item(0)
                ?.attributes
                ?.getNamedItem("cleartextTrafficPermitted")
                ?.nodeValue
        }

        val debugConfig = layout.buildDirectory
            .file("intermediates/packaged_res/debug/packageDebugResources/xml/network_security_config.xml")
            .get()
            .asFile
        val releaseConfig = layout.buildDirectory
            .file("intermediates/packaged_res/release/packageReleaseResources/xml/network_security_config.xml")
            .get()
            .asFile
        check(cleartextTrafficPermission(debugConfig) == "false") {
            "Debug must prohibit cleartext traffic by default."
        }
        val debugDocument = parse(debugConfig)
        val domainConfigs = debugDocument.getElementsByTagName("domain-config")
        check(domainConfigs.length == 1) {
            "Debug must define exactly one cleartext domain override."
        }
        val domainConfig = domainConfigs.item(0)
        check(domainConfig.attributes.getNamedItem("cleartextTrafficPermitted")?.nodeValue == "true") {
            "Debug 10.0.2.2 override must permit cleartext traffic."
        }
        val domains = debugDocument.getElementsByTagName("domain")
        check(
            domains.length == 1 &&
                domains.item(0).textContent.trim() == "10.0.2.2" &&
                domains.item(0).attributes.getNamedItem("includeSubdomains")?.nodeValue == "false"
        ) {
            "Debug cleartext traffic must be limited to exactly 10.0.2.2 without subdomains."
        }
        check(cleartextTrafficPermission(releaseConfig) == "false") {
            "Release network security config must prohibit cleartext traffic."
        }

        listOf("debug", "release").forEach { variant ->
            val manifest = layout.buildDirectory
                .file("intermediates/merged_manifest/$variant/process${variant.replaceFirstChar(Char::uppercase)}MainManifest/AndroidManifest.xml")
                .get()
                .asFile
            val permissions = parse(manifest).getElementsByTagName("uses-permission")
            check((0 until permissions.length).any { index ->
                permissions.item(index).attributes.getNamedItem("android:name")?.nodeValue ==
                    "android.permission.INTERNET"
            }) {
                "${variant.replaceFirstChar(Char::uppercase)} merged manifest must declare android.permission.INTERNET."
            }
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

val pointOpenApiSpec = providers.gradleProperty("pointOpenApiSpec")
    .orElse("${rootDir}/../point/openapi/openapi.json")
val pointOpenApiSpecFile = layout.file(pointOpenApiSpec.map(::file))

openApiValidate {
    inputSpec.set(pointOpenApiSpecFile)
}

openApiGenerate {
    generatorName.set("kotlin")
    inputSpec.set(pointOpenApiSpecFile)
    outputDir.set(layout.buildDirectory.dir("generated/openapi"))
    apiPackage.set("com.pointquest.android.generated.api")
    modelPackage.set("com.pointquest.android.generated.model")
    packageName.set("com.pointquest.android.generated")
    library.set("jvm-retrofit2")
    configOptions.set(mapOf(
        "sourceFolder" to "src/main/kotlin",
        "serializationLibrary" to "moshi",
        "useCoroutines" to "true",
        "useResponseAsReturnType" to "true",
        "dateLibrary" to "java8",
        "enumPropertyNaming" to "UPPERCASE",
        "enumUnknownDefaultCase" to "true",
        "modelMutable" to "false",
        "omitGradleWrapper" to "true",
        "omitGradlePluginVersions" to "true",
    ))
}

tasks.withType<KotlinCompile>().configureEach {
    dependsOn(tasks.named("openApiValidate"), tasks.named("openApiGenerate"))
}

tasks.named("openApiGenerate") {
    doLast {
        val apiClient = layout.buildDirectory
            .file("generated/openapi/src/main/kotlin/com/pointquest/android/generated/infrastructure/ApiClient.kt")
            .get()
            .asFile
        apiClient.writeText(
            apiClient.readText().replace(
                "import com.pointquest.android.generated.auth.ApiKeyAuth\n".repeat(2),
                "import com.pointquest.android.generated.auth.ApiKeyAuth\n",
            ),
        )

        val enumTail = Regex(
            """(\n\s+@Json\(name = \"[^\"]+\"\)\s+[^\n;]+\([^\n;]+\));(\n\s+})""",
        )
        val generatedModels = layout.buildDirectory
            .dir("generated/openapi/src/main/kotlin/com/pointquest/android/generated/model")
            .get()
            .asFile
        generatedModels.walkTopDown()
            .filter { it.isFile && it.extension == "kt" && "enum class" in it.readText() }
            .forEach { modelFile ->
                val source = modelFile.readText()
                val withUnknownDefault = source.replace(enumTail) { match ->
                    "${match.groupValues[1]},\n" +
                        "        @Json(name = \"unknown_default_open_api\") " +
                        "UNKNOWN_DEFAULT_OPEN_API(\"unknown_default_open_api\");" +
                        match.groupValues[2]
                }
                check(withUnknownDefault != source) {
                    "Could not add unknown enum fallback to ${modelFile.name}."
                }
                modelFile.writeText(withUnknownDefault)
            }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.moshi)
    implementation(libs.retrofit.converter.scalars)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging.interceptor)
    implementation(libs.moshi)
    implementation(libs.moshi.kotlin)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    testImplementation(libs.junit)
    testImplementation(libs.turbine)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.okhttp.mockwebserver)
    androidTestImplementation(libs.okhttp.tls)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
