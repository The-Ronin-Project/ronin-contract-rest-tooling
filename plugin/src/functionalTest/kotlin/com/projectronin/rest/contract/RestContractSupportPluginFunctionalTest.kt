package com.projectronin.rest.contract

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.databind.node.TextNode
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import okhttp3.OkHttpClient
import okhttp3.Request
import org.assertj.core.api.Assertions.assertThat
import org.gradle.internal.impldep.org.eclipse.jgit.api.Git
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.utility.DockerImageName
import java.io.File
import java.net.URL
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.test.Test

/**
 * A simple functional test for the 'com.projectronin.rest.contract.support' plugin.
 */
class RestContractSupportPluginFunctionalTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    private val jsonMapper = ObjectMapper()
    private val yamlMapper = ObjectMapper(YAMLFactory())

    private fun getProjectDir() = tempFolder.root
    private fun getBuildFile() = getProjectDir().resolve("build.gradle.kts")
    private fun getSettingsFile() = getProjectDir().resolve("settings.gradle.kts")

    @Test
    fun `lists all the correct tasks`() {
        val result = setupTestProject(listOf("tasks", "--stacktrace"))
        listOf(
            "cleanApiOutput",
            "compileApi",
            "compileApi-v1",
            "compileApi-v2",
            "downloadApiDependencies",
            "downloadApiDependencies-v1",
            "downloadApiDependencies-v2",
            "generateApiDocumentation",
            "generateApiDocumentation-v1",
            "generateApiDocumentation-v2",
            "incrementApiVersion",
            "incrementApiVersion-v1",
            "incrementApiVersion-v2",
            "tarApi",
            "tarApi-v1",
            "tarApi-v2",
            "cleanApiOutput-v1",
            "cleanApiOutput-v2",
            "generateApiDocumentation-v1",
            "generateApiDocumentation-v2",
            "publish",
            "publishToMavenLocal",
            "publishV1ExtendedPublicationToMavenLocal",
            "publishV1ExtendedPublicationToNexusSnapshotsRepository",
            "publishV1ExtendedPublicationToNexusReleasesRepository",
            "publishV1PublicationToMavenLocal",
            "publishV1PublicationToNexusSnapshotsRepository",
            "publishV1PublicationToNexusReleasesRepository",
            "publishV2ExtendedPublicationToMavenLocal",
            "publishV2ExtendedPublicationToNexusSnapshotsRepository",
            "publishV2ExtendedPublicationToNexusReleasesRepository",
            "publishV2PublicationToMavenLocal",
            "publishV2PublicationToNexusSnapshotsRepository",
            "publishV2PublicationToNexusReleasesRepository",
            "lintApi",
        ).forEach { taskName ->
            assertThat(result.output).contains(taskName)
        }
    }

    @Test
    fun `lists all the correct tasks with only v1`() {
        val result = setupTestProject(listOf("tasks", "--stacktrace"), includeV2 = false)
        listOf(
            "cleanApiOutput",
            "compileApi",
            "compileApi-v1",
            "downloadApiDependencies",
            "downloadApiDependencies-v1",
            "generateApiDocumentation",
            "generateApiDocumentation-v1",
            "incrementApiVersion",
            "incrementApiVersion-v1",
            "tarApi",
            "tarApi-v1",
            "cleanApiOutput-v1",
            "generateApiDocumentation-v1",
            "publish",
            "publishToMavenLocal",
            "publishV1ExtendedPublicationToMavenLocal",
            "publishV1ExtendedPublicationToNexusSnapshotsRepository",
            "publishV1ExtendedPublicationToNexusReleasesRepository",
            "publishV1PublicationToMavenLocal",
            "publishV1PublicationToNexusSnapshotsRepository",
            "publishV1PublicationToNexusReleasesRepository",
            "lintApi",
        ).forEach { taskName ->
            assertThat(result.output).contains(taskName)
        }
    }

    @Test
    fun `uses the project name and fails when it cannot find the right schema files`() {
        val result = setupTestProject(
            listOf("tasks", "--stacktrace"),
            settingsText = """
            |rootProject.name = "rest-contract-support"
            |""".trimMargin(),
            fail = true,
        ) {
            (getProjectDir() + "v1/questionnaire.json").copyTo(getProjectDir() + "v1/another-questionnaire.json")
        }

        assertThat(result.output).containsPattern(
            """Cannot determine what schema file to use in .*/v1\. *Suggest creating primary file named .*/v1/rest-contract-support.\(json\|yaml\|yml\)""".toRegex().toPattern()
        )
    }

    @Test
    fun `uses the project name and succeeds`() {
        val result = setupTestProject(
            listOf("tasks", "--stacktrace"),
            settingsText = """
            |rootProject.name = "questionnaire"
            |""".trimMargin(),
        ) {
            (getProjectDir() + "v1/questionnaire.json").copyTo(getProjectDir() + "v1/another-questionnaire.json")
        }

        assertThat(result.output).contains("lintApi")
    }

    @Test
    fun `increments schema versions`() {
        val result = setupTestProject(
            listOf("incrementApiVersion"),
        ) {
            (getProjectDir() + "v1/questionnaire.json").setVersion("1.0.0")
            (getProjectDir() + "v2/questionnaire.yml").setVersion("1.0.0")
        }

        assertThat(result.output).contains("Task :incrementApiVersion-v1")
        assertThat(result.output).contains("Task :incrementApiVersion-v2")
        assertThat(result.output).contains("Task :incrementApiVersion")
        assertThat(result.output).contains("Version 1.0.0 doesn't match directory's version of 2.  Will set it to: 2.0.0")

        assertThat((getProjectDir() + "v1/questionnaire.json").readFileVersion()).isEqualTo("1.0.1")
        assertThat((getProjectDir() + "v2/questionnaire.yml").readFileVersion()).isEqualTo("2.0.0")
    }

    @Test
    fun `increments schema versions to snapshots`() {
        setupTestProject(
            listOf("incrementApiVersion", "-Psnapshot=true"),
        ) {
            (getProjectDir() + "v1/questionnaire.json").setVersion("1.0.0")
            (getProjectDir() + "v2/questionnaire.yml").setVersion("2.0.0")
        }

        assertThat((getProjectDir() + "v1/questionnaire.json").readFileVersion()).isEqualTo("1.0.1-SNAPSHOT")
        assertThat((getProjectDir() + "v2/questionnaire.yml").readFileVersion()).isEqualTo("2.0.1-SNAPSHOT")
    }

    @Test
    fun `increments schema versions to minor version`() {
        setupTestProject(
            listOf("incrementApiVersion", "-Pversion-increment=MINOR"),
            includeV2 = false,
        ) {
            (getProjectDir() + "v1/questionnaire.json").setVersion("1.0.0")
        }

        assertThat((getProjectDir() + "v1/questionnaire.json").readFileVersion()).isEqualTo("1.1.0")
    }

    @Test
    fun `increments schema versions removes a snapshot version`() {
        setupTestProject(
            listOf("incrementApiVersion", "-Pversion-increment=NONE"),
            includeV2 = false,
        ) {
            (getProjectDir() + "v1/questionnaire.json").setVersion("1.0.0-SNAPSHOT")
        }

        assertThat((getProjectDir() + "v1/questionnaire.json").readFileVersion()).isEqualTo("1.0.0")
    }

    @Test
    fun `linting succeeds`() {
        val result = setupTestProject(
            listOf("lintApi", "--info", "--stacktrace"),
        )

        assertThat(result.output).contains("No results with a severity of 'warn' or higher found!")
    }

    @Test
    fun `linting fails and lists both error sets`() {
        val result = setupTestProject(
            listOf("lintApi"),
            fail = true,
        ) {
            val v1Tree = (getProjectDir() + "v1/questionnaire.json").readTree()
            v1Tree.remove("tags")
            (getProjectDir() + "v1/questionnaire.json").writeValue(v1Tree)

            val v2Tree = (getProjectDir() + "v2/questionnaire.yml").readTree()
            (v2Tree["paths"]["/questionnaire"]["post"] as ObjectNode).remove("tags")
            (getProjectDir() + "v2/questionnaire.yml").writeValue(v2Tree)
        }

        assertThat(result.output).contains("operation-tag-defined")
        assertThat(result.output).contains("operation-tags")
    }

    @Test
    fun `linting does not fail if we disable linting`() {
        val result = setupTestProject(
            listOf("lintApi"),
            extraBuildFileText = """
                
                configure<com.projectronin.rest.contract.RestContractSupportExtension> {
                    disableLinting.set(true)
                }
                
            """.trimIndent()
        ) {
            val v1Tree = (getProjectDir() + "v1/questionnaire.json").readTree()
            v1Tree.remove("tags")
            (getProjectDir() + "v1/questionnaire.json").writeValue(v1Tree)

            val v2Tree = (getProjectDir() + "v2/questionnaire.yml").readTree()
            (v2Tree["paths"]["/questionnaire"]["post"] as ObjectNode).remove("tags")
            (getProjectDir() + "v2/questionnaire.yml").writeValue(v2Tree)
        }

        assertThat(result.output).contains("Task :lintApi SKIPPED")
    }

    @Test
    fun `check succeeds`() {
        val result = setupTestProject(
            listOf("check"),
        )

        assertThat(result.output).contains("No results with a severity of 'warn' or higher found!")
    }

    @Test
    fun `check fails and lists both error sets`() {
        val result = setupTestProject(
            listOf("check"),
            fail = true,
        ) {
            val v1Tree = (getProjectDir() + "v1/questionnaire.json").readTree()
            v1Tree.remove("tags")
            (getProjectDir() + "v1/questionnaire.json").writeValue(v1Tree)

            val v2Tree = (getProjectDir() + "v2/questionnaire.yml").readTree()
            (v2Tree["paths"]["/questionnaire"]["post"] as ObjectNode).remove("tags")
            (getProjectDir() + "v2/questionnaire.yml").writeValue(v2Tree)
        }

        assertThat(result.output).contains("operation-tag-defined")
        assertThat(result.output).contains("operation-tags")
    }

    //             downloadTaskName = "downloadApiDependencies",
    @Test
    fun `dependencies download`() {
        val result = setupTestProject(
            listOf("downloadApiDependencies"),
            extraBuildFileText = """
                val v1 by configurations.creating
                
                dependencies {
                    v1("com.projectronin.contract.event:event-interop-resource-retrieve:1.0.0")
                    v1("com.projectronin.product.json-schema:com.projectronin.product.json-schema.gradle.plugin:1.3.0@pom")
                }
            """.trimIndent()
        )

        assertThat(result.output).contains("Task :downloadApiDependencies-v1")
        assertThat(result.output).contains("Task :downloadApiDependencies-v2")
        assertThat(result.output).contains("Task :downloadApiDependencies")

        assertThat(getProjectDir() + "v1/.dependencies").exists()
        assertThat((getProjectDir() + "v1/.dependencies").listFiles()).hasSize(2)

        assertThat(getProjectDir() + "v1/.dependencies/event-interop-resource-retrieve").exists()
        assertThat(getProjectDir() + "v1/.dependencies/event-interop-resource-retrieve/META-INF").exists()
        assertThat(getProjectDir() + "v1/.dependencies/event-interop-resource-retrieve/com").exists()

        assertThat(getProjectDir() + "v1/.dependencies/com.projectronin.product.json-schema.gradle.plugin").exists()
        assertThat((getProjectDir() + "v1/.dependencies/com.projectronin.product.json-schema.gradle.plugin").listFiles()).hasSize(1)
        assertThat(getProjectDir() + "v1/.dependencies/com.projectronin.product.json-schema.gradle.plugin/com.projectronin.product.json-schema.gradle.plugin.pom").exists()

        assertThat(getProjectDir() + "v2/.dependencies").doesNotExist()
    }

    // compileTaskName = "compileApi",
    @Test
    fun `compiling the API works`() {
        // the lintResult tasks are used to just make sure that the compiled single-file schema output
        // also fully lints correctly.  The build would fail if they do not.
        setupTestProject(
            listOf("compileApi", "lintResult-v1", "lintResult-v2"),
            prependedBuildFileText = "import com.github.gradle.node.npm.task.NpxTask",
            extraBuildFileText = """
                tasks.register<NpxTask>("lintResult-v1") {
                    group = "test"
                    dependsOn("npmSetup")
                    command.set("@stoplight/spectral-cli@~6.6.0")
                    args.set(
                        listOf(
                            "lint",
                            "--fail-severity=warn",
                            "--ruleset=spectral.yaml",
                            "v1/build/${getProjectDir().name}.json",
                            "v1/build/${getProjectDir().name}.yaml",
                        )
                    )
                }
                
                tasks.register<NpxTask>("lintResult-v2") {
                    group = "test"
                    dependsOn("npmSetup")
                    command.set("@stoplight/spectral-cli@~6.6.0")
                    args.set(
                        listOf(
                            "lint",
                            "--fail-severity=warn",
                            "--ruleset=spectral.yaml",
                            "v2/build/${getProjectDir().name}.json",
                            "v2/build/${getProjectDir().name}.yaml",
                        )
                    )
                }
            """.trimIndent()
        )

        assertThat(getProjectDir() + "v1/build").exists()
        assertThat(getProjectDir() + "v1/build/${getProjectDir().name}.json").exists()
        assertThat(getProjectDir() + "v1/build/${getProjectDir().name}.yaml").exists()

        assertThat(getProjectDir() + "v2/build").exists()
        assertThat(getProjectDir() + "v2/build/${getProjectDir().name}.json").exists()
        assertThat(getProjectDir() + "v2/build/${getProjectDir().name}.yaml").exists()
    }

    @Test
    fun `building the API works`() {
        setupTestProject(
            listOf("build"),
        )

        assertThat(getProjectDir() + "v1/build").exists()
        assertThat(getProjectDir() + "v1/build/${getProjectDir().name}.json").exists()
        assertThat(getProjectDir() + "v1/build/${getProjectDir().name}.yaml").exists()

        assertThat(getProjectDir() + "v2/build").exists()
        assertThat(getProjectDir() + "v2/build/${getProjectDir().name}.json").exists()
        assertThat(getProjectDir() + "v2/build/${getProjectDir().name}.yaml").exists()
    }

    // docsTaskName = "generateApiDocumentation",
    @Test
    fun `documentation succeeds`() {
        setupTestProject(
            listOf("generateApiDocumentation"),
        )

        assertThat(getProjectDir() + "v1/docs").exists()
        assertThat(getProjectDir() + "v1/docs/index.html").exists()
        assertThat((getProjectDir() + "v1/docs").listFiles()).hasSize(1)

        assertThat(getProjectDir() + "openapitools.json").doesNotExist()
    }

    // cleanTaskName = "cleanApiOutput",
    @Test
    fun `clean output works`() {
        setupTestProject(
            listOf("clean"),
        ) {
            copyDirectory("test-apis/v1/questionnaire.json", "v1/build")
            copyDirectory("test-apis/v1/questionnaire.json", "v1/docs")
            copyDirectory("test-apis/v1/questionnaire.json", "v1/.dependencies")

            assertThat(getProjectDir() + "v1/docs").exists()
            assertThat(getProjectDir() + "v1/build").exists()
            assertThat(getProjectDir() + "v1/.dependencies").exists()

            copyDirectory("test-apis/v1/questionnaire.json", "v2/build")
            copyDirectory("test-apis/v1/questionnaire.json", "v2/docs")
            copyDirectory("test-apis/v1/questionnaire.json", "v2/.dependencies")

            assertThat(getProjectDir() + "v2/docs").exists()
            assertThat(getProjectDir() + "v2/build").exists()
            assertThat(getProjectDir() + "v2/.dependencies").exists()
        }

        assertThat(getProjectDir() + "v1").exists()
        assertThat(getProjectDir() + "v1/questionnaire.json").exists()
        assertThat(getProjectDir() + "v1/docs").doesNotExist()
        assertThat(getProjectDir() + "v1/build").doesNotExist()
        assertThat(getProjectDir() + "v1/.dependencies").doesNotExist()

        assertThat(getProjectDir() + "v2").exists()
        assertThat(getProjectDir() + "v2/questionnaire.yml").exists()
        assertThat(getProjectDir() + "v2/docs").doesNotExist()
        assertThat(getProjectDir() + "v2/build").doesNotExist()
        assertThat(getProjectDir() + "v2/.dependencies").doesNotExist()
    }

    // tarTaskName = "tarApi",
    @Test
    fun `tar succeeds`() {
        setupTestProject(
            listOf("tarApi"),
            extraBuildFileText = """
                val v1 by configurations.creating
                
                dependencies {
                    v1("com.projectronin.contract.event:event-interop-resource-retrieve:1.0.0")
                    v1("com.projectronin.product.json-schema:com.projectronin.product.json-schema.gradle.plugin:1.3.0@pom")
                }
            """.trimIndent()
        )

        assertThat(getProjectDir() + "v1/build/${getProjectDir().name}.tar.gz").exists()
        val tarContents = listOf("tar", "tzvf", "${getProjectDir() + "v1/build/${getProjectDir().name}.tar.gz"}").runCommand(getProjectDir())
        assertThat(tarContents).contains("docs/index.html")
        assertThat(tarContents).contains("schemas/shared-complex-types.json")
        assertThat(tarContents).contains("questionnaire.json")
        assertThat(tarContents).contains(".dependencies/event-interop-resource-retrieve/com/projectronin/event/interop/resource/retrieve/v1/InteropResourceRetrieveV1.class")
        assertThat(tarContents).contains(".dependencies/com.projectronin.product.json-schema.gradle.plugin/com.projectronin.product.json-schema.gradle.plugin.pom")


        assertThat(getProjectDir() + "v2/build/${getProjectDir().name}.tar.gz").exists()
    }

    @Test
    fun `assemble succeeds`() {
        setupTestProject(
            listOf("assemble"),
        )

        assertThat(getProjectDir() + "v1/build/${getProjectDir().name}.tar.gz").exists()
        assertThat(getProjectDir() + "v2/build/${getProjectDir().name}.tar.gz").exists()
    }

    @Test
    fun `local publish succeeds`() {
        val m2RepositoryDir = getProjectDir() + ".m2/repository"
        val hostRepositoryDir = getProjectDir() + "host-repository"
        (hostRepositoryDir + "com/projectronin/rest/contract").mkdirs()
        (hostRepositoryDir + "com/projectronin/rest/contract/foo.txt").writeText("foo")
        (hostRepositoryDir + "com/projectronin/rest/contract/bar").mkdirs()
        (hostRepositoryDir + "com/projectronin/rest/contract/bar/bar.txt").writeText("bar")
        setupTestProject(
            listOf("publishToMavenLocal", "-Phost-repository=$hostRepositoryDir"),
            prependedBuildFileText = """
                System.setProperty("maven.repo.local", "$m2RepositoryDir")
            """.trimIndent(),
            settingsText = """
                rootProject.name = "questionnaire"
            """.trimIndent(),
        ) {
            m2RepositoryDir.mkdirs()
        }

        assertThat(m2RepositoryDir).exists()

        val gitHash = Git.open(getProjectDir()).repository.refDatabase.findRef("HEAD").objectId.abbreviate(7).name()
        val dateString = m2RepositoryDir.walk().find { it.name.matches("v1-[0-9]+-$gitHash".toRegex()) }?.name?.replace("v1-([0-9]+)-$gitHash".toRegex(), "$1") ?: "undated"

        assertThat(m2RepositoryDir + "com/projectronin/rest/contract/questionnaire/1.0.0/questionnaire-1.0.0.json").exists()
        assertThat(m2RepositoryDir + "com/projectronin/rest/contract/questionnaire/1.0.0/questionnaire-1.0.0.yaml").exists()
        assertThat(m2RepositoryDir + "com/projectronin/rest/contract/questionnaire/1.0.0/questionnaire-1.0.0.tar.gz").exists()
        assertThat(m2RepositoryDir + "com/projectronin/rest/contract/questionnaire/2.0.0/questionnaire-2.0.0.tar.gz").exists()
        assertThat(m2RepositoryDir + "com/projectronin/rest/contract/questionnaire/2.0.0/questionnaire-2.0.0.yaml").exists()
        assertThat(m2RepositoryDir + "com/projectronin/rest/contract/questionnaire/2.0.0/questionnaire-2.0.0.json").exists()
        assertThat(m2RepositoryDir + "com/projectronin/rest/contract/questionnaire/v1-$dateString-$gitHash/questionnaire-v1-$dateString-$gitHash.json").exists()
        assertThat(m2RepositoryDir + "com/projectronin/rest/contract/questionnaire/v1-$dateString-$gitHash/questionnaire-v1-$dateString-$gitHash.yaml").exists()
        assertThat(m2RepositoryDir + "com/projectronin/rest/contract/questionnaire/v1-$dateString-$gitHash/questionnaire-v1-$dateString-$gitHash.tar.gz").exists()
        assertThat(m2RepositoryDir + "com/projectronin/rest/contract/questionnaire/v2-$dateString-$gitHash/questionnaire-v2-$dateString-$gitHash.tar.gz").exists()
        assertThat(m2RepositoryDir + "com/projectronin/rest/contract/questionnaire/v2-$dateString-$gitHash/questionnaire-v2-$dateString-$gitHash.yaml").exists()
        assertThat(m2RepositoryDir + "com/projectronin/rest/contract/questionnaire/v2-$dateString-$gitHash/questionnaire-v2-$dateString-$gitHash.json").exists()

        assertThat(m2RepositoryDir + "com/projectronin/rest/contract/foo.txt").exists()
        assertThat(m2RepositoryDir + "com/projectronin/rest/contract/bar/bar.txt").exists()

        assertThat(hostRepositoryDir + "com/projectronin/rest/contract/questionnaire/1.0.0/questionnaire-1.0.0.json").exists()
        assertThat(hostRepositoryDir + "com/projectronin/rest/contract/questionnaire/1.0.0/questionnaire-1.0.0.yaml").exists()
        assertThat(hostRepositoryDir + "com/projectronin/rest/contract/questionnaire/1.0.0/questionnaire-1.0.0.tar.gz").exists()
        assertThat(hostRepositoryDir + "com/projectronin/rest/contract/questionnaire/2.0.0/questionnaire-2.0.0.tar.gz").exists()
        assertThat(hostRepositoryDir + "com/projectronin/rest/contract/questionnaire/2.0.0/questionnaire-2.0.0.yaml").exists()
        assertThat(hostRepositoryDir + "com/projectronin/rest/contract/questionnaire/2.0.0/questionnaire-2.0.0.json").exists()
        assertThat(hostRepositoryDir + "com/projectronin/rest/contract/questionnaire/v1-$dateString-$gitHash/questionnaire-v1-$dateString-$gitHash.json").exists()
        assertThat(hostRepositoryDir + "com/projectronin/rest/contract/questionnaire/v1-$dateString-$gitHash/questionnaire-v1-$dateString-$gitHash.yaml").exists()
        assertThat(hostRepositoryDir + "com/projectronin/rest/contract/questionnaire/v1-$dateString-$gitHash/questionnaire-v1-$dateString-$gitHash.tar.gz").exists()
        assertThat(hostRepositoryDir + "com/projectronin/rest/contract/questionnaire/v2-$dateString-$gitHash/questionnaire-v2-$dateString-$gitHash.tar.gz").exists()
        assertThat(hostRepositoryDir + "com/projectronin/rest/contract/questionnaire/v2-$dateString-$gitHash/questionnaire-v2-$dateString-$gitHash.yaml").exists()
        assertThat(hostRepositoryDir + "com/projectronin/rest/contract/questionnaire/v2-$dateString-$gitHash/questionnaire-v2-$dateString-$gitHash.json").exists()

        assertThat(hostRepositoryDir + "com/projectronin/rest/contract/foo.txt").exists()
        assertThat(hostRepositoryDir + "com/projectronin/rest/contract/bar/bar.txt").exists()
    }

    @Test
    fun `remote publish succeeds`() {
        val secret = UUID.randomUUID().toString()
        val container = GenericContainer(DockerImageName.parse("dzikoysk/reposilite:3.4.0"))
            .withEnv("REPOSILITE_OPTS", "--token admin:$secret")
            .withExposedPorts(8080)
            .waitingFor(Wait.forLogMessage(".*Uptime:.*", 1))
        kotlin.runCatching { container.start() }
            .onFailure { e ->
                println(container.getLogs())
                throw e
            }

        val containerPort = container.getMappedPort(8080)

        val httpClient = OkHttpClient.Builder()
            .connectTimeout(60L, TimeUnit.SECONDS)
            .readTimeout(60L, TimeUnit.SECONDS)
            .build()

        try {
            val m2RepositoryDir = getProjectDir() + ".m2/repository"
            val result = setupTestProject(
                listOf(
                    "publishToMavenLocal",
                    "publish",
                    "--stacktrace",
                    "-Pnexus-user=admin",
                    "-Pnexus-password=$secret",
                    "-Pnexus-snapshot-repo=http://localhost:$containerPort/snapshots",
                    "-Pnexus-release-repo=http://localhost:$containerPort/releases/",
                    "-Pnexus-insecure=true"
                ),
                prependedBuildFileText = """
                    System.setProperty("maven.repo.local", "$m2RepositoryDir")
                """.trimIndent(),
                settingsText = """
                    rootProject.name = "questionnaire"
                """.trimIndent(),
            ) {
                m2RepositoryDir.mkdirs()
                (getProjectDir() + "v2/questionnaire.yml").setVersion("2.0.0-SNAPSHOT")
            }

            assertThat(result.output).contains("Task :publishV1ExtendedPublicationToNexusReleasesRepository\n")
            assertThat(result.output).contains("Task :publishV1ExtendedPublicationToNexusSnapshotsRepository SKIPPED")
            assertThat(result.output).contains("Task :publishV1PublicationToNexusReleasesRepository\n")
            assertThat(result.output).contains("Task :publishV1PublicationToNexusSnapshotsRepository SKIPPED")
            assertThat(result.output).contains("Task :publishV2ExtendedPublicationToNexusReleasesRepository\n")
            assertThat(result.output).contains("Task :publishV2ExtendedPublicationToNexusSnapshotsRepository SKIPPED")
            assertThat(result.output).contains("Task :publishV2PublicationToNexusReleasesRepository SKIPPED")
            assertThat(result.output).contains("Task :publishV2PublicationToNexusSnapshotsRepository\n")

            val gitHash = Git.open(getProjectDir()).repository.refDatabase.findRef("HEAD").objectId.abbreviate(7).name()
            val dateString = m2RepositoryDir.walk().find { it.name.matches("v1-[0-9]+-$gitHash".toRegex()) }?.name?.replace("v1-([0-9]+)-$gitHash".toRegex(), "$1") ?: "undated"

            fun verifyFile(
                isSnapshot: Boolean,
                version: String,
                extension: String,
                artifact: String = "questionnaire",
                packageDir: String = "com/projectronin/rest/contract",
                expectedCode: Int = 200,
                realVersion: String = version,
            ) {
                httpClient.newCall(
                    Request.Builder()
                        .head()
                        .url("http://localhost:$containerPort/${if (isSnapshot) "snapshots" else "releases"}/$packageDir/$artifact/$version/$artifact-$realVersion.$extension")
                        .build()
                )
                    .execute().use { response ->
                        assertThat(response.code).isEqualTo(expectedCode)
                    }
            }

            verifyFile(false, "1.0.0", "json")
            verifyFile(false, "1.0.0", "yaml")
            verifyFile(false, "1.0.0", "tar.gz")

            // /api/maven/details/snapshots/com/projectronin/rest/contract/questionnaire/2.0.0-SNAPSHOT
            val tree = jsonMapper.readTree(URL("http://localhost:$containerPort/api/maven/details/snapshots/com/projectronin/rest/contract/questionnaire/2.0.0-SNAPSHOT"))
            val actualSnapshotVersion = tree["files"].find { jn -> jn["name"].textValue().endsWith(".pom") }!!["name"].textValue().replace("""questionnaire-(.+)\.pom""".toRegex(), "$1")

            verifyFile(true, "2.0.0-SNAPSHOT", "tar.gz", realVersion = actualSnapshotVersion)
            verifyFile(true, "2.0.0-SNAPSHOT", "yaml", realVersion = actualSnapshotVersion)
            verifyFile(true, "2.0.0-SNAPSHOT", "json", realVersion = actualSnapshotVersion)
            verifyFile(false, "v1-$dateString-$gitHash", "json")
            verifyFile(false, "v1-$dateString-$gitHash", "yaml")
            verifyFile(false, "v1-$dateString-$gitHash", "tar.gz")
            verifyFile(false, "v2-$dateString-$gitHash", "tar.gz")
            verifyFile(false, "v2-$dateString-$gitHash", "yaml")
            verifyFile(false, "v2-$dateString-$gitHash", "json")

            // let's run the project again
            val secondResult = runProjectBuild(
                buildArguments = listOf(
                    "publishToMavenLocal",
                    "publish",
                    "--stacktrace",
                    "-Pnexus-user=admin",
                    "-Pnexus-password=$secret",
                    "-Pnexus-snapshot-repo=http://localhost:$containerPort/snapshots",
                    "-Pnexus-release-repo=http://localhost:$containerPort/releases/",
                    "-Pnexus-insecure=true"
                ),
                fail = false
            )
            assertThat(secondResult.output).contains("Task :publishV1ExtendedPublicationToNexusReleasesRepository\n")
            assertThat(secondResult.output).contains("Task :publishV1ExtendedPublicationToNexusSnapshotsRepository SKIPPED")
            assertThat(secondResult.output).contains("Task :publishV1PublicationToNexusReleasesRepository SKIPPED")
            assertThat(secondResult.output).contains("Task :publishV1PublicationToNexusSnapshotsRepository SKIPPED") // this time republish of identical content should be skipped
            assertThat(secondResult.output).contains("Task :publishV2ExtendedPublicationToNexusReleasesRepository\n")
            assertThat(secondResult.output).contains("Task :publishV2ExtendedPublicationToNexusSnapshotsRepository SKIPPED")
            assertThat(secondResult.output).contains("Task :publishV2PublicationToNexusReleasesRepository SKIPPED")
            assertThat(secondResult.output).contains("Task :publishV2PublicationToNexusSnapshotsRepository\n")

        } finally {
            container.stop()
        }
    }

    private fun mapperForFile(file: File): ObjectMapper = if (file.name.endsWith(".json")) jsonMapper else yamlMapper

    private fun File.setVersion(version: String) {
        val tree = readTree()
        (tree["info"] as ObjectNode).set<ObjectNode>("version", TextNode(version))
        writeValue(tree)
    }

    private fun File.readFileVersion(): String {
        return this.readTree()["info"]["version"].asText()
    }

    private fun File.readTree(): ObjectNode {
        return mapperForFile(this).readTree(this) as ObjectNode
    }

    private fun File.writeValue(node: JsonNode) {
        mapperForFile(this).writeValue(this, node)
    }

    private fun setupTestProject(
        buildArguments: List<String>,
        includeV1: Boolean = true,
        includeV2: Boolean = true,
        settingsText: String = "",
        prependedBuildFileText: String = "",
        extraBuildFileText: String? = null,
        fail: Boolean = false,
        extraStuffToDo: () -> Unit = {},
    ): BuildResult {
        val git = Git.init().setDirectory(getProjectDir()).call()
        File(getProjectDir(), ".gitignore").writeText(".idea/")
        git.add().addFilepattern(".gitignore").call()
        git.commit().setMessage("Initial Commit").call()

        if (includeV1) {
            copyDirectory("test-apis/v1/questionnaire.json", "v1")
        }
        if (includeV2) {
            copyDirectory("test-apis/v2/questionnaire.yml", "v2")
        }
        getSettingsFile().writeText(settingsText)
        getBuildFile().writeText("$prependedBuildFileText\n")
        getBuildFile().appendText(
            """
            node {
               download.set(true)
               version.set("18.12.1")
            }
                
            plugins {
                id("com.projectronin.rest.contract.support")
            }
            """.trimIndent()
        )
        (getProjectDir() + "spectral.yaml").writeText(
            """
            extends: ["spectral:oas"]
            
            rules:
              oas3-unused-component: info
        """.trimIndent()
        )

        extraBuildFileText?.run {
            getBuildFile().appendText("\n$this")
        }

        extraStuffToDo()

        println("=".repeat(80))
        println(getBuildFile().readText())
        println("=".repeat(80))

        // Run the build
        return runProjectBuild(buildArguments, fail)
    }

    private fun runProjectBuild(buildArguments: List<String>, fail: Boolean): BuildResult {
        val runner = GradleRunner.create()
        runner.forwardOutput()
        runner.withPluginClasspath()
        runner.withArguments(buildArguments)
        runner.withProjectDir(getProjectDir())
        runner.withDebug(System.getenv("DEBUG_RUNNER")?.lowercase() == "true")
        return if (fail) {
            runner.buildAndFail()
        } else {
            runner.build()
        }
    }

    private fun copyDirectory(resourceName: String, folderName: String) {
        val classLoader = javaClass.classLoader
        val file = File(classLoader.getResource(resourceName)!!.file)
        file.parentFile.copyRecursively(File(tempFolder.root, folderName))
    }

    private operator fun File.plus(other: String): File = File(this, other)
}
