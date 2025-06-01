package tech.dolch.plantarch.cmd

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import tech.dolch.plantarch.ClassDiagram


fun main() {
    while (true) {
        print("'Ready\n")
        val eingabe = readlnOrNull() ?: return

        if (eingabe.lowercase() == "exit") {
            println("'Shutting down")
            break
        }

        val arguments = Json.decodeFromString<RenderJob>(eingabe)
        val plantumls = renderDiagrams(arguments)
        println(plantumls)
    }
}

/*
 {"classDiagrams":[{"title":"Class Diagram","description":"Description","classesToAnalyze":["tech.dolch.plantarch.ClassDiagram"],"containersToHide":["kotlin.Pair","java.lang.String"],"containersToExpand":["tech.dolch.plantarch.ClassDiagram"]}]}
 */
private fun renderDiagrams(parameters: RenderJob) =
    parameters.classDiagrams.joinToString(separator = "\n\n") { classDiagramParams ->
        val classLoader = ClassLoader.getSystemClassLoader()
        val classesToAnalyze =
            classDiagramParams.classesToAnalyze
                .map { classname -> classLoader.loadClass(classname) }
                .toMutableSet()
        val classDiagram = ClassDiagram(classDiagramParams.title, classDiagramParams.description, classesToAnalyze)
        classDiagramParams.containersToHide
            .forEach { classDiagram.getContainer(it)?.isHidden = true }
        classDiagramParams.classesToAnalyze
            .map { classname -> classLoader.loadClass(classname) }
            .forEach { classDiagram.getContainer(it).isExpanded = true }
        classDiagram.useByMemberHidden = false
        classDiagram.useByReturnHidden = false
        classDiagram.useByParameterHidden = false
        classDiagram.useByMethodNamesHidden = !classDiagramParams.showUseByMethodNames
        classDiagram.toPlantuml().lines()
            .filter { line -> !classDiagramParams.classesToHide.any { classToHide -> line.contains(classToHide) } }
            .joinToString("\n")
    }


@Serializable
data class RenderJob(val classDiagrams: List<ClassDiagramParams>) {
    @Serializable
    data class ClassDiagramParams(
        val title: String = "",
        val description: String = "",
        val classesToAnalyze: List<String> = emptyList(),
        val containersToHide: List<String> = emptyList(),
        val classesToHide: List<String> = emptyList(),
        val showUseByMethodNames: Boolean = false,
    )
}