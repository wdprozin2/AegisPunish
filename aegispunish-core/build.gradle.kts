dependencies {
    api(project(":aegispunish-api"))

    api("com.zaxxer:HikariCP:5.1.0")
    api("com.github.ben-manes.caffeine:caffeine:3.1.8")
    api("com.google.code.gson:gson:2.10.1")
    api("org.yaml:snakeyaml:2.2")
    api("com.mysql:mysql-connector-j:8.3.0")
    api("org.xerial:sqlite-jdbc:3.45.2.0")

    // CompileOnly para interfaces de plugins externos
    compileOnly("org.geysermc.floodgate:api:2.2.2-SNAPSHOT")
    compileOnly("net.skinsrestorer:skinsrestorer-api:15.0.3")
}
