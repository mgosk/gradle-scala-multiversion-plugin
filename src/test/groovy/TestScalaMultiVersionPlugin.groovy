/*
 * Copyright 2017 ADTRAN, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import com.adtran.ScalaMultiVersionPlugin
import com.adtran.ScalaMultiVersionPluginExtension
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.tasks.GradleBuild
import org.gradle.testfixtures.ProjectBuilder

class TestScalaMultiVersionPlugin extends GroovyTestCase {

    private Project createProject(
            String scalaVersions,
            ScalaMultiVersionPluginExtension extension = new ScalaMultiVersionPluginExtension(),
            Map<String,String> properties = [:])
    {
        Project project = ProjectBuilder.builder()
                .withProjectDir(new File(System.getProperty("user.dir"), "testProjects/simpleProject"))
                .build()
        project.ext.scalaVersions = scalaVersions
        project.ext.scalaMultiVersion = extension
        properties.each { k,v -> project.ext.set(k, v) }
        project.pluginManager.apply("java")
        project.pluginManager.apply(ScalaMultiVersionPlugin)
        project.evaluate()
        return project
    }

    void testBadScalaVersions() {
        [ [null, "Must set `scalaVersions` property."],
          ["", "Invalid scala version"],
          ["2.12", "Invalid scala version"]
        ].each {
            def (scalaVersions, error_msg) = it
            def msg = shouldFailWithCause(GradleException) { createProject(scalaVersions) }
            assert msg.contains(error_msg)
        }
    }

    void testPluginApply() {
        def project = createProject("2.12.1")
        assertTrue(project.pluginManager.hasPlugin("com.adtran.scala-multiversion-plugin"))
    }

    void testSingleVersion() {
        def project = createProject("2.12.1")
        assert ["2.12.1"] == project.ext.scalaVersions
        assert ["_2.12"] == project.ext.scalaSuffixes
        assert "2.12.1" == project.ext.scalaVersion
        assert "_2.12" == project.ext.scalaSuffix
    }

    void testMultipleVersions() {
        // comma-separated lists, with or without whitespace, should be allowed
        ["2.12.1,2.11.8", "2.12.1, 2.11.8"].each {
            def project = createProject(it)
            assert ["2.12.1", "2.11.8"] == project.ext.scalaVersions
            assert ["_2.12", "_2.11"] == project.ext.scalaSuffixes
            assert "2.12.1" == project.ext.scalaVersion
            assert "_2.12" == project.ext.scalaSuffix
        }
    }

    void testBaseName() {
        def project = createProject("2.12.1")
        assert project.jar.baseName.endsWith("_2.12")
    }

    void testTasks() {
        def versions = ["2.12.1", "2.11.8"]
        def project = createProject(versions.join(","))
        assert project.tasks.buildMultiVersion instanceof DefaultTask
        project.tasks.withType(GradleBuild).each { assert it.tasks == ['build'] }
        versions.each {
            def task = project.tasks.getByName("build_$it")
            assert task instanceof GradleBuild
            assert project.tasks.buildMultiVersion.getDependsOn().contains(task)
        }
    }

    void testResolutionStrategy() {
        def project = createProject("2.12.1")
        def conf = project.configurations.getByName("compile").resolvedConfiguration.lenientConfiguration
        assert conf.unresolvedModuleDependencies.size() == 0
        def deps = conf.getAllModuleDependencies()
        assert deps.size() == 3
        deps.each { assert !it.name.contains("_%%") }
        deps.each { assert !it.moduleVersion.contains("scalaVersion") }
    }

    void testExtension() {
        def buildTasks = ["t1", "t2"]
        def extension = new ScalaMultiVersionPluginExtension(
                buildTasks: buildTasks,
                scalaVersionPlaceholder: "<<sv>>",
                scalaSuffixPlaceholder: "_##"
        )
        def project = createProject("2.12.1", extension)
        project.tasks.withType(GradleBuild).each { assert it.tasks == buildTasks }
        def conf = project.configurations.getByName("compile").resolvedConfiguration.lenientConfiguration
        assert conf.unresolvedModuleDependencies.size() == 2
        assert conf.getAllModuleDependencies().size() == 1
    }

    void testExtensionOverride() {
        def extension = new ScalaMultiVersionPluginExtension(
                buildTasks: ['a1', 'a2'],
                scalaVersionPlaceholder: "<<sv>>",
                scalaSuffixPlaceholder: "_##"
        )
        def project = createProject("2.12.1", extension, ["buildTasks": "t1, t2"])
        project.tasks.withType(GradleBuild).each { assert it.tasks == ["t1", "t2"] }
    }
}