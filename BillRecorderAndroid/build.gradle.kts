// Top-level build file
buildscript {
    ext.kotlin_version = "1.8.0"
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath("com.android.tools.build:gradle:8.0.2")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlin_version")
    }
}

plugins {
    id("com.android.application") version "8.0.2" apply false
    id("org.jetbrains.kotlin.android") version "1.8.0" apply false
}
