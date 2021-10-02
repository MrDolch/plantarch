package tech.dolch.plantarch

import com.tngtech.archunit.core.domain.JavaClass

data class Relation(
    val actor: Actor? = null,
    val source: Class<*>? = null,
    val target: Class<*>,
    val label: String? = null,
    val type: RelationType,
    val arrow: String = type.defaultArrow
) {
    companion object {
        fun of(s: JavaClass, t: JavaClass, type: RelationType): Relation =
            Relation(source = s.reflect(), target = t.reflect(), type = type)

        fun of(s: Class<*>?, t: Class<*>, type: RelationType): Relation =
            Relation(source = s, target = t, type = type)

        fun ofUserInteraction(p: Actor?, t: Class<*>, label: String?): Relation =
            Relation(actor = p, target = t, label = label, type = RelationType.USERINTERACTION)

        fun ofData(s: Class<*>?, t: Class<*>, type: RelationType): Relation =
            Relation(source = s, target = t, type = type)
    }
}