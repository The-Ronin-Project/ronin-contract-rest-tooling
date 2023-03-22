package com.projectronin.rest.contract.model

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.databind.node.TextNode
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.projectronin.rest.contract.util.WriterFactory
import io.swagger.parser.OpenAPIParser
import io.swagger.v3.core.util.Json
import io.swagger.v3.core.util.Yaml
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.parser.core.models.ParseOptions
import org.semver4j.Semver
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.lang.IllegalStateException

class VersionDir(val dir: File, settings: Settings) {

    companion object {
        private val logger: Logger = LoggerFactory.getLogger(VersionDir::class.java)
        private val simpleObjectMapper: ObjectMapper = ObjectMapper()
        private val yamlObjectMapper: ObjectMapper = ObjectMapper(YAMLFactory())
    }

    val name: String = dir.name
    private val absolutePath: String = dir.absolutePath
    val schema: File = dir.run {
        when(val artifactFile = dir.listFiles()!!.find { f -> f.name.matches("${settings.schemaProjectArtifactId}\\.(?:json|yaml|yml)".toRegex()) }) {
            null -> {
                val foundFiles = dir.listFiles { f -> f.name.matches(".*\\.(?:json|yaml|yml)".toRegex()) }
                if (foundFiles?.size == 1) {
                    foundFiles[0]
                } else {
                    throw IllegalStateException("Cannot determine what schema file to use in $dir.  Suggest creating primary file named $dir/${settings.schemaProjectArtifactId}.(json|yaml|yml)")
                }
            }
            else -> artifactFile
        }
    }
    fun asTaskName(taskPrefix: String) = "$taskPrefix-$name"
    val openApiSpec: OpenAPI by lazy {
        OpenAPIParser().readLocation(
            schema.absolutePath.toString(),
            null,
            ParseOptions().apply {
                @Suppress("UsePropertyAccessSyntax")
                setResolve(true)
            }
        ).openAPI
    }
    private val semanticVersion: Semver by lazy { Semver(
        if (schema.extension == "json") {
            simpleObjectMapper.readTree(schema)["info"]["version"].textValue()
        } else {
            yamlObjectMapper.readTree(schema)["info"]["version"].textValue()
        }
    ) }
    private val versionNumber = dir.name.replace("[^0-9]".toRegex(), "").toInt()
    private val extendedVersion = "v$versionNumber-${settings.schemaProjectDateString}-${settings.schemaProjectShortHash}"
    operator fun plus(subDirectory: String): File = File(dir, subDirectory)
    val publications: List<VersionPublicationGroup> by lazy {
        listOf(
            VersionPublicationGroup(
                versionNumber = versionNumber,
                extended = false,
                version = semanticVersion.toString(),
                extensions = listOf(
                    VersionPublicationArtifact("tar.gz", this + "build/${settings.schemaProjectArtifactId}.tar.gz"),
                    VersionPublicationArtifact("json", this + "build/${settings.schemaProjectArtifactId}.json"),
                    VersionPublicationArtifact("yaml", this + "build/${settings.schemaProjectArtifactId}.yaml"),
                )
            ),
            VersionPublicationGroup(
                versionNumber = versionNumber,
                extended = true,
                version = extendedVersion,
                extensions = listOf(
                    VersionPublicationArtifact("tar.gz", this + "build/${settings.schemaProjectArtifactId}.tar.gz"),
                    VersionPublicationArtifact("json", this + "build/${settings.schemaProjectArtifactId}.json"),
                    VersionPublicationArtifact("yaml", this + "build/${settings.schemaProjectArtifactId}.yaml"),
                )
            ),
        )
    }

    fun incrementVersion(increment: VersionIncrement, snapshot: Boolean) {
        val newVersion = if (semanticVersion.major != versionNumber) {
            val expectedVersion = Semver.parse("$versionNumber.0.0")
            logger.warn("Version $semanticVersion doesn't match directory's version of $versionNumber.  Will set it to: $expectedVersion")
            expectedVersion
        } else {
            when (increment) {
                VersionIncrement.NONE -> semanticVersion.withClearedPreRelease()
                VersionIncrement.PATCH -> semanticVersion.withClearedPreRelease().nextPatch()
                VersionIncrement.MINOR -> semanticVersion.withClearedPreRelease().nextMinor()
            }
        }
        val finalNewVersion = if (snapshot) {
            newVersion.withPreRelease("SNAPSHOT")
        } else {
            newVersion
        }
        logger.info("$name: $semanticVersion -> $finalNewVersion")
        fun writeToTree(tree: JsonNode): JsonNode {
            (tree["info"] as ObjectNode).set<ObjectNode>("version", TextNode(finalNewVersion.toString()))
            return tree
        }
        if (schema.name.endsWith("json")) {
            Json.mapper().writer(WriterFactory.jsonPrettyPrinter()).writeValue(schema, writeToTree(Json.mapper().readTree(schema)))
        } else {
            WriterFactory.yamlWriter().writeValue(schema, writeToTree(Yaml.mapper().readTree(schema)))
        }
    }
}
