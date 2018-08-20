// IGNORE_BACKEND: JVM_IR
fun foo() {
    bar {
        nop()
        baz()
    }
}

inline fun bar(f: () -> Unit) {
    nop()
    f()
}

inline fun baz() {
    nop()
}

fun nop() {}

// 3 21 22 4 5 26 27 6 28 7 10 11 12 15 16 18