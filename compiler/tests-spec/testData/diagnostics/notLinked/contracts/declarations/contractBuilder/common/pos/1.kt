// !LANGUAGE: +AllowContractsForCustomFunctions +UseCallsInPlaceEffect
// !DIAGNOSTICS: -INVISIBLE_REFERENCE -INVISIBLE_MEMBER

/*
 KOTLIN DIAGNOSTICS NOT LINKED SPEC TEST (POSITIVE)

 SECTION: contracts
 CATEGORY: declarations, contractBuilder, common
 NUMBER: 1
 DESCRIPTION: Functions with simple contracts.
 */

import kotlin.internal.contracts.*

/*
 UNEXPECTED BEHAVIOUR
 */
inline fun case_1(block: () -> Unit) {
    contract { }
    return block()
}

inline fun case_2(block: () -> Unit) {
    contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
    return block()
}

inline fun case_3(value_1: Int?, block: () -> Unit): Boolean {
    contract {
        callsInPlace(block, InvocationKind.EXACTLY_ONCE)
        returns(true) implies (value_1 != null)
    }
    block()
    return value_1 != null
}

inline fun <T> T?.case_4(value_1: Int?, value_2: Boolean, value_3: Int?, block: () -> Unit): Boolean? {
    contract {
        callsInPlace(block, InvocationKind.EXACTLY_ONCE)
        returns(true) implies (value_1 != null)
        returns(false) implies (!value_2)
        returnsNotNull() implies (this@case_4 != null && value_3 != null)
    }
    block()
    return value_1 != null
}
