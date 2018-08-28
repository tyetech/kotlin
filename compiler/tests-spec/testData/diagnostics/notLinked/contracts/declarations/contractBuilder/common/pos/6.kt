// !LANGUAGE: +AllowContractsForCustomFunctions +UseCallsInPlaceEffect
// !DIAGNOSTICS: -INVISIBLE_REFERENCE -INVISIBLE_MEMBER

/*
 KOTLIN DIAGNOSTICS NOT LINKED SPEC TEST (POSITIVE)

 SECTION: contracts
 CATEGORY: declarations, contractBuilder, common
 NUMBER: 6
 DESCRIPTION: Functions with contracts and external effect builder.
 UNEXPECTED BEHAVIOUR
 ISSUES: KT-26186
 */

import kotlin.internal.contracts.*

internal inline fun ContractBuilder.callsInPlaceEffectBuilder(block: () -> Unit) =
    callsInPlace(block, InvocationKind.EXACTLY_ONCE)

internal fun ContractBuilder.returnsEffectBuilder(value_1: Int?) =
    returns(true) implies (value_1 != null)

internal inline fun case_1(block: () -> Unit) {
    contract(builder = { callsInPlaceEffectBuilder(block) })
    return block()
}

internal inline fun case_2(block: () -> Unit) {
    contract { callsInPlaceEffectBuilder(block) }
    return block()
}

internal inline fun case_3(value_1: Int?, block: () -> Unit) {
    contract({ returnsEffectBuilder(value_1); callsInPlaceEffectBuilder(block) })
    return block()
}
