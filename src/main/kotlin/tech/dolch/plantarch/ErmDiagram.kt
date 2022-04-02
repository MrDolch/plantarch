package tech.dolch.plantarch

import com.tngtech.archunit.base.Optional
import com.tngtech.archunit.core.domain.JavaAccess
import com.tngtech.archunit.core.domain.JavaClass
import com.tngtech.archunit.core.domain.JavaField
import com.tngtech.archunit.core.domain.JavaType
import com.tngtech.archunit.core.importer.ClassFileImporter
import javassist.Modifier
import java.lang.reflect.ParameterizedType

open class ErmDiagram(
    private val name: String = "",
    private val description: String = "",
    private val clazzToAnalyze: MutableSet<Class<*>?> = HashSet()
) {
    companion object {
        private val UNKNOWN = Container("unknown", isHidden = true)
    }

    private val containers: MutableMap<String?, Container> = HashMap()
    private val relations: MutableSet<Relation> = HashSet()

    open fun <T> analyzeClass(clazz: Class<T>?) = clazzToAnalyze.add(clazz)

    open fun analyzePackage(vararg packages: String) = ClassFileImporter()
        .importPackages(*packages)
        .map { it.reflect() }
        .forEach { clazz -> analyzeClass(clazz) }

    private fun addRelation(relation: Relation) {
        if (relation.source != relation.target) relations.add(relation)
    }

    open fun toPlantuml(): String {
        with(ClassFileImporter().importClasses(clazzToAnalyze)) {
            // 1. Hierarchy
            forEach { addImplementRelation(it) }
            forEach { addExtendRelation(it) }
            // 2. DDD
            forEach { addCompositeRelation(it) }
            // 3. Dependencies
            forEach { addUseRelation(it) }
            forEach { addUsedRelation(it) }
        }
        return ("@startuml\n"
                // classes, enums, interfaces, and abstracts
                + containers.values.filter { obj: Container -> obj.isVisible() }
            .filter { obj: Container -> obj.isExpanded }
            .flatMap { obj: Container -> obj.classes }
            .joinToString("\n") { c: Class<*>? -> renderDeclaration(c!!) }
                + "\n"
                // relations
                + relations.map { r: Relation -> this.toPlantuml(r) }
            .sorted()
            .distinct()
            .joinToString("\n")
                + "\ntitle\n$name\nendtitle"
                + "\ncaption\n$description\nendcaption"
                + "\nskinparam linetype polyline\n@enduml")
    }

    private fun renderDeclaration(c: Class<*>): String {
        val type = when {
            c.isInterface -> "interface"
            Modifier.isAbstract(c.modifiers) -> "abstract"
            c.isEnum -> "enum"
            else -> "class"
        }
        return type + " " + c.name + when {
            !clazzToAnalyze.contains(c) -> " #ccc"
            c.isEnum -> "<<data>> #afa{\n" + c.enumConstants.joinToString("\n") + "\n}"
            else -> "<<data>> #afa{\n" + c.declaredFields.joinToString("\n") { f -> f.name + ":" + f.type.simpleName } + "\n}"
        }
    }

    private fun toPlantuml(container: Container): String = String
        .format(
            "object \"%s\" as %d #ccc{\n%s\n}\n",
            container.name, container.id(), container.classes
                .map { it.name }
                .sorted()
                .joinToString("\n")
        )

    private fun toPlantuml(r: Relation): String {
        val sourceContainer = getContainer(r.source)
        val targetContainer = getContainer(r.target)
        if (targetContainer.isHidden) return ""
        return String.format(
            "%s ${r.arrow} %s",
            r.source!!.name,
            if (sourceContainer.isExpanded && targetContainer.isClosed()) targetContainer.id() else r.target.name
        )
    }

    private fun addUseRelation(source: JavaClass) = listOf(source)
        .filter { s -> getContainer(s).isVisible() }
        .flatMap { s -> s.allAccessesFromSelf }
        .map { a -> a.targetOwner }
        .distinct()
        .filter { t -> source != t }
        .filter { t -> !t.isAssignableFrom(source.reflect()) }
        .filter { t -> getContainer(t).isVisible() }
        .filter { t -> relations.none { r -> r.source == source.reflect() && r.target == t.reflect() } }
        .forEach { t -> addRelation(Relation.of(source, t, RelationType.USE)) }

    private fun addUsedRelation(source: JavaClass) = listOf(source)
        .filter { s: JavaClass -> getContainer(s).isVisible() }
        .flatMap { obj: JavaClass -> obj.accessesToSelf }
        .map { obj: JavaAccess<*> -> obj.targetOwner }
        .filter { t: JavaClass -> !source.isAssignableFrom(t.reflect()) }
        .filter { t: JavaClass -> getContainer(t).isVisible() }
        .forEach { t: JavaClass -> addRelation(Relation.of(source, t, RelationType.USED)) }

    private fun addImplementRelation(source: JavaClass) = listOf(source)
        .filter { s: JavaClass -> getContainer(s).isVisible() }
        .flatMap { obj: JavaClass -> obj.interfaces }
        .map { obj: JavaType -> obj.toErasure() }
        .filter { t: JavaClass -> getContainer(t).isVisible() }
        .forEach { t: JavaClass -> addRelation(Relation.of(source, t, RelationType.IMPLEMENT)) }

    private fun addExtendRelation(source: JavaClass) {
        listOf(source)
            .filter { s: JavaClass -> getContainer(s).isVisible() }
            .map { obj: JavaClass -> obj.superclass }
            .flatMap { obj: Optional<JavaType?> -> obj.asSet() }
            .map { obj -> obj!!.toErasure() }
            .filter { t: JavaClass -> getContainer(t).isVisible() }
            .forEach { t: JavaClass -> addRelation(Relation.of(source, t, RelationType.EXTEND)) }
        listOf(source)
            .filter { s: JavaClass -> getContainer(s).isVisible() }
            .filter { s: JavaClass -> !s.isInterface }
            .flatMap { obj: JavaClass -> obj.allSubclasses }
            .map { obj: JavaClass -> obj.toErasure() }
            .filter { t: JavaClass -> getContainer(t).isVisible() }
            .forEach { t: JavaClass -> addRelation(Relation.of(t, source, RelationType.EXTEND)) }
    }

    private fun addCompositeRelation(source: JavaClass) = listOf(source)
        .filter { getContainer(it).isVisible() }
        .filter { s -> s.reflect().kotlin.isData }
        .flatMap { it.allFields }
        .forEach { f: JavaField ->
            val t = f.rawType
            if (MutableCollection::class.java.isAssignableFrom(t.reflect())) {
                val genericType = f.reflect().genericType
                try {
                    for (ta in (genericType as ParameterizedType).actualTypeArguments) {
                        val c = Class.forName(ta.typeName)
                        if (getContainer(c).isVisible())
                            addRelation(Relation.of(source.reflect(), c, RelationType.AGGREGATE))
                    }
                } catch (ignore: ClassNotFoundException) {
//                    ignore.printStackTrace()
                }
            } else if (getContainer(t).isVisible())
                addRelation(Relation.of(source, t, RelationType.COMPOSE))
        }

    private fun getContainer(clazz: JavaClass): Container = getContainer(clazz.reflect())

    fun getContainer(clazz: Class<*>?): Container {
        val result: Container = if (clazz != null
            && !clazz.isAnonymousClass
            && !clazz.isMemberClass
            && !clazz.isSynthetic
            && !clazz.isLocalClass
        ) {
            val resource = clazz.getResource("/" + clazz.name.replace('.', '/') + ".class")
            if (resource != null) {
                val protocol = resource.protocol
                val file = resource.file
                if ("jrt" == protocol)
                    containers.computeIfAbsent("jrt") { name -> Container(name!!, isHidden = true) }
                else if (file.startsWith("file:"))
                    containers.computeIfAbsent(file.replace(".*/([^!]+.jar)!.*".toRegex(), "$1")) {
                        Container(it!!, isHidden = true)
                    }
                else if (file.contains("/target/classes/"))
                    containers.computeIfAbsent(
                        file.replace(".*/([^/]+)/target/classes/.*".toRegex(), "$1")
                    ) { Container(it!!, isHidden = true) }
                else UNKNOWN
            } else UNKNOWN
        } else UNKNOWN
        result.addClass(clazz!!)
        return result
    }

}