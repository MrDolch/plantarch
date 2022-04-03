package tech.dolch.plantarch

import org.junit.jupiter.api.Test
import playground.erm.cars.Car
import java.nio.file.Path

internal class ErmDiagramTest {
    private val testee = ErmDiagram(
        "Erm-Diagram of PlantArch",
        "Shows the fields of the classes in package tech.dolch.plantarch"
    )

    @Test
    fun testDiagram() {
        // add to diagram
        testee.analyzePackage(Car::class.java.packageName)
        testee.analyzeClass(Car::class.java)

        // expand container for details
        testee.getContainer(Car::class.java).isExpanded = true
        testee.getContainer(Car::class.java).isHidden = false

        // render diagram
        val plantuml = testee.toPlantuml()
        println(plantuml)
        Path.of("target", "ERM Diagram.puml")
            .toFile().writer().use {
                it.write(plantuml)
            }
    }
}