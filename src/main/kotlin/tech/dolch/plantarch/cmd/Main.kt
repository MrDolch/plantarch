package tech.dolch.plantarch.cmd

import com.tngtech.archunit.core.importer.ClassFileImporter
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import tech.dolch.plantarch.ClassDiagram
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.name


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

fun renderDiagram(parameters: RenderJob) =
  parameters.classDiagrams.let { classDiagramParams ->
    val classLoader = ClassLoader.getSystemClassLoader()
    val classesToAnalyze =
      classDiagramParams.classesToAnalyze.mapNotNull { runCatching { classLoader.loadClass(it) }.getOrNull() }
        .toMutableSet()
    val classDiagram = ClassDiagram(
      name = classDiagramParams.title,
      description = classDiagramParams.description,
      classesToAnalyze = classesToAnalyze,
      workingDirs = (listOf(classDiagramParams.projectDir) + classDiagramParams.moduleDirs)
        .distinct().map { Paths.get(it) },
    ).apply {
      (classDiagramParams.moduleDirs + classDiagramParams.projectDir).forEach {
        getContainer(Paths.get(it).name).isExpanded = true
      }
      classDiagramParams.containersToHide.forEach { getContainer(it).isHidden = true }
      classesToAnalyze.forEach { getContainer(it).isExpanded = true }
      classDiagramParams.packagesToAnalyze.forEach { analyzePackage(it) }
      useByMemberHidden = false
      useByReturnHidden = false
      useByParameterHidden = false
      useByMethodNames = classDiagramParams.showUseByMethodNames
    }
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
  var showPackages: ShowPackages,
  var classesInFocus: List<String>,
  var classesInFocusSelected: List<String>,
  var hiddenContainers: List<String>,
  var hiddenContainersSelected: List<String>,
  var hiddenClasses: List<String>,
  var hiddenClassesSelected: List<String>,
)

enum class ShowPackages {
  NONE, NESTED, FLAT
}

@Serializable
data class RenderJob(val classDiagrams: ClassDiagramParams) {
  @Serializable
  data class ClassDiagramParams(
    var title: String = "",
    var description: String = "",
    var packagesToAnalyze: List<String> = emptyList(),
    var classesToAnalyze: List<String> = emptyList(),
    var containersToHide: List<String> = emptyList(),
    var classesToHide: List<String> = emptyList(),
    var showUseByMethodNames: ClassDiagram.UseByMethodNames = ClassDiagram.UseByMethodNames.NONE,
    var projectDir: String,
    var moduleDirs: List<String> = emptyList(),
  )
}
