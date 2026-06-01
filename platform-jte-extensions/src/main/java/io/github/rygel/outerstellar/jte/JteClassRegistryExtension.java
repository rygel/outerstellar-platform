package io.github.rygel.outerstellar.jte;

import gg.jte.extension.api.JteConfig;
import gg.jte.extension.api.JteExtension;
import gg.jte.extension.api.TemplateDescription;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@SuppressWarnings("unused")
public class JteClassRegistryExtension implements JteExtension {
    private static final String REGISTRY_CONTRACT =
            "io.github.rygel.outerstellar.platform.extension.PrecompiledJteTemplateRegistry";


    @Override
    public String name() {
        return "JTE class registry generator";
    }

    @Override
    public Collection<Path> generate(
            final JteConfig config,
            final Set<TemplateDescription> templates
    ) {
        Collection<Path> result = List.of();
        if (config.generatedSourcesRoot() != null
                && !templates.isEmpty()) {
            result = doGenerate(config, templates);
        }
        return result;
    }

    private Collection<Path> doGenerate(
            final JteConfig config,
            final Set<TemplateDescription> templates
    ) {
        final String basePackage = config.packageName();
        final String registryPackage = deriveRegistryPackage(
                basePackage, templates.iterator().next());

        final Map<String, List<TemplateDescription>> categories =
                categorize(templates);

        final List<String> pages = toClassNames(
                categories.getOrDefault("pageClasses", List.of()));
        final List<String> fragments = toClassNames(
                categories.getOrDefault("fragmentClasses", List.of()));
        final List<String> components = toClassNames(
                categories.getOrDefault("componentClasses", List.of()));
        final List<String> layouts = toClassNames(
                categories.getOrDefault("layoutClasses", List.of()));

        final String source = generateSource(
                registryPackage, basePackage,
                pages, fragments, components, layouts);

        final Path outputDir =
                config.generatedSourcesRoot()
                        .resolve(registryPackage.replace('.', '/'));
        try {
            Files.createDirectories(outputDir);
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }

        final Path outputFile = outputDir.resolve("JteClassRegistry.kt");
        try {
            Files.writeString(outputFile, source);
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }

        writeServiceProvider(config, registryPackage);

        return List.of(outputFile);
    }

    private void writeServiceProvider(
            final JteConfig config,
            final String registryPackage
    ) {
        if (config.generatedResourcesRoot() == null) {
            return;
        }

        final Path serviceDir =
                config.generatedResourcesRoot()
                        .resolve("META-INF")
                        .resolve("services");
        final Path serviceFile = serviceDir.resolve(REGISTRY_CONTRACT);
        try {
            Files.createDirectories(serviceDir);
            Files.writeString(serviceFile, registryPackage + ".JteClassRegistryProvider\n");
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private String deriveRegistryPackage(
            final String basePackage,
            final TemplateDescription firstTemplate
    ) {
        final String fqn = firstTemplate.fullyQualifiedClassName();
        final String withoutBase = fqn.substring(basePackage.length() + 1);
        final int lastDot = withoutBase.lastIndexOf('.');
        final String result;
        if (lastDot > 0) {
            result = withoutBase.substring(0, lastDot);
        } else {
            result = withoutBase;
        }
        return result;
    }

    private Map<String, List<TemplateDescription>> categorize(
            final Set<TemplateDescription> descriptions
    ) {
        final Map<String, List<TemplateDescription>> categories =
                new LinkedHashMap<>();
        categories.put("pageClasses", new ArrayList<>());
        categories.put("fragmentClasses", new ArrayList<>());
        categories.put("componentClasses", new ArrayList<>());
        categories.put("layoutClasses", new ArrayList<>());

        for (final TemplateDescription desc : descriptions) {
            final String name = desc.name();
            if (name.contains("/layouts/")) {
                categories.get("layoutClasses").add(desc);
            } else if (name.contains("/components/")) {
                categories.get("componentClasses").add(desc);
            } else if (name.endsWith("Fragment") || name.endsWith("Form")) {
                categories.get("fragmentClasses").add(desc);
            } else {
                categories.get("pageClasses").add(desc);
            }
        }
        return categories;
    }

    private List<String> toClassNames(
            final List<TemplateDescription> descs
    ) {
        return descs.stream()
                .map(TemplateDescription::fullyQualifiedClassName)
                .sorted()
                .collect(Collectors.toList());
    }

    private String generateSource(
            final String registryPackage,
            final String basePackage,
            final List<String> pages,
            final List<String> fragments,
            final List<String> components,
            final List<String> layouts
    ) {
        return new RegistrySourceWriter(
                registryPackage,
                basePackage,
                pages,
                fragments,
                components,
                layouts
        ).generate();
    }

    private static final class RegistrySourceWriter {
        private final String registryPackage;
        private final String basePackage;
        private final List<String> pages;
        private final List<String> fragments;
        private final List<String> components;
        private final List<String> layouts;

        private RegistrySourceWriter(
                final String registryPackage,
                final String basePackage,
                final List<String> pages,
                final List<String> fragments,
                final List<String> components,
                final List<String> layouts
        ) {
            this.registryPackage = registryPackage;
            this.basePackage = basePackage;
            this.pages = pages;
            this.fragments = fragments;
            this.components = components;
            this.layouts = layouts;
        }

        private String generate() {
            final StringWriter stringWriter = new StringWriter();
            final PrintWriter out = new PrintWriter(stringWriter);

            writeHeader(out, allClassNames());
            writeRegistryHeader(out);
            writeClassList(out, "pageClasses", pages);
            writeClassList(out, "fragmentClasses", fragments);
            writeClassList(out, "componentClasses", components);
            writeClassList(out, "layoutClasses", layouts);
            writeRegistryBody(out);
            writeRegistryProvider(out);

            out.flush();
            return stringWriter.toString();
        }

        private List<String> allClassNames() {
            final List<String> allClassNames = new ArrayList<>(
                    pages.size() + fragments.size()
                            + components.size() + layouts.size());
            allClassNames.addAll(pages);
            allClassNames.addAll(fragments);
            allClassNames.addAll(components);
            allClassNames.addAll(layouts);
            return allClassNames;
        }

        private void writeHeader(
                final PrintWriter out,
                final List<String> allClassNames
        ) {
            out.println("package " + registryPackage);
            out.println();

            for (final String fqn : allClassNames) {
                out.println("import " + fqn);
            }
            out.println("import " + REGISTRY_CONTRACT);
            out.println("import org.slf4j.LoggerFactory");
            out.println();
        }

        private void writeRegistryHeader(final PrintWriter out) {
            out.println("object JteClassRegistry : PrecompiledJteTemplateRegistry {");
            out.println(
                    "    private val logger = "
                            + "LoggerFactory.getLogger(JteClassRegistry::class.java)");
            out.println();
        }

        private void writeClassList(
                final PrintWriter out,
                final String fieldName,
                final List<String> classNames
        ) {
            out.println("    private val " + fieldName + " =");
            if (classNames.isEmpty()) {
                out.println("        listOf<Class<*>>()");
                out.println();
            } else {
                out.println("        listOf(");
                for (int i = 0; i < classNames.size(); i++) {
                    final String fqn = classNames.get(i);
                    final String simpleName =
                            fqn.substring(fqn.lastIndexOf('.') + 1);
                    final String entry = "            "
                            + simpleName + "::class.java";
                    if (i < classNames.size() - 1) {
                        out.println(entry + ",");
                    } else {
                        out.println(entry);
                    }
                }
                out.println("        )");
                out.println();
            }
        }

        private void writeRegistryBody(final PrintWriter out) {
            out.println("    override val allClasses: List<Class<*>> =");
            out.println(
                    "        pageClasses + fragmentClasses "
                            + "+ componentClasses + layoutClasses");
            out.println();
            out.println("    private val classMap: Map<String, Class<*>> =");
            out.println("        allClasses.associateBy { it.name }");
            out.println();
            writeRegistryInitializer(out);
            writeTemplateLookup(out);
            out.println("}");
        }

        private void writeRegistryInitializer(final PrintWriter out) {
            out.println("    init {");
            out.println(
                    "        logger.info("
                            + "\"Initializing {} JTE template classes\", "
                            + "allClasses.size)");
            out.println("        for (cls in allClasses) {");
            out.println("            try {");
            out.println("                Class.forName(cls.name, true, cls.classLoader)");
            out.println("            } catch (e: ClassNotFoundException) {");
            out.println(
                    "                logger.warn("
                            + "\"Failed to force-load JTE template class "
                            + "{}: {}\", cls.name, e.message)");
            out.println("            }");
            out.println("        }");
            out.println("    }");
            out.println();
        }

        private void writeTemplateLookup(final PrintWriter out) {
            out.println("    override fun getTemplateClass(templateName: String): Class<*>? {");
            out.println("        val templatePath = templateName.removeSuffix(\".kte\")");
            out.println("        val slash = templatePath.lastIndexOf('/')");
            out.println("        val packagePath =");
            out.println(
                    "            if (slash >= 0) "
                            + "templatePath.substring(0, slash)"
                            + ".replace('/', '.') else \"\"");
            out.println(
                    "        val baseName = if (slash >= 0) "
                            + "templatePath.substring(slash + 1) "
                            + "else templatePath");
            out.println(
                    "        val className = "
                            + "\"Jte${baseName.replace"
                            + "(\"-\", \"\").replace(\".\", \"\")}"
                            + "Generated\"");
            out.println("        val fullName =");
            out.println("            if (packagePath.isEmpty()) {");
            out.println("                \"" + basePackage + ".$className\"");
            out.println("            } else {");
            out.println("                \"" + basePackage + ".$packagePath.$className\"");
            out.println("            }");
            out.println("        return classMap[fullName]");
            out.println("    }");
        }

        private void writeRegistryProvider(final PrintWriter out) {
            out.println();
            out.println("class JteClassRegistryProvider : PrecompiledJteTemplateRegistry {");
            out.println("    override val allClasses: List<Class<*>>");
            out.println("        get() = JteClassRegistry.allClasses");
            out.println();
            out.println("    override fun getTemplateClass(templateName: String): Class<*>? =");
            out.println("        JteClassRegistry.getTemplateClass(templateName)");
            out.println("}");
        }
    }
}
