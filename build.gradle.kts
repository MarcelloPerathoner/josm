import com.github.spotbugs.snom.Confidence
import com.github.spotbugs.snom.Effort
import com.github.spotbugs.snom.SpotBugsTask
import net.ltgt.gradle.errorprone.CheckSeverity
import net.ltgt.gradle.errorprone.errorprone
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent
import java.net.URL

import org.gradle.api.artifacts.result.DependencyResult
import org.gradle.api.artifacts.result.ResolvedDependencyResult

val java_lang_version = 19

val javaccOutputDir = "${buildDir}/generated/javacc"
val mapcssSrcDir    = "org/openstreetmap/josm/gui/mappaint/mapcss"
val mapcssOutputDir = "${javaccOutputDir}/${mapcssSrcDir}/parsergen"
val reportsDir      = "${buildDir}/reports"

plugins {
  id("application")
  id("com.diffplug.spotless") version "6.9.1"
  id("com.github.ben-manes.versions") version "0.42.0"
  id("com.github.spotbugs") version "5.0.13"
  id("net.ltgt.errorprone") version "2.0.2"
  id("org.sonarqube") version "3.4.0.2513"
  //id("org.checkerframework") version "0.6.14"

  checkstyle
  eclipse
  jacoco
  java
  pmd
}

repositories {
    mavenCentral()
    maven {
        url = uri("https://josm.openstreetmap.de/nexus/content/repositories/releases/")
    }
}

sourceSets {
	main {
        java {
            setSrcDirs(listOf("src", javaccOutputDir))
        }
        resources {
            setSrcDirs(listOf("resources"))
        }
    }
	create("scripts") {
		java {
			setSrcDirs(listOf("scripts"))
		}
 	}
    create("sources") {
    }
}

val sourcesImplementation by configurations.getting
val scriptsImplementation by configurations.getting
val testImplementation by configurations.getting

val integrationTestImplementation by configurations.creating {
    isCanBeResolved = true
    isCanBeConsumed = false
    extendsFrom(configurations["testImplementation"])
}
val functionalTestImplementation by configurations.creating {
    isCanBeResolved = true
    isCanBeConsumed = false
    extendsFrom(configurations["testImplementation"])
}
val performanceTestImplementation by configurations.creating {
    isCanBeResolved = true
    isCanBeConsumed = false
    extendsFrom(configurations["testImplementation"])
}

val versions = mapOf(
  "awaitility" to "4.2.0",
  // Errorprone 2.11 requires Java 11+
  "errorprone" to if (JavaVersion.current() >= JavaVersion.VERSION_11) "2.15.0" else "2.10.0",
  "jdatepicker" to "1.3.4",
  "junit" to "5.9.1",
  "pmd" to "6.42.0",
  "spotbugs" to "4.7.3",
  "wiremock" to "2.33.2"
)

dependencies {
    implementation("ch.poole:OpeningHoursParser:0.27.0")
    implementation("com.adobe.xmp:xmpcore:6.1.11")
    implementation("com.drewnoakes:metadata-extractor:2.18.0") // { transitive = false }
    implementation("com.formdev:svgSalamander:1.1.4")
    implementation("javax.json:javax.json-api:1.1.4")
    implementation("oauth.signpost:signpost-core:2.1.1")
    implementation("org.apache.commons:commons-compress:1.22")
    implementation("org.apache.commons:commons-jcs3-core:3.1")
    implementation("org.glassfish:javax.json:1.1.4")
    implementation("org.openstreetmap.jmapviewer:jmapviewer:2.16")
    implementation("org.tukaani:xz:1.9")
    implementation("org.webjars.npm:tag2link:2021.3.21")

    compileOnly("net.java.dev.javacc:javacc:7.0.12")

    implementation("com.google.code.findbugs:jsr305:3.0.2")
    testImplementation("org.apache.commons:commons-lang3:3.12.0")

    if (!JavaVersion.current().isJava9Compatible) {
        errorproneJavac("com.google.errorprone:javac:9+181-r4173-1")
    }
    errorprone("com.google.errorprone:error_prone_core:${versions["errorprone"]}")
    // testImplementation("org.openstreetmap.josm:josm-unittest:SNAPSHOT"){ isChanging = true }
    testImplementation("com.github.tomakehurst:wiremock-jre8:${versions["wiremock"]}")

    testImplementation(platform("org.junit:junit-bom:${versions["junit"]}"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.junit.jupiter:junit-jupiter-api")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
    testImplementation("com.ginsberg:junit5-system-exit:1.1.2")

    // This can be removed once JOSM drops all JUnit4 support.
    testImplementation("org.junit.jupiter:junit-jupiter-params")
    testImplementation("org.junit.vintage:junit-vintage-engine")

    testImplementation("com.github.spotbugs:spotbugs-annotations:${versions["spotbugs"]}")
    testImplementation("io.github.classgraph:classgraph:4.8.151")
    testImplementation("net.bytebuddy:byte-buddy:1.12.19")
    testImplementation("net.trajano.commons:commons-testing:2.1.0")
    testImplementation("nl.jqno.equalsverifier:equalsverifier:3.11")
    testImplementation("org.awaitility:awaitility:${versions["awaitility"]}")
    testImplementation("org.jmockit:jmockit:1.49.a") // from JOSM nexus
    // private github clone with patch for condy arrayoutofbounds applied
    // testImplementation("com.github.MarcelloPerathoner:jmockit1:master-SNAPSHOT"){ isChanging = true }

    // dependencies for scripts
    scriptsImplementation(sourceSets["main"].output)
    scriptsImplementation(sourceSets["test"].output)
    scriptsImplementation("com.github.spotbugs:spotbugs-annotations:${versions["spotbugs"]}")
    scriptsImplementation("javax.json:javax.json-api:1.1.4")
    scriptsImplementation("org.apache.commons:commons-lang3:3.12.0")
    scriptsImplementation("org.openstreetmap.jmapviewer:jmapviewer:2.16")

    // dependencies for sources
    for (ra in configurations["runtimeClasspath"].resolvedConfiguration.resolvedArtifacts) {
        val id = ra.moduleVersion.id
        if (id.name != "xmpcore" && id.name != "tag2link") {
            sourcesImplementation("${id.group}:${id.name}:${id.version}:sources"){ isTransitive = false }
        }
    }
}

tasks.named("compileScriptsJava") {
    dependsOn("compileTestJava")
    /* doFirst {
        logger.lifecycle("Compiling Scripts. classpath =")
        sourceSets["scripts"].compileClasspath.forEach { logger.lifecycle(it.getPath()) }
    }*/
}

tasks {
	compileJava {
        options.release.set(java_lang_version)
		options.errorprone.isEnabled.set(false) // takes forever
        dependsOn("compileJavacc")
        /* options.compilerArgs.addAll(listOf(
            "--add-exports", "java.desktop/com.sun.java.swing.plaf.gtk=ALL-UNNAMED",
        ))*/
	}
	compileTestJava {
        options.release.set(java_lang_version)
		options.errorprone.isEnabled.set(false) // takes forever
	}
    processResources {
        from(project.projectDir) {
            duplicatesStrategy = DuplicatesStrategy.EXCLUDE
            include("CONTRIBUTION")
            include("README")
            include("LICENSE")
        }
    }
    jar {
        manifest {
            attributes(
                "Application-Name" to "JOSM - Java OpenStreetMap Editor",
                "Codebase"         to "josm.openstreetmap.de",
                "Main-class"       to "org.openstreetmap.josm.gui.MainApplication",
                // "Main-Version"     to "${version.entry.commit.revision} SVN",
                // "Main-Date"        to "${version.entry.commit.date}",
                "Permissions"      to "all-permissions",

                // Java 9 stuff. Entries are safely ignored by Java 8
                "Add-Exports" to "java.desktop/com.sun.java.swing.plaf.gtk java.base/sun.security.action java.desktop/com.apple.eawt java.desktop/com.sun.imageio.spi java.desktop/com.sun.imageio.plugins.jpeg javafx.graphics/com.sun.javafx.application jdk.deploy/com.sun.deploy.config",
                "Add-Opens"   to "java.base/java.lang java.base/java.nio java.base/jdk.internal.loader java.base/jdk.internal.ref java.desktop/javax.imageio.spi java.desktop/javax.swing.text.html java.prefs/java.util.prefs",
                // Indicate that this jar may have version specific classes. Only used in Java9+
                "Multi-Release" to "true"
            )
        }
        from(configurations.runtimeClasspath.get().map({ if (it.isDirectory) it else zipTree(it) }))
        // include sources in jar
        from(sourceSets["main"].allSource)
        setDuplicatesStrategy(DuplicatesStrategy.INCLUDE)

        // exclude("META-INF/*")
        exclude("META-INF/maven/**")
        // exclude("META-INF/resources/webjars/tag2link/*/LICENSE")
        // exclude("META-INF/resources/webjars/tag2link/*/README.md")
        // exclude("META-INF/resources/webjars/tag2link/*/build.js")
        // exclude("META-INF/resources/webjars/tag2link/*/package.json")
        // exclude("META-INF/resources/webjars/tag2link/*/schema.json")
        // exclude("META-INF/resources/webjars/tag2link/*/tag2link.sophox.sparql")
        // exclude("META-INF/resources/webjars/tag2link/*/tag2link.wikidata.sparql")
        // exclude("META-INF/versions/**")
        exclude("org/openstreetmap/gui/jmapviewer/Demo*")
        exclude("com/drew/imaging/FileTypeDetector*")
        exclude("com/drew/imaging/ImageMetadataReader*")
        exclude("com/drew/imaging/avi/**")
        exclude("com/drew/imaging/bmp/**")
        exclude("com/drew/imaging/eps/**")
        exclude("com/drew/imaging/gif/**")
        exclude("com/drew/imaging/heif/**")
        exclude("com/drew/imaging/ico/**")
        exclude("com/drew/imaging/mp3/**")
        exclude("com/drew/imaging/mp4/**")
        exclude("com/drew/imaging/pcx/**")
        exclude("com/drew/imaging/psd/**")
        exclude("com/drew/imaging/quicktime/**")
        exclude("com/drew/imaging/raf/**")
        exclude("com/drew/imaging/riff/**")
        exclude("com/drew/imaging/wav/**")
        exclude("com/drew/imaging/webp/**")
        exclude("com/drew/metadata/avi/**")
        exclude("com/drew/metadata/bmp/**")
        exclude("com/drew/metadata/eps/**")
        exclude("com/drew/metadata/gif/**")
        exclude("com/drew/metadata/heif/**")
        exclude("com/drew/metadata/ico/**")
        exclude("com/drew/metadata/mov/**")
        exclude("com/drew/metadata/mp3/**")
        exclude("com/drew/metadata/mp4/**")
        exclude("com/drew/metadata/pcx/**")
        exclude("com/drew/metadata/wav/**")
        exclude("com/drew/metadata/webp/**")
        exclude("com/drew/tools/**")
        exclude("com/kitfox/svg/app/ant/**")
        exclude("com/kitfox/svg/app/*Dialog*")
        exclude("com/kitfox/svg/app/*Frame*")
        exclude("com/kitfox/svg/app/*Player*")
        exclude("com/kitfox/svg/app/*Viewer*")
        exclude("org/apache/commons/compress/PasswordRequiredException*")
        exclude("org/apache/commons/compress/archivers/**")
        exclude("org/apache/commons/compress/changes/**")
        exclude("org/apache/commons/compress/compressors/bzip2/BZip2Utils*")
        exclude("org/apache/commons/compress/compressors/brotli/**")
        exclude("org/apache/commons/compress/compressors/CompressorStreamFactory*")
        exclude("org/apache/commons/compress/compressors/CompressorStreamProvider*")
        exclude("org/apache/commons/compress/compressors/CompressorException*")
        exclude("org/apache/commons/compress/compressors/FileNameUtil*")
        exclude("org/apache/commons/compress/compressors/deflate/**")
        exclude("org/apache/commons/compress/compressors/gzip/**")
        exclude("org/apache/commons/compress/compressors/lz4/**")
        exclude("org/apache/commons/compress/compressors/lzma/**")
        exclude("org/apache/commons/compress/compressors/lz77support/**")
        exclude("org/apache/commons/compress/compressors/pack200/**")
        exclude("org/apache/commons/compress/compressors/snappy/**")
        exclude("org/apache/commons/compress/compressors/xz/XZUtils*")
        exclude("org/apache/commons/compress/compressors/z/**")
        exclude("org/apache/commons/compress/compressors/zstandard/**")
        exclude("org/apache/commons/compress/java/util/jar/Pack200*")
        exclude("org/apache/commons/compress/harmony/pack200/**")
        exclude("org/apache/commons/compress/harmony/unpack200/**")
        exclude("org/apache/commons/compress/parallel/**")
        exclude("org/apache/commons/compress/utils/ArchiveUtils*")
        exclude("org/apache/commons/jcs3/auxiliary/disk/jdbc/**")
        exclude("org/apache/commons/jcs3/auxiliary/remote/http/client/**")
        exclude("org/apache/commons/jcs3/auxiliary/remote/http/server/RemoteHttpCacheServlet*")
        exclude("org/apache/commons/jcs3/auxiliary/remote/server/RemoteCacheStartupServlet*")
        exclude("org/apache/commons/jcs3/log/Log4j2Factory*")
        exclude("org/apache/commons/jcs3/log/Log4j2LogAdapter*")
        exclude("org/apache/commons/jcs3/utils/servlet/**")
    }
}

testing {
    suites {
        val applyDefaults = { suite: JvmTestSuite ->
            suite.useJUnitJupiter()
    		suite.sources.resources {
                setSrcDirs(listOf("test/data"))
            }
        }

        val test by getting(JvmTestSuite::class) {
            applyDefaults(this)
            sources {
                java {
                    setSrcDirs(listOf("test/unit"))
                }
            }
            targets {
                all {
                    testTask.configure {
                        systemProperty("josm.home", "test/config/unit-josm.home")
                    }
                }
            }
        }

        val integrationTest by registering(JvmTestSuite::class) {
            applyDefaults(this)
            testType.set(TestSuiteType.INTEGRATION_TEST)
            dependencies {
                implementation(sourceSets["main"].output)
                implementation(sourceSets["test"].output)
            }
            sources {
                java {
                    setSrcDirs(listOf("test/integration"))
                }
            }
            targets {
                all {
                    testTask.configure {
                        shouldRunAfter(test)
                        systemProperty("josm.home", "test/config/unit-josm.home")
                    }
                }
            }
        }

        val functionalTest by registering(JvmTestSuite::class) {
            applyDefaults(this)
            testType.set(TestSuiteType.FUNCTIONAL_TEST)
            dependencies {
                implementation(sourceSets["main"].output)
                implementation(sourceSets["test"].output)
            }
            sources {
                java {
                    setSrcDirs(listOf("test/functional"))
                }
            }
            targets {
                all {
                    testTask.configure {
                        shouldRunAfter(test)
                        systemProperty("josm.home", "test/config/functional-josm.home")
                    }
                }
            }
        }

        val performanceTest by registering(JvmTestSuite::class) {
            applyDefaults(this)
            testType.set(TestSuiteType.PERFORMANCE_TEST)
            dependencies {
                implementation(sourceSets["main"].output)
                implementation(sourceSets["test"].output)
            }
            sources {
                java {
                    setSrcDirs(listOf("test/performance"))
                }
            }
            targets {
                all {
                    testTask.configure {
                        shouldRunAfter(test)
                        systemProperty("josm.home", "test/config/performance-josm.home")
                    }
                }
            }
        }
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    jvmArgs = listOf(
        "-Xmx2048m",
        "-javaagent:test/lib/jmockit.jar",
        "--add-opens", "java.prefs/java.util.prefs=ALL-UNNAMED",
	    "--add-opens", "java.base/java.io=ALL-UNNAMED",
	    "--add-opens", "java.base/java.lang=ALL-UNNAMED",
	    "--add-opens", "java.base/java.nio=ALL-UNNAMED",
	    "--add-opens", "java.base/java.text=ALL-UNNAMED",
	    "--add-opens", "java.base/java.util=ALL-UNNAMED",
	    "--add-opens", "java.base/jdk.internal.loader=ALL-UNNAMED",
	    "--add-opens", "java.desktop/java.awt=ALL-UNNAMED",
        "--add-opens", "java.prefs/java.util.prefs=ALL-UNNAMED",
    )

    systemProperty("junit.jupiter.extensions.autodetection.enabled", true)
    systemProperty("java.awt.headless", true)
	systemProperty("josm.test.data",    "test/data")
	systemProperty("glass.platform",    "Monocle")
	systemProperty("monocle.platform",  "Headless")
	systemProperty("prism.order",       "sw")
	// systemProperty("suppressPermanentFailure", "${suppressPermanentFailure}")

    // all tests run sequentially by default,
    // use @Execution(ExecutionMode.CONCURRENT) on test class or method to change
	systemProperty("junit.jupiter.extensions.autodetection.enabled",        true)
	systemProperty("junit.jupiter.execution.parallel.enabled",              true)
	systemProperty("junit.jupiter.execution.parallel.mode.default",         "same_thread")
	systemProperty("junit.jupiter.execution.parallel.mode.classes.default", "same_thread")

    if (JavaVersion.current() >= JavaVersion.VERSION_1_9) {
        systemProperty("java.locale.providers", "SPI,JRE,CLDR")
    }
    if (JavaVersion.current() >= JavaVersion.VERSION_17) {
        systemProperty("java.security.manager", "allow")
    }
    maxParallelForks = (Runtime.getRuntime().availableProcessors() / 2).takeIf { it > 0 } ?: 1
    testLogging {
        exceptionFormat = TestExceptionFormat.FULL
        events("passed", "failed", "skipped")
        showCauses = true
        info {
            events = setOf(TestLogEvent.STARTED, TestLogEvent.PASSED, TestLogEvent.SKIPPED, TestLogEvent.FAILED, TestLogEvent.STANDARD_OUT, TestLogEvent.STANDARD_ERROR)
            showStandardStreams = true
        }
    }
}

tasks.named("check") {
    // dependsOn(testing.suites.named("functionalTest"))
    // dependsOn(testing.suites.named("integrationTest"))
    // dependsOn(testing.suites.named("performaceTest"))
}

task("compileJavacc", JavaExec::class) {
    // do this without javacc plugins, they all suck
    description = "Builds the MapCSS Parser sources."
    mainClass.set("org.javacc.parser.Main")
    classpath = sourceSets["main"].compileClasspath + files("tools/javacc")
    outputs.dir(file(mapcssOutputDir))

    args = listOf(
        "-DEBUG_PARSER=false",
        "-DEBUG_TOKEN_MANAGER=false",
        "-JDK_VERSION=1.${java_lang_version}",
        "-UNICODE_INPUT=true",
        "-OUTPUT_DIRECTORY=${mapcssOutputDir}",
        "src/${mapcssSrcDir}/MapCSSParser.jj"
    )
}

task("compileEpsg", JavaExec::class) {
    description = "Builds the EPSG definitions file."
    dependsOn("compileScriptsJava")
    mainClass.set("BuildProjectionDefinitions")
    classpath = sourceSets["scripts"].runtimeClasspath
    outputs.file("resources/data/projection/custom-epsg")
}

tasks.register<Copy>("downloadDependenciesSources") {
    description = "Downloads the source jars of the runtime dependencies."
    from(sourceSets["sources"].runtimeClasspath)
    into("lib/sources")
}

java {
    withJavadocJar()
    withSourcesJar()
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(java_lang_version))
    }
}

// Set up ErrorProne
tasks.withType(JavaCompile::class).configureEach {
    options.compilerArgs.addAll(listOf(
        "-XDcompilePolicy=simple",
        "-Xlint:all",
        "-Xlint:-serial",
        "-Xlint:cast",
        "-Xlint:deprecation",
        "-Xlint:dep-ann",
        "-Xlint:divzero",
        "-Xlint:empty",
        "-Xlint:finally",
        "-Xlint:overrides",
        "-Xlint:rawtypes",
        "-Xlint:static",
        "-Xlint:try",
        "-Xlint:unchecked",
        "-Xmaxwarns", "1000",
    ))

    // "-Xplugin:semanticdb -sourceroot:@{srcdir} -targetroot:${build.dir}/semanticdb" if:set="lsif" ,
    // "-Xplugin:ErrorProne -XepExcludedPaths:.*/parsergen/.* -Xep:ReferenceEquality:OFF -Xep:FutureReturnValueIgnored:OFF -Xep:JdkObsolete:OFF -Xep:EqualsGetClass:OFF -Xep:UndefinedEquals:OFF -Xep:BadImport:OFF -Xep:AnnotateFormatMethod:OFF -Xep:JavaUtilDate:OFF -Xep:DoNotCallSuggester:OFF -Xep:BanSerializableRead:OFF -Xep:RestrictedApiChecker:OFF -Xep:InlineMeSuggester:OFF" unless:set="noErrorProne",
    // "-processorpath",
    // "${toString:errorprone.classpath}:${toString:semanticdb.classpath}",
    // <!-- Undocumented argument to ignore "Sun internal proprietary API" warning, see http://stackoverflow.com/a/13862308/2257172 -->
    // <compilerarg value="-XDignore.symbol.file"/>

    options.release.set(java_lang_version)

    options.errorprone.isEnabled.set(false) // takes forever

    options.errorprone {
        check("ClassCanBeStatic", CheckSeverity.ERROR)
        check("ConstantField", CheckSeverity.WARN)
        check("DefaultCharset", CheckSeverity.ERROR)
        check("FieldCanBeFinal", CheckSeverity.WARN)
        check("Finally", CheckSeverity.OFF)
        check("LambdaFunctionalInterface", CheckSeverity.WARN)
        check("MethodCanBeStatic", CheckSeverity.WARN)
        check("MultiVariableDeclaration", CheckSeverity.WARN)
        check("PrivateConstructorForUtilityClass", CheckSeverity.WARN)
        check("RemoveUnusedImports", CheckSeverity.WARN)
        check("ReferenceEquality", CheckSeverity.ERROR)
        check("UngroupedOverloads", CheckSeverity.WARN)
        check("UnnecessaryLambda", CheckSeverity.OFF)
        check("WildcardImport", CheckSeverity.ERROR)
    }
}

/**
 * Get a specific property, either from gradle or from the environment
 */
fun getProperty(key: String): Any? {
    if (hasProperty(key)) {
        return findProperty(key)
    }
    return System.getenv(key)
}

spotless {
    format("misc") {
        target("**/*.gradle", "**.*.md", "**/.gitignore")

        trimTrailingWhitespace()
        indentWithSpaces(4)
        endWithNewline()
    }
    java {
        // eclipse().configFile("config/format/code_format.xml")
        endWithNewline()
        importOrder("java", "javax", "")
        indentWithSpaces(4)
        licenseHeader("// License: GPL. For details, see LICENSE file.")
        // Avoid large formatting commits.
        ratchetFrom("origin/master")
        removeUnusedImports()
        trimTrailingWhitespace()
    }
}

eclipse {
    project {
        name = "JOSM"
        comment = "JOSM OpenStreetMap Editor"
        natures("org.sonarlint.eclipse.core.sonarlintNature", "ch.acanda.eclipse.pmd.builder.PMDNature", "org.eclipse.buildship.core.gradleprojectnature")
        buildCommand("org.sonarlint.eclipse.core.sonarlintBuilder")
        buildCommand("ch.acanda.eclipse.pmd.builder.PMDBuilder")
        buildCommand("org.eclipse.buildship.core.gradleprojectbuilder")
    }
    classpath {
        isDownloadJavadoc = true
        isDownloadSources = true
    }
}

tasks["eclipseClasspath"].dependsOn("cleanEclipseClasspath")
tasks["eclipseProject"].dependsOn("cleanEclipseProject")
tasks["eclipse"].setDependsOn(setOf("eclipseClasspath", "eclipseProject"))

tasks.withType(JavaCompile::class) {
  options.encoding = "UTF-8"  // The encoding of the java files
}
tasks.withType(Javadoc::class) {
  isFailOnError = false
}

// Spotbugs config
spotbugs {
    ignoreFailures.set(true)
    effort.set(Effort.MAX)
    reportLevel.set(Confidence.LOW)
    reportsDir.set(file("$reportsDir/spotbugs"))
}
tasks.withType(SpotBugsTask::class) {
    reports.create("html") {
        outputLocation.set(file(File(spotbugs.reportsDir.get().asFile, "baseName.html")))
        // setStylesheet("color.xsl")
    }
}


// JaCoCo config
jacoco {
  toolVersion = "0.8.8"
}

tasks.jacocoTestReport {
  reports {
    html.required.set(true)
    xml.required.set(true)
    csv.required.set(true)
    html.outputLocation.set(jacoco.reportsDirectory)
  }
}

// PMD config
pmd {
    toolVersion = versions.getValue("pmd")
    isIgnoreFailures = true
    ruleSetConfig = resources.text.fromFile("$projectDir/config/pmd/ruleset.xml")
    ruleSets = listOf()
    sourceSets = listOf(project.sourceSets.main.get(), project.sourceSets.test.get())
}

// SonarQube config
sonarqube {
    properties {
        property("sonar.forceAuthentication", "true")
        property("sonar.host.url", "https://sonarcloud.io")
        property("sonar.projectKey", "???")
        property("sonar.organization", "josm")
        property("sonar.projectVersion", project.version)
        findProperty("plugin.description")?.let { property("sonar.projectDescription", it) }
        property("sonar.sources", listOf("src"))
    }
}
