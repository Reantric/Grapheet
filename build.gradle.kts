plugins {
    id("java")
    application
}

group = "org.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven {
        url = uri("https://jogamp.org/deployment/maven")
    }
}

// Use non-standard source layout where Java files live in `src/`
sourceSets {
    main {
        java.setSrcDirs(listOf("src"))
    }
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}
tasks.withType<JavaExec>().configureEach {
    javaLauncher.set(javaToolchains.launcherFor {
        languageVersion.set(JavaLanguageVersion.of(17))
    })
}



tasks.withType<JavaExec> {
    systemProperty("java.library.path", "library/processing/macos-aarch64")
}

application {
    // Main class for running via Gradle 'run'
    mainClass.set("Main")
}

// Ensure the Gradle 'run' task also sets the native lib path via JVM args
tasks.named<JavaExec>("run") {
    jvmArgs("-Djava.library.path=${projectDir}/library/processing/macos-aarch64")
}


dependencies {
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    // https://mvnrepository.com/artifact/org.processing/core
 //   implementation("org.processing:core:4.3.3")
    implementation(files("library/videoExport.jar"))
    // https://mvnrepository.com/artifact/org.reflections/reflections
    implementation("org.reflections:reflections:0.10.2")
    // https://mvnrepository.com/artifact/org.json/json
    implementation("org.json:json:20250107")

    implementation(fileTree(mapOf("dir" to "library/processing", "include" to listOf("*.jar"))))

    // Additional dependencies required by sources under src/
    // Apache Commons Math for Taylors.java
    implementation("org.apache.commons:commons-math3:3.6.1")
    // JLaTeXMath for TeX rendering in util/tex
    implementation("org.scilab.forge:jlatexmath:1.0.7")
    // Apache Batik for SVG DOM and SVG generation/transcoding
    implementation("org.apache.xmlgraphics:batik-dom:1.17")
    implementation("org.apache.xmlgraphics:batik-svggen:1.17")
    implementation("org.apache.xmlgraphics:batik-transcoder:1.17")
    // Apache FOP for PDF/PS/EPS transcoders used by SVGConverter
    implementation("org.apache.xmlgraphics:fop:2.9")


}

tasks.test {
    useJUnitPlatform()
}