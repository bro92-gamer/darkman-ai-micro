package com.darkman

import java.io.File
import java.util.concurrent.TimeUnit

interface Tool {
    val name: String
    val description: String
    fun execute(params: Map<String, String>): ToolResult
}

data class ToolResult(
    val ok: Boolean,
    val output: String
)

class FileSystemTool(
    private val root: File
) : Tool {

    override val name = "filesystem"

    override val description =
        "Read, write, list, and search files below the selected project root."

    private fun safe(path: String): File? =
        runCatching {
            File(root, path).canonicalFile
        }.getOrNull()?.takeIf {
            it.path == root.canonicalPath ||
                it.path.startsWith(root.canonicalPath + File.separator)
        }

    override fun execute(
        params: Map<String, String>
    ): ToolResult = try {

        when (params["action"]) {

            "read" -> {
                val file = safe(params["path"].orEmpty())
                    ?: return ToolResult(false, "Unsafe path")

                if (!file.isFile) {
                    ToolResult(false, "Not a file")
                } else {
                    ToolResult(
                        true,
                        file.readText(Charsets.UTF_8).take(50000)
                    )
                }
            }

            "write" -> {
                val file = safe(params["path"].orEmpty())
                    ?: return ToolResult(false, "Unsafe path")

                file.parentFile?.mkdirs()

                file.writeText(
                    params["content"].orEmpty(),
                    Charsets.UTF_8
                )

                ToolResult(
                    true,
                    "Wrote ${file.relativeTo(root)}"
                )
            }

            "search" -> {
                val query = params["query"].orEmpty()

                val matches =
                    root.walkTopDown()
                        .filter {
                            it.isFile && it.length() < 2_000_000
                        }
                        .mapNotNull { file ->
                            runCatching {
                                if (
                                    file.readText(
                                        Charsets.UTF_8
                                    ).contains(query, true)
                                ) {
                                    file.relativeTo(root).path
                                } else {
                                    null
                                }
                            }.getOrNull()
                        }
                        .take(100)
                        .toList()

                ToolResult(
                    true,
                    matches.joinToString("\n")
                )
            }

            else -> {

                val files =
                    root.walkTopDown()
                        .filter { it != root }
                        .take(300)
                        .map {
                            it.relativeTo(root).path
                        }
                        .toList()

                ToolResult(
                    true,
                    files.joinToString("\n")
                )
            }
        }

    } catch (e: Exception) {

        ToolResult(
            false,
            e.message ?: "Filesystem error"
        )
    }
}


class TermuxTool(
    private val workDir: File
) : Tool {

    override val name = "shell"

    override val description =
        "Run a user-authorized shell command. Compound commands are allowed after approval."

    override fun execute(
        params: Map<String, String>
    ): ToolResult = try {

        val command =
            params["command"].orEmpty().trim()

        if (command.isEmpty()) {
            return ToolResult(
                false,
                "Empty command"
            )
        }

        val process =
            ProcessBuilder(
                "sh",
                "-c",
                command
            )
                .directory(workDir)
                .redirectErrorStream(true)
                .start()

        if (
            !process.waitFor(
                120,
                TimeUnit.SECONDS
            )
        ) {
            process.destroyForcibly()

            return ToolResult(
                false,
                "Command timed out after 120 seconds"
            )
        }

        ToolResult(
            process.exitValue() == 0,
            process.inputStream
                .bufferedReader()
                .readText()
                .take(30000)
        )

    } catch (e: Exception) {

        ToolResult(
            false,
            e.message ?: "Shell unavailable"
        )
    }
}


class GitTool(
    private val workDir: File
) : Tool {

    override val name = "git"

    override val description =
        "Run an explicitly authorized git command."

    override fun execute(
        params: Map<String, String>
    ): ToolResult = try {

        val command =
            params["command"].orEmpty().trim()

        if (command.isEmpty()) {
            return ToolResult(
                false,
                "Empty git command"
            )
        }

        val process =
            ProcessBuilder(
                "sh",
                "-c",
                "git $command"
            )
                .directory(workDir)
                .redirectErrorStream(true)
                .start()

        if (
            !process.waitFor(
                120,
                TimeUnit.SECONDS
            )
        ) {
            process.destroyForcibly()

            return ToolResult(
                false,
                "Git command timed out"
            )
        }

        ToolResult(
            process.exitValue() == 0,
            process.inputStream
                .bufferedReader()
                .readText()
                .take(30000)
        )

    } catch (e: Exception) {

        ToolResult(
            false,
            e.message ?: "Git unavailable"
        )
    }
}


class GodotTool(
    private val root: File
) : Tool {

    override val name = "godot"

    override val description =
        "Inspect a Godot project or run an authorized Godot command."

    override fun execute(
        params: Map<String, String>
    ): ToolResult = try {

        val project =
            if (
                File(
                    root,
                    "project.godot"
                ).exists()
            ) {
                root
            } else {
                root.walkTopDown()
                    .firstOrNull {
                        it.name == "project.godot"
                    }
                    ?.parentFile
            }
                ?: return ToolResult(
                    false,
                    "No project.godot found"
                )

        if (
            params["action"] == "run"
        ) {

            val command =
                params["command"]
                    .orEmpty()
                    .trim()

            if (command.isEmpty()) {
                return ToolResult(
                    false,
                    "Missing Godot command"
                )
            }

            val process =
                ProcessBuilder(
                    "sh",
                    "-c",
                    command
                )
                    .directory(project)
                    .redirectErrorStream(true)
                    .start()

            if (
                !process.waitFor(
                    120,
                    TimeUnit.SECONDS
                )
            ) {
                process.destroyForcibly()

                return ToolResult(
                    false,
                    "Godot command timed out"
                )
            }

            return ToolResult(
                process.exitValue() == 0,
                process.inputStream
                    .bufferedReader()
                    .readText()
                    .take(30000)
            )
        }

        val scripts =
            project.walkTopDown()
                .count {
                    it.isFile &&
                        it.extension == "gd"
                }

        val scenes =
            project.walkTopDown()
                .count {
                    it.isFile &&
                        it.extension == "tscn"
                }

        ToolResult(
            true,
            "Godot project: ${project.absolutePath}\n" +
                "GDScript files: $scripts\n" +
                "Scene files: $scenes"
        )

    } catch (e: Exception) {

        ToolResult(
            false,
            e.message ?: "Godot scan failed"
        )
    }
}


class UnityTool(
    private val root: File
) : Tool {

    override val name = "unity"

    override val description =
        "Detect and inspect Unity projects."

    override fun execute(
        params: Map<String, String>
    ): ToolResult = try {

        val project =
            root.walkTopDown()
                .firstOrNull {
                    it.name == "ProjectSettings"
                }
                ?.parentFile
                ?: return ToolResult(
                    false,
                    "No Unity project found"
                )

        ToolResult(
            true,
            "Unity project: ${project.absolutePath}"
        )

    } catch (e: Exception) {

        ToolResult(
            false,
            e.message ?: "Unity scan failed"
        )
    }
}


class UnrealTool(
    private val root: File
) : Tool {

    override val name = "unreal"

    override val description =
        "Detect Unreal Engine projects."

    override fun execute(
        params: Map<String, String>
    ): ToolResult = try {

        val project =
            root.walkTopDown()
                .firstOrNull {
                    it.extension.equals(
                        "uproject",
                        true
                    )
                }
                ?: return ToolResult(
                    false,
                    "No .uproject file found"
                )

        ToolResult(
            true,
            "Unreal project: ${project.absolutePath}"
        )

    } catch (e: Exception) {

        ToolResult(
            false,
            e.message ?: "Unreal scan failed"
        )
    }
}


class ProjectDetector(
    private val root: File
) {

    fun detect(): String {

        val godot =
            root.walkTopDown()
                .any {
                    it.name == "project.godot"
                }

        val unity =
            root.walkTopDown()
                .any {
                    it.name == "ProjectSettings" ||
                        (
                            it.name == "manifest.json" &&
                            it.parentFile?.name == "Packages"
                        )
                }

        val unreal =
            root.walkTopDown()
                .any {
                    it.extension.equals(
                        "uproject",
                        true
                    )
                }

        return buildList {

            if (godot) {
                add("Godot")
            }

            if (unity) {
                add("Unity")
            }

            if (unreal) {
                add("Unreal Engine")
            }

        }.ifEmpty {
            listOf("Unknown")
        }.joinToString(", ")
    }
}


enum class PermissionMode {
    ONCE,
    SESSION
}


class PermissionManager {

    private val sessionGrants =
        mutableSetOf<String>()

    fun isGranted(
        capability: String
    ): Boolean =
        sessionGrants.contains(
            capability
        )

    fun grant(
        capability: String,
        mode: PermissionMode
    ) {

        if (
            mode == PermissionMode.SESSION
        ) {
            sessionGrants.add(
                capability
            )
        }
    }

    fun revokeAll() {
        sessionGrants.clear()
    }
}


class AgentEngine(
    private val tools: List<Tool>
) {

    fun choose(
        prompt: String
    ): Tool? =
        when {

            prompt.startsWith(
                "shell:",
                true
            ) ||
                prompt.startsWith(
                    "termux:",
                    true
                ) ->
                tools.firstOrNull {
                    it.name == "shell"
                }

            prompt.startsWith(
                "git:",
                true
            ) ->
                tools.firstOrNull {
                    it.name == "git"
                }

            prompt.startsWith(
                "godot:",
                true
            ) ||
                prompt.contains(
                    "godot",
                    true
                ) ->
                tools.firstOrNull {
                    it.name == "godot"
                }

            prompt.startsWith(
                "unity:",
                true
            ) ||
                prompt.contains(
                    "unity",
                    true
                ) ->
                tools.firstOrNull {
                    it.name == "unity"
                }

            prompt.startsWith(
                "unreal:",
                true
            ) ||
                prompt.contains(
                    "unreal",
                    true
                ) ->
                tools.firstOrNull {
                    it.name == "unreal"
                }

            prompt.contains(
                "file",
                true
            ) ||
                prompt.contains(
                    "ملف",
                    true
                ) ->
                tools.firstOrNull {
                    it.name == "filesystem"
                }

            else -> null
        }


    fun commandFor(
        prompt: String
    ): Pair<Tool, Map<String, String>>? {

        val tool =
            choose(prompt)
                ?: return null

        return when (
            tool.name
        ) {

            "shell" ->
                tool to mapOf(
                    "command" to
                        prompt.substringAfter(":")
                            .trim()
                )

            "git" ->
                tool to mapOf(
                    "command" to
                        prompt.substringAfter(":")
                            .trim()
                            .ifEmpty {
                                "status --short"
                            }
                )

            "godot" ->

                if (
                    prompt.startsWith(
                        "godot:",
                        true
                    )
                ) {
                    tool to mapOf(
                        "action" to "run",
                        "command" to
                            prompt.substringAfter(":")
                                .trim()
                    )
                } else {
                    tool to emptyMap()
                }

            "unity",
            "unreal" ->
                tool to emptyMap()

            "filesystem" ->
                tool to mapOf(
                    "action" to "list"
                )

            else -> null
        }
    }
}
