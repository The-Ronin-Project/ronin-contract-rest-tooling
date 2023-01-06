package com.projectronin.rest.contract

import com.github.gradle.node.npm.task.NpxTask
import com.projectronin.rest.contract.model.Settings
import com.projectronin.rest.contract.model.SettingsImpl
import com.projectronin.rest.contract.model.VersionDir
import com.projectronin.rest.contract.model.VersionIncrement
import com.projectronin.rest.contract.util.WriterFactory
import io.swagger.v3.core.util.Json
import org.eclipse.jgit.api.Git
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.ArtifactRepositoryContainer
import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.api.artifacts.repositories.MavenArtifactRepository
import org.gradle.api.internal.artifacts.mvnsettings.DefaultLocalMavenRepositoryLocator
import org.gradle.api.internal.artifacts.mvnsettings.DefaultMavenFileLocations
import org.gradle.api.internal.artifacts.mvnsettings.DefaultMavenSettingsProvider
import org.gradle.api.logging.LogLevel
import org.gradle.api.plugins.BasePlugin
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.internal.DefaultPublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.TaskContainer
import org.gradle.api.tasks.bundling.Compression
import org.gradle.api.tasks.bundling.Tar
import org.gradle.language.base.plugins.LifecycleBasePlugin
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.net.URI
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * A simple 'hello world' plugin.
 */
class RestContractSupportPlugin : Plugin<Project> {

    companion object {
        private val logger: Logger = LoggerFactory.getLogger(RestContractSupportPlugin::class.java)
    }

    private fun applyPlugin(project: Project, id: String) {
        if (!project.pluginManager.hasPlugin(id)) {
            project.pluginManager.apply(id)
        }
    }

    override fun apply(project: Project) {

        applyPlugin(project, "com.github.node-gradle.node")
        applyPlugin(project, "base")
        applyPlugin(project, "maven-publish")

        addRepositories(project)

        val gitHash: String = if (File(project.rootDir, ".git").exists()) {
            val git = Git.open(project.rootDir)
            val head = git.repository.refDatabase.findRef("HEAD")
            head.objectId.abbreviate(7).name()
        } else {
            "notagitrepository"
        }

        val settings = SettingsImpl(
            schemaProjectArtifactId = project.name,
            schemaProjectDateString = DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
                .format(OffsetDateTime.now(ZoneOffset.UTC)),
            schemaProjectShortHash = gitHash,
            schemaProjectGroupId = "com.projectronin.rest.contract",
            lintTaskName = "lintApi",
            downloadTaskName = "downloadApiDependencies",
            compileTaskName = "compileApi",
            docsTaskName = "generateApiDocumentation",
            cleanTaskName = "cleanApiOutput",
            tarTaskName = "tarApi",
            publishCopyTaskName = "copyHostRepoIfNecessary",
            incrementVersionTaskName = "incrementApiVersion",
            mappedMavenRepo = File(project.properties.getOrDefault("host-repository", "/home/ronin/host-repository").toString()),
        )

        val versionFiles = project.versionFiles(settings)
        val tasks = project.tasks

        tasks.register(settings.publishCopyTaskName) {
            it.doFirst {
                copyHostMavenRepoIfNecessary(settings)
            }
        }

        tasks.register(settings.lintTaskName, NpxTask::class.java) { task ->
            task.group = LifecycleBasePlugin.VERIFICATION_GROUP
            task.dependsOn("npmSetup")
            task.command.set("@stoplight/spectral-cli@~6.6.0")
            task.args.set(
                listOf(
                    "lint",
                    "--fail-severity=warn",
                    "--ruleset=${
                        if (project.projectDir.listFiles { f -> f.name == "spectral.yaml" }.isNotEmpty()) {
                            "spectral.yaml"
                        } else {
                            "/etc/contract-tools-config/spectral.yaml"
                        }
                    }",
                ) + versionFiles.map { it.schema.absolutePath }
            )
        }

        versionFiles.forEach { versionDir ->
            registerCleanTask(tasks, versionDir, settings)
            registerIncrementVersionTask(tasks, versionDir, settings, project)
            registerDownloadTask(tasks, versionDir, settings, project)
            registerCompileTask(tasks, versionDir, settings)
            registerDocsTask(tasks, versionDir, settings, project)
            registerTarTask(tasks, versionDir, settings, project)
            registerPublications(project, versionDir, settings, tasks)
        }

        fun registerBundleTasks(name: String) {
            tasks.register(name) { task ->
                task.group = BasePlugin.BUILD_GROUP
                task.dependsOn(versionFiles.map { it.asTaskName(name) })
            }
        }

        registerBundleTasks(settings.downloadTaskName)
        registerBundleTasks(settings.compileTaskName)
        registerBundleTasks(settings.docsTaskName)
        registerBundleTasks(settings.tarTaskName)

        registerBundleTasks(settings.incrementVersionTaskName)

        tasks.getByName(BasePlugin.CLEAN_TASK_NAME) { task ->
            task.dependsOn(versionFiles.map { it.asTaskName(settings.cleanTaskName) })
        }
        tasks.getByName(LifecycleBasePlugin.CHECK_TASK_NAME) { task ->
            task.dependsOn(settings.lintTaskName)
        }
        tasks.getByName(LifecycleBasePlugin.BUILD_TASK_NAME) { task ->
            task.dependsOn(settings.compileTaskName)
        }
        tasks.getByName(LifecycleBasePlugin.ASSEMBLE_TASK_NAME) { task ->
            task.dependsOn(settings.tarTaskName)
        }
        tasks.getByName("publishToMavenLocal").run {
            doLast {
                if (settings.mappedMavenRepo.exists()) {
                    val localRepoDir = getLocalRepositoryDirectory()
                    val localGroupDir = File(localRepoDir, settings.schemaProjectGroupId.replace(".", "/"))
                    if (localGroupDir.exists()) {
                        logger.info("Copying .m2 repo into mapped repo")
                        val mappedGroupDir = File(settings.mappedMavenRepo, settings.schemaProjectGroupId.replace(".", "/"))
                        localGroupDir.copyRecursively(mappedGroupDir, overwrite = true)
                    }
                }
            }
        }
    }

    private fun copyHostMavenRepoIfNecessary(settings: SettingsImpl) {
        if (settings.mappedMavenRepo.exists()) {
            val mappedGroupDir = File(settings.mappedMavenRepo, settings.schemaProjectGroupId.replace(".", "/"))
            if (mappedGroupDir.exists()) {
                logger.info("Copying mapped repository into .m2 repo")
                val localRepoDir = getLocalRepositoryDirectory()
                val localGroupDir = File(localRepoDir, settings.schemaProjectGroupId.replace(".", "/"))
                mappedGroupDir.copyRecursively(localGroupDir, overwrite = true)
            }
        }
    }

    private fun getLocalRepositoryDirectory(): File {
        return DefaultLocalMavenRepositoryLocator(DefaultMavenSettingsProvider(DefaultMavenFileLocations())).localMavenRepository
    }

    private fun addRepositories(project: Project) {
        fun RepositoryHandler.conditionallyAddMavenRepo(uri: String, snapshots: Boolean = false) {
            if (find { if (it is MavenArtifactRepository) it.url.toString() == uri else false } == null) {
                maven { ar ->
                    ar.url = URI(uri)
                    ar.mavenContent { mrcd ->
                        if (snapshots) {
                            mrcd.snapshotsOnly()
                        } else {
                            mrcd.releasesOnly()
                        }
                    }
                }
            }
        }

        project.repositories.run {
            conditionallyAddMavenRepo("https://repo.devops.projectronin.io/repository/maven-snapshots/", true)
            conditionallyAddMavenRepo("https://repo.devops.projectronin.io/repository/maven-releases/")
            conditionallyAddMavenRepo("https://repo.devops.projectronin.io/repository/maven-public/")
            if (findByName(ArtifactRepositoryContainer.DEFAULT_MAVEN_LOCAL_REPO_NAME) == null) {
                mavenLocal()
            }
        }
    }

    private fun registerPublications(
        project: Project,
        versionDir: VersionDir,
        settings: SettingsImpl,
        tasks: TaskContainer
    ) {
        if (project.extensions.findByName(PublishingExtension.NAME) == null) {
            project.extensions.create(PublishingExtension::class.java, PublishingExtension.NAME, DefaultPublishingExtension::class.java)
        }
        (project.extensions.getByName(PublishingExtension.NAME) as PublishingExtension).run {
            if (repositories.find { r -> r is MavenArtifactRepository } == null) {
                project.logger.info("Adding maven repository")
                repositories { rh ->
                    rh.maven { mar ->
                        mar.name = "nexus"
                        mar.credentials { pc ->
                            pc.username = System.getenv("NEXUS_USER")
                            pc.password = System.getenv("NEXUS_TOKEN")
                        }
                        mar.url = if (project.version.toString().endsWith("SNAPSHOT")) {
                            URI("https://repo.devops.projectronin.io/repository/maven-snapshots/")
                        } else {
                            URI("https://repo.devops.projectronin.io/repository/maven-releases/")
                        }
                    }
                }
            }
            publications { publications ->
                versionDir.publications
                    .forEach { publication ->
                        publications.register(
                            publication.publicationName,
                            MavenPublication::class.java
                        ) { mp ->
                            mp.groupId = settings.schemaProjectGroupId
                            mp.artifactId = settings.schemaProjectArtifactId
                            mp.version = publication.version
                            publication.extensions.forEach { publicationExtension ->
                                mp.artifact(publicationExtension.artifactFile) { ma ->
                                    ma.extension = publicationExtension.extension
                                }
                            }
                        }
                    }
            }
        }
        versionDir.publications.forEach { publication ->
            tasks.getByName(publication.localPublishTaskName) { task ->
                task.dependsOn(settings.publishCopyTaskName)
                task.dependsOn(versionDir.asTaskName(settings.tarTaskName))
            }
            tasks.getByName(publication.publishTaskName) { task ->
                task.dependsOn(versionDir.asTaskName(settings.tarTaskName))
            }
        }
    }

    private fun registerTarTask(
        tasks: TaskContainer,
        versionDir: VersionDir,
        settings: SettingsImpl,
        project: Project
    ) {
        tasks.register(versionDir.asTaskName(settings.tarTaskName), Tar::class.java) { task ->
            task.group = BasePlugin.BUILD_GROUP
            task.dependsOn(versionDir.asTaskName(settings.docsTaskName))
            task.compression = Compression.GZIP
            task.archiveExtension.set("tar.gz")
            task.archiveFileName.set("${settings.schemaProjectArtifactId}.tar.gz")
            task.destinationDirectory.set(File("${versionDir.name}/build"))
            task.from(project.fileTree(versionDir.dir)) {
                it.exclude("**/build/**", "**/DEPENDENCIES")
            }
        }
    }

    private fun registerDocsTask(
        tasks: TaskContainer,
        versionDir: VersionDir,
        settings: SettingsImpl,
        project: Project
    ) {
        tasks.register(versionDir.asTaskName(settings.docsTaskName), NpxTask::class.java) { task ->
            task.group = BasePlugin.BUILD_GROUP
            task.dependsOn("npmSetup", versionDir.asTaskName(settings.compileTaskName))
            task.logging.captureStandardOutput(LogLevel.DEBUG)
            task.logging.captureStandardError(LogLevel.ERROR)
            task.command.set("@openapitools/openapi-generator-cli")
            task.args.set(
                listOf(
                    "generate",
                    "-i",
                    (versionDir + "build/${settings.schemaProjectArtifactId}.json").absolutePath,
                    "-g",
                    "html2",
                    "-o",
                    (versionDir + "docs").absolutePath
                )
            )
            task.doLast {
                deleteIfExists(File(project.rootDir, "openapitools.json"))
                deleteIfExists(versionDir + "docs/.openapi-generator")
                deleteIfExists(versionDir + "docs/.openapi-generator-ignore")
                deleteIfExists(versionDir + "docs/README.md")
            }
        }
    }

    private fun registerCompileTask(tasks: TaskContainer, versionDir: VersionDir, settings: SettingsImpl) {
        tasks.register(versionDir.asTaskName(settings.compileTaskName)) { task ->
            task.group = BasePlugin.BUILD_GROUP
            task.dependsOn(versionDir.asTaskName(settings.downloadTaskName), settings.lintTaskName)
            task.doLast {
                val buildDir = versionDir + "build"
                if (!buildDir.exists()) {
                    buildDir.mkdir()
                }
                versionDir.schema.run {
                    Json.mapper().writer(WriterFactory.jsonPrettyPrinter()).writeValue(File(buildDir, "${settings.schemaProjectArtifactId}.json"), versionDir.openApiSpec)
                    WriterFactory.yamlWriter().writeValue(File(buildDir, "${settings.schemaProjectArtifactId}.yaml"), versionDir.openApiSpec)
                }
            }
        }
    }

    private fun registerDownloadTask(
        tasks: TaskContainer,
        versionDir: VersionDir,
        settings: SettingsImpl,
        project: Project
    ) {
        tasks.register(versionDir.asTaskName(settings.downloadTaskName)) { task ->
            task.group = "Build Setup"
            task.doLast {
                project.configurations.findByName(versionDir.name)?.run {
                    dependencies.forEach { dependency ->
                        val dependencyFile = files(dependency).first()
                        val dependencyArtifactId = dependency.name
                        val destinationDirectory = versionDir + ".dependencies/$dependencyArtifactId"
                        if (destinationDirectory.exists()) {
                            destinationDirectory.deleteRecursively()
                        }
                        destinationDirectory.mkdirs()
                        if (dependencyFile.name.endsWith(".tar.gz")) {
                            project.copy {
                                it.from(project.tarTree(dependencyFile))
                                it.into(destinationDirectory)
                            }
                        } else if (dependencyFile.name.matches(""".*\.(zip|jar)""".toRegex())) {
                            project.copy {
                                it.from(project.zipTree(dependencyFile))
                                it.into(destinationDirectory)
                            }
                        } else {
                            val extension = dependencyFile.name.replace(".*$dependencyArtifactId-${dependency.version}\\.".toRegex(), "")
                            dependencyFile.copyTo(File(destinationDirectory, "$dependencyArtifactId.$extension"))
                        }
                    }
                }
            }
        }
    }

    private fun registerIncrementVersionTask(
        tasks: TaskContainer,
        versionDir: VersionDir,
        settings: SettingsImpl,
        project: Project
    ) {
        tasks.register(versionDir.asTaskName(settings.incrementVersionTaskName)) { task ->
            task.group = "Build Setup"
            task.doLast {
                versionDir.incrementVersion(
                    VersionIncrement.valueOf(project.properties.getOrDefault("version-increment", "PATCH").toString()),
                    project.properties.getOrDefault("snapshot", "false").toString().toBoolean()
                )
            }
        }
    }

    private fun registerCleanTask(tasks: TaskContainer, versionDir: VersionDir, settings: SettingsImpl) {
        tasks.register(versionDir.asTaskName(settings.cleanTaskName)) { task ->
            task.group = BasePlugin.BUILD_GROUP
            deleteIfExists(versionDir + "docs")
            deleteIfExists(versionDir + "build")
            deleteIfExists(versionDir + ".dependencies")
        }
    }

    private fun deleteIfExists(file: File) {
        if (file.exists()) {
            if (file.isDirectory) {
                file.deleteRecursively()
            } else {
                file.delete()
            }
        }
    }

    private fun Project.versionFiles(settings: Settings): Iterable<VersionDir> = project.projectDir.listFiles { f -> f.name.matches("v[0-9]+".toRegex()) }
        .map { VersionDir(it, settings) }
        .toList()
}