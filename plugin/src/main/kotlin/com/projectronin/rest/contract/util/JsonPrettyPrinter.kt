package com.projectronin.rest.contract.util

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.PrettyPrinter
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter

class JsonPrettyPrinter(b: DefaultPrettyPrinter) : PrettyPrinter by b {
    override fun writeObjectFieldValueSeparator(gen: JsonGenerator?) {
        gen?.writeRaw(": ")
    }
}
