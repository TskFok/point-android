import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.net.URI

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
        versionName = "1.0"
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
        val rawApiBaseUrl = providers.gradleProperty("pointApiBaseUrl").orNull
            ?: throw GradleException("pointApiBaseUrl is required for release builds.")
        val uri = runCatching { URI(rawApiBaseUrl) }
            .getOrElse { throw GradleException("pointApiBaseUrl must be a valid HTTPS URL.", it) }
        if (!uri.scheme.equals("https", ignoreCase = true) || uri.host.isNullOrBlank()) {
            throw GradleException("pointApiBaseUrl must be a valid HTTPS URL.")
        }
        if (uri.path == "/api/v1" || uri.path.startsWith("/api/v1/")) {
            throw GradleException("pointApiBaseUrl must not include the /api/v1 path.")
        }
        if (!rawApiBaseUrl.endsWith("/")) {
            throw GradleException("pointApiBaseUrl must end with '/'.")
        }
    }
}

tasks.configureEach {
    if (name == "preReleaseBuild") {
        dependsOn(validateReleaseApiBaseUrl)
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

    testImplementation(libs.junit)
    testImplementation(libs.turbine)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
