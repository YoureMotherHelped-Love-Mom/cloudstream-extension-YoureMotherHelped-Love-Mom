pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

rootProject.name = "Video-extensions"

file(".").listFiles()?.forEach { file ->
    if (file.isDirectory && file.name != "build" && file.name != ".gradle" && file.name != ".git") {
        val buildFile = file("${file.absolutePath}/build.gradle.kts")
        if (buildFile.exists()) {
            include(":${file.name}")
            project(":${file.name}").projectDir = file
        }
    }
}
