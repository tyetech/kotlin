import org.jetbrains.kotlin.gradle.dsl.Coroutines

plugins {
    kotlin("jvm")
}

dependencies {
    compile(project(":examples:scripting-jvm-maven-deps"))
    compile(project(":kotlin-scripting-jvm-host"))
    compile(projectDist(":kotlin-stdlib"))
    compile(projectDist(":kotlin-reflect"))
    compileOnly(project(":compiler:util"))
    runtime(projectRuntimeJar(":kotlin-compiler"))
    testCompile(commonDep("junit"))
}

sourceSets {
    "main" { projectDefault(project) }
    "test" { projectDefault(project) }
}

kotlin {
    experimental.coroutines = Coroutines.ENABLE
}
