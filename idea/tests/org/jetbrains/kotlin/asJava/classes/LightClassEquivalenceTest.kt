/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.asJava.classes

import com.intellij.psi.*
import org.jetbrains.kotlin.asJava.toLightClass
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtFile

class LightClassEquivalenceTest : KotlinLightCodeInsightFixtureTestCase() {
    override fun getProjectDescriptor() = KotlinWithJdkAndRuntimeLightProjectDescriptor.INSTANCE

    fun testSimpleFunctions() {
        checkClassEquivalence(
            """
class Foo {
  open fun bar(a: Int, b:Any, c:Foo): Unit {}
  internal fun bar2(a: Sequence, b: Unresolved) {}
  private fun bar3(x: Foo.Inner, vararg y: Inner) = "str"
  fun bar4() = 42

  class Inner {}
}
"""
        )
    }

    fun testClassModifiers() {
        checkClassEquivalence(
            """
package pkg

open class Open {
  private class Private: Open {}
  protected inner class Private2 {}
  internal class StaticInternal {}
}
internal class OuterInternal {}
private class TopLevelPrivate {}
"""
        )
    }

    fun testGenerics() {
        checkClassEquivalence(
            """
class C<T> {
  fun foo<V>(p1: V, p2: C<V>, p4: Sequence<V>): T {}
}
"""
        )
    }

    fun testAnnotations() {
        checkClassEquivalence(
            """
import kotlin.reflect.KClass

annotation class Anno(val p: String = "", val x: Array<Anno> = arrayOf(Anno(p="a"), Anno(p="b")))

@Anno class F: Runnable {
  @Anno("f") fun f(@Anno p: String) {}
  @Anno("p") var prop = "x"
}

@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION,
        AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.EXPRESSION)
@Retention(AnnotationRetention.SOURCE)
@MustBeDocumented
@Deprecated("This anno is deprecated, use === instead", ReplaceWith("this === other"))
annotation class Fancy

class Foo @Anno constructor(dependency: MyDependency) {
  var x: String? = null
        @Anno set
}

annotation class ReplaceWith(val expression: String)

annotation class Deprecated(
    val message: String,
    val replaceWith: ReplaceWith = ReplaceWith(""))

annotation class Ann(val arg1: KClass<*>, val arg2: KClass<out Any>)

@Ann(String::class, Int::class) class MyClass

class Example(@field:Ann val foo,    // annotate Java field
              @get:Ann val bar,      // annotate Java getter
              @param:Ann val quux)   // annotate Java constructor parameter
"""
        )
    }

    fun testProperties() {
        checkClassEquivalence(
            """
class Foo(a: Int, val b:Foo, var c:Boolean, private val d: List, protected val e: Long = 2) {
  val f1 = 2

  protected var f2 = 3

  var name: String = "x"

  val isEmpty get() = false
  var isEmptyMutable: Boolean?
  var islowercase: Boolean?
  var isEmptyInt: Int?
  var getInt: Int?
  private var noAccessors: String

  internal var stringRepresentation: String
    get() = this.toString()
    set(value) {
        setDataFromString(value) // parses the string and assigns values to other properties
    }

  const val SUBSYSTEM_DEPRECATED: String = "This subsystem is deprecated"

  var counter = 0 // Note: the initializer assigns the backing field directly
    set(value) {
        if (value >= 0) field = value
    }
  lateinit var subject: Unresolved

  var delegatedProp: String by Delegate()
}

"""
        )
    }

    fun testInheritance() {
        checkClassEquivalence(
            """
interface Intf {
  fun v(): Int
}
interface IntfWithProp : Intf {
  val x: Int
}
abstract class Base(p: Int) {
    open protected fun v(): Int? { }
    fun nv() { }
    internal open val x: Int get() { }
    abstract fun abs(): Int
    open var y = 1
}
class Derived(p: Int) : Base(p), IntfWithProp {
    override fun v() = unknown()
    override val x = 3
    override fun abs() = 0
}
abstract class AnotherDerived(override val x: Int, override val y: Int) : Base(2) {
    final override fun v() { }
}
"""
        )
    }

    fun testObjects() {
        checkClassEquivalence(
            """
class C {
    companion object {
        @JvmStatic fun foo() {}
        fun bar() {}
        @JvmStatic var x: String = ""
    }
}

class C1 {
  private companion object {}
}

interface I {
  companion object { }
}

object Obj : java.lang.Runnable {
    @JvmStatic var x: String = ""
    override fun run() {}
    @JvmStatic fun zoo(): Int = 2
}
"""
        )
    }

    private fun checkClassEquivalence(text: String) {
        val file = myFixture.addFileToProject("a.kt", text) as KtFile
        val ktClasses = SyntaxTraverser.psiTraverser(file).filterIsInstance<KtClassOrObject>().toList()
        val goldText = ktClasses.joinToString("\n\n") { it.toLightClass()?.clsDelegate?.render().orEmpty() }
        val newText = ktClasses.joinToString("\n\n") { it.toLightClass()?.render().orEmpty() }
        assertEquals(goldText, newText)
    }

    private fun PsiClass.render(): String {
        fun PsiAnnotation.renderAnnotation() =
            "@" + qualifiedName
            //todo fix attribute names: + "(" + parameterList.attributes.joinToString { it.name + "=" + (it.value?.text ?: "?") } + ")"

        fun PsiModifierListOwner.renderModifiers() =
            annotations.joinToString("") { it.renderAnnotation() + (if (this is PsiParameter) " " else "\n") } +
                    PsiModifier.MODIFIERS.filter(::hasModifierProperty).joinToString("") { "$it " }

        fun PsiType.renderType() = getCanonicalText(true)

        fun PsiReferenceList?.renderRefList(keyword: String): String {
            if (this == null || this.referencedTypes.isEmpty()) return ""
            return " " + keyword + " " + referencedTypes.joinToString { it.renderType() }
        }

        fun PsiVariable.renderVar(): String {
            var result = this.renderModifiers() + type.renderType() + " " + name
            if (this is PsiParameter && this.isVarArgs) {
                result += " /* vararg */"
            }
            computeConstantValue()?.let { result += " /* constant value $it */" }
            return result
        }

        fun PsiTypeParameterListOwner.renderTypeParams() =
            if (typeParameters.isEmpty()) ""
            else "<" + typeParameters.joinToString { it.name!! } + "> "

        fun PsiMethod.renderMethod() =
            renderModifiers() +
                    (if (isVarArgs) "/* vararg */ " else "") +
                    renderTypeParams() +
                    (returnType?.renderType() ?: "") + " " +
                    name +
                    "(" + parameterList.parameters.joinToString { it.renderModifiers() + it.type.renderType() } + ")" +
                    (this as? PsiAnnotationMethod)?.defaultValue?.let { " default " + it.text }.orEmpty() +
                    throwsList.referencedTypes.let { thrownTypes ->
                        if (thrownTypes.isEmpty()) ""
                        else "throws " + thrownTypes.joinToString { it.renderType() }
                    } +
                    ";"

        val classWord = when {
            isAnnotationType -> "@interface"
            isInterface -> "interface"
            isEnum -> "enum"
            else -> "class"
        }

        return renderModifiers() +
                classWord + " " +
                name + " /* " + qualifiedName + "*/" +
                renderTypeParams() +
                extendsList.renderRefList("extends") +
                implementsList.renderRefList("implements") +
                " {\n" +
                fields.map { it.renderVar().prependIndent("  ") + ";\n\n" }.sorted().joinToString("") +
                methods.map { it.renderMethod().prependIndent("  ") + "\n\n" }.sorted().joinToString("") +
                innerClasses.map { "  class ${it.name} ...\n" }.sorted().joinToString("") +
                "}"
    }
}

