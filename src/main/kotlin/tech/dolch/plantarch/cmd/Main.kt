package tech.dolch.plantarch.cmd

import com.tngtech.archunit.core.importer.ClassFileImporter
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import tech.dolch.plantarch.ClassDiagram
import java.io.File
import java.nio.file.Path


fun main() {
    while (true) {
        print("'Ready\n")
        val eingabe = readlnOrNull() ?: return

        if (eingabe.lowercase() == "exit") {
            println("'Shutting down")
            break
        }

        val arguments = Json.decodeFromString<RenderJob>(eingabe)
        val plantuml = renderDiagram(arguments)
        println(plantuml)
    }
}

private fun renderDiagram(parameters: RenderJob) =
    parameters.classDiagrams.let { classDiagramParams ->
        val classLoader = ClassLoader.getSystemClassLoader()
        val classesToAnalyze =
            classDiagramParams.classesToAnalyze
                .map { classname -> classLoader.loadClass(classname) }
                .toMutableSet()
        val classDiagram = ClassDiagram(classDiagramParams.title, classDiagramParams.description, classesToAnalyze)
        classDiagramParams.containersToHide
            .forEach { classDiagram.getContainer(it).isHidden = true }
        classDiagramParams.classesToAnalyze
            .map { classname -> classLoader.loadClass(classname) }
            .forEach { classDiagram.getContainer(it).isExpanded = true }
        classDiagram.useByMemberHidden = false
        classDiagram.useByReturnHidden = false
        classDiagram.useByParameterHidden = false
        classDiagram.useByMethodNamesHidden = !classDiagramParams.showUseByMethodNames
        val importClasspath = ClassFileImporter().importPaths(findLocalClasspathDirs())
        classDiagram.toPlantuml(importClasspath).lines()
            .filter { line -> !classDiagramParams.classesToHide.any { classToHide -> line.contains(classToHide) } }
            .joinToString("\n")
    }

fun findLocalClasspathDirs(): List<Path> = System.getProperty("java.class.path")
    .split(File.pathSeparator)
    .map { File(it) }
    .filter { it.exists() && it.isDirectory }
    .map { it.toPath() }

@Serializable
data class IdeaRenderJob(
    val projectName: String,
    val moduleName: String,
    val classPaths: Set<String>,
    val renderJob: RenderJob,
    val optionPanelState: OptionPanelState,
)

@Serializable
data class OptionPanelState(
    val targetPumlFile: String,
    var showPackages: Boolean,
    var flatPackages: Boolean,
    var classesInFocus: List<String>,
    var classesInFocusSelected: List<String>,
    var hiddenContainers: List<String>,
    var hiddenContainersSelected: List<String>,
    var hiddenClasses: List<String>,
    var hiddenClassesSelected: List<String>,
)

@Serializable
data class RenderJob(val classDiagrams: ClassDiagramParams) {
    @Serializable
    data class ClassDiagramParams(
        var title: String = "",
        var description: String = "",
        var classesToAnalyze: List<String> = emptyList(),
        var containersToHide: List<String> = emptyList(),
        var classesToHide: List<String> = emptyList(),
        var showUseByMethodNames: Boolean = false,
    )
}
