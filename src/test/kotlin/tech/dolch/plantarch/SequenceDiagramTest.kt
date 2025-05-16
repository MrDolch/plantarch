package tech.dolch.plantarch

import org.junit.jupiter.api.Test
import java.nio.file.Paths

internal class SequenceDiagramTest {
    @Test
    fun toPlantuml() {
        val testee = SequenceDiagram(
            "Sequence Diagram of PlantArch",
            "Shows the dependencies of the classes in package tech.dolch.plantarch",
            arrayListOf("tech.dolch.plantarch", "com.tngtech.archunit.core.importer"),
        )

        testee.analyzeClass(ClassDiagram::class.java)
            .toPlantuml()
        val plantuml = testee.toPlantuml()
        println(plantuml)
        Paths.get("target", "SequenceDiagram.plantuml")
            .toFile().writer().use {
                it.write(plantuml)
            }
    }
}