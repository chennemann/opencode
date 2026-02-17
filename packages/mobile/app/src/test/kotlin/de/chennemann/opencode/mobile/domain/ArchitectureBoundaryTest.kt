package de.chennemann.opencode.mobile.domain

import java.nio.file.Files
import java.nio.file.Path
import java.util.stream.Collectors
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
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
            val forbiddenDataImports = imports
                .filter { it.contains("de.chennemann.opencode.mobile.data.") }
                .filterNot { it.contains("de.chennemann.opencode.mobile.data.repository.") }
            assertTrue(
                forbiddenDataImports.isEmpty(),
                "Domain code may only import repository contracts from data layer: ${forbiddenDataImports.joinToString()}"
            )
            assertFalse(imports.any { it.contains("de.chennemann.opencode.mobile.ui.") })
            assertFalse(imports.any { it.contains("de.chennemann.opencode.mobile.navigation.") })
            assertFalse(imports.any { it.contains("android.") })
        }
    }

    @Test
    fun uiDoesNotImportRemoteAdaptersOrLegacySessionOrchestrator() {
        val root = Path.of("src", "main", "kotlin", "de", "chennemann", "opencode", "mobile", "ui")
        Files.walk(root).use { paths ->
            val imports = paths
                .filter { Files.isRegularFile(it) && it.toString().endsWith(".kt") }
                .flatMap { file -> Files.readAllLines(file).stream() }
                .filter { it.startsWith("import ") }
                .collect(Collectors.toList())
            val blocked = listOf(
                "de.chennemann.opencode.mobile.data.ServerGateway",
                "de.chennemann.opencode.mobile.data.ServerRepository",
                "de.chennemann.opencode.mobile.domain.session.SessionServiceApi",
            )
            val violations = imports.filter { line -> blocked.any { token -> line.contains(token) } }
            assertTrue(violations.isEmpty(), "UI layer imports forbidden types: ${violations.joinToString()}")
        }
    }

    @Test
    fun syncServicesDoNotDependOnUiFrameworks() {
        val root = Path.of("src", "main", "kotlin", "de", "chennemann", "opencode", "mobile", "domain", "service", "sync")
        val blocked = listOf("androidx.compose.", "androidx.lifecycle.ViewModel", "de.chennemann.opencode.mobile.navigation.")
        Files.walk(root).use { paths ->
            val violations = paths
                .filter { Files.isRegularFile(it) && it.toString().endsWith(".kt") }
                .flatMap { file ->
                    Files.readAllLines(file)
                        .mapIndexedNotNull { index, line ->
                            blocked.firstOrNull { token -> line.contains(token) }
                                ?.let { token -> "${file.toString().replace('\\', '/')}#L${index + 1} uses $token" }
                        }
                        .stream()
                }
                .collect(Collectors.toList())
            assertTrue(violations.isEmpty(), "Sync services import UI frameworks: ${violations.joinToString()}")
        }
    }

    @Test
    fun repositoriesDoNotDependOnUiFrameworks() {
        val root = Path.of("src", "main", "kotlin", "de", "chennemann", "opencode", "mobile", "data", "repository")
        val blocked = listOf("androidx.compose.", "androidx.lifecycle.ViewModel", "de.chennemann.opencode.mobile.navigation.")
        Files.walk(root).use { paths ->
            val violations = paths
                .filter { Files.isRegularFile(it) && it.toString().endsWith(".kt") }
                .flatMap { file ->
                    Files.readAllLines(file)
                        .mapIndexedNotNull { index, line ->
                            blocked.firstOrNull { token -> line.contains(token) }
                                ?.let { token -> "${file.toString().replace('\\', '/')}#L${index + 1} uses $token" }
                        }
                        .stream()
                }
                .collect(Collectors.toList())
            assertTrue(violations.isEmpty(), "Repositories import UI frameworks: ${violations.joinToString()}")
        }
    }

    @Test
    fun mobilePipelineDoesNotUseLegacyCursorOrAsyncPromptTokens() {
        val roots = listOf(
            Path.of("src", "main", "kotlin", "de", "chennemann", "opencode", "mobile"),
            Path.of("src", "main", "sqldelight", "de", "chennemann", "opencode", "mobile", "db"),
        )
        val blocked = listOf("Last-Event-ID", "event_cursor:", "session.prompt_async", "streamCursor(", "setStreamCursor(")
        val violations = mutableListOf<String>()
        roots.forEach { root ->
            Files.walk(root).use { paths ->
                paths
                    .filter { Files.isRegularFile(it) && (it.toString().endsWith(".kt") || it.toString().endsWith(".sq") || it.toString().endsWith(".sqm")) }
                    .forEach { file ->
                        Files.readAllLines(file)
                            .forEachIndexed { index, line ->
                                blocked.firstOrNull { token -> line.contains(token) }
                                    ?.let { token -> violations += "${file.toString().replace('\\', '/')}#L${index + 1} uses $token" }
                            }
                    }
            }
        }
        assertTrue(violations.isEmpty(), "Legacy cursor/prompt_async tokens found: ${violations.joinToString()}")
    }
}
