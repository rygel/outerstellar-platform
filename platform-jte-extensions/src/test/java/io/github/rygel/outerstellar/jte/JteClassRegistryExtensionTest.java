package io.github.rygel.outerstellar.jte;

import gg.jte.ContentType;
import gg.jte.extension.api.JteConfig;
import gg.jte.extension.api.ParamDescription;
import gg.jte.extension.api.TemplateDescription;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JteClassRegistryExtensionTest {
    private static final String REGISTRY_CONTRACT =
            "io.github.rygel.outerstellar.platform.extension.PrecompiledJteTemplateRegistry";

    @TempDir
    Path tempDir;

    @Test
    void generatesServiceProviderForPrecompiledTemplateRegistry() throws Exception {
        final Path sources = tempDir.resolve("generated-sources");
        final Path resources = tempDir.resolve("generated-resources");
        final JteConfig config = new TestConfig(
                sources,
                resources,
                "gg.jte.generated.precompiled.website"
        );
        final TemplateDescription template = new TestTemplateDescription(
                "pages/Home",
                "gg.jte.generated.precompiled.website.pages",
                "JteHomeGenerated"
        );

        new JteClassRegistryExtension().generate(config, Set.of(template));

        final Path sourceFile = sources
                .resolve("pages")
                .resolve("JteClassRegistry.kt");
        final String generatedSource = Files.readString(sourceFile);
        assertTrue(generatedSource.contains(
                "object JteClassRegistry : PrecompiledJteTemplateRegistry"));
        assertTrue(generatedSource.contains(
                "class JteClassRegistryProvider : PrecompiledJteTemplateRegistry"));

        final Path serviceFile = resources
                .resolve("META-INF")
                .resolve("services")
                .resolve(REGISTRY_CONTRACT);
        assertEquals(
                "pages.JteClassRegistryProvider",
                Files.readString(serviceFile).trim()
        );
    }

    private record TestConfig(
            Path generatedSourcesRoot,
            Path generatedResourcesRoot,
            String packageName
    ) implements JteConfig {
        @Override
        public String projectNamespace() {
            return "";
        }

        @Override
        public ContentType contentType() {
            return ContentType.Html;
        }

        @Override
        public ClassLoader classLoader() {
            return Thread.currentThread().getContextClassLoader();
        }
    }

    private record TestTemplateDescription(
            String name,
            String packageName,
            String className
    ) implements TemplateDescription {
        @Override
        public List<ParamDescription> params() {
            return List.of();
        }

        @Override
        public List<String> imports() {
            return List.of();
        }
    }
}
