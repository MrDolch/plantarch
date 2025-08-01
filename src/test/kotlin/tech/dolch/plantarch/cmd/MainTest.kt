package tech.dolch.plantarch.cmd

import tech.dolch.plantarch.ClassDiagram
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertContains

class MainTest {
  @Test
  fun test() {
    val jobParams = RenderJob(
      classDiagrams = RenderJob.ClassDiagramParams(
        title = "Dependencies of ClassDiagram",
        description = "",
        classesToAnalyze = listOf(ClassDiagram::class.qualifiedName!!),
        containersToHide = listOf("jrt"),
        projectDir = Paths.get(".").toAbsolutePath().parent.toString(),
        showUseByMethodNames = ClassDiagram.UseByMethodNames.DEFINITION
      )
    )
    val plantuml = renderDiagram(jobParams)

    Paths.get("src/test/resources/expected.puml")
      .toFile().writer().use {
        it.write(plantuml)
      }

    assertContains(plantuml, "class tech.dolch.plantarch.Actor #ccc {\n--\n    getName\n}")
  }
}