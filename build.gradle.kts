import com.android.build.gradle.BaseExtension
import com.lagradost.cloudstream3.gradle.CloudstreamExtension
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

buildscript {
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }

    dependencies {
        classpath("com.android.tools.build:gradle:8.7.3")
        classpath("com.github.recloudstream:gradle:-SNAPSHOT")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.3.21")
    }
}

allprojects {
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
}

fun Project.cloudstream(configuration: CloudstreamExtension.() -> Unit) =
    extensions.getByName<CloudstreamExtension>("cloudstream").configuration()

fun Project.android(configuration: BaseExtension.() -> Unit) =
    extensions.getByName<BaseExtension>("android").configuration()

subprojects {
    apply(plugin = "com.android.library")
    apply(plugin = "kotlin-android")
    apply(plugin = "com.lagradost.cloudstream3.gradle")

    cloudstream {
        setRepo(System.getenv("GITHUB_REPOSITORY") ?: "YoureMotherHelped-Love-Mom/cloudstream-extension-YoureMotherHelped-Love-Mom")
    }

    android {
        compileSdkVersion(34)

        defaultConfig {
            minSdk = 21
            targetSdk = 34
        }

        compileOptions {
            sourceCompatibility = JavaVersion.VERSION_1_8
            targetCompatibility = JavaVersion.VERSION_1_8
        }

        tasks.withType<KotlinJvmCompile> {
            compilerOptions {
                jvmTarget.set(JvmTarget.JVM_1_8)
                freeCompilerArgs.addAll(
                    "-Xskip-metadata-version-check",
                    "-Xno-call-assertions",
                    "-Xno-param-assertions",
                    "-Xno-receiver-assertions"
                )
            }
        }
    }

    dependencies {
        val cloudstream by configurations
        val implementation by configurations

        cloudstream("com.lagradost:cloudstream3:pre-release")
        implementation(kotlin("stdlib"))
        implementation("org.jsoup:jsoup:1.18.3")
        implementation("com.google.code.gson:gson:2.11.0")
        implementation("com.github.Blatzar:NiceHttp:0.4.11")
        implementation("com.squareup.okhttp3:okhttp:4.12.0")
    }
}

tasks.register("make") {
    subprojects.forEach { sub ->
        dependsOn(sub.tasks.named("assembleRelease"))
    }
}

data class PluginInfo(
    val name: String,
    val iconFile: String,
    val description: String,
    val tvTypes: List<String>,
    val internalName: String,
    val pluginClass: String
)

tasks.register("generatePluginsJson") {
    doLast {
        val pluginInfos = mapOf(
            "Kimcartoon2" to PluginInfo(
                name = "Kimcartoon2",
                iconFile = "Kimcartoon_Icon.png",
                description = "KimCartoon is the best cartoons online website, where you can watch cartoon online completely free.",
                tvTypes = listOf("TvSeries", "Movie", "Cartoon"),
                internalName = "Kimcartoon2",
                pluginClass = "com.kimcartoon2.Kimcartoon2Plugin"
            ),
            "WCO" to PluginInfo(
                name = "WCO",
                iconFile = "WCO.png",
                description = "WCO - Watch cartoons online free. Stream your favorite cartoons and anime.",
                tvTypes = listOf("TvSeries", "Cartoon", "Anime", "Movie"),
                internalName = "WCO",
                pluginClass = "com.wco.WCOPlugin"
            )
        )

        val baseUrl = "https://raw.githubusercontent.com/YoureMotherHelped-Love-Mom/cloudstream-extension-YoureMotherHelped-Love-Mom"

        val plugins = pluginInfos.values.map { info ->
            val iconUrl = "$baseUrl/master/${info.name}/${info.iconFile}"
            val fileUrl = "$baseUrl/refs/heads/builds/${info.name}.cs3"
            val tvTypesStr = info.tvTypes.joinToString("\", \"") { it }

            """{
    "name": "${info.name}",
    "description": "${info.description}",
    "iconUrl": "$iconUrl",
    "url": "$fileUrl",
    "file": "$fileUrl",
    "entry": "${info.pluginClass}",
    "class": "${info.pluginClass}",
    "internalName": "${info.internalName}",
    "version": 1,
    "apiVersion": 1,
    "language": "en",
    "authors": ["YoureMotherHelped-Love-Mom"],
    "status": 1,
    "tvTypes": ["$tvTypesStr"],
    "repositoryUrl": "$baseUrl"
}"""
        }
        file("plugins.json").writeText("[\n${plugins.joinToString(",\n")}\n]")
        println("Generated plugins.json")
    }
}
