package tech.dolch.plantarch

import org.junit.jupiter.api.Test
import java.nio.file.Path

internal class ClassDiagramTest {
    private val testee = ClassDiagram(
        "Class Diagram of PlantArch",
        "Shows the dependencies of the classes in package tech.dolch.plantarch"
    )

    @Test
    fun testDiagram() {
        // add to diagram
        testee.analyzePackage(ClassDiagram::class.java.packageName)
        testee.analyzeClass(Relation::class.java)

        /*
        // actors are not visible on class diagrams
        val developer = Actor("Developer")
        testee.addExternalInteraction(developer, ClassDiagram::class.java).analyzeClass<Any>(null)
        testee.addExternalInteraction(developer, ClassDiagram::class.java).analyzePackage()
         */

        // hide system packages
        testee.getContainer(Any::class.java).isHidden = true
        testee.getContainer(Unit::class.java).isHidden = true

        // expand container for details
        testee.getContainer(ClassDiagram::class.java).isExpanded = true

        // render diagram
        val plantuml = testee.toPlantuml()
        println(plantuml)
        Path.of("target", "Class Diagram.plantuml")
            .toFile().writer().use {
                it.write(plantuml)
            }
    }
}