package tech.dolch.plantarch

import javassist.ClassPool
import javassist.CtClass
import javassist.Modifier
import javassist.Translator
import java.util.*

open class Recorder(
    private val packagesToAnalyze: Set<String>,
) : Translator {

    override fun start(pool: ClassPool) {}

    companion object {
        var deep: Int = 0
        val callers = Stack<String>()
        val recordedSequence = StringBuilder()

        init {
            callers.push("start")
        }

        fun enter(className: String, methodName: String) {
            val caller = callers.peek()
            recordedSequence.append("${simplify(caller)} -> ${simplify(className)}: $methodName\n")
            recordedSequence.append("activate ${simplify(className)}\n")
            deep++
            callers.push(className);
        }

        fun leave(className: String, methodName: String) {
            deep--
            callers.pop()
            val caller = callers.peek()
            recordedSequence.append("${simplify(caller)} <-- ${simplify(className)}\n")
            recordedSequence.append("deactivate ${simplify(className)}\n")
        }

        private fun simplify(className: String) = className
            .replaceBeforeLast('.', "").replace(".", "")
            .replaceAfterLast('$', "").replace("$", "")

        fun getData(): String {
            return recordedSequence.toString()
        }
    }

    override fun onLoad(pool: ClassPool, classname: String) {
        val ctClass = pool.getCtClass(classname)
        if (packagesToAnalyze.contains(ctClass.packageName)) {
            ctClass.methods
                .filter { m -> !Modifier.isNative(m.modifiers) }
                .filter { m -> !Modifier.isAbstract(m.modifiers) }
                .filter { m -> !Modifier.isFinal(m.modifiers) }
                .filter { m -> !Modifier.isPrivate(m.modifiers) }
                .filter { m -> !m.declaringClass.isFrozen }
                .forEach { m ->
                    val className = (m.declaringClass as CtClass).name
                    val methodName = m.name
                    m.insertBefore("tech.dolch.plantarch.Recorder.Companion.enter(\"${className}\",\"${methodName}\");")
                    m.insertAfter("tech.dolch.plantarch.Recorder.Companion.leave(\"${className}\",\"${methodName}\");")
                }
        }
    }
}
