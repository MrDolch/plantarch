package tech.dolch.plantarch

import org.junit.jupiter.api.Test
import playground.erm.jpa.Vehicle
import java.nio.file.Paths
import kotlin.test.assertContains
import playground.erm.jpa.Car as jpa_Car
import playground.erm.pojos.Car as pojo_Car

internal class ErmDiagramTest {
    private val testee = ErmDiagram(
        "Erm-Diagram of PlantArch",
        "Shows the fields of the classes in package tech.dolch.plantarch"
    )

    @Test
    fun testPojosDiagram() {
        // add to diagram
        testee.analyzePackage(pojo_Car::class.java.`package`.name)
        testee.analyzeClass(pojo_Car::class.java)

        // expand container for details
        testee.getContainer(pojo_Car::class.java).isExpanded = true
        testee.getContainer(pojo_Car::class.java).isHidden = false

        // render diagram
        val plantuml = testee.toPlantuml()
        println(plantuml)
        Paths.get("target", "ERM Diagram.puml")
            .toFile().writer().use {
                it.write(plantuml)
            }

        // assert
        assertContains(plantuml, "playground.erm.pojos.Car ||-- playground.erm.pojos.Manufacturer")
        assertContains(plantuml, "playground.erm.pojos.Car ||-- playground.erm.pojos.Type")
        assertContains(plantuml, "playground.erm.pojos.Car ||--{ playground.erm.pojos.Tire")
    }

    @Test
    fun testJpaDiagram() {
        // add to diagram
        testee.analyzePackage(jpa_Car::class.java.`package`.name)
        testee.analyzeClass(jpa_Car::class.java)
        testee.addMarkerInterface(Vehicle::class.java)

        // expand container for details
        testee.getContainer(jpa_Car::class.java).isExpanded = true
        testee.getContainer(jpa_Car::class.java).isHidden = false

        // render diagram
        val plantuml = testee.toPlantuml()
        println(plantuml)
        Paths.get("target", "JPA Diagram.puml")
            .toFile().writer().use {
                it.write(plantuml)
            }

        // assert
        assertContains(plantuml, "playground.erm.jpa.Car ||--|{ playground.erm.jpa.Seat")
        assertContains(plantuml, "playground.erm.jpa.Car ||--|| playground.erm.jpa.Engine")
        assertContains(plantuml, "playground.erm.jpa.Car }|--|{ playground.erm.jpa.Driver")
        assertContains(plantuml, "playground.erm.jpa.Driver -up-|> playground.erm.jpa.Person")
        assertContains(plantuml, "playground.erm.jpa.Owner -up-|> playground.erm.jpa.Person")
        assertContains(plantuml, "playground.erm.jpa.Owner ||--|{ playground.erm.jpa.Car")
    }
}