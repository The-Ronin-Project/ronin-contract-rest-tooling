package com.projectronin.rest.contract.model

class VersionPublicationGroup(
    versionNumber: Int,
    extended: Boolean,
    val version: String,
    val extensions: List<VersionPublicationArtifact>,
) {
    val publicationName = "V$versionNumber${if (extended) "Extended" else ""}"
    val publishTaskName = "publish${publicationName}PublicationToNexusRepository"
    val localPublishTaskName = "publish${publicationName}PublicationToMavenLocal"
}
