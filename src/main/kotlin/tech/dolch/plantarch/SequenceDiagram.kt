package tech.dolch.plantarch

import javassist.ClassPool
import javassist.Loader
import javassist.util.proxy.ProxyFactory


open class SequenceDiagram(
    private val name: String,
    private val description: String,
    private val packagesToAnalyze: ArrayList<String> = ArrayList(),
) {
    private var methodName: String? = null
    private var methodParams: Array<Any>? = null
    private var clazzName: String? = null
    private var constructorParams: Array<out Any>? = null

    private var participants: ArrayList<String> = ArrayList()
    private var hiddenParticipants: ArrayList<String> = ArrayList()


    @Suppress("UNCHECKED_CAST")
    fun <T> analyzeClass(clazz: Class<T>, vararg parameters: Any): T {
        clazzName = clazz.name
        constructorParams = parameters

        // record construction and method call
        val factory = ProxyFactory()
        factory.superclass = clazz
        val proxiedClass =
            factory.createClass { m -> !m.name.equals("finalize") }
                .getDeclaredConstructor(*constructorParams?.map { it::class.java }?.toTypedArray() ?: arrayOf())
                .newInstance(*constructorParams?.map { it }?.toTypedArray() ?: arrayOf()) as T
        (proxiedClass as javassist.util.proxy.Proxy).setHandler { _, method, _, args ->
            if (methodName == null) {
                methodName = method.name
                methodParams = args
            } else throw IllegalStateException("Only one method call is allowed to record.")
            return@setHandler null
        }
        return proxiedClass
    }

    private fun record(): String {
        // build recording environment
        val pool = ClassPool.getDefault()
        val loader = Loader(pool)
        val recorder = Recorder(packagesToAnalyze.toSet(), hiddenParticipants.toSet())
        loader.addTranslator(pool, recorder)

        // record calls
        val clazz = loader.loadClass(clazzName)
        val instance = clazz
            .getDeclaredConstructor(*constructorParams?.map { it::class.java }?.toTypedArray() ?: arrayOf())
            .newInstance(*constructorParams?.map { it }?.toTypedArray() ?: arrayOf())
        val types = methodParams?.map { it::class.java }?.toTypedArray() ?: arrayOf()
        val values = methodParams?.map { it }?.toTypedArray() ?: arrayOf()
        clazz.getDeclaredMethod(methodName ?: "", *types)
            .invoke(instance, *values)

        // print out uml
        val recorderClass = loader.loadClass("tech.dolch.plantarch.Recorder")
        val recorderCompanionObject = recorderClass.getField("Companion").get(recorderClass)
        return recorderCompanionObject.javaClass
            .getDeclaredMethod("getData")
            .invoke(recorderCompanionObject) as String
    }

    fun addParticipants(vararg participants: Class<*>) {
        participants.map { c ->
            c.name
                .replaceBeforeLast('.', "").replace(".", "")
                .replaceAfterLast('$', "").replace("$", "")
        }
            .forEach(this.participants::add)

        participants.map { c -> c.packageName }.distinct().forEach(packagesToAnalyze::add)
    }

    fun addHiddenParticipants(vararg participants: Class<*>) {
        participants.map { c -> c.name }
            .forEach(this.hiddenParticipants::add)
    }

    fun toPlantuml(): String {
        val recordedSequences = record()
        return ("@startuml\n" +
                "actor start\n"
                + participants.joinToString("\n") { s -> "participant $s" } + "\n"
                + recordedSequences + "\n"
                + "\ntitle\n$name\nendtitle"
                + "\ncaption\n$description\nendcaption"
                + "\nskinparam linetype polyline\n@enduml")
    }


}