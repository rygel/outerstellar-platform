# SEO & Metadata

## Overview

SEO metadata is generated using the `fragments-seo-core` library (v0.6.5). The `SeoMetadata.forPage()` factory builds Open Graph, Twitter Card, and JSON-LD structured data for every page. `ShellView` carries all SEO fields, and `LayoutHead.kte` emits the tags.

## Features

### Standard Meta Tags

- `<title>` — `pageTitle - appTitle`
- `<meta name="description">` — i18n-backed per-section descriptions
- `<link rel="canonical">` — `appBaseUrl + currentPath`
- `<meta name="robots">` — `noindex, nofollow` on admin/auth/error pages
- `<meta name="viewport">` — `width=device-width, initial-scale=1.0`
- `<meta name="theme-color">` — `#0f172a`

### Open Graph

| Tag | Source |
|---|---|
| `og:title` | ShellView.pageTitle |
| `og:description` | ShellView.pageDescription |
| `og:image` | ShellView.ogImage (per-page configurable) |
| `og:url` | ShellView.canonicalUrl |
| `og:type` | `website` |
| `og:locale` | Current language (e.g. `en`, `fr`) |

### Twitter Card

| Tag | Source |
|---|---|
| `twitter:card` | `summary` |
| `twitter:title` | ShellView.pageTitle |
| `twitter:description` | ShellView.pageDescription |
| `twitter:image` | ShellView.ogImage |

### JSON-LD

`WebSite` schema generated on all non-noindex pages:

```json
{
  "@context": "https://schema.org",
  "@type": "WebSite",
  "name": "...",
  "url": "..."
}
```

### Hreflang

```html
<link rel="alternate" hreflang="en" href="https://example.com/page?lang=en">
<link rel="alternate" hreflang="fr" href="https://example.com/page?lang=fr">
<link rel="alternate" hreflang="x-default" href="https://example.com/page?lang=en">
```

### Sitemap

`/sitemap.xml` lists public pages (/, /auth, /search) with changefreq and priority. Dynamic route in `App.kt`.

### Robots.txt

Dynamic `/robots.txt` disallows `/api/`, `/admin/`, `/ws/`, `/auth/`, `/errors/`, `/components/`, `/messages/`, `/notifications/`, `/settings/`. References `/sitemap.xml`.

## Implementation

- `SeoMetadata` class in `platform-core` — lightweight inline replacement for fragments-seo-core
- `ShellView` fields: `pageDescription`, `canonicalUrl`, `noIndex`, `supportedLocales`, `appBaseUrl`, `ogImage`
- `WebContext.shell()` populates SEO fields
- `LayoutHead.kte` emits tags using `SeoMetadata.forPage().generateAllMetaTags()`
