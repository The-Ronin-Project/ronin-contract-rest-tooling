package com.projectronin.rest.contract

import org.slf4j.LoggerFactory
import java.io.File
import java.util.concurrent.TimeUnit

fun List<String>.runCommand(workingDir: File): String? = runCatching {
    val proc = ProcessBuilder(*toTypedArray())
        .directory(workingDir)
        .redirectOutput(ProcessBuilder.Redirect.PIPE)
        .redirectError(ProcessBuilder.Redirect.PIPE)
        .start()

    proc.waitFor(60, TimeUnit.MINUTES)
    val errorText = proc.errorStream.bufferedReader().readText()
    if (errorText.isNotBlank()) {
        LoggerFactory.getLogger(String::class.java).error(errorText)
    }
    proc.inputStream.bufferedReader().readText()
}.onFailure { it.printStackTrace() }.getOrNull()
