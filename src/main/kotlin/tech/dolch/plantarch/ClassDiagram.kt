package tech.dolch.plantarch

import com.tngtech.archunit.core.domain.JavaClass
import com.tngtech.archunit.core.domain.JavaClasses
import com.tngtech.archunit.core.domain.JavaField
import com.tngtech.archunit.core.domain.JavaType
import com.tngtech.archunit.core.importer.ClassFileImporter
import javassist.util.proxy.MethodHandler
import javassist.util.proxy.ProxyFactory
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*
import kotlin.io.path.name
import kotlin.jvm.optionals.getOrNull

open class ClassDiagram(
  private val name: String = "",
  private val description: String = "",
  private val classesToAnalyze: MutableSet<Class<*>> = HashSet(),
  private val workingDir: Path = Paths.get("").toAbsolutePath()
) {
  companion object {
    private val UNKNOWN = Container("unknown", isHidden = true)
  }

  private val containers: MutableMap<String?, Container> = HashMap()
  private val relations: MutableSet<Relation> = HashSet()
  var useByMemberHidden = false
  var useByReturnHidden = false
  var useByParameterHidden = false
  var useByMethodNames = UseByMethodNames.ARROW

  enum class UseByMethodNames {
    NONE, DEFINITION, ARROW
  }

  var useByMemberColor: String? = null
  var useByReturnTypesColor: String? = null
  var useByParameterColor: String? = null

  open fun <T> analyzeClass(clazz: Class<T>) = classesToAnalyze.add(clazz)

  open fun analyzePackage(vararg packages: String) = ClassFileImporter()
    .importPackages(*packages)
    .map { it.reflect() }
    .forEach { clazz -> analyzeClass(clazz) }

  private fun addRelation(relation: Relation) {
    if (relation.source == relation.target) return
    val find =
      relations.find { r ->
        r.source == relation.source && r.target == relation.target
            && r.type.defaultArrow == relation.type.defaultArrow
      }
    if (find == null) relations.add(relation)
    else if (relation.action != null) {
      if (find.action == null) find.action = relation.action
      else find.action += "\\n" + relation.action
    }
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

  open fun toPlantuml(): String =
    toPlantuml(ClassFileImporter().importClasspath())

  open fun toPlantuml(importClasspath: JavaClasses): String {
    val classnamesToAnalyze = classesToAnalyze.map { it.name }
    with(importClasspath.filter { javaClass -> classnamesToAnalyze.contains(javaClass.fullName) }) {
      // 1. Hierarchy
      forEach { addImplementRelation(it) }
      forEach { addExtendRelation(it) }
      // 2. DDD
      //    forEach { addCompositeRelation(it) }
      // 3. Dependencies
      forEach { addUseRelation(it) }
      forEach { addUsedRelation(it) }
      // 4. Check Dependencies in Context Classes
      val contextClasses = relations.flatMap { listOf(it.source ?: it.target, it.target) }
        .filter { getContainer(it).isExpanded }
        .filter { !classesToAnalyze.contains(it) }
        .distinct()
        .map { importClasspath.get(it.name) }
      contextClasses.forEach { addImplementRelation(it) { t -> contextClasses.contains(t) } }
      contextClasses.forEach { addExtendRelation(it) { t -> contextClasses.contains(t) } }
    }
    return ("@startuml\n"
        // actors
        + relations.sortedBy { it.source?.name }
      .map { obj: Relation -> obj.actor }
      .filter { Objects.nonNull(it) }
      .joinToString("\n") { a -> "() " + a?.name }
        + "\n"
        + toPlantUmlClassesDefinitions()
        + "\n"
        + toPlantUmlInterfaceDefinitions()
        + "\n"
        + toPlantUmlEnumDefinitions()
        + "\n\n"
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
        + "\n\ntitle\n$name\nendtitle"
        + "\n\ncaption\n$description\nendcaption"
        + "\n\nskinparam linetype polyline\n\n@enduml")
  }

  private fun toPlantUmlInterfaceDefinitions(): String = containers.values.filter { obj: Container -> obj.isVisible() }
    .filter { obj: Container -> obj.isExpanded }
    .flatMap { obj: Container -> obj.classes }
    .sortedBy { it.name }
    .filter { obj: Class<*>? -> obj!!.isInterface || Modifier.isAbstract(obj.modifiers) }
    .joinToString("\n") { c ->
      (if (c.isInterface) "interface " else "abstract ") +
          c.name + if (classesToAnalyze.contains(c)) "" else " #ccc" + constantsAndMethods(c)
    }

  private fun toPlantUmlEnumDefinitions(): String = containers.values.filter { obj: Container -> obj.isVisible() }
    .filter { obj: Container -> obj.isExpanded }
    .flatMap { obj: Container -> obj.classes }
    .sortedBy { it.name }
    .filter { obj: Class<*>? -> obj!!.isEnum }
    .joinToString("\n") { c ->
      "enum " + c.name + if (classesToAnalyze.contains(c)) " #afa" else " #ccc" + constantsAndMethods(c)
    }

  private fun toPlantUmlClassesDefinitions(): String {
    return containers.values.filter { obj: Container -> obj.isVisible() }
      .filter { obj: Container -> obj.isExpanded }
      .flatMap { obj: Container -> obj.classes }
      .sortedBy { it.name }
      .filter { aClass: Class<*>? -> !aClass!!.isInterface }
      .filter { aClass: Class<*>? -> !aClass!!.isEnum }
      .filter { c: Class<*>? -> !Modifier.isAbstract(c!!.modifiers) }
      .joinToString("\n") { c: Class<*> ->
        "class " + c.name + when {
          !classesToAnalyze.contains(c) -> " #ccc"
          c.kotlin.isData -> "<<data>> #afa"
          else -> ""
        } + constantsAndMethods(c)
      }
  }

  private fun constantsAndMethods(c: Class<*>): String {
    if (useByMethodNames != UseByMethodNames.DEFINITION) return ""
    val methodsAndConstants = relations.filter { it.target == c }
      .filter { it.action != null }
      .flatMap { it.action!!.split("\\\\n".toRegex()) }
      .sorted().distinct()
    val constants = methodsAndConstants.filter { it[0].isUpperCase() }.joinToString("") { "\n    $it" }
    val methods = methodsAndConstants.filter { it[0].isLowerCase() }.joinToString("") { "\n    $it" }
    return " {$constants\n--$methods\n}\n"
  }

  private fun toPlantuml(container: Container): String = String
    .format(
      "object \"%s\" as %d #ccc{\n%s\n}\n",
      container.name, container.id(), container.classes
        .map { "    " + it.name }
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
        "%s ${r.arrow} %s %s %s",
        r.source!!.name, r.target.name, r.color ?: "",
        if (useByMethodNames != UseByMethodNames.ARROW || r.action.isNullOrBlank()) ""
        else r.action!!.split("\\\\n".toRegex()).sorted().joinToString("\\n", ":")
      )
    }
  }

  private fun addUseRelation(source: JavaClass, targetFilter: (JavaType) -> Boolean = { true }) {
    val visibleSources = listOf(source).filter { s -> isContainerVisible(s) }
    addUseRelation(
      source, RelationType.USES, null, visibleSources
        .flatMap { s -> s.allAccessesFromSelf }
        .groupBy { it.targetOwner }
        .filter { targetFilter.invoke(it.key) }
        .mapValues { (_, group) -> group.map { it.name }.sorted().distinct().joinToString("\\n") }
    )
    if (!useByMemberHidden) addUseRelation(
      source, RelationType.USES_AS_MEMBER, useByMemberColor, visibleSources
        .flatMap { s -> s.allMembers }
        .filter { it.owner == source }
        .filterIsInstance<JavaField>()
        //       .onEach { println(source.name + " " + it) }
        .filter { targetFilter.invoke(it.type) }
        .associate { it.type to null }
    )
    if (!useByParameterHidden) addUseRelation(
      source, RelationType.USES_AS_PARAMETER, useByParameterColor, visibleSources
        .flatMap { s -> s.allMethods }
        .filter { it.owner == source }
        .flatMap { it.parameterTypes }
        .filterIsInstance<JavaClass>()
        .filter { targetFilter.invoke(it) }
        .associateWith { null }
    )
    if (!useByReturnHidden) addUseRelation(
      source, RelationType.USES_AS_RETURN_TYPE, useByReturnTypesColor, visibleSources
        .flatMap { s -> s.allMethods }
        .filter { it.owner == source }
        .filter { targetFilter.invoke(it.rawReturnType) }
        .associate { it.rawReturnType to null }
    )
  }

  private fun addUseRelation(
    source: JavaClass,
    relationType: RelationType,
    color: String?,
    targets: Map<JavaType, String?>,
  ) = targets.forEach { type, action ->
    type.allInvolvedRawTypes.filter { !it.isPrimitive }
      .distinct()
      .filter { t -> source != t }
      .filter { t -> !t.isArray || source != t.componentType }
      .filter { t -> !t.isAssignableFrom(source.reflect()) }
      .filter { t -> isContainerVisible(t) }
//            .filter { t -> relations.none { r -> r.source == source.reflect() && r.target == t.reflect() } }
      .forEach { t ->
        addRelation(
          Relation.of(source, t, relationType, color, action)
        )
      }
  }

  private fun ClassDiagram.isContainerVisible(t: JavaClass) = getContainer(t)?.isVisible() ?: false

  private fun addUsedRelation(source: JavaClass) = listOf(source)
    .filter { s: JavaClass -> isContainerVisible(s) }
    .flatMap { obj: JavaClass ->
      (obj.accessesToSelf.map { it.originOwner } +
          obj.codeUnitCallsToSelf.map { it.originOwner } +
          obj.codeUnitReferencesToSelf.map { it.originOwner } +
          obj.constructorReferencesToSelf.map { it.originOwner } +
          obj.directDependenciesToSelf.map { it.originClass } +
          obj.methodReferencesToSelf.map { it.originOwner }).distinct()
    }.distinct()
    .filter { t: JavaClass -> t != source }
    .filter { t: JavaClass -> !source.isAssignableFrom(t.reflect()) }
    .filter { t: JavaClass -> isContainerVisible(t) }
    .forEach { t: JavaClass ->
      addRelation(Relation.of(t, source, RelationType.USES))
      if (!classesToAnalyze.contains(t.reflect())) addUseRelation(t) { it == source }
    }

  private fun addImplementRelation(source: JavaClass, targetFilter: (JavaType) -> Boolean = { true }) {
    listOf(source)
      .filter { s: JavaClass -> isContainerVisible(s) }
      .flatMap { obj: JavaClass -> obj.interfaces }
      .map { obj: JavaType -> obj.toErasure() }
      .filter { targetFilter.invoke(it) }
      .filter { t: JavaClass -> isContainerVisible(t) }
      .forEach { t: JavaClass -> addRelation(Relation.of(source, t, RelationType.IMPLEMENTS)) }
    listOf(source)
      .filter { s: JavaClass -> s.isInterface }
      .filter { s: JavaClass -> isContainerVisible(s) }
      .flatMap { obj: JavaClass -> obj.subclasses }
      .map { obj: JavaType -> obj.toErasure() }
      .filter { targetFilter.invoke(it) }
      .filter { t: JavaClass -> isContainerVisible(t) }
      .forEach { t: JavaClass -> addRelation(Relation.of(t, source, RelationType.IMPLEMENTS)) }
  }

  private fun addExtendRelation(source: JavaClass, targetFilter: (JavaType) -> Boolean = { true }) {
    listOf(source)
      .filter { s: JavaClass -> isContainerVisible(s) }
      .map { obj: JavaClass -> obj.superclass }
      .filter { it.isPresent }
      .map { it.get() }
      .filterIsInstance<JavaClass>()
      .filter { targetFilter.invoke(it) }
      .filter { t -> isContainerVisible(t) }
      .forEach { t -> addRelation(Relation.of(source, t, RelationType.EXTENDS)) }
    listOf(source)
      .filter { s: JavaClass -> isContainerVisible(s) }
      .filter { s: JavaClass -> !s.isInterface }
      .flatMap { obj: JavaClass -> obj.allSubclasses }
      .map { obj: JavaClass -> obj.toErasure() }
      .filter { targetFilter.invoke(it) }
      .filter { t: JavaClass -> t.superclass.getOrNull() == source } // only direct children
      .filter { t: JavaClass -> isContainerVisible(t) }
      .forEach { t: JavaClass -> addRelation(Relation.of(t, source, RelationType.EXTENDS)) }
  }

  private fun getContainer(clazz: JavaClass): Container? {
    try {
      val reflect = clazz.reflect()
      return getContainer(reflect)
    } catch (_: IllegalAccessError) {
      return null
    }
  }

  private fun getContainer(user: Actor?): Container =
    if (user == null) UNKNOWN
    else containers.computeIfAbsent(user.name) { name -> Container(name!!) }

  fun getContainer(name: String): Container = containers.computeIfAbsent(name) { key -> Container(key!!) }

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
        else if (protocol== "jar" || file.startsWith("file:") || file.startsWith("jar:"))
          containers.computeIfAbsent(file.replace(".*/([^!]+.jar)!.*".toRegex(), "$1")) { Container(it!!) }
        else if (Paths.get(resource.toURI()).startsWith(workingDir))
          containers.computeIfAbsent(workingDir.name) { Container(it!!) }
        else UNKNOWN
      } else UNKNOWN
    } else UNKNOWN
    result.addClass(clazz!!)
    return result
  }

}