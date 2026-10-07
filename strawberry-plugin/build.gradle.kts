plugins {
    id("java")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.3.build.+")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

tasks.withType<Jar> {
    archiveBaseName.set("StrawberrySMP")
}
