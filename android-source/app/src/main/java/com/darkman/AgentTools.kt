package com.darkman

import java.io.File
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

interface Tool {
    val name: String
    val description: String
    fun execute(params: Map<String, String>): ToolResult
}

data class ToolResult(val ok: Boolean, val output: String)

class FileSystemTool(private val root: File) : Tool {
    override val name = "filesystem"
    override val description = "Read, write, list, and search files below the selected project root."

    private fun safe(path: String): File? =
        runCatching { File(root, path).canonicalFile }
            .getOrNull()
            ?.takeIf {
                it.path == root.canonicalPath ||
                it.path.startsWith(root.canonicalPath + File.separator)
            }

    override fun execute(params: Map<String, String>): ToolResult {
        return try {
            when (params["action"]) {
                "read" -> {
                    val f = safe(params["path"].orEmpty())
                        ?: return ToolResult(false, "Unsafe path")
                    ToolResult(
                        f.isFile,
                        if (f.isFile) f.readText(Charsets.UTF_8).take(50000)
                        else "Not a file"
                    )
                }

                "write" -> {
                    val f = safe(params["path"].orEmpty())
                        ?: return ToolResult(false, "Unsafe path")
                    f.parentFile?.mkdirs()
                    f.writeText(params["content"].orEmpty(), Charsets.UTF_8)
                    ToolResult(true, "Wrote ${f.relativeTo(root)}")
                }

                "search" -> {
                    val needle = params["query"].orEmpty()
                    val matches = root.walkTopDown()
                        .filter { it.isFile && it.length() < 2_000_000 }
                        .mapNotNull { f ->
                            runCatching {
                                if (f.readText().contains(needle, true))
                                    f.relativeTo(root).path
                                else null
                            }.getOrNull()
                        }
                        .take(100)
                        .toList()

                    ToolResult(true, matches.joinToString("\n"))
                }

                else -> {
                    ToolResult(
                        true,
                        root.walkTopDown()
                            .filter { it != root }
                            .take(200)
                            .joinToString("\n") { it.relativeTo(root).path }
                    )
                }
            }
        } catch (e: Exception) {
            ToolResult(false, e.message ?: "Filesystem error")
        }
    }
}

class TermuxTool(private val workDir: File) : Tool {
    override val name = "termux"
    override val description =
        "Execute a user-authorized shell command in the configured local/Termux working directory."

    override fun execute(params: Map<String, String>): ToolResult {
        return try {
            val command = params["command"].orEmpty().trim()

            if (
                command.isEmpty() ||
                command.contains("&&") ||
                command.contains(";") ||
                command.contains("|")
            ) {
                return ToolResult(
                    false,
                    "Only one command without shell chaining is allowed"
                )
            }

            val process = ProcessBuilder("sh", "-c", command)
                .directory(workDir)
                .redirectErrorStream(true)
                .start()

            if (!process.waitFor(30, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                return ToolResult(false, "Command timed out")
            }

            ToolResult(
                process.exitValue() == 0,
                process.inputStream.bufferedReader().readText().take(20000)
            )
        } catch (e: Exception) {
            ToolResult(
                false,
                e.message ?: "Shell unavailable; install Termux or choose a project directory"
            )
        }
    }
}

class GodotTool(private val r
    override val name = "godot"
    override val descrip
 

    private val errorPattern = Pattern.compile(
        "\\b(error|parse error|unexpected indent|identifier .* no
        Pattern.CASE_INSENSITIVE
    )

    override fun execute(params: Map<String, String>): ToolResult {
        return try {
         

                    root
                } 
                    root.walkTopDown()
               
                        
                }

   
                return ToolResult(false, "No pro
            }

            val findings = project.walkTopDown()
       
             

                        if (
                            line.conta
                    
                        ) {
         
                        } e
                            null
                        }
                    }
                }
                .take(200)
                .toList()

    
                true,
           
                    if (findings.isEmpty())
                        "No obvi
                    else
                
            )
       
            ToolR

    }
}

class AgentEngine(p

    fun choose(prompt: String): Tool? =
        when {
  
            prompt.contains("gdscript", true) ->
                t

            prompt.contains("file", true)
            prompt.contains("ملف", true) ||
            prompt.contains("
                tools.firstOrNull { it.name == "filesystem" }

    
            promp


            else -> null
   

    fun execute(prompt: String): ToolResult? {
        va

        val params = when (tool.name) {
           
                mapOf("command" to prompt.substringAfter(":").trim())

 
                mapOf("action" to "list")

            else ->
                empty
        }

        return tool.execute(params)
    }
}
