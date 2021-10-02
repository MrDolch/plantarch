package tech.dolch.plantarch

import kotlin.math.absoluteValue

data class Container(
    val name: String,
    var isExpanded: Boolean = false,
    var isHidden: Boolean = false
) {
    val classes: MutableSet<Class<*>> = HashSet()

    fun addClass(clazz: Class<*>) = classes.add(clazz)
    fun isVisible(): Boolean = !isHidden
    fun isClosed(): Boolean = !isExpanded
    fun id(): Int = hashCode().absoluteValue
}