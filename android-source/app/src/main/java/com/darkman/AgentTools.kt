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

private const val MAX_OUTPUT = 30_000
private const val MAX_READ = 50_000

private fun clipOutput(text: String, max: Int = MAX_OUTPUT): String {
    if (text.length <= max) return text
    return text.take(max) + "\n...[output truncated]"
}

private fun runShellCommand(
    command: String,
    workDir: File,
    timeoutSeconds: Long = 120
): ToolResult {
    if (command.isBlank()) return ToolResult(false, "Command is empty")

    return try {
        val process = ProcessBuilder("sh", "-c", command)
            .directory(workDir)
            .redirectErrorStream(true)
            .start()

        val output = process.inputStream.bufferedReader().use { reader ->
            val buffer = StringBuilder()
            val chars = CharArray(4096)
            var total = 0
            while (true) {
                val count = reader.read(chars)
                if (count < 0) break
                if (total < MAX_OUTPUT) {
                    val allowed = minOf(count, MAX_OUTPUT - total)
                    buffer.append(chars, 0, allowed)
                    total += allowed
                }
            }
            buffer.toString()
        }

        if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
            process.destroy()
            if (process.isAlive) process.destroyForcibly()
            return ToolResult(false, "Command timed out after ${timeoutSeconds}s\n${clipOutput(output)}")
        }

        val exit = process.exitValue()
        val resultText = clipOutput(output).trimEnd()
        if (exit == 0) {
            ToolResult(true, "exit=0\n$resultText")
        } else {
            ToolResult(false, "exit=$exit\n$resultText")
        }
    } catch (e: Exception) {
        ToolResult(false, "Command failed: ${e.javaClass.simpleName}: ${e.message}")
    }
}

class FileSystemTool(private val root: File) : Tool {
    override val name = "filesystem"
    override val description = "Read, write, search and list files inside the allowed project root."

    private fun safe(path: String): File? {
        return try {
            val base = root.canonicalFile
            val candidate = File(base, path.ifBlank { "." }).canonicalFile
            val basePath = base.path
            val candidatePath = candidate.path
            if (candidatePath == basePath || candidatePath.startsWith(basePath + File.separator)) {
                candidate
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    override fun execute(params: Map<String, String>): ToolResult {
        return try {
            when (params["action"]?.lowercase()) {
                "read" -> {
                    val file = safe(params["path"].orEmpty())
                        ?: return ToolResult(false, "Unsafe path")
                    if (!file.isFile) return ToolResult(false, "File not found: ${params["path"]}")
                    if (file.length() > MAX_READ) {
                        return ToolResult(false, "File is larger than ${MAX_READ} bytes")
                    }
                    ToolResult(true, file.readText())
                }

                "write" -> {
                    val file = safe(params["path"].orEmpty())
                        ?: return ToolResult(false, "Unsafe path")
                    file.parentFile?.mkdirs()
                    file.writeText(params["content"].orEmpty())
                    ToolResult(true, "Wrote ${file.length()} bytes to ${file.path}")
                }

                "search" -> {
                    val query = params["query"].orEmpty()
                    if (query.isBlank()) return ToolResult(false, "Search query is empty")
                    val matches = mutableListOf<String>()
                    root.walkTopDown()
                        .filter { it.isFile && it.length() <= 2L * 1024L * 1024L }
                        .forEach { file ->
                            if (matches.size >= 100) return@forEach
                            try {
                                if (file.readText().contains(query, ignoreCase = true)) {
                                    matches.add(file.relativeTo(root).path)
                                }
                            } catch (_: Exception) {
                                // Ignore unreadable/binary files.
                            }
                        }
                    ToolResult(true, if (matches.isEmpty()) "No matches" else matches.joinToString("\n"))
                }

                "list" -> {
                    val lines = mutableListOf<String>()
                    root.walkTopDown().forEach { file ->
                        if (lines.size >= 300) return@forEach
                        if (file != root) lines.add(file.relativeTo(root).path)
                    }
                    ToolResult(true, lines.joinToString("\n"))
                }

                else -> ToolResult(false, "Unknown filesystem action. Use read, write, search, or list.")
            }
        } catch (e: Exception) {
            ToolResult(false, "Filesystem error: ${e.javaClass.simpleName}: ${e.message}")
        }
    }
}

class TermuxTool(private val workDir: File) : Tool {
    override val name = "shell"
    override val description = "Execute shell commands after the Android app permission gate. Compound commands are supported."

    override fun execute(params: Map<String, String>): ToolResult {
        val command = params["command"].orEmpty().trim()
        return runShellCommand(command, workDir, 120)
    }
}

class GitTool(private val workDir: File) : Tool {
    override val name = "git"
    override val description = "Inspect and operate on a Git repository. Risky Git operations are still subject to the app permission gate."

    private fun findGitRoot(): File? {
        var current: File? = workDir.canonicalFile
        repeat(8) {
            if (current?.resolve(".git")?.exists() == true) return current
            current = current?.parentFile
        }
        return null
    }

    override fun execute(params: Map<String, String>): ToolResult {
        val command = params["command"].orEmpty().trim()
        if (command.isBlank()) return ToolResult(false, "Git command is empty")

        val repo = findGitRoot()
            ?: return ToolResult(false, "No Git repository found from ${workDir.canonicalPath}. Pass the repository path in a future GitTool configuration.")

        return runShellCommand("git $command", repo, 120)
    }
}

private fun findProjectRoot(root: File, marker: String): File? {
    val base = try { root.canonicalFile } catch (_: Exception) { return null }
    if (base.resolve(marker).exists()) return base

    return try {
        base.walkTopDown()
            .maxDepth(4)
            .firstOrNull { it.isDirectory && it.resolve(marker).exists() }
    } catch (_: Exception) {
        null
    }
}

class GodotTool(private val root: File) : Tool {
    override val name = "godot"
    override val description = "Inspect and run commands against a Godot project."

    override fun execute(params: Map<String, String>): ToolResult {
        val project = findProjectRoot(root, "project.godot")
            ?: return ToolResult(false, "No project.godot found under ${root.path}")

        val command = params["command"]?.trim().orEmpty()
        if (command.isNotEmpty()) {
            return runShellCommand(command, project, 120)
        }

        val gd = project.walkTopDown().count { it.isFile && it.extension.equals("gd", true) }
        val scenes = project.walkTopDown().count { it.isFile && it.extension.equals("tscn", true) }
        return ToolResult(
            true,
            "Godot project detected: ${project.path}\nGDScript files: $gd\nScenes: $scenes"
        )
    }
}

class UnityTool(private val root: File) : Tool {
    override val name = "unity"
    override val description = "Detect and inspect a Unity project."

    override fun execute(params: Map<String, String>): ToolResult {
        val project = try {
            root.canonicalFile.walkTopDown()
                .maxDepth(4)
                .firstOrNull {
                    it.isDirectory &&
                        it.resolve("ProjectSettings").isDirectory &&
                        it.resolve("Assets").isDirectory
                }
        } catch (_: Exception) {
            null
        }

        if (project == null) return ToolResult(false, "No Unity project found under ${root.path}")

        val scripts = project.resolve("Assets").walkTopDown()
            .count { it.isFile && it.extension.equals("cs", true) }
        return ToolResult(true, "Unity project detected: ${project.path}\nC# scripts: $scripts")
    }
}

class UnrealTool(private val root: File) : Tool {
    override val name = "unreal"
    override val description = "Detect and inspect an Unreal Engine project."

    override fun execute(params: Map<String, String>): ToolResult {
        val project = try {
            root.canonicalFile.walkTopDown()
                .maxDepth(5)
                .firstOrNull { it.isFile && it.extension.equals("uproject", true) }
        } catch (_: Exception) {
            null
        }

        if (project == null) return ToolResult(false, "No .uproject file found under ${root.path}")
        return ToolResult(true, "Unreal project detected: ${project.path}")
    }
}

class ProjectDetector(private val root: File) {
    fun detect(): String {
        val godot = findProjectRoot(root, "project.godot")
        if (godot != null) return "Godot: ${godot.path}"

        val unity = try {
            root.canonicalFile.walkTopDown()
                .maxDepth(4)
                .firstOrNull {
                    it.isDirectory &&
                        it.resolve("ProjectSettings").isDirectory &&
                        it.resolve("Assets").isDirectory
                }
        } catch (_: Exception) {
            null
        }
        if (unity != null) return "Unity: ${unity.path}"

        val unreal = try {
            root.canonicalFile.walkTopDown()
                .maxDepth(5)
                .firstOrNull { it.isFile && it.extension.equals("uproject", true) }
        } catch (_: Exception) {
            null
        }
        if (unreal != null) return "Unreal: ${unreal.path}"

        return "Unknown project"
    }
}

enum class PermissionMode {
    ONCE,
    SESSION
}

class PermissionManager {
    private val sessionGrants = mutableSetOf<String>()

    fun isGranted(capability: String): Boolean {
        return sessionGrants.contains(capability)
    }

    fun grant(capability: String, mode: PermissionMode) {
        if (mode == PermissionMode.SESSION) {
            sessionGrants.add(capability)
        }
    }

    fun revokeAll() {
        sessionGrants.clear()
    }
}

class AgentEngine(private val tools: List<Tool>) {
    fun choose(prompt: String): Tool? {
        val text = prompt.trim()
        return when {
            text.startsWith("shell:", true) || text.startsWith("termux:", true) ->
                tools.firstOrNull { it.name == "shell" }

            text.startsWith("git:", true) || text.contains("git status", true) || text.contains("git diff", true) ->
                tools.firstOrNull { it.name == "git" }

            text.startsWith("godot:", true) || text.contains("godot", true) || text.contains("gdscript", true) ->
                tools.firstOrNull { it.name == "godot" }

            text.startsWith("unity:", true) || text.contains("unity", true) ->
                tools.firstOrNull { it.name == "unity" }

            text.startsWith("unreal:", true) || text.contains("unreal", true) || text.contains("uproject", true) ->
                tools.firstOrNull { it.name == "unreal" }

            text.contains("file", true) || text.contains("ملف", true) || text.contains("مشروع", true) ->
                tools.firstOrNull { it.name == "filesystem" }

            else -> null
        }
    }

    fun commandFor(prompt: String): Pair<Tool, Map<String, String>>? {
        val tool = choose(prompt) ?: return null
        val text = prompt.trim()

        val params = when (tool.name) {
            "shell" -> mapOf(
                "command" to text.substringAfter(":", "").trim()
            )

            "git" -> mapOf(
                "command" to text.substringAfter(":", "status --short").trim()
                    .ifBlank { "status --short" }
            )

            "godot" -> {
                if (text.startsWith("godot:", true)) {
                    mapOf("command" to text.substringAfter(":").trim())
                } else {
                    emptyMap()
                }
            }

            "unity", "unreal" -> emptyMap()

            "filesystem" -> mapOf("action" to "list")

            else -> emptyMap()
        }

        return Pair(tool, params)
    }

    fun execute(prompt: String): ToolResult? {
        val command = commandFor(prompt) ?: return null
        return command.first.execute(command.second)
    }
}
