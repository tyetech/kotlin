
plugins {
    kotlin("jvm")
    id("jps-compatible")
}

dependencies {
    compile(projectDist(":kotlin-stdlib"))
    compile(project(":compiler:backend"))
    compile(files(toolsJar()))
    compileOnly(intellijDep()) { includeJars("asm-all") }
    testCompile(projectDist(":kotlin-test:kotlin-test-junit"))
    testCompile(commonDep("junit:junit"))
    testCompile(intellijDep()) { includeJars("asm-all") }
}

sourceSets {
    "main" { projectDefault(project) }
    "test" { projectDefault(project) }
}

projectTest {
    dependsOn(":dist")
    workingDir = rootDir
}
