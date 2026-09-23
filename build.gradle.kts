plugins {
    `java-library`
}

allprojects {
    group = "com.aegispunish"
    version = "1.0.5"

    repositories {
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/")
        maven("https://repo.extendedclip.com/content/repositories/placeholderapi/")
        maven("https://repo.opencollab.dev/main/")
        maven("https://repo.codemc.io/repository/maven-public/")
        maven("https://oss.sonatype.org/content/repositories/snapshots/")
    }
}

subprojects {
    apply(plugin = "java-library")

    java {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(21))
        }
    }

    tasks.withType<JavaCompile> {
        options.encoding = "UTF-8"
        options.release.set(21)
    }

    tasks.register<Jar>("fatJar") {
        archiveClassifier.set("all")
        from(sourceSets.main.get().output)
        dependsOn(configurations.runtimeClasspath)
        from({
            configurations.runtimeClasspath.get().filter { it.name.endsWith("jar") }.map { zipTree(it) }
        })
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    }
}
