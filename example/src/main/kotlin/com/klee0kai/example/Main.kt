package com.klee0kai.example

import com.klee0kai.example.model.Simple
import com.klee0kai.reflection.ReflectionAnalyzer

fun main(arg: Array<String>) {
    val simple = Simple()
    val un: Short? = 1
    ReflectionAnalyzer.analyze(un)

//    println("init ${SimpleJniEngine.initLib()}")
//    println("deinit ${SimpleJniEngine.deinitLib()}")
}