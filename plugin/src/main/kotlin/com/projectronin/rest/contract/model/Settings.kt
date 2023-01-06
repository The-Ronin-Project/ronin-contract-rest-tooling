package com.projectronin.rest.contract.model

import java.io.File

interface Settings {
    val schemaProjectArtifactId: String
    val schemaProjectDateString: String
    val schemaProjectShortHash: String
    val schemaProjectGroupId: String
    val lintTaskName: String
    val downloadTaskName: String
    val compileTaskName: String
    val docsTaskName: String
    val cleanTaskName: String
    val tarTaskName: String
    val publishCopyTaskName: String
    val incrementVersionTaskName: String
    val mappedMavenRepo: File
}

data class SettingsImpl(
    override val schemaProjectArtifactId: String,
    override val schemaProjectDateString: String,
    override val schemaProjectShortHash: String,
    override val schemaProjectGroupId: String,
    override val lintTaskName: String,
    override val downloadTaskName: String,
    override val compileTaskName: String,
    override val docsTaskName: String,
    override val cleanTaskName: String,
    override val tarTaskName: String,
    override val publishCopyTaskName: String,
    override val incrementVersionTaskName: String,
    override val mappedMavenRepo: File,
): Settings
