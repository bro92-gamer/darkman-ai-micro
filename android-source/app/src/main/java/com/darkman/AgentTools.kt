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

    private fun safe(path: String): File? {
        return runCatching {
            File(root, path).canonicalFile
        }.getOrNull()?.takeIf {
            it.path == root.canonicalPath ||
                it.path.startsWith(root.canonicalPath + File.separator)
        }
    }

    override fun execute(
        params: Map<String, String>
    ): ToolResult {

        return try {

            when (params["action"]) {

                "read" -> {
                    val file = safe(
                        params["path"].orEmpty()
                    )

                    if (file == null) {
                        ToolResult(false, "Unsafe path")
                    } else if (!file.isFile) {
                        ToolResult(false, "Not a file")
                    } else {
                        ToolResult(
                            true,
                            file.readText(
                                Charsets.UTF_8
                            ).take(50000)
                        )
                    }
                }

                "write" -> {
                    val file = safe(
                        params["path"].orEmpty()
                    )

                    if (file == null) {
                        ToolResult(false, "Unsafe path")
                    } else {
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
                }

                "search" -> {
                    val query =
                        params["query"].orEmpty()

                    val matches =
                        root.walkTopDown()
                            .filter {
                                it.isFile &&
                                    it.length() < 2_000_000
                            }
                            .mapNotNull { file ->
                                runCatching {
                                    if (
                                        file.readText(
                                            Charsets.UTF_8
                                        ).contains(
                                            query,
                                            true
                                        )
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
                            .filter {
                                it != root
                            }
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


                fals
                e.message ?
            )
        }
    }
}


class TermuxTool(

)

    override val name

    override val de
        "Run a user-au



    ): ToolResult {



            val command =


                    .trim()



                    false,

                )
            } else {

                val process =


                        "-c",

                    )

                        .redirectErrorStre
                        .start(

                if (
                    !p
                        120,
                        TimeUnit.SECONDS





                    ToolR
                        false,



                } el

                    ToolResult(



                            .readTex
                            .take(30000)

                }


        } catch (e: Exception) {


                false,
                e.message ?: "She
            )
        }
    }
}


class GitToo
    private val workDir: File
) : Tool {

    override

    override val descriptio
        "Run an explicitly authoriz

    override fun execute(

    ): ToolResult {

        return try {

            val command =
                para
                    .orEmpty()


            if (command.

                Tool


                )



                val process =

                        "sh",



                        .directory(work
                        .redirectErrorStream(true)


                if (

                        120,


                ) {



                    ToolResult(

                        "Git command timed
                    )



                    ToolResult(

                        process.i
                            .bufferedReader()


                    )
                }


        } catch (e: Exceptio

            ToolResult(

                e.message ?: "Git unavailable"


    }
}


class GodotTool(
    pr
) : Tool {

    override val name = "godot

    override val description =


    override fun execute(
        params: M
    ): ToolResult {

        return try {

            val
                if (

                        root,

                    ).exists()

                    root

                    root.walkTopDown()

                            it.name == "project.godot"

                        ?.parentFile


            if (project == null) {


                    false,
                    "
                )

            } else if
                params["action"] == "ru
            ) {

                val command =

                        .orEmpty()


                if (command.isEmpty()) {


                        false,

                    )

                } else

                    val proc
                        ProcessBuilder
                            "sh",


                        )

                            .
                            .start()





                        )


                        proces

                        ToolResult(

                            "Godot co
                        )



                        Tool
                            process.ex
                            proces
                                .bufferedReader()


                        )



            } else {


                    project.w
                        .count {






                        .count {




                ToolResult







        } catch




            )


}


class UnityTool
    private val root:




    override val d



        params: Map<Strin
    ): Too

        return try {





                    }







                )





                    "Unity pr

            }




                false,
                e.m
            )
        }
    }
}


class UnrealTool(

) : Tool {

    overri

    override val descriptio
        "Detect Unreal Engine projects."


        p




            val project =
                root.walkTopDo





                    }





                    "No .up
                )

            } els

                ToolResult(


                )


        } catch (e: Exception) {


                false,
                e.message ?: "Unr
            )
        }
    }
}


class Projec
    private val root: File
)

    fun d

        val g

                .any {

                }

        val unit

                .any {







        val unreal =

                .any
                    it.extensi


                    )


        return build

            if (

            }





            if (unreal) {



        }.ifEmpty {

        }.joinToSt
    }
}


enum class

    SESSION
}


class PermissionManager

    private val sessionGrants =
        mutableSe



    ): Boolean {


        )
    }

    fun gra
        capability:

    ) {

        if

        ) {


            )
        }
    }


        sessionGrants.c
    }
}


class AgentEngine(
    private v
) {

    fun choose(





            prompt.startsWith(


            ) ||

                    "termux:",


                tools.fir
                    i
                }


            prompt.startsWit

                true


                    it.name == "git"











                tools.firstOrN

                }




                true

                p


                ) ->


                }



                "unreal:",

            ) ||

                    "unreal",


                tools.fi

                }
            }



                true
            ) ||


                    true



                }
            }



    }

    fu
        prompt: String


        val tool =

                ?

        return when (

        ) {

            "shell" -> {



                            .
                )


            "git" -> {



                            .trim()



                )




                if (



                    )

                    tool to mapOf(

                        "command"


                    )


                }


            "unity",
            "u



            "filesys
                t

                )



        }
    }
}
