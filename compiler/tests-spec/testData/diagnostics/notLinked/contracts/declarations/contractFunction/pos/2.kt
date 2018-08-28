// !LANGUAGE: +AllowContractsForCustomFunctions +UseCallsInPlaceEffect
// !DIAGNOSTICS: -INVISIBLE_REFERENCE -INVISIBLE_MEMBER

/*
 KOTLIN DIAGNOSTICS NOT LINKED SPEC TEST (POSITIVE)

 SECTION: contracts
 CATEGORY: declarations, contractFunction
 NUMBER: 2
 DESCRIPTION: Check report about use contracts in literal functions, lambdas or not top-level functions.
 UNEXPECTED BEHAVIOUR
 ISSUES: KT-26149
 */

import kotlin.internal.contracts.*

fun case_1() {
    val fun_1 = fun(block: () -> Unit) {
        contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
        return block()
    }

    fun_1 { throw Exception() }
    println("1")
}

fun case_2() {
    val lambda_1 = { block: () -> Unit ->
        contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
        block()
    }

    lambda_1 { throw Exception() }
    println("1")
}

object case_3 {
    fun case_3(block: () -> Unit) {
        contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
        return block()
    }
}

class case_4 {
    fun case_4(block: () -> Unit) {
        contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
        return block()
    }
}
