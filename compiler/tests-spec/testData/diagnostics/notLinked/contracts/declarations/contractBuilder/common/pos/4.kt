// !LANGUAGE: +AllowContractsForCustomFunctions +UseCallsInPlaceEffect
// !DIAGNOSTICS: -INVISIBLE_REFERENCE -INVISIBLE_MEMBER

/*
 KOTLIN DIAGNOSTICS NOT LINKED SPEC TEST (POSITIVE)

 SECTION: contracts
 CATEGORY: declarations, contractBuilder, common
 NUMBER: 4
 DESCRIPTION: Functions with contract and builder lambda in parentheses.
 */

import kotlin.internal.contracts.*

/*
 UNEXPECTED BEHAVIOUR
 */
inline fun case_1(block: () -> Unit) {
    contract({ })
    return block()
}

inline fun case_2(block: () -> Unit) {
    contract(builder = { })
    return block()
}

inline fun case_3(block: () -> Unit) {
    contract({ callsInPlace(block, InvocationKind.EXACTLY_ONCE) })
    return block()
}

inline fun case_4(block: () -> Unit) {
    contract(builder = { callsInPlace(block, InvocationKind.EXACTLY_ONCE) })
    return block()
}
