package tech.dolch.plantarch

import javassist.ClassPool
import javassist.Modifier
import javassist.Translator
import java.util.*

open class Recorder(
    private val packagesToAnalyze: Set<String>,
    private val hiddenParticipants: Set<String>,
) : Translator {

    override fun start(pool: ClassPool) {}

    companion object {
        val callers = Stack<String>()
        val recordedSequence = StringBuilder()

        init {
            callers.push("start")
        }

        fun enter(className: String, methodName: String) {
            val caller = callers.peek()
            recordedSequence.append("${simplify(caller)} -> ${simplify(className)}: $methodName\n")
            recordedSequence.append("activate ${simplify(className)}\n")
            callers.push(className);
        }

        fun leave(className: String, methodName: String) {
            callers.pop()
            val caller = callers.peek()
//            recordedSequence.append("${simplify(caller)} <-- ${simplify(className)}\n")
            recordedSequence.append("deactivate ${simplify(className)}\n")
        }

        private fun simplify(className: String) = className
//            .replaceBeforeLast('.', "").replace("$", "_").replace(".", "")
            .replaceBeforeLast('.', "").replace(".", "")
            .replaceAfterLast('$', "").replace("$", "")

        fun getData(): String {
            return recordedSequence.toString()
        }
    }

    private val instrumentedMethod = Stack<String>()

    override fun onLoad(pool: ClassPool, classname: String) {
        val ctClass = pool.getCtClass(classname)
        if (!packagesToAnalyze.contains(ctClass.packageName)
            || hiddenParticipants.contains(classname)
        ) return
        ctClass.methods
            .filter { m -> !Modifier.isNative(m.modifiers) }
            .filter { m -> !Modifier.isAbstract(m.modifiers) }
            .filter { m -> !Modifier.isFinal(m.modifiers) }
            .filter { m -> !Modifier.isPrivate(m.modifiers) }
            .filter { m -> !m.declaringClass.isFrozen }
            .forEach { m ->
                val className = m.declaringClass.name
                val methodName = m.name
                val signatur = className + ":" + methodName + ":" + m.signature
                if (!instrumentedMethod.contains(signatur)) {
                    instrumentedMethod.add(signatur)
                    m.insertBefore("tech.dolch.plantarch.Recorder.Companion.enter(\"${className}\",\"${methodName}\");")
                    m.insertAfter("tech.dolch.plantarch.Recorder.Companion.leave(\"${className}\",\"${methodName}\");")
                }
            }

    }
}
