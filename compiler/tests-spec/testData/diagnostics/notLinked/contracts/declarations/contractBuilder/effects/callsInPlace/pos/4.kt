// !LANGUAGE: +AllowContractsForCustomFunctions +UseCallsInPlaceEffect
// !DIAGNOSTICS: -INVISIBLE_REFERENCE -INVISIBLE_MEMBER -UNUSED_VARIABLE

/*
 KOTLIN DIAGNOSTICS NOT LINKED SPEC TEST (POSITIVE)

 SECTION: contracts
 CATEGORY: declarations, contractBuilder, effects, callsInPlace
 NUMBER: 4
 DESCRIPTION: Contract with 'this' in first parameter of CallsInPlace.
 UNEXPECTED BEHAVIOUR
 ISSUES: KT-26294
 */

import kotlin.internal.contracts.*

inline fun <T : Function<*>> T.case_1(block: () -> Unit) {
    contract {
        callsInPlace(this@case_1, InvocationKind.EXACTLY_ONCE)
        callsInPlace(block, InvocationKind.EXACTLY_ONCE)
    }
    block()
    (this@case_1 as kotlin.reflect.KFunction<*>).call()
}
