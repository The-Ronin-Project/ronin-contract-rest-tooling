package com.projectronin.rest.contract

import org.gradle.api.provider.Property

interface RestContractSupportExtension {
    val disableLinting: Property<Boolean>
}
