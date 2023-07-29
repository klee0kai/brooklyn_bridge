package com.github.klee0kai.bridge.brooklyn

import com.google.auto.service.AutoService
import org.jetbrains.kotlin.compiler.plugin.AbstractCliOption
import org.jetbrains.kotlin.compiler.plugin.CliOption
import org.jetbrains.kotlin.compiler.plugin.CommandLineProcessor
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.CompilerConfigurationKey

@AutoService(CommandLineProcessor::class)
class BrooklynCommandLineProcessor : CommandLineProcessor {
    companion object {
        private const val OUT_DIR = "outDir"

        val ARG_OUT_DIR = CompilerConfigurationKey<String>(OUT_DIR)
    }

    override val pluginId: String = BuildConfig.KOTLIN_PLUGIN_ID

    override val pluginOptions: Collection<CliOption> = listOf(
        CliOption(
            optionName = OUT_DIR,
            valueDescription = "file",
            description = "Generate code to out dir",
            required = false,
        ),
    )

    override fun processOption(
        option: AbstractCliOption,
        value: String,
        configuration: CompilerConfiguration
    ) {
        return when (option.optionName) {
            OUT_DIR -> configuration.put(ARG_OUT_DIR, value)
            else -> throw IllegalArgumentException("Unexpected config option ${option.optionName}")
        }
    }
}
