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
    val nexusReleaseRepo: String
    val nexusSnapshotRepo: String
    val nexusUsername: String?
    val nexusPassword: String?
    val isNexusInsecure: Boolean // only use this for testing
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
    override val nexusReleaseRepo: String,
    override val nexusSnapshotRepo: String,
    override val nexusUsername: String?,
    override val nexusPassword: String?,
    override val isNexusInsecure: Boolean,
) : Settings
