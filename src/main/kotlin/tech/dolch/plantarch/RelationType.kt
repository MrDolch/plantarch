package tech.dolch.plantarch

enum class RelationType(val defaultArrow: String) {
    USES("..>"),
    USES_AS_MEMBER("..>"),
    USES_AS_PARAMETER("..>"),
    USES_AS_RETURN_TYPE("..>"),
    AGGREGATES("*-"),
    COMPOSES("\"*\" o- \"1\""),
    IMPLEMENTS(".up.|>"),
    IMPLEMENTED_BY("<|.."),
    EXTENDS("-up-|>"),
    EXTENDED_BY("<|--"),
    USER_INTERACTS(".right.>"),

    // ERM
    UNKNOWN_TO_UNKNOWN("--"),
    ZERO_OR_ONE_TO_UNKNOWN("|o--"),
    EXACTLY_ONE_TO_UNKNOWN("||--"),
    ZERO_OR_MANY_TO_UNKNOWN("}o--"),
    ONE_OR_MANY_TO_UNKNOWN("}|--"),

    UNKNOWN_TO_ZERO_OR_ONE("--o|"),
    ZERO_OR_ONE_TO_ZERO_OR_ONE("|o--o|"),
    EXACTLY_ONE_TO_ZERO_OR_ONE("||--o|"),
    ZERO_OR_MANY_TO_ZERO_OR_ONE("}o--o|"),
    ONE_OR_MANY_TO_ZERO_OR_ONE("}|--o|"),

    UNKNOWN_TO_EXACTLY_ONE("--||"),
    ZERO_OR_ONE_TO_EXACTLY_ONE("|o--||"),
    EXACTLY_ONE_TO_EXACTLY_ONE("||--||"),
    ZERO_OR_MANY_TO_EXACTLY_ONE("}o--||"),
    ONE_OR_MANY_TO_EXACTLY_ONE("}|--||"),

    UNKNOWN_TO_ZERO_OR_MANY("--o{"),
    ZERO_OR_ONE_TO_ZERO_OR_MANY("|o--o{"),
    EXACTLY_ONE_TO_ZERO_OR_MANY("||--o{"),
    ZERO_OR_MANY_TO_ZERO_OR_MANY("}o--o{"),
    ONE_OR_MANY_TO_ZERO_OR_MANY("}|--o{"),

    UNKNOWN_TO_ONE_OR_MANY("--|{"),
    ZERO_OR_ONE_TO_ONE_OR_MANY("|o--|{"),
    EXACTLY_ONE_TO_ONE_OR_MANY("||--|{"),
    ZERO_OR_MANY_TO_ONE_OR_MANY("}o--|{"),
    ONE_OR_MANY_TO_ONE_OR_MANY("}|--|{"),

    UNKNOWN_TO_MANY("--{"),
    ZERO_OR_ONE_TO_MANY("|o--{"),
    EXACTLY_ONE_TO_MANY("||--{"),
    ZERO_OR_MANY_TO_MANY("}o--{"),
    ONE_OR_MANY_TO_MANY("}|--{"),
}