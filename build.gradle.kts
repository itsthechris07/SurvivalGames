import java.util.concurrent.atomic.AtomicLong

plugins {
    java
    id("com.gradleup.shadow") version "9.6.1"
    id("com.modrinth.minotaur") version "2.10.0"
    id("io.papermc.hangar-publish-plugin") version "0.1.4"
}

group = "com.christian34.survivalgames"
version = "2.0.0"

java {
    toolchain.languageVersion = JavaLanguageVersion.of(25)
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.extendedclip.com/releases/")
    // party plugins (integrations/PartySupport)
    maven("https://repo.alessiodp.com/releases/") { content { includeGroup("com.alessiodp.parties") } }
    maven("https://simonsator.de/repo/") { content { includeGroup("de.simonsator") } }
    // NPC plugins (integrations/NpcSupport), Floodgate (integrations/BedrockForms)
    maven("https://maven.citizensnpcs.co/repo") { content { includeGroup("net.citizensnpcs") } }
    maven("https://repo.fancyinnovations.com/releases") { content { includeGroup("de.oliver") } }
    maven("https://repo.opencollab.dev/main/") { content { includeGroupByRegex("org\\.geysermc.*") } }
    // TAB, EssentialsX, Multiverse (integrations/TabSupport, EssentialsSupport, MultiverseSupport)
    maven("https://jitpack.io") { content { includeGroup("com.github.NEZNAMY") } }
    maven("https://repo.essentialsx.net/releases/") { content { includeGroup("net.essentialsx") } }
    maven("https://repo.onarandombox.com/multiverse-releases") { content { includeGroupByRegex("org\\.mvplugins.*") } }
    // VaultUnlocked API (integrations/EconomySupport)
    maven("https://repo.codemc.io/repository/creatorfromhell/") { content { includeGroup("net.milkbowl.vault") } }
}

// the Paper version the plugin is built for; -PpaperApi=<version> compiles against a newer one to find API changes
// early (the tests keep using MockBukkit and the API of this version)
val paperApi = providers.gradleProperty("paperApi").getOrElse("26.2.build.129-stable")

dependencies {
    // Paper API already ships Adventure (incl. MiniMessage + legacy serializer)
    compileOnly("io.papermc.paper:paper-api:$paperApi")
    compileOnly("org.jetbrains:annotations:26.1.0")
    // optional: placeholders for other plugins (integrations/SgExpansion)
    compileOnly("me.clip:placeholderapi:2.12.3")
    // optional: parties join together and play in the same team (integrations/PartySupport)
    compileOnly("com.alessiodp.parties:parties-api:3.2.18")
    compileOnly("de.simonsator:spigot-party-api-for-party-and-friends:1.0.7-RELEASE") { isTransitive = false }
    compileOnly("de.simonsator:Party-and-Friends-MySQL-Edition-Spigot-API:1.6.2-RELEASE") { isTransitive = false }
    // optional: join NPCs (integrations/NpcSupport)
    compileOnly("net.citizensnpcs:citizens-main:2.0.44-SNAPSHOT") { isTransitive = false }
    compileOnly("de.oliver:FancyNpcs:2.11.0") { isTransitive = false }
    // optional: forms instead of chest menus for Bedrock players (integrations/BedrockForms)
    // not transitive: geyser-common contains an old Gson, which would hide the one of Paper
    compileOnly("org.geysermc.floodgate:api:2.2.5-SNAPSHOT") { isTransitive = false }
    compileOnly("org.geysermc.cumulus:cumulus:1.1.2") { isTransitive = false }
    // optional: placeholders for MiniMessage texts of other plugins (integrations/SgMiniPlaceholders)
    compileOnly("io.github.miniplaceholders:miniplaceholders-api:3.2.0")
    // optional: TAB doesn't overwrite tab list names, name tags and sidebar in games (integrations/TabSupport)
    compileOnly("com.github.NEZNAMY:TAB-API:6.1.2") { isTransitive = false }
    // optional: god mode, vanish, /back, ... (integrations/EssentialsSupport)
    compileOnly("net.essentialsx:EssentialsX:2.21.2") { isTransitive = false }
    // optional: the worlds of games aren't managed by Multiverse (integrations/MultiverseSupport)
    compileOnly("org.mvplugins.multiverse.core:multiverse-core:5.8.1") { isTransitive = false }
    compileOnly("org.mvplugins.multiverse.inventories:multiverse-inventories:5.3.6") { isTransitive = false }
    // optional: money for kills, wins and games (integrations/EconomySupport); VaultUnlocked (the Vault fork) contains
    // the legacy Vault api (net.milkbowl.vault) and its new one (net.milkbowl.vault2)
    compileOnly("net.milkbowl.vault:VaultUnlockedAPI:2.20") { isTransitive = false }

    implementation("org.incendo:cloud-annotations:2.1.0")
    implementation("org.incendo:cloud-paper:2.0.1")
    implementation("org.incendo:cloud-minecraft-extras:2.0.1")

    // tests run the plugin against a mocked Paper server
    testImplementation("io.papermc.paper:paper-api:26.2.build.129-stable")
    testImplementation("org.mockbukkit.mockbukkit:mockbukkit-v26.2:4.116.1")
    testImplementation(platform("org.junit:junit-bom:6.1.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.jetbrains:annotations:26.1.0")
    testImplementation("me.clip:placeholderapi:2.12.3")
    testImplementation("io.github.miniplaceholders:miniplaceholders-api:3.2.0")
    testImplementation("net.milkbowl.vault:VaultUnlockedAPI:2.20") { isTransitive = false }
    // the database of the statistics (MySQL on the servers, which ship its driver) - H2 speaks its dialect
    testRuntimeOnly("com.h2database:h2:2.3.232")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks {
    processResources {
        // read at configuration time (Task.project at execution time is deprecated)
        val version = project.version.toString()
        inputs.property("version", version)
        filesMatching("plugin.yml") {
            expand("version" to version)
        }
    }
    compileJava {
        options.encoding = Charsets.UTF_8.name()
        options.release = 25
        options.compilerArgs.addAll(listOf("-Xlint:deprecation", "-Xlint:removal"))
    }
    compileTestJava {
        options.encoding = Charsets.UTF_8.name()
        options.compilerArgs.addAll(listOf("-Xlint:deprecation", "-Xlint:removal"))
    }

    build {
        dependsOn(shadowJar)
    }

    test {
        useJUnitPlatform()
        testLogging {
            events("failed", "skipped")
            exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        }
        // MockBukkit reports server methods it doesn't implement as skipped tests - they must not pass silently
        val skipped = AtomicLong()
        addTestListener(object : TestListener {
            override fun beforeSuite(suite: TestDescriptor) {}
            override fun afterSuite(suite: TestDescriptor, result: TestResult) {
                if (suite.parent == null) skipped.set(result.skippedTestCount)
            }
            override fun beforeTest(testDescriptor: TestDescriptor) {}
            override fun afterTest(testDescriptor: TestDescriptor, result: TestResult) {}
        })
        doLast {
            if (skipped.get() > 0) {
                throw GradleException("${skipped.get()} tests were skipped (MockBukkit: not implemented?) - see build/reports/tests")
            }
        }
    }

    // copies the plugin into the update folder of a local test server (swapped in on its next start, as Windows
    // locks the loaded jar); set serverPluginsDir in the (git-ignored) gradle.properties to enable
    providers.gradleProperty("serverPluginsDir").orNull?.let { pluginsDir ->
        val deployPlugin = register<Copy>("deployPlugin") {
            from(shadowJar)
            into(file(pluginsDir).resolve("update"))
        }
        build {
            finalizedBy(deployPlugin)
        }

        // builds, deploys, starts the test server until it is ready and stops it again;
        // console commands to run in between: -Pcommands="sg debug;plugins"
        register<Exec>("testServer") {
            group = "verification"
            dependsOn(deployPlugin)
            val java = project.extensions.getByType<JavaToolchainService>()
                .launcherFor(java.toolchain).get().executablePath.asFile.absolutePath
            val commands = providers.gradleProperty("commands").orNull
            commandLine(buildList {
                addAll(
                    listOf(
                        "powershell",
                        "-NoProfile",
                        "-ExecutionPolicy",
                        "Bypass",
                        "-File",
                        file("scripts/test-server.ps1").absolutePath
                    )
                )
                addAll(listOf("-ServerDir", file(pluginsDir).parentFile.absolutePath, "-Java", java))
                if (commands != null) addAll(listOf("-Commands", commands))
            })
        }
    }

    shadowJar {
        archiveClassifier = ""
        // relocates the ServiceLoader files of cloud together with the classes
        mergeServiceFiles()
        // the service files of all cloud modules have to reach the transformer to be merged
        filesMatching("META-INF/services/**") {
            duplicatesStrategy = DuplicatesStrategy.INCLUDE
        }
        // the annotation processor of cloud-annotations is only needed at compile time
        exclude("META-INF/services/javax.annotation.processing.Processor", "META-INF/gradle/**", "META-INF/maven/**")
        // HotSwap replaces classes with unrelocated IDE-compiled ones, so local dev builds may skip relocation
        if (providers.gradleProperty("relocateLibraries").orNull == "false") {
            doFirst { logger.warn("relocateLibraries=false: libraries are not relocated, do not release this jar") }
            return@shadowJar
        }
        relocate("org.incendo.cloud", "com.christian34.survivalgames.libs.cloud")
        relocate("io.leangen.geantyref", "com.christian34.survivalgames.libs.geantyref")
    }
}

// uploads to Modrinth (modrinth) and Hangar (publishPluginPublicationToHangar), run by .github/workflows/release.yml;
// -PpublishDryRun only shows what Modrinth would get
val minecraftVersions = listOf("26.2")
// the release notes, written by the workflow
val releaseNotes = providers.gradleProperty("releaseNotes").map { file(it).readText() }
    .orElse("See https://github.com/itsthechris07/SurvivalGames/releases")

modrinth {
    // token: MODRINTH_TOKEN environment variable; the project has to exist on Modrinth with this slug
    projectId = "survivalgames-varo"
    versionName = "SurvivalGames $version"
    versionType = "release"
    uploadFile.set(tasks.shadowJar)
    gameVersions.addAll(minecraftVersions)
    loaders.addAll("paper", "purpur")
    detectLoaders = false
    changelog = releaseNotes
    debugMode = providers.gradleProperty("publishDryRun").isPresent
    // the optional integrations available on Modrinth (Citizens isn't, Floodgate only for mod loaders)
    dependencies {
        for (slug in listOf(
            "placeholderapi", "miniplaceholders", "parties", "fancynpcs", "vaultunlocked", "tab-was-taken",
            "essentialsx", "multiverse-core", "multiverse-inventories"
        )) {
            optional.project(slug)
        }
    }
}

hangarPublish {
    publications.register("plugin") {
        // the project has to exist on Hangar with this name
        id = "SurvivalGames"
        version = project.version.toString()
        channel = "Release"
        changelog = releaseNotes
        apiKey = providers.environmentVariable("HANGAR_API_KEY")
        platforms {
            paper {
                jar = tasks.shadowJar.flatMap { it.archiveFile }
                platformVersions = minecraftVersions
                // the optional integrations (Vault = VaultUnlocked on Hangar, Party and Friends isn't there)
                dependencies {
                    for (slug in listOf(
                        "PlaceholderAPI", "MiniPlaceholders", "Parties", "FancyNpcs", "VaultUnlocked", "TAB",
                        "Essentials", "Multiverse-Core", "Multiverse-Inventories", "Floodgate"
                    )) {
                        hangar(slug) { required = false }
                    }
                    url("Citizens", "https://www.spigotmc.org/resources/citizens.13811/") { required = false }
                }
            }
        }
    }
}
