/*
 * Copyright 2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.api.provider

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class PropertyIntegrationTest extends AbstractIntegrationSpec {
    def "can use property as task input"() {
        given:
        buildFile << """
class SomeTask extends DefaultTask {
    @Input
    final Property<String> prop = project.objects.property(String)
    
    @OutputFile
    final Property<RegularFile> outputFile = project.objects.fileProperty()
    
    @TaskAction
    void go() { 
        outputFile.get().asFile.text = prop.get()
    }
}

task thing(type: SomeTask) {
    prop = System.getProperty('prop')
    outputFile = layout.buildDirectory.file("out.txt")
}

"""

        when:
        executer.withArgument("-Dprop=123")
        run("thing")

        then:
        executedAndNotSkipped(":thing")

        when:
        executer.withArgument("-Dprop=123")
        run("thing")

        then:
        skipped(":thing")

        when:
        executer.withArgument("-Dprop=abc")
        run("thing")

        then:
        executedAndNotSkipped(":thing")

        when:
        executer.withArgument("-Dprop=abc")
        run("thing")

        then:
        skipped(":thing")
    }

    def "can set property value from DSL using a value or a provider"() {
        given:
        buildFile << """
class SomeExtension {
    final Property<String> prop
    
    @javax.inject.Inject
    SomeExtension(ObjectFactory objects) {
        prop = objects.property(String)
    }
}

class SomeTask extends DefaultTask {
    final Property<String> prop = project.objects.property(String)
}

extensions.create('custom', SomeExtension, objects)
custom.prop = "value"
assert custom.prop.get() == "value"

custom.prop = providers.provider { "new value" }
assert custom.prop.get() == "new value"

tasks.create('t', SomeTask)
tasks.t.prop = custom.prop
assert tasks.t.prop.get() == "new value"

custom.prop = "changed"
assert custom.prop.get() == "changed"
assert tasks.t.prop.get() == "changed"

"""

        expect:
        succeeds()
    }

    def "can set String property value from DSL using a GString"() {
        given:
        buildFile << """
class SomeExtension {
    final Property<String> prop
    
    @javax.inject.Inject
    SomeExtension(ObjectFactory objects) {
        prop = objects.property(String)
    }
}

extensions.create('custom', SomeExtension, objects)
custom.prop = "\${'some value'.substring(5)}"
assert custom.prop.get() == "value"

custom.prop = providers.provider { "\${'some new value'.substring(5)}" }
assert custom.prop.get() == "new value"

custom.prop.set("\${'some other value'.substring(5)}")
assert custom.prop.get() == "other value"
"""

        expect:
        succeeds()
    }

    def "reports failure to set property value using incompatible type"() {
        given:
        buildFile << """
class SomeExtension {
    final Property<String> prop
    
    @javax.inject.Inject
    SomeExtension(ObjectFactory objects) {
        prop = objects.property(String)
    }
}

extensions.create('custom', SomeExtension, objects)

task wrongValueTypeDsl {
    doLast {
        custom.prop = 123
    }
}

task wrongValueTypeApi {
    doLast {
        custom.prop.set(123)
    }
}

task wrongPropertyTypeDsl {
    doLast {
        custom.prop = objects.property(Integer)
    }
}

task wrongPropertyTypeApi {
    doLast {
        custom.prop.set(objects.property(Integer))
    }
}

task wrongRuntimeType {
    doLast {
        custom.prop = providers.provider { 123 }
        custom.prop.get()
    }
}
"""

        when:
        fails("wrongValueTypeDsl")

        then:
        failure.assertHasDescription("Execution failed for task ':wrongValueTypeDsl'.")
        failure.assertHasCause("Cannot set the value of a property of type java.lang.String using an instance of type java.lang.Integer.")

        when:
        fails("wrongValueTypeApi")

        then:
        failure.assertHasDescription("Execution failed for task ':wrongValueTypeApi'.")
        failure.assertHasCause("Cannot set the value of a property of type java.lang.String using an instance of type java.lang.Integer.")

        when:
        fails("wrongPropertyTypeDsl")

        then:
        failure.assertHasDescription("Execution failed for task ':wrongPropertyTypeDsl'.")
        failure.assertHasCause("Cannot set the value of a property of type java.lang.String using a provider of type java.lang.Integer.")

        when:
        fails("wrongPropertyTypeApi")

        then:
        failure.assertHasDescription("Execution failed for task ':wrongPropertyTypeApi'.")
        failure.assertHasCause("Cannot set the value of a property of type java.lang.String using a provider of type java.lang.Integer.")

        when:
        fails("wrongRuntimeType")

        then:
        failure.assertHasDescription("Execution failed for task ':wrongRuntimeType'.")
        failure.assertHasCause("Cannot get the value of a property of type java.lang.String as the provider associated with this property returned a value of type java.lang.Integer.")
    }
}
