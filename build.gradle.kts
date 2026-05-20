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

tasks.register("generatePluginsJson") {
    doLast {
        val plugins = subprojects.map { sub ->
            val manifestFile = file("${sub.projectDir}/src/main/AndroidManifest.xml")
            val manifestText = manifestFile.readText()
            val name = Regex("""android:name="([^"]+)"""").find(manifestText)?.groupValues?.get(1) ?: sub.name
            val iconFile = file("${sub.projectDir}/${sub.name}.cs3")
            """{
  "name": "${sub.name}",
  "icon": "https://raw.githubusercontent.com/YoureMotherHelped-Love-Mom/cloudstream-extension-YoureMotherHelped-Love-Mom/refs/heads/main/plugins/${sub.name}/${sub.name}.png",
  "file": "https://raw.githubusercontent.com/YoureMotherHelped-Love-Mom/cloudstream-extension-YoureMotherHelped-Love-Mom/refs/heads/builds/${iconFile.name}",
  "entry": "$name",
  "class": "$name",
  "version": "1",
  "lang": "en",
  "author": "YoureMotherHelped-Love-Mom (forked from phisher98)"
}"""
        }
        file("plugins.json").writeText("[\n${plugins.joinToString(",\n")}\n]")
        println("Generated plugins.json")
    }
}
