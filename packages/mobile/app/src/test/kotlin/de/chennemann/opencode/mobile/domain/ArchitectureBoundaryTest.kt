package de.chennemann.opencode.mobile.domain

import java.nio.file.Files
import java.nio.file.Path
import java.util.stream.Collectors
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class ArchitectureBoundaryTest {
    @Test
    fun domainDoesNotImportDataOrAndroid() {
        val root = Path.of("src", "main", "kotlin", "de", "chennemann", "opencode", "mobile", "domain")
        Files.walk(root).use { paths ->
            val imports = paths
                .filter { Files.isRegularFile(it) && it.toString().endsWith(".kt") }
                .flatMap { file -> Files.readAllLines(file).stream() }
                .filter { it.startsWith("import ") }
                .collect(Collectors.toList())
            assertFalse(imports.any { it.contains("de.chennemann.opencode.mobile.data.") })
            assertFalse(imports.any { it.contains("android.") })
        }
    }
}
