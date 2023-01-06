package com.projectronin.rest.contract.util

import com.fasterxml.jackson.core.PrettyPrinter
import com.fasterxml.jackson.core.util.DefaultIndenter
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator
import io.swagger.v3.core.util.Yaml

object WriterFactory {

    fun yamlWriter(): ObjectMapper {
        return Yaml.mapper().copyWith(
            YAMLFactory.builder()
                .enable(YAMLGenerator.Feature.MINIMIZE_QUOTES)
                .enable(YAMLGenerator.Feature.INDENT_ARRAYS)
                .enable(YAMLGenerator.Feature.INDENT_ARRAYS_WITH_INDICATOR)
                .build()
        )
    }

    fun jsonPrettyPrinter(): PrettyPrinter = JsonPrettyPrinter(
        DefaultPrettyPrinter()
            .withArrayIndenter(DefaultIndenter.SYSTEM_LINEFEED_INSTANCE)
    )

}
