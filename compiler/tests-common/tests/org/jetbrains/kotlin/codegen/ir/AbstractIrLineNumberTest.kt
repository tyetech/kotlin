/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.codegen.ir

import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.codegen.AbstractLineNumberTest
import org.jetbrains.kotlin.codegen.ClassFileFactory
import org.jetbrains.kotlin.config.JVMConfigurationKeys
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.test.testFramework.KtUsefulTestCase
import org.jetbrains.org.objectweb.asm.ClassReader

abstract class AbstractIrLineNumberTest : AbstractLineNumberTest() {
    override fun createEnvironment(setup: Boolean): KotlinCoreEnvironment {
        val environment = super.createEnvironment(setup)
        if (!setup) {
            // Compile setup class always with original non-IR backend
            environment.configuration.put(JVMConfigurationKeys.IR, true)
        }
        return environment
    }

    override fun compareCustom(filename: String, psiFile: KtFile, classFileFactory: ClassFileFactory) {
        val fileText = psiFile.text
        val expectedLineNumbers = normalize(
            fileText.substring(fileText.indexOf("//") + 2)
                .trim().split(" ").map { it.trim() }.toMutableList()
        )
        val actualLineNumbers = normalize(extractActualLineNumbersFromBytecode(classFileFactory, false))
        KtUsefulTestCase.assertSameElements(actualLineNumbers, expectedLineNumbers)
    }

    override fun readAllLineNumbers(reader: ClassReader) =
        normalize(super.readAllLineNumbers(reader))

    override fun extractSelectedLineNumbersFromSource(file: KtFile) =
        normalize(super.extractSelectedLineNumbersFromSource(file))

    override fun readTestFunLineNumbers(cr: ClassReader) =
        normalize(super.readTestFunLineNumbers(cr))

    private fun normalize(numbers: MutableList<String>) =
        numbers
            .map { if (it.startsWith('+')) it.substring(1) else it }
            .toSet()
            .toMutableList()
            .sortedBy { it.toInt() }
            .toList()
}
