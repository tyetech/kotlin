// IGNORE_BACKEND: JVM_IR
fun test() {
    foo()
    bar()
}

fun foo(i: Int = 1) {
}

inline fun bar(i: Int = 1) {
}

// 3 4 14 15 5 8 7 11 10 16