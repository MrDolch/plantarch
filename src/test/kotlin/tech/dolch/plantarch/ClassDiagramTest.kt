package tech.dolch.plantarch

import org.junit.jupiter.api.Test
import playground.erm.jpa.Car
import java.nio.file.Paths
import kotlin.reflect.KClass

class ClassDiagramTest {

    @Test
    fun docClassDiagram() {
        renderDiagram(ClassDiagram::class)
        renderDiagram(ErmDiagram::class)
        renderDiagram(SequenceDiagram::class)
        renderDiagram(Relation::class, ClassDiagram::class, ErmDiagram::class, SequenceDiagram::class, Actor::class)
    }

    private fun renderDiagram(klass: KClass<out Any>, vararg klasses: KClass<out Any>) {
        val testee = ClassDiagram(
            "Class Diagram of ${klass.simpleName}",
            "Shows the dependencies of ${klass.simpleName}"
        )
        // add to diagram
        //testee.analyzePackage(ClassDiagram::class.java.`package`.name)
        testee.analyzeClass(klass.java)
        klasses.forEach { testee.analyzeClass(it.java) }

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
        testee.getContainer(klass.java).isExpanded = true

        // render diagram
        val plantuml = testee.toPlantuml()
        println(plantuml)
        Paths.get("target", klass.simpleName + ".plantuml")
            .toFile().writer().use {
                it.write(plantuml)
            }
    }

    @Test
    fun testDiagram() {
        val testee = ClassDiagram(
            "Test Class Diagram of Car",
            "Shows the dependencies of the example package jpa"
        )
//        testee.useByMemberHidden = true
        testee.useByMemberColor = "#F00"
        testee.useByReturnTypesColor = "#0F0"
        testee.useByParameterColor = "#AA0"

        // add to diagram
        testee.analyzePackage(Car::class.java.`package`.name)
        testee.analyzeClass(Car::class.java)

        // hide system packages
        testee.getContainer(Any::class.java).isHidden = true
        testee.getContainer(Unit::class.java).isHidden = true

        // expand container for details
        testee.getContainer(Car::class.java).isExpanded = true
        testee.getContainer(Car::class.java).isHidden = false

        // render diagram
        val plantuml = testee.toPlantuml()
        println(plantuml)
        Paths.get("target", "Test_ClassDiagram.plantuml")
            .toFile().writer().use {
                it.write(plantuml)
            }
    }
}