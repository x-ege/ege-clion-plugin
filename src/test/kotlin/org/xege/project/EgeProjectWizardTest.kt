package org.xege.project

import com.jetbrains.cidr.cpp.cmake.projectWizard.generators.CLionProjectGenerator
import java.util.Properties
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class EgeProjectWizardTest {
    @Test
    fun `wizard is grouped with C++ projects instead of Other`() {
        val generator = EgeProjectGenerator()

        assertEquals("C++", generator.groupName)
        assertEquals("C++", generator.groupDisplayName)
        assertEquals(CLionProjectGenerator.GroupOrders.CPP.order, generator.groupOrder)
    }

    @Test
    fun `both locales identify the wizard as Xege and EGE`() {
        // Read each bundle directly so locale fallback cannot hide a missing label.
        for (bundle in listOf("XegeBundle.properties", "XegeBundle_zh_CN.properties")) {
            val properties = Properties()
            resource("/messages/$bundle").bufferedReader(Charsets.UTF_8).use(properties::load)
            assertEquals("Xege (EGE)", properties.getProperty("generator.name"), bundle)
        }
    }

    @Test
    fun `packaged CLion descriptor registers the wizard once`() {
        val factory = DocumentBuilderFactory.newInstance().apply {
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        }
        val builder = factory.newDocumentBuilder()
        val plugin = resource("/META-INF/plugin.xml").use(builder::parse)
        val dependencies = plugin.getElementsByTagName("depends")
        val clion = (0 until dependencies.length)
            .map { dependencies.item(it) }
            .single { it.textContent.trim() == "com.intellij.clion" }
        val descriptor = assertNotNull(clion.attributes.getNamedItem("config-file")).nodeValue
        val support = resource("/META-INF/$descriptor").use(builder::parse)
        val generators = support.getElementsByTagName("directoryProjectGenerator")

        assertEquals(1, generators.length)
        assertEquals(
            EgeProjectGenerator::class.java.name,
            generators.item(0).attributes.getNamedItem("implementation").nodeValue
        )
    }

    private fun resource(path: String) = assertNotNull(javaClass.getResourceAsStream(path), path)
}
