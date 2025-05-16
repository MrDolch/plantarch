package tech.dolch.plantarch

import com.tngtech.archunit.core.domain.JavaClass
import com.tngtech.archunit.core.domain.JavaField
import com.tngtech.archunit.core.domain.JavaType
import com.tngtech.archunit.core.importer.ClassFileImporter
import java.lang.IllegalArgumentException
import java.lang.reflect.Modifier
import kotlin.reflect.jvm.kotlinProperty

open class ErmDiagram(
    private val name: String = "",
    private val description: String = "",
    private val clazzToAnalyze: MutableSet<Class<*>> = HashSet(),
    private val markerInterfaces: MutableSet<Class<*>> = HashSet()
) {
    companion object {
        private val UNKNOWN = Container("unknown", isHidden = true)
    }

    private val containers: MutableMap<String?, Container> = HashMap()
    private val relations: MutableSet<Relation> = HashSet()

    open fun <T> analyzeClass(clazz: Class<T>) = clazzToAnalyze.add(clazz)

    open fun <T> addMarkerInterface(clazz: Class<T>) =
        if (clazz.isInterface) markerInterfaces.add(clazz)
        else throw IllegalArgumentException("$clazz is not an Interface.")

    open fun analyzePackage(vararg packages: String) = ClassFileImporter()
        .importPackages(*packages)
        .map { it.reflect() }
        .forEach { clazz -> analyzeClass(clazz) }

    private fun addRelation(relation: Relation) {
        if (relation.source != relation.target) relations.add(relation)
    }

    open fun toPlantuml(): String {
        val javaClasses = ClassFileImporter().importClasses(clazzToAnalyze).sortedBy { it.fullName }
        with(javaClasses) {
            // 1. Hierarchy
            forEach { addInheritanceRelations(it) }
            // 2. DDD
            forEach { addCompositionRelations(it) }
        }
        combineRelations()


        return ("@startuml\n"
                // classes, enums, interfaces, and abstracts
                + containers.values.filter { obj: Container -> obj.isVisible() }
            .filter { obj: Container -> obj.isExpanded }
            .flatMap { obj: Container -> obj.classes }
            .sortedBy { it.name }
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

    private fun combineRelations() {
        val doubleRelations = relations
            .sortedBy { it.source!!.name }
            .groupBy { sortedSetOf(it.source!!.name, it.target.name) }
            .filterValues { it.size == 2 }
            .values
        doubleRelations.forEach { (r1, r2) ->
            combiners
                .filterValues { it.first.contains(r1.type) }
                .filterValues { it.second.contains(r2.type) }
                .firstNotNullOfOrNull { combiner ->
                    if (relations.remove(r1) && relations.remove(r2))
                        addRelation(Relation.of(r1.source, r1.target, combiner.key))
                }
            combiners
                .filterValues { it.first.contains(r2.type) }
                .filterValues { it.second.contains(r1.type) }
                .firstNotNullOfOrNull { combiner ->
                    if (relations.remove(r1) && relations.remove(r2))
                        addRelation(Relation.of(r2.source, r2.target, combiner.key))
                }
            if (relations.contains(r1))
                println("Combination not found: ${r1.type} / ${r2.type}\n   $r1\n   $r2")
        }
    }


    private val combiners = mapOf(
        Pair(
            RelationType.EXACTLY_ONE_TO_ONE_OR_MANY, Pair(
                arrayOf(RelationType.EXACTLY_ONE_TO_MANY, RelationType.EXACTLY_ONE_TO_ONE_OR_MANY),
                arrayOf(RelationType.EXACTLY_ONE_TO_UNKNOWN, RelationType.ONE_OR_MANY_TO_EXACTLY_ONE)
            )
        ),
        Pair(
            RelationType.EXACTLY_ONE_TO_EXACTLY_ONE,
            Pair(arrayOf(RelationType.EXACTLY_ONE_TO_UNKNOWN), arrayOf(RelationType.EXACTLY_ONE_TO_UNKNOWN))
        ),
        Pair(
            RelationType.ONE_OR_MANY_TO_ONE_OR_MANY,
            Pair(arrayOf(RelationType.EXACTLY_ONE_TO_MANY), arrayOf(RelationType.EXACTLY_ONE_TO_MANY))
        )
    )

    private fun renderDeclaration(c: Class<*>): String {
        if (markerInterfaces.contains(c)) return "";
        val type = when {
            c.isInterface -> "interface"
            Modifier.isAbstract(c.modifiers) -> "abstract"
            c.isEnum -> "enum"
            else -> "class"
        }
        val ms = markerInterfaces.filter { it.isAssignableFrom(c) }
        val marker = if (ms.isEmpty()) "" else ms.joinToString(", ", "<<", ">>") { it.simpleName }

        return type + " " + c.name + when {
            !clazzToAnalyze.contains(c) -> " #ccc"
            c.isEnum -> "$marker #afa{\n" + c.enumConstants.joinToString("\n") + "\n}"
            else -> "$marker #afa{\n" + c.declaredFields.joinToString("\n") { f -> f.name + ":" + f.type.simpleName } + "\n}"
        }
    }

    private fun toPlantuml(r: Relation): String {
        val sourceContainer = getContainer(r.source!!)
        val targetContainer = getContainer(r.target)
        if (targetContainer.isHidden) return ""
        return String.format(
            "%s ${r.arrow} %s",
            r.source.name,
            if (sourceContainer.isExpanded && targetContainer.isClosed()) targetContainer.id() else r.target.name
        )
    }

    private fun addInheritanceRelations(source: JavaClass) {
        listOf(source)
            .filter { s: JavaClass -> getContainer(s).isVisible() }
            .flatMap { obj: JavaClass -> obj.interfaces }
            .sortedBy { it.name }
            .map { obj: JavaType -> obj.toErasure() }
            .filter { !markerInterfaces.contains(it.reflect()) }
            .filter { t: JavaClass -> getContainer(t).isVisible() }
            .forEach { t: JavaClass -> addRelation(Relation.of(source, t, RelationType.IMPLEMENTS)) }
        listOf(source)
            .filter { s: JavaClass -> getContainer(s).isVisible() }
            .map { obj: JavaClass -> obj.superclass }
            .filter { it.isPresent }
            .map { it.get() }
            .filterIsInstance<JavaClass>()
            .filter { t: JavaClass -> getContainer(t).isVisible() }
            .forEach { t: JavaClass -> addRelation(Relation.of(source, t, RelationType.EXTENDS)) }
        listOf(source)
            .filter { s: JavaClass -> getContainer(s).isVisible() }
            .filter { s: JavaClass -> !s.isInterface }
            .flatMap { obj: JavaClass -> obj.allSubclasses }
            .sortedBy { it.name }
            .map { obj: JavaClass -> obj.toErasure() }
            .filter { t: JavaClass -> t.superclass == source } // only direct children
            .filter { t: JavaClass -> getContainer(t).isVisible() }
            .forEach { t: JavaClass -> addRelation(Relation.of(t, source, RelationType.EXTENDS)) }
    }

    private fun addCompositionRelations(source: JavaClass) = listOf(source)
        .filter { getContainer(it).isVisible() }
        .filter { s -> isEntity(s) }
        .flatMap { it.fields }
        .sortedBy { it.name }
        .forEach { jf: JavaField ->
            val t = jf.rawType
            val f = jf.reflect()
            val srcIsNullable = f.kotlinProperty?.returnType?.isMarkedNullable ?: false
            when {
                Collection::class.java.isAssignableFrom(t.reflect()) -> {
                    for (ta in jf.allInvolvedRawTypes.sortedBy { it.fullName }) {
                        try {
                            val genericType = ta.reflect()
                            if (getContainer(genericType).isVisible()) {
                                val toMany =
                                    if (srcIsNullable) RelationType.ZERO_OR_ONE_TO_MANY
                                    else RelationType.EXACTLY_ONE_TO_MANY
                                addRelation(Relation.of(source.reflect(), genericType, toMany))
                            }
                        } catch (ignore: ClassNotFoundException) {
                        }
                    }
                }

                getContainer(t).isVisible() -> {
                    val toZeroOrOne =
                        if (srcIsNullable) RelationType.ZERO_OR_ONE_TO_UNKNOWN
                        else RelationType.EXACTLY_ONE_TO_UNKNOWN
                    addRelation(Relation.of(source, t, toZeroOrOne))
                }
            }
        }

    private fun isEntity(c: JavaClass): Boolean =
        c.reflect().kotlin.isData
                || c.simpleName.endsWith("Entity") || c.simpleName.endsWith("Dto")
                || c.annotations.map { a -> "Entity" == a.javaClass.simpleName }.any()

    private fun getContainer(clazz: JavaClass): Container = getContainer(clazz.reflect())

    fun getContainer(clazz: Class<*>): Container {
        val resource = clazz.getResource("/" + clazz.name.replace('.', '/') + ".class")
        val protocol = resource?.protocol
        val file = resource?.file
        val result: Container =
            when {
                clazz.isAnonymousClass -> UNKNOWN
                clazz.isMemberClass -> UNKNOWN
                clazz.isSynthetic -> UNKNOWN
                clazz.isLocalClass -> UNKNOWN
                resource == null -> UNKNOWN
                "jrt" == protocol ->
                    containers.computeIfAbsent("jrt") { name -> Container(name!!, isHidden = true) }

                file!!.startsWith("file:") ->
                    containers.computeIfAbsent(file.replace(".*/([^!]+.jar)!.*".toRegex(), "$1")) {
                        Container(it!!, isHidden = true)
                    }

                file.matches(".*/([^/]+)/target/(test-)?classes/.*".toRegex()) ->
                    containers.computeIfAbsent(file.replace(".*/([^/]+)/target/(test-)?classes/.*".toRegex(), "$1")) {
                        Container(it!!, isHidden = true)
                    }

                else -> UNKNOWN
            }
        result.addClass(clazz)
        return result
    }

}