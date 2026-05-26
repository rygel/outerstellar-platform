$ErrorActionPreference = "Stop"

$RepoRoot = Resolve-Path (Join-Path $PSScriptRoot "..")
$JteRoot = Join-Path $RepoRoot "platform-web/src/main/jte"
$OutputFile = Join-Path $RepoRoot "platform-web/src/main/kotlin/io/github/rygel/outerstellar/platform/infra/JteClassRegistry.kt"

if (-not (Test-Path $JteRoot)) {
    Write-Error "JTE root not found: $JteRoot"
    exit 1
}

$templateFiles = Get-ChildItem -LiteralPath $JteRoot -Recurse -Filter "*.kte" | Sort-Object { $_.FullName }
Write-Host "Found $($templateFiles.Count) .kte files"

$entries = @()
foreach ($file in $templateFiles) {
    $relativePath = $file.FullName.Substring($JteRoot.Length + 1).Replace("\", "/")
    $relativePathNoExt = $relativePath -replace '\.kte$', ''
    $dirPart = ""
    $lastSlash = $relativePathNoExt.LastIndexOf('/')
    if ($lastSlash -ge 0) {
        $dirPart = $relativePathNoExt.Substring(0, $lastSlash)
    }
    $baseName = if ($lastSlash -ge 0) { $relativePathNoExt.Substring($lastSlash + 1) } else { $relativePathNoExt }

    $sanitizedBase = $baseName -replace '-', '' -replace '\.', ''
    $className = "Jte${sanitizedBase}Generated"

    $dirPartDots = $dirPart.Replace("/", ".")
    if ($dirPart -eq "") {
        $fqcn = "gg.jte.generated.precompiled.outerstellar.$className"
    } else {
        $fqcn = "gg.jte.generated.precompiled.outerstellar.$dirPartDots.$className"
    }

    $category = "page"
    if ($relativePath -match '/layouts/') {
        $category = "layout"
    } elseif ($relativePath -match '/components/') {
        $category = "component"
    } elseif ($baseName -match '(Fragment|Form)$') {
        $category = "fragment"
    }

    $entries += [PSCustomObject]@{
        FQCN      = $fqcn
        ClassName = $className
        Category  = $category
        SortKey   = $fqcn
    }
}

$pages = $entries | Where-Object { $_.Category -eq "page" } | Sort-Object { $_.SortKey }
$fragments = $entries | Where-Object { $_.Category -eq "fragment" } | Sort-Object { $_.SortKey }
$components = $entries | Where-Object { $_.Category -eq "component" } | Sort-Object { $_.SortKey }
$layouts = $entries | Where-Object { $_.Category -eq "layout" } | Sort-Object { $_.SortKey }

$allImports = @()
$allImports += $pages | ForEach-Object { "import $($_.FQCN)" }
$allImports += $fragments | ForEach-Object { "import $($_.FQCN)" }
$allImports += $components | ForEach-Object { "import $($_.FQCN)" }
$allImports += $layouts | ForEach-Object { "import $($_.FQCN)" }
$allImports += "import org.slf4j.LoggerFactory"
$allImports = $allImports | Sort-Object { if ($_ -match "org\.slf4j") { "zzz" } else { $_ } }

$lines = @()
$lines += "package io.github.rygel.outerstellar.platform.infra"
$lines += ""
foreach ($imp in $allImports) {
    $lines += $imp
}
$lines += ""
$lines += "object JteClassRegistry {"
$lines += "    private val logger = LoggerFactory.getLogger(JteClassRegistry::class.java)"
$lines += ""
$lines += "    private val pageClasses ="
$lines += "        listOf("
foreach ($entry in $pages) {
    $lines += "            $($entry.ClassName)::class.java,"
}
$lines += "        )"
$lines += ""
$lines += "    private val fragmentClasses ="
$lines += "        listOf("
foreach ($entry in $fragments) {
    $lines += "            $($entry.ClassName)::class.java,"
}
$lines += "        )"
$lines += ""
$lines += "    private val componentClasses ="
$lines += "        listOf("
foreach ($entry in $components) {
    $lines += "            $($entry.ClassName)::class.java,"
}
$lines += "        )"
$lines += ""
$lines += "    private val layoutClasses ="
$lines += "        listOf("
foreach ($entry in $layouts) {
    $lines += "            $($entry.ClassName)::class.java,"
}
$lines += "        )"
$lines += ""
$lines += "    val allClasses: List<Class<*>> = pageClasses + fragmentClasses + componentClasses + layoutClasses"
$lines += ""
$lines += "    private val classMap: Map<String, Class<*>> = allClasses.associateBy { it.name }"
$lines += ""

$verbatimBlock = @'
    init {
        logger.info("Initializing {} JTE template classes", allClasses.size)
        for (cls in allClasses) {
            try {
                Class.forName(cls.name, true, cls.classLoader)
            } catch (e: ClassNotFoundException) {
                logger.warn("Failed to force-load JTE template class {}: {}", cls.name, e.message)
            }
        }
    }

    fun getTemplateClass(templateName: String): Class<*>? {
        val templatePath = templateName.removeSuffix(".kte")
        val slash = templatePath.lastIndexOf('/')
        val packagePath = if (slash >= 0) templatePath.substring(0, slash).replace('/', '.') else ""
        val baseName = if (slash >= 0) templatePath.substring(slash + 1) else templatePath
        val className = "Jte${baseName.replace("-", "").replace(".", "")}Generated"
        val fullName =
            if (packagePath.isEmpty()) {
                "gg.jte.generated.precompiled.outerstellar.$className"
            } else {
                "gg.jte.generated.precompiled.outerstellar.$packagePath.$className"
            }
        return classMap[fullName]
    }
}
'@

$lines += $verbatimBlock

$lines | Out-File -FilePath $OutputFile -Encoding utf8NoBOM

$total = $pages.Count + $fragments.Count + $components.Count + $layouts.Count
Write-Host "Generated $OutputFile with $total entries ($($pages.Count) pages, $($fragments.Count) fragments, $($components.Count) components, $($layouts.Count) layouts)"
