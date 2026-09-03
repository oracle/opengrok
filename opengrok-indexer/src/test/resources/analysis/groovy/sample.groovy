package edge.cases

import java.util.regex.Pattern
import groovy.transform.CompileStatic

// ---- slashy / division cases ----
def a = /\d+/
def b = name.split(/,/)
def c = x / y
def d = (x + 1) / 2
def e = list[0] / count
def f = call() / 2
def g = arr++ / 3
def h = /multi
line/
def i = /\$literal/
def j = /${foo}/

// ---- regex after =~ and ==~ ----
def k = text =~ /pattern/
def l = text ==~ /^[A-Z]+$/

// ---- regex in ternary ----
def m = flag ? /yes/ : /no/

// ---- regex in closure / method-call arg ----
list.each { it =~ /\d+/ }
def parts = name.split(/,\s*/)

// ---- division ambiguity edge cases ----
def divs = [a / b, (a+b)/(c-d), arr[0]/arr[1]]
def assign = a /= 2

// ---- string literals (all four kinds) ----
def s1 = "hello $name"
def s2 = 'no $interp'
def s3 = """multi
$interp ${expr.call()}"""
def s4 = '''triple
no $interp'''
def s5 = "escaped \"quote\" and \$dollar and \\backslash"
def s6 = "embedded 'single' quote"
def s7 = 'embedded "double" quote'

// ---- numeric literals ----
def n1 = 42
def n2 = 42L
def n3 = 3.14
def n4 = 1.5e10
def n5 = 0xff
def n6 = 0b1010
def n7 = 1_000_000

// ---- operators ----
def ops1 = a + b * c - d % e
def ops2 = a < b && b > c || a != b
def ops3 = a & b | c ^ ~d
def ops4 = a << 2 >> 1 >>> 1
def ops5 = 2 ** 8
def safe = obj?.field ?: 'default'
def ref = String::length
def range = 1..10
def excl = 1..<10
def spread = [*list, *other]

// ---- comments ----
// single line $with $sigils and "fake strings"
/* block /comment/ with /slashes/ inside */
/** javadoc */

// ---- closures ----
def cl1 = { it * 2 }
def cl2 = { a, b -> a + b }
list.each { println it }

// ---- annotations / classes / generics ----
@CompileStatic
@SuppressWarnings("unchecked")
class Sample<T extends Comparable<T>> {
    static final String NAME = "Sample"
    int count = 0

    def <U> U pick(List<U> xs) { xs[0] }

    String greet(String who) {
        return "hi, $who!"
    }
}

// ---- map / list literals ----
def map = [key: 'value', count: 42, nested: [a: 1, b: 2]]
def lst = [1, 2, 3]

// ---- control flow ----
if (count > 0) {
    println "positive"
} else if (count < 0) {
    println "negative"
}

for (i in 0..9) {
    if (i == 5) continue
    if (i > 7) break
}

while (running) update()

switch (color) {
    case 'red': handle(); break
    case ~/^[A-Z]/: matchedCaps(); break
    default: unknown()
}

try {
    doSomething()
} catch (Exception ex) {
    log.error(ex)
} finally {
    cleanup()
}

// ---- keyword-followed-by-regex (semantically Groovy allows regex after most keywords) ----
def afterReturn() { return /pat/ }
def afterNew = new Pattern(/foo/)
def afterThrow = { throw new Exception(/oops/) }

// ---- value-keyword followed by anything (regex NOT allowed after these) ----
def isNull = null
def t = true
def f2 = false
def thisRef = this

// ---- dollar-slashy strings ($/.../$) ----
def ds1 = $/simple dollar-slashy/$
def ds2 = $/multi
line dollar-slashy/$
def ds3 = $/with $name interpolation/$
def ds4 = $/with ${expr.call()} complex/$
def ds5 = $/escape literal slash via $/$ here/$
def ds6 = $/escape literal dollar via $$ here/$
def ds7 = $/regex meta: \d+\s*\w+/$
def ds8 = $/path: c:\Users\name/$
def ds9 = afterId = $/should still open/$

// ---- GString path mode (property chain) ----
def gp1 = "hello $user.name.email"
def gp2 = "and $person.address.city plus tail"
def gp3 = """multi $user.foo.bar end"""
