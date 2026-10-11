package com.example.demo

import groovy.transform.CompileStatic
import static java.util.Collections.emptyList

@CompileStatic
class Sample {

    def name = "demo"

    def greet(String who) {
        return "hi, $who"
    }
}

interface Greeter {
    def greet(String who)
}

trait Walker {
    def walk() { println "walking" }
}

enum Color {
    RED, GREEN, BLUE
}

@interface Stamp {
    String value() default ""
}
