package com.vectras.vm.integration

import java.security.MessageDigest

/** Canonical, bounded IPC contract for Vectras -> Termux RAFCODE-Phi. */
object VectrasTermuxIpcContract {

    const val PROTOCOL = "raf.vectras-termux-ipc.v3"
    const val PROTOCOL_VERSION = 3

    const val TERMUX_PACKAGE = "com.termux.rafacodephi"
    const val SERVICE_CLASS = "com.termux.app.RunCommandService"
    const val ACTION_RUN_COMMAND = "$TERMUX_PACKAGE.RUN_COMMAND"
    const val RUN_COMMAND_PERMISSION = "$TERMUX_PACKAGE.permission.RUN_COMMAND"

    const val EXTRA_COMMAND_PATH = "$TERMUX_PACKAGE.RUN_COMMAND_PATH"
    const val EXTRA_ARGUMENTS = "$TERMUX_PACKAGE.RUN_COMMAND_ARGUMENTS"
    const val EXTRA_WORKDIR = "$TERMUX_PACKAGE.RUN_COMMAND_WORKDIR"
    const val EXTRA_RUNNER = "$TERMUX_PACKAGE.RUN_COMMAND_RUNNER"
    const val EXTRA_PENDING_INTENT = "$TERMUX_PACKAGE.RUN_COMMAND_PENDING_INTENT"

    const val RESULT_BUNDLE = "result"
    const val RESULT_STDOUT = "stdout"
    const val RESULT_STDOUT_ORIGINAL_LENGTH = "stdout_original_length"
    const val RESULT_STDERR = "stderr"
    const val RESULT_STDERR_ORIGINAL_LENGTH = "stderr_original_length"
    const val RESULT_EXIT_CODE = "exitCode"
    const val RESULT_ERR = "err"
    const val RESULT_ERRMSG = "errmsg"

    const val RUNNER_APP_SHELL = "app-shell"
    const val WORKDIR = "~/"

    const val MAX_TOTAL_ARGUMENTS = 32
    const val MAX_ARGUMENT_LENGTH = 256
    const val MAX_ARGUMENT_BYTES = 4096

    val fixedArguments: List<String> = listOf(
        "-accel", "tcg",
        "-display", "none",
        "-monitor", "none",
        "-serial", "stdio",
        "-no-reboot",
        "-name", "vectras-termux-ipc-v3",
    )

    const val MAX_EXTRA_ARGUMENTS = MAX_TOTAL_ARGUMENTS - 11

    private val protectedOptions = setOf(
        "-accel",
        "-display",
        "-monitor",
        "-serial",
        "-no-reboot",
        "-name",
        "-daemonize",
        "-pidfile",
        "-qmp",
        "-readconfig",
        "-writeconfig",
    )

    fun boundedArguments(extraArguments: List<String>): List<String>? {
        if (extraArguments.size > MAX_EXTRA_ARGUMENTS) return null
        if (extraArguments.any(::overridesProtectedOption)) return null

        val combined = fixedArguments + extraArguments
        if (combined.size > MAX_TOTAL_ARGUMENTS) return null

        var totalBytes = 0
        for (argument in combined) {
            if (argument.length > MAX_ARGUMENT_LENGTH) return null
            if ('\u0000' in argument || '\n' in argument || '\r' in argument) return null
            totalBytes += argument.toByteArray(Charsets.UTF_8).size
            if (totalBytes > MAX_ARGUMENT_BYTES) return null
        }
        return combined
    }

    fun commandPath(binaryName: String): String = "\$PREFIX/bin/$binaryName"

    fun canonicalRequest(
        transactionId: String,
        binaryName: String,
        arguments: List<String>,
        guestBootNonce: String? = null,
    ): String = buildString {
        appendField("protocol", PROTOCOL)
        appendField("transaction_id", transactionId)
        appendField("target_package", TERMUX_PACKAGE)
        appendField("service_class", SERVICE_CLASS)
        appendField("action", ACTION_RUN_COMMAND)
        appendField("permission", RUN_COMMAND_PERMISSION)
        appendField("command_path", commandPath(binaryName))
        appendField("workdir", WORKDIR)
        appendField("runner", RUNNER_APP_SHELL)
        appendField("guest_boot_evidence_schema", GuestBootEvidenceContract.SCHEMA)
        appendField("guest_boot_nonce", guestBootNonce ?: "TOKEN_VAZIO_NOT_REQUESTED")
        appendField("argument_count", arguments.size.toString())
        arguments.forEachIndexed { index, argument ->
            appendField("argument_$index", argument)
        }
        appendField("result_bundle", RESULT_BUNDLE)
        appendField("result_exit_code", RESULT_EXIT_CODE)
        appendField("result_error_code", RESULT_ERR)
    }

    fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    private fun overridesProtectedOption(argument: String): Boolean =
        protectedOptions.any { option -> argument == option || argument.startsWith("$option=") }

    private fun StringBuilder.appendField(name: String, value: String) {
        append(name.length).append(':').append(name)
        append('=').append(value.toByteArray(Charsets.UTF_8).size).append(':').append(value)
        append('\n')
    }
}
