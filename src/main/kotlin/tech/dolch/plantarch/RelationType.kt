package tech.dolch.plantarch

enum class RelationType(val defaultArrow: String) {
    USE("..>"),
    USED("<.."),
    AGGREGATE("*-"),
    COMPOSE("\"*\" o- \"1\""),
    IMPLEMENT(".up.|>"),
    EXTEND("-up-|>"),
    USERINTERACTION(".right.>")
}