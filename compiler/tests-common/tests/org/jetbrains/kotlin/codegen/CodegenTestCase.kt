/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.codegen

import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableMap
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Ref
import com.intellij.openapi.util.io.FileUtil
import com.intellij.testFramework.TestDataFile
import junit.framework.TestCase
import org.jetbrains.kotlin.backend.common.output.OutputFile
import org.jetbrains.kotlin.backend.common.output.SimpleOutputFileCollection
import org.jetbrains.kotlin.checkers.CheckerTestUtil
import org.jetbrains.kotlin.checkers.CompilerTestLanguageVersionSettings
import org.jetbrains.kotlin.cli.common.CLIConfigurationKeys
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.cli.jvm.compiler.NoScopeRecordCliBindingTrace
import org.jetbrains.kotlin.codegen.forTestCompile.ForTestCompileRuntime
import org.jetbrains.kotlin.config.*
import org.jetbrains.kotlin.fileClasses.JvmFileClassUtil
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.script.ScriptDependenciesProvider
import org.jetbrains.kotlin.test.ConfigurationKind
import org.jetbrains.kotlin.test.InTextDirectivesUtils
import org.jetbrains.kotlin.test.KotlinTestUtils
import org.jetbrains.kotlin.test.TestJdkKind
import org.jetbrains.kotlin.test.clientserver.TestProxy
import org.jetbrains.kotlin.test.testFramework.KtUsefulTestCase
import org.jetbrains.kotlin.utils.*
import org.jetbrains.org.objectweb.asm.ClassReader
import org.jetbrains.org.objectweb.asm.tree.ClassNode
import org.jetbrains.org.objectweb.asm.tree.analysis.Analyzer
import org.jetbrains.org.objectweb.asm.tree.analysis.AnalyzerException
import org.jetbrains.org.objectweb.asm.tree.analysis.SimpleVerifier
import org.jetbrains.org.objectweb.asm.util.Textifier
import org.jetbrains.org.objectweb.asm.util.TraceMethodVisitor

import java.io.File
import java.io.IOException
import java.io.PrintWriter
import java.lang.reflect.Field
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.net.MalformedURLException
import java.net.URL
import java.net.URLClassLoader
import java.util.ArrayList
import java.util.Collections
import java.util.regex.Pattern

import org.jetbrains.kotlin.checkers.API_VERSION_DIRECTIVE
import org.jetbrains.kotlin.checkers.parseLanguageVersionSettings
import org.jetbrains.kotlin.cli.common.output.writeAllTo
import org.jetbrains.kotlin.codegen.CodegenTestUtil.*
import org.jetbrains.kotlin.test.KotlinTestUtils.getAnnotationsJar
import org.jetbrains.kotlin.test.clientserver.getBoxMethodOrNull
import org.jetbrains.kotlin.test.clientserver.getGeneratedClass

abstract class CodegenTestCase : KtUsefulTestCase() {

    @JvmField
    protected var myEnvironment: KotlinCoreEnvironment? = null
    @JvmField
    protected var myFiles: CodegenTestFiles? = null
    @JvmField
    protected var classFileFactory: ClassFileFactory? = null
    @JvmField
    protected var initializedClassLoader: GeneratedClassLoader? = null
    @JvmField
    protected var javaClassesOutputDirectory: File? = null
    @JvmField
    protected var additionalDependencies: List<File>? = null
    @JvmField
    protected var coroutinesPackage: String? = null

    @JvmField
    protected var configurationKind = ConfigurationKind.JDK_ONLY

    private val defaultJvmTarget = System.getProperty(DEFAULT_JVM_TARGET_FOR_TEST)
    private val boxInSeparateProcessPort = System.getProperty(RUN_BOX_TEST_IN_SEPARATE_PROCESS_PORT)
    private val javaCompilationTarget = System.getProperty(JAVA_COMPILATION_TARGET)

    protected val prefix: String
        get() = throw UnsupportedOperationException()

    protected val classPathURLs: Array<URL>
        get() {
            val files = ArrayList<File>()
            if (javaClassesOutputDirectory != null) {
                files.add(javaClassesOutputDirectory!!)
            }
            if (additionalDependencies != null) {
                files.addAll(additionalDependencies!!)
            }

            val externalImportsProvider = ScriptDependenciesProvider.getInstance(myEnvironment!!.project)
            myEnvironment!!.getSourceFiles().forEach { file ->
                val dependencies = externalImportsProvider.getScriptDependencies(file)
                if (dependencies != null) {
                    files.addAll(dependencies.classpath)
                }
            }

            try {
                return files.map { it.toURI().toURL() }.toTypedArray()
            } catch (e: MalformedURLException) {
                throw rethrow(e)
            }

        }

    protected val classBuilderFactory: ClassBuilderFactory
        get() = ClassBuilderFactories.TEST

    protected fun createEnvironmentWithMockJdkAndIdeaAnnotations(
        configurationKind: ConfigurationKind,
        vararg javaSourceRoots: File
    ) {
        createEnvironmentWithMockJdkAndIdeaAnnotations(configurationKind, emptyList(), TestJdkKind.MOCK_JDK, *javaSourceRoots)
    }

    protected fun createEnvironmentWithMockJdkAndIdeaAnnotations(
        configurationKind: ConfigurationKind,
        testFilesWithConfigurationDirectives: List<TestFile>,
        testJdkKind: TestJdkKind,
        vararg javaSourceRoots: File
    ) {
        if (myEnvironment != null) {
            throw IllegalStateException("must not set up myEnvironment twice")
        }

        val configuration = createConfiguration(
            configurationKind,
            testJdkKind,
            listOf(getAnnotationsJar()),
            javaSourceRoots.filterNotNull(),
            testFilesWithConfigurationDirectives
        )

        myEnvironment = KotlinCoreEnvironment.createForTests(
            testRootDisposable, configuration, EnvironmentConfigFiles.JVM_CONFIG_FILES
        )
    }

    protected fun createConfiguration(
        kind: ConfigurationKind,
        jdkKind: TestJdkKind,
        classpath: List<File>,
        javaSource: List<File>,
        testFilesWithConfigurationDirectives: List<TestFile>
    ): CompilerConfiguration {
        val configuration = KotlinTestUtils.newConfiguration(kind, jdkKind, classpath, javaSource)

        updateConfigurationByDirectivesInTestFiles(testFilesWithConfigurationDirectives, configuration, coroutinesPackage!!)
        updateConfiguration(configuration)
        setCustomDefaultJvmTarget(configuration)

        return configuration
    }

    @Throws(Exception::class)
    override fun setUp() {
        coroutinesPackage = ""
        super.setUp()
    }

    @Throws(Exception::class)
    override fun tearDown() {
        myFiles = null
        myEnvironment = null
        classFileFactory = null

        if (initializedClassLoader != null) {
            initializedClassLoader!!.dispose()
            initializedClassLoader = null
        }

        super.tearDown()
    }

    protected fun loadText(text: String) {
        myFiles = CodegenTestFiles.create("$DEFAULT_TEST_FILE_NAME.kt", text, myEnvironment!!.project)
    }

    protected fun loadFile(@TestDataFile name: String): String {
        return loadFileByFullPath(KotlinTestUtils.getTestDataPathBase() + "/codegen/" + name)
    }

    protected fun loadFileByFullPath(fullPath: String): String {
        try {
            val file = File(fullPath)
            val content = FileUtil.loadFile(file, Charsets.UTF_8.name(), true)
            assert(myFiles == null) { "Should not initialize myFiles twice" }
            myFiles = CodegenTestFiles.create(file.name, content, myEnvironment!!.project)
            return content
        } catch (e: IOException) {
            throw RuntimeException(e)
        }

    }

    protected fun loadFiles(vararg names: String) {
        myFiles = CodegenTestFiles.create(myEnvironment!!.project, names)
    }

    protected fun loadFile() {
        loadFile(prefix + "/" + getTestName(true) + ".kt")
    }

    protected open fun loadMultiFiles(files: List<TestFile>) {
        myFiles = loadMultiFiles(files, myEnvironment!!.project)
    }

    protected open fun codegenTestBasePath(): String {
        return "compiler/testData/codegen/"
    }

    protected fun relativePath(file: File): String {
        return file.absoluteFile.toRelativeString(File(codegenTestBasePath()).absoluteFile)
    }

    protected fun generateAndCreateClassLoader(): GeneratedClassLoader {
        if (initializedClassLoader != null) {
            TestCase.fail("Double initialization of class loader in same test")
        }

        initializedClassLoader = createClassLoader()

        if (!verifyAllFilesWithAsm(generateClassesInFile(), initializedClassLoader!!)) {
            TestCase.fail("Verification failed: see exceptions above")
        }

        return initializedClassLoader!!
    }

    protected fun createClassLoader(): GeneratedClassLoader {
        val classLoader: ClassLoader
        if (configurationKind.withReflection && configurationKind.withCoroutines) {
            classLoader = ForTestCompileRuntime.reflectAndCoroutinesJarClassLoader()
        } else if (configurationKind.withUnsignedTypes && configurationKind.withReflection) {
            classLoader = ForTestCompileRuntime.reflectAndUnsignedTypesJarClassLoader()
        } else if (configurationKind.withReflection) {
            classLoader = ForTestCompileRuntime.runtimeAndReflectJarClassLoader()
        } else if (configurationKind.withCoroutines) {
            classLoader = ForTestCompileRuntime.runtimeAndCoroutinesJarClassLoader()
        } else if (configurationKind.withUnsignedTypes) {
            classLoader = ForTestCompileRuntime.runtimeAndUnsignedTypesJarClassLoader()
        } else {
            classLoader = ForTestCompileRuntime.runtimeJarClassLoader()
        }

        return GeneratedClassLoader(
            generateClassesInFile(),
            classLoader,
            *classPathURLs
        )
    }

    @JvmOverloads
    protected fun generateToText(ignorePathPrefix: String? = null): String {
        if (classFileFactory == null) {
            classFileFactory = generateFiles(myEnvironment!!, myFiles!!)
        }
        return classFileFactory!!.createText(ignorePathPrefix)
    }

    protected fun generateEachFileToText(): Map<String, String> {
        if (classFileFactory == null) {
            classFileFactory = generateFiles(myEnvironment!!, myFiles!!)
        }
        return classFileFactory!!.createTextForEachFile()
    }

    protected fun generateFacadeClass(): Class<*> {
        val facadeClassFqName = JvmFileClassUtil.getFileClassInfoNoResolve(myFiles!!.psiFile).facadeClassFqName
        return generateClass(facadeClassFqName.asString())
    }

    protected fun generateClass(name: String): Class<*> {
        try {
            return generateAndCreateClassLoader().loadClass(name)
        } catch (e: ClassNotFoundException) {
            TestCase.fail("No class file was generated for: $name")
            throw e
        }

    }

    protected fun generateClassesInFile(): ClassFileFactory {
        if (classFileFactory == null) {
            try {
                val generationState = GenerationUtils.compileFiles(
                    myFiles!!.psiFiles, myEnvironment!!, classBuilderFactory,
                    NoScopeRecordCliBindingTrace()
                )
                classFileFactory = generationState.factory

                if (verifyWithDex() && DxChecker.RUN_DX_CHECKER) {
                    DxChecker.check(classFileFactory)
                }
            } catch (e: Throwable) {
                e.printStackTrace()
                System.err.println("Generating instructions as text...")
                try {
                    if (classFileFactory == null) {
                        System.err.println("Cannot generate text: exception was thrown during generation")
                    } else {
                        System.err.println(classFileFactory!!.createText())
                    }
                } catch (e1: Throwable) {
                    System.err.println("Exception thrown while trying to generate text, the actual exception follows:")
                    e1.printStackTrace()
                    System.err.println("-----------------------------------------------------------------------------")
                }

                TestCase.fail("See exceptions above")
            }

        }
        return classFileFactory!!
    }

    protected open fun verifyWithDex(): Boolean {
        return true
    }

    protected fun generateFunction(): Method {
        val aClass = generateFacadeClass()
        try {
            return findTheOnlyMethod(aClass)
        } catch (e: Error) {
            println(generateToText())
            throw e
        }

    }

    protected fun generateFunction(name: String): Method {
        return findDeclaredMethodByName(generateFacadeClass(), name)
    }

    fun loadAnnotationClassQuietly(fqName: String): Class<out Annotation> {
        try {

            return initializedClassLoader!!.loadClass(fqName) as Class<out Annotation>
        } catch (e: ClassNotFoundException) {
            throw rethrow(e)
        }

    }

    protected open fun updateConfiguration(configuration: CompilerConfiguration) {

    }

    protected open fun setupEnvironment(environment: KotlinCoreEnvironment) {

    }

    protected fun setCustomDefaultJvmTarget(configuration: CompilerConfiguration) {
        val target = configuration.get(JVMConfigurationKeys.JVM_TARGET)
        if (target == null && defaultJvmTarget != null) {
            val value = JvmTarget.fromString(defaultJvmTarget) ?: error("Can't construct JvmTarget for $defaultJvmTarget")
            configuration.put(JVMConfigurationKeys.JVM_TARGET, value)
        }
    }

    protected fun compile(
        files: List<TestFile>,
        javaSourceDir: File?
    ) {
        configurationKind = extractConfigurationKind(files)
        val loadAndroidAnnotations =
            files.stream().anyMatch({ it -> InTextDirectivesUtils.isDirectiveDefined(it.content, "ANDROID_ANNOTATIONS") }
            )

        val javacOptions = extractJavacOptions(files)
        val classpath = ArrayList<File>()
        classpath.add(getAnnotationsJar())

        if (loadAndroidAnnotations) {
            classpath.add(ForTestCompileRuntime.androidAnnotationsForTests())
        }

        val configuration = createConfiguration(
            configurationKind, getJdkKind(files),
            classpath,
            listOfNotNull(javaSourceDir),
            files
        )

        myEnvironment = KotlinCoreEnvironment.createForTests(
            testRootDisposable, configuration, EnvironmentConfigFiles.JVM_CONFIG_FILES
        )
        setupEnvironment(myEnvironment!!)

        loadMultiFiles(files)

        generateClassesInFile()

        if (javaSourceDir != null) {
            // If there are Java files, they should be compiled against the class files produced by Kotlin, so we dump them to the disk
            val kotlinOut: File
            try {
                kotlinOut = KotlinTestUtils.tmpDir(toString())
            } catch (e: IOException) {
                throw rethrow(e)
            }

            classFileFactory!!.writeAllTo(kotlinOut)

            val javaClasspath = ArrayList<String>()
            javaClasspath.add(kotlinOut.path)

            if (loadAndroidAnnotations) {
                javaClasspath.add(ForTestCompileRuntime.androidAnnotationsForTests().path)
            }
            if (configurationKind.withCoroutines) {
                javaClasspath.add(ForTestCompileRuntime.coroutinesJarForTests().path)
            }
            if (configurationKind.withUnsignedTypes) {
                javaClasspath.add(ForTestCompileRuntime.unsignedTypesJarForTests().path)
            }

            javaClassesOutputDirectory = CodegenTestUtil.compileJava(
                findJavaSourcesInDirectory(javaSourceDir), javaClasspath, javacOptions
            )
        }
    }


    protected open fun extractConfigurationKind(files: List<TestFile>): ConfigurationKind {
        var addRuntime = false
        var addReflect = false
        var addCoroutines = false
        var addUnsignedTypes = false
        for (file in files) {
            if (InTextDirectivesUtils.isDirectiveDefined(file.content, "COMMON_COROUTINES_TEST") ||
                InTextDirectivesUtils.isDirectiveDefined(file.content, "!LANGUAGE: +ReleaseCoroutines") ||
                InTextDirectivesUtils.isDirectiveDefined(file.content, "LANGUAGE_VERSION: 1.3")
            ) {
                addCoroutines = true
            }
            if (InTextDirectivesUtils.isDirectiveDefined(file.content, "WITH_RUNTIME")) {
                addRuntime = true
            }
            if (InTextDirectivesUtils.isDirectiveDefined(file.content, "WITH_REFLECT")) {
                addReflect = true
            }
            if (InTextDirectivesUtils.isDirectiveDefined(file.content, "WITH_UNSIGNED")) {
                addUnsignedTypes = true
            }
        }

        return if (addReflect && addCoroutines && addUnsignedTypes)
            ConfigurationKind.ALL
        else if (addReflect && addCoroutines)
            ConfigurationKind.WITH_COROUTINES_AND_REFLECT
        else if (addReflect)
            ConfigurationKind.WITH_REFLECT
        else if (addCoroutines)
            ConfigurationKind.WITH_COROUTINES
        else if (addUnsignedTypes)
            ConfigurationKind.WITH_UNSIGNED_TYPES
        else if (addRuntime)
            ConfigurationKind.NO_KOTLIN_REFLECT
        else
            ConfigurationKind.JDK_ONLY
    }

    protected fun extractJavacOptions(files: List<TestFile>): List<String> {
        val javacOptions = ArrayList<String>(0)
        for (file in files) {
            javacOptions.addAll(InTextDirectivesUtils.findListWithPrefixes(file.content, "// JAVAC_OPTIONS:"))
        }
        updateJavacOptions(javacOptions)
        return javacOptions
    }

    protected fun updateJavacOptions(javacOptions: MutableList<String>) {
        if (javaCompilationTarget != null && !javacOptions.contains("-target")) {
            javacOptions.add("-source")
            javacOptions.add(javaCompilationTarget)
            javacOptions.add("-target")
            javacOptions.add(javaCompilationTarget)
        }
    }

    class TestFile(@JvmField val name: String, @JvmField val content: String) : Comparable<TestFile> {

        override fun compareTo(other: TestFile): Int {
            return name.compareTo(other.name)
        }

        override fun hashCode(): Int {
            return name.hashCode()
        }

        override fun equals(other: Any?): Boolean {
            return other is TestFile && other.name == name
        }

        override fun toString(): String {
            return name
        }
    }

    @Throws(Exception::class)
    protected open fun doTest(filePath: String) {
        val file = File(filePath)
        val expectedText = KotlinTestUtils.doLoadFile(file)
        val javaFilesDir = Ref.create<File>()

        val testFiles = createTestFiles(file, expectedText, javaFilesDir, "")

        doMultiFileTest(file, testFiles, javaFilesDir.get())
    }

    @Throws(Exception::class)
    protected fun doTestWithCoroutinesPackageReplacement(filePath: String, packageName: String) {
        val file = File(filePath)
        var expectedText = KotlinTestUtils.doLoadFile(file)
        expectedText = expectedText.replace("COROUTINES_PACKAGE", packageName)
        this.coroutinesPackage = packageName
        val javaFilesDir = Ref.create<File>()

        val testFiles = createTestFiles(file, expectedText, javaFilesDir, coroutinesPackage!!)

        doMultiFileTest(file, testFiles, javaFilesDir.get())
    }

    @Throws(Exception::class)
    protected open fun doMultiFileTest(
        wholeFile: File,
        files: List<TestFile>,
        javaFilesDir: File?
    ) {
        throw UnsupportedOperationException("Multi-file test cases are not supported in this test")
    }

    @Throws(IOException::class, InvocationTargetException::class, IllegalAccessException::class)
    protected fun callBoxMethodAndCheckResult(classLoader: URLClassLoader, className: String) {
        val aClass = getGeneratedClass(classLoader, className)
        val method = getBoxMethodOrNull(aClass)
        TestCase.assertTrue("Can't find box method in $aClass", method != null)
        callBoxMethodAndCheckResult(classLoader, aClass, method!!)
    }

    @Throws(IOException::class, IllegalAccessException::class, InvocationTargetException::class)
    protected fun callBoxMethodAndCheckResult(classLoader: URLClassLoader, aClass: Class<*>, method: Method) {
        val result: String
        if (boxInSeparateProcessPort != null) {
            result = invokeBoxInSeparateProcess(classLoader, aClass)
        } else {
            val savedClassLoader = Thread.currentThread().contextClassLoader
            if (savedClassLoader !== classLoader) {
                // otherwise the test infrastructure used in the test may conflict with the one from the context classloader
                Thread.currentThread().contextClassLoader = classLoader
            }
            try {
                result = method.invoke(null) as String
            } finally {
                if (savedClassLoader !== classLoader) {
                    Thread.currentThread().contextClassLoader = savedClassLoader
                }
            }
        }
        TestCase.assertEquals("OK", result)
    }

    @Throws(IOException::class)
    private fun invokeBoxInSeparateProcess(classLoader: URLClassLoader, aClass: Class<*>): String {
        val classPath = classLoader.extractUrls()
        val newClassPath = if (classLoader is GeneratedClassLoader) {
            val outDir = KotlinTestUtils.tmpDirForTest(this)
            val currentOutput = SimpleOutputFileCollection(classLoader.allGeneratedFiles)
            currentOutput.writeAllTo(outDir)
            classPath + outDir.toURI().toURL()
        } else classPath

        return TestProxy(Integer.valueOf(boxInSeparateProcessPort!!), aClass.canonicalName, newClassPath).runTest()
    }

    companion object {
        private val DEFAULT_TEST_FILE_NAME = "a_test"
        private val DEFAULT_JVM_TARGET_FOR_TEST = "kotlin.test.default.jvm.target"
        private val JAVA_COMPILATION_TARGET = "kotlin.test.java.compilation.target"
        val RUN_BOX_TEST_IN_SEPARATE_PROCESS_PORT = "kotlin.test.box.in.separate.process.port"

        @JvmStatic
        protected fun getJdkKind(files: List<TestFile>): TestJdkKind {
            for (file in files) {
                if (InTextDirectivesUtils.isDirectiveDefined(file.content, "FULL_JDK")) {
                    return TestJdkKind.FULL_JDK
                }
            }
            return TestJdkKind.MOCK_JDK
        }

        @JvmStatic
        @JvmOverloads
        protected fun updateConfigurationByDirectivesInTestFiles(
            testFilesWithConfigurationDirectives: List<TestFile>,
            configuration: CompilerConfiguration,
            coroutinesPackage: String = ""
        ) {
            var explicitLanguageVersionSettings: LanguageVersionSettings? = null
            var explicitLanguageVersion: LanguageVersion? = null

            val kotlinConfigurationFlags = ArrayList<String>(0)
            for (testFile in testFilesWithConfigurationDirectives) {
                kotlinConfigurationFlags.addAll(
                    InTextDirectivesUtils.findListWithPrefixes(
                        testFile.content,
                        "// KOTLIN_CONFIGURATION_FLAGS:"
                    )
                )

                val lines = InTextDirectivesUtils.findLinesWithPrefixesRemoved(testFile.content, "// JVM_TARGET:")
                if (!lines.isEmpty()) {
                    val targetString = lines.single()
                    val jvmTarget = JvmTarget.fromString(targetString) ?: error("Unknown target: $targetString")
                    configuration.put(JVMConfigurationKeys.JVM_TARGET, jvmTarget)
                }

                val version = InTextDirectivesUtils.findStringWithPrefixes(testFile.content, "// LANGUAGE_VERSION:")
                if (version != null) {
                    assertDirectivesToNull(explicitLanguageVersionSettings, explicitLanguageVersion)
                    explicitLanguageVersion = LanguageVersion.fromVersionString(version)
                }
                if (!InTextDirectivesUtils.findLinesWithPrefixesRemoved(testFile.content, "// COMMON_COROUTINES_TEST").isEmpty()) {
                    assert(!testFile.content.contains("COROUTINES_PACKAGE")) { "Must replace COROUTINES_PACKAGE prior to tests compilation" }
                    if (!coroutinesPackage.isEmpty()) {
                        if (coroutinesPackage == "kotlin.coroutines.experimental") {
                            explicitLanguageVersion = LanguageVersion.KOTLIN_1_2
                        } else {
                            explicitLanguageVersion = LanguageVersion.KOTLIN_1_3
                        }
                    }
                }

                val directives = KotlinTestUtils.parseDirectives(testFile.content)

                if (InTextDirectivesUtils.isDirectiveDefined(testFile.content, "WITH_UNSIGNED")) {
                    assertDirectivesToNull(explicitLanguageVersionSettings, explicitLanguageVersion)
                    explicitLanguageVersion = LanguageVersion.KOTLIN_1_3
                    directives[API_VERSION_DIRECTIVE] = ApiVersion.KOTLIN_1_3.versionString
                }

                val fileLanguageVersionSettings = parseLanguageVersionSettings(directives)
                if (fileLanguageVersionSettings != null) {
                    assertDirectivesToNull(explicitLanguageVersionSettings, null)
                    explicitLanguageVersionSettings = fileLanguageVersionSettings
                }
            }

            if (explicitLanguageVersionSettings != null) {
                configuration.languageVersionSettings = explicitLanguageVersionSettings
            } else if (explicitLanguageVersion != null) {
                val compilerLanguageVersionSettings = CompilerTestLanguageVersionSettings(
                    emptyMap(),
                    ApiVersion.createByLanguageVersion(explicitLanguageVersion),
                    explicitLanguageVersion,
                    emptyMap<AnalysisFlag<*>, Any>()
                )
                configuration.languageVersionSettings = compilerLanguageVersionSettings
            }

            updateConfigurationWithFlags(configuration, kotlinConfigurationFlags)
        }

        private fun assertDirectivesToNull(settings: LanguageVersionSettings?, version: LanguageVersion?) {
            assert(settings == null && version == null) { "Should not specify LANGUAGE_VERSION twice or together with !LANGUAGE directive" }
        }

        private val FLAG_NAMESPACE_TO_CLASS = ImmutableMap.of(
            "CLI", CLIConfigurationKeys::class.java,
            "JVM", JVMConfigurationKeys::class.java
        )

        private val FLAG_CLASSES = ImmutableList.of(CLIConfigurationKeys::class.java, JVMConfigurationKeys::class.java)

        private val BOOLEAN_FLAG_PATTERN = Pattern.compile("([+-])(([a-zA-Z_0-9]*)\\.)?([a-zA-Z_0-9]*)")
        private val CONSTRUCTOR_CALL_NORMALIZATION_MODE_FLAG_PATTERN = Pattern.compile(
            "CONSTRUCTOR_CALL_NORMALIZATION_MODE=([a-zA-Z_\\-0-9]*)"
        )
        private val ASSERTIONS_MODE_FLAG_PATTERN = Pattern.compile("ASSERTIONS_MODE=([a-zA-Z_0-9-]*)")

        private fun updateConfigurationWithFlags(configuration: CompilerConfiguration, flags: List<String>) {
            for (flag in flags) {
                var m = BOOLEAN_FLAG_PATTERN.matcher(flag)
                if (m.matches()) {
                    val flagEnabled = "-" != m.group(1)
                    val flagNamespace = m.group(3)
                    val flagName = m.group(4)

                    tryApplyBooleanFlag(configuration, flag, flagEnabled, flagNamespace, flagName)
                    continue
                }

                m = CONSTRUCTOR_CALL_NORMALIZATION_MODE_FLAG_PATTERN.matcher(flag)
                if (m.matches()) {
                    val flagValueString = m.group(1)
                    val mode = JVMConstructorCallNormalizationMode.fromStringOrNull(flagValueString)
                        ?: error("Wrong CONSTRUCTOR_CALL_NORMALIZATION_MODE value: $flagValueString")
                    configuration.put(JVMConfigurationKeys.CONSTRUCTOR_CALL_NORMALIZATION_MODE, mode)
                }

                m = ASSERTIONS_MODE_FLAG_PATTERN.matcher(flag)
                if (m.matches()) {
                    val flagValueString = m.group(1)
                    val mode = JVMAssertionsMode.fromStringOrNull(flagValueString) ?: error("Wrong ASSERTIONS_MODE value: $flagValueString")
                    configuration.put(JVMConfigurationKeys.ASSERTIONS_MODE, mode)
                }
            }
        }

        private fun tryApplyBooleanFlag(
            configuration: CompilerConfiguration,
            flag: String,
            flagEnabled: Boolean,
            flagNamespace: String?,
            flagName: String
        ) {
            val configurationKeysClass: Class<*>?
            var configurationKeyField: Field? = null
            if (flagNamespace == null) {
                for (flagClass in FLAG_CLASSES) {
                    try {
                        configurationKeyField = flagClass.getField(flagName)
                        break
                    } catch (ignored: Exception) {
                    }

                }
            } else {
                configurationKeysClass = FLAG_NAMESPACE_TO_CLASS[flagNamespace]
                assert(configurationKeysClass != null) { "Expected [+|-][namespace.]configurationKey, got: $flag" }
                try {
                    configurationKeyField = configurationKeysClass!!.getField(flagName)
                } catch (e: Exception) {
                    configurationKeyField = null
                }

            }
            assert(configurationKeyField != null) { "Expected [+|-][namespace.]configurationKey, got: $flag" }

            try {

                val configurationKey = configurationKeyField!!.get(null) as CompilerConfigurationKey<Boolean>
                configuration.put(configurationKey, flagEnabled)
            } catch (e: Exception) {
                assert(false) { "Expected [+|-][namespace.]configurationKey, got: $flag" }
            }

        }

        @JvmStatic
        fun loadMultiFiles(files: List<TestFile>, project: Project): CodegenTestFiles {
            Collections.sort(files)

            val ktFiles = ArrayList<KtFile>(files.size)
            for (file in files) {
                if (file.name.endsWith(".kt")) {
                    val content = CheckerTestUtil.parseDiagnosedRanges(file.content, ArrayList(0))
                    ktFiles.add(KotlinTestUtils.createFile(file.name, content, project))
                }
            }

            return CodegenTestFiles.create(ktFiles)
        }

        @JvmStatic
        protected fun verifyAllFilesWithAsm(factory: ClassFileFactory, loader: ClassLoader): Boolean {
            var noErrors = true
            for (file in factory.getClassFiles()) {
                noErrors = noErrors and verifyWithAsm(file, loader)
            }
            return noErrors
        }

        private fun verifyWithAsm(file: OutputFile, loader: ClassLoader): Boolean {
            val classNode = ClassNode()
            ClassReader(file.asByteArray()).accept(classNode, 0)

            val verifier = SimpleVerifier()
            verifier.setClassLoader(loader)
            val analyzer = Analyzer(verifier)

            var noErrors = true
            for (method in classNode.methods) {
                try {
                    analyzer.analyze(classNode.name, method)
                } catch (e: Throwable) {
                    System.err.println(file.asText())
                    System.err.println(classNode.name + "::" + method.name + method.desc)


                    if (e is AnalyzerException) {
                        // Print the erroneous instruction
                        val tmv = TraceMethodVisitor(Textifier())
                        e.node.accept(tmv)
                        val pw = PrintWriter(System.err)
                        tmv.p.print(pw)
                        pw.flush()
                    }

                    e.printStackTrace()
                    noErrors = false
                }

            }
            return noErrors
        }

        private fun createTestFiles(file: File, expectedText: String, javaFilesDir: Ref<File>, coroutinesPackage: String): List<TestFile> {
            return KotlinTestUtils.createTestFiles(file.name, expectedText, object : KotlinTestUtils.TestFileFactoryNoModules<TestFile>() {
                override fun create(fileName: String, text: String, directives: Map<String, String>): TestFile {
                    if (fileName.endsWith(".java")) {
                        if (javaFilesDir.isNull) {
                            try {
                                javaFilesDir.set(KotlinTestUtils.tmpDir("java-files"))
                            } catch (e: IOException) {
                                throw rethrow(e)
                            }

                        }
                        writeSourceFile(fileName, text, javaFilesDir.get())
                    }

                    return TestFile(fileName, text)
                }

                private fun writeSourceFile(fileName: String, content: String, targetDir: File) {
                    val file = File(targetDir, fileName)
                    KotlinTestUtils.mkdirs(file.parentFile)
                    file.writeText(content, Charsets.UTF_8)
                }
            }, coroutinesPackage)
        }
    }
}
