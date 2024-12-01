package tech.dolch.plantarch

import com.tngtech.archunit.core.domain.JavaAccess
import com.tngtech.archunit.core.domain.JavaClass
import com.tngtech.archunit.core.domain.JavaField
import com.tngtech.archunit.core.domain.JavaType
import com.tngtech.archunit.core.importer.ClassFileImporter
import javassist.Modifier
import javassist.util.proxy.MethodHandler
import javassist.util.proxy.ProxyFactory
import java.lang.reflect.Method
import java.lang.reflect.ParameterizedType
import java.util.*

open class ClassDiagram(
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

    fun <T> addExternalInteraction(p: Actor?, clazz: Class<T>): T {
        val factory = ProxyFactory()
        factory.superclass = clazz
        factory.setFilter { method -> Modifier.isPublic(method.modifiers) }
        val handler = MethodHandler { _: Any?, thisMethod: Method, _: Method?, _: Array<Any?>? ->
            addRelation(Relation.ofUserInteraction(p, clazz, thisMethod.name))
            null
        }
        return factory.create(arrayOfNulls(0), arrayOfNulls(0), handler) as T
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
                // actors
                + relations.map { obj: Relation -> obj.actor }
            .filter { Objects.nonNull(it) }
            .joinToString("\n") { a -> "() " + a?.name }
                + "\n"
                // classes
                + containers.values.filter { obj: Container -> obj.isVisible() }
            .filter { obj: Container -> obj.isExpanded }
            .flatMap { obj: Container -> obj.classes }
            .filter { aClass: Class<*>? -> !aClass!!.isInterface }
            .filter { c: Class<*>? -> !Modifier.isAbstract(c!!.modifiers) }
            .joinToString("\n") { c: Class<*>? ->
                "class " + c!!.name + when {
                    !clazzToAnalyze.contains(c) -> " #ccc"
                    c.kotlin.isData -> "<<data>> #afa"
                    else -> ""
                }
            }
                + "\n"
                // interfaces
                + containers.values.filter { obj: Container -> obj.isVisible() }
            .filter { obj: Container -> obj.isExpanded }
            .flatMap { obj: Container -> obj.classes }
            .filter { obj: Class<*>? -> obj!!.isInterface }
            .filter { c: Class<*>? -> Modifier.isAbstract(c!!.modifiers) }
            .joinToString("\n") { c ->
                (if (c!!.isInterface) "interface " else "abstract ") +
                        c.name + if (clazzToAnalyze.contains(c)) "" else " #ccc"
            }
                + "\n"
                // enum
                + containers.values.filter { obj: Container -> obj.isVisible() }
            .filter { obj: Container -> obj.isExpanded }
            .flatMap { obj: Container -> obj.classes }
            .filter { obj: Class<*>? -> obj!!.isEnum }
            .joinToString("\n") { c -> "enum " + c.name + if (clazzToAnalyze.contains(c)) " #afa" else " #ccc" }
                + "\n"
                // libraries
                + containers.values.asSequence()
            .filter { obj: Container -> obj.isVisible() }
            .filter { obj: Container -> obj.isClosed() }
            .map { container: Container -> this.toPlantuml(container) }
            .sorted()
            .distinct()
            .joinToString("\n")
                // relations
                + relations.map { r: Relation -> this.toPlantuml(r) }
            .sorted()
            .distinct()
            .joinToString("\n")
                + "\ntitle\n$name\nendtitle"
                + "\ncaption\n$description\nendcaption"
                + "\nskinparam linetype polyline\n@enduml")
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
        val userContainer = getContainer(r.actor)
        val targetContainer = getContainer(r.target)
        if (userContainer.isHidden && sourceContainer.isHidden
            || targetContainer.isHidden
            || userContainer.isHidden && sourceContainer.isClosed() && targetContainer.isClosed()
        ) return ""
        return if ((userContainer.isVisible() || sourceContainer.isExpanded) && targetContainer.isClosed()) {
            when (r.type) {
                RelationType.USER_INTERACTS -> String.format(
                    "%s ${r.arrow} %s : %s",
                    r.actor!!.name, targetContainer.id(), r.label
                )
                else -> String.format(
                    "%s ${r.arrow} %s",
                    r.source!!.name, targetContainer.id()
                )
            }
        } else when (r.type) {
            RelationType.USER_INTERACTS -> String.format(
                "%s ${r.arrow} %s : %s",
                r.actor!!.name, r.target.name, r.label
            )
            else -> String.format(
                "%s ${r.arrow} %s ",
                r.source!!.name, r.target.name
            )
        }
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
        .forEach { t -> addRelation(Relation.of(source, t, RelationType.USES)) }

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
        .forEach { t: JavaClass -> addRelation(Relation.of(source, t, RelationType.IMPLEMENTS)) }

    private fun addExtendRelation(source: JavaClass) {
        listOf(source)
            .filter { s: JavaClass -> getContainer(s).isVisible() }
            .map { obj: JavaClass -> obj.superclass }
            .filter { it.isPresent }
            .map { it.get() }
            .filterIsInstance<JavaClass>()
            .filter { t -> getContainer(t).isVisible() }
            .forEach { t -> addRelation(Relation.of(source, t, RelationType.EXTENDS)) }
        listOf(source)
            .filter { s: JavaClass -> getContainer(s).isVisible() }
            .filter { s: JavaClass -> !s.isInterface }
            .flatMap { obj: JavaClass -> obj.allSubclasses }
            .map { obj: JavaClass -> obj.toErasure() }
            .filter { t: JavaClass -> getContainer(t).isVisible() }
            .forEach { t: JavaClass -> addRelation(Relation.of(t, source, RelationType.EXTENDS)) }
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
                            addRelation(Relation.of(source.reflect(), c, RelationType.AGGREGATES))
                    }
                } catch (ignore: ClassNotFoundException) {
//                    ignore.printStackTrace()
                }
            } else if (getContainer(t).isVisible())
                addRelation(Relation.of(source, t, RelationType.COMPOSES))
        }

    private fun getContainer(clazz: JavaClass): Container = getContainer(clazz.reflect())
    private fun getContainer(user: Actor?): Container =
        if (user == null) UNKNOWN
        else containers.computeIfAbsent(user.name) { name -> Container(name!!) }

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
                    containers.computeIfAbsent("jrt") { name -> Container(name!!) }
                else if (file.startsWith("file:"))
                    containers.computeIfAbsent(file.replace(".*/([^!]+.jar)!.*".toRegex(), "$1")) { Container(it!!) }
                else if (file.contains("/target/classes/"))
                    containers.computeIfAbsent(
                        file.replace(
                            ".*/([^/]+)/target/classes/.*".toRegex(),
                            "$1"
                        )
                    ) { Container(it!!) }
                else UNKNOWN
            } else UNKNOWN
        } else UNKNOWN
        result.addClass(clazz!!)
        return result
    }

}