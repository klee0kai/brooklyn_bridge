package com.github.klee0kai.bridge.brooklyn.cpp.mirror

import com.github.klee0kai.bridge.brooklyn.cpp.common.*
import com.github.klee0kai.bridge.brooklyn.cpp.common.CommonNaming.BROOKLYN
import com.github.klee0kai.bridge.brooklyn.cpp.common.CommonNaming.MAPPER
import com.github.klee0kai.bridge.brooklyn.cpp.mapper.cppNameMirror
import com.github.klee0kai.bridge.brooklyn.cpp.mapper.indexVariableName
import com.github.klee0kai.bridge.brooklyn.cpp.typemirros.cppMappingNameSpace
import com.github.klee0kai.bridge.brooklyn.cpp.typemirros.cppModelMirror
import com.github.klee0kai.bridge.brooklyn.cpp.typemirros.jniType
import com.github.klee0kai.bridge.brooklyn.cpp.typemirros.joinArgs
import org.jetbrains.kotlin.backend.jvm.fullValueParameterList
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.isUnit
import org.jetbrains.kotlin.ir.util.classId
import org.jetbrains.kotlin.ir.util.constructors
import org.jetbrains.kotlin.ir.util.functions
import org.jetbrains.kotlin.ir.util.isObject

fun CodeBuilder.declareClassMirror(jClass: IrClass) = apply {
    val usedTypes = mutableSetOf<IrType>()

    body {
        val clMirror = jClass.cppModelMirror() ?: return@body
        lines(1)
        line("class $clMirror {")
        line("public: ")

        statement("${clMirror}(jobject jvmSelf)")

        if (!jClass.isObject) jClass.constructors.forEach { func ->
            val args = func.mirrorFuncArgs(env = true)?.joinToString(", ") ?: return@forEach
            usedTypes.addAll(func.allUsedTypes())

            statement("${clMirror}($args)")
        }

        jClass.functions.forEach { func ->
            val args = func.mirrorFuncArgs()?.joinToString(", ") ?: return@forEach
            val returnType = func.returnType.jniType()?.cppPtrTypeMirror ?: "void"
            usedTypes.addAll(func.allUsedTypes())

            if (jClass.isObject) {
                post("static ")
            }
            statement("$returnType ${func.name}($args)")
        }

        statement("~${clMirror}()")

        line("private: ")
        statement("jobject jvmSelf")
        statement("}")
    }


    header {
        usedTypes.mapNotNull { type ->
            type.jniType()?.classId
        }.toSet().forEach { classId ->
            include(classId.modelHeaderFile.path)
        }
    }
}


fun CodeBuilder.implementClassMirror(jClass: IrClass) = apply {
    val usedTypes = mutableSetOf<IrType>()

    header {
        statement("using namespace ${BROOKLYN}::${MAPPER}")
    }
    body {
        val clMirror = jClass.cppModelMirror() ?: return@body
        val mappingNamespace = jClass.cppMappingNameSpace()
        val indexField = jClass.classId!!.indexVariableName
        lines(1)

        line("${clMirror}::${clMirror}(jobject jvmSelf) {")
        statement("JNIEnv* env = ${BROOKLYN}::env()")
        statement("${clMirror}::jvmSelf = env->NewGlobalRef(jvmSelf)")
        line("}")

        if (!jClass.isObject) jClass.constructors.forEach { func ->
            if (func.isExternal) return@forEach
            val args = func.mirrorFuncArgs(env = true)?.joinToString(", ") ?: return@forEach
            line("${clMirror}::${clMirror}($args) {")

            val arguments = func.fullValueParameterList.joinToString("") { param ->
                val fieldTypeMirror = param.type.jniType() ?: return@joinToString ""
                ",\n " + fieldTypeMirror.transformToJni.invoke("${param.name}")
            }
            statement("JNIEnv* env = ${BROOKLYN}::env()")
            statement(
                "${clMirror}::jvmSelf = env->NewGlobalRef(env->NewObject( " +
                        "${mappingNamespace}::${indexField}->cls, " +
                        "${mappingNamespace}::${indexField}->${func.cppNameMirror} " +
                        "$arguments ))"
            )
            line("}")
        }

        jClass.functions.forEach { func ->
            if (func.isExternal) return@forEach
            val argsDeclaration = func.mirrorFuncArgs()?.joinToString(", ") ?: return@forEach
            val returnType = func.returnType.jniType()
            val arguments = func.fullValueParameterList.joinToString(",\n ") { param ->
                val fieldTypeMirror = param.type.jniType() ?: return@joinToString ""
                fieldTypeMirror.transformToJni.invoke("${param.name}")
            }

            when {
                jClass.isObject && returnType != null -> {
                    line("${returnType.cppPtrTypeMirror} ${clMirror}::${func.name}($argsDeclaration) {")
                    statement("JNIEnv* env = ${BROOKLYN}::env()")
                    post("return ")
                    statement(
                        returnType.transformToCpp.invoke(
                            returnType.extractFromStaticMethod.invoke(
                                "${mappingNamespace}::${indexField}->cls",
                                "${mappingNamespace}::${indexField}->${func.cppNameMirror} ",
                                arguments
                            )
                        )
                    )
                    line("}")
                }

                jClass.isObject -> {
                    line("void ${clMirror}::${func.name}($argsDeclaration) {")
                    statement("JNIEnv* env = ${BROOKLYN}::env()")
                    statement(
                        "env->CallStaticVoidMethod(${
                            listOf(
                                "${mappingNamespace}::${indexField}->cls",
                                "${mappingNamespace}::${indexField}->${func.cppNameMirror}",
                                arguments
                            ).joinArgs()
                        })"
                    )
                    line("}")
                }

                returnType != null -> {
                    line("${returnType.cppPtrTypeMirror} ${clMirror}::${func.name}($argsDeclaration) {")
                    statement("JNIEnv* env = ${BROOKLYN}::env()")
                    post("return ")
                    statement(
                        returnType.transformToCpp.invoke(
                            returnType.extractFromMethod.invoke(
                                "jvmSelf",
                                "${mappingNamespace}::${indexField}->${func.cppNameMirror} ",
                                arguments
                            )
                        )
                    )
                    line("}")
                }

                else -> {
                    line("void ${clMirror}::${func.name}($argsDeclaration) {")
                    statement("JNIEnv* env = ${BROOKLYN}::env()")
                    statement(
                        "env->CallVoidMethod(${
                            listOf(
                                "jvmSelf",
                                "${mappingNamespace}::${indexField}->${func.cppNameMirror}",
                                arguments
                            ).joinArgs()
                        })"
                    )
                    line("}")
                }
            }
        }

        line("${clMirror}::~${clMirror}() {")
        statement("if (jvmSelf) ${BROOKLYN}::env()->DeleteGlobalRef(jvmSelf)")
        statement("jvmSelf = NULL")
        line("}")
    }


    header {
        usedTypes.mapNotNull { type ->
            type.jniType()?.classId
        }.toSet().forEach { classId ->
            include(classId.modelHeaderFile.path)
        }
    }
}


fun IrFunction.mirrorFuncArgs(env: Boolean = false) = runCatching {
    fullValueParameterList.map {
        "const ${it.type.jniType()!!.cppPtrTypeMirror}& ${it.name}"
    }
}.getOrNull()


fun IrFunction.allUsedTypes(): List<IrType> =
    listOf(returnType) + fullValueParameterList.map { it.type }


fun IrType.jniTypeStr() =
    when {
        isUnit() -> "void"
        else -> jniType()?.jniTypeStr ?: "jobject"
    }

