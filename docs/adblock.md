# Adblock & Element Picker Features

Yue Browser features a high-efficiency modular adblock engine with remote policy sync via Yue-AdBlock Strategy Center, alongside an interactive DOM Element Picker for user-driven content hiding.

---

## 1. AdBlock Engine Architecture

The adblock system uses a three-layer modular architecture:

| Layer | Responsibility | Components |
|-------|---------------|------------|
| **Strategy (BE)** | Policy profiles, filter rules, metadata versioning | Yue-AdBlock (Bun + Elysia on Vercel) |
| **Rules** | Filter data — domain lists, cosmetic selectors, exceptions | `easylist.txt`, `abpindo.txt`, `ad_domains.txt`, `cosmetic_selectors.txt` |
| **Engine (Android)** | Native adblock implementation | `AdBlockEngine`, `HostBlocker`, `CosmeticService`, `RedirectService` |

### Policy Profiles

Four profiles available, fetched from the Strategy Center:

| Profile | Description |
|---------|-------------|
| **Balanced** | Default — blocks trackers and ads, allows non-intrusive formats |
| **Aggressive** | Maximum blocking — includes all lists, skips redirects, blocks cookie banners |
| **Minimal** | Lightweight — only blocks known malicious domains |
| **Anti-Judol** | Specialized for gambling/blocking sites in Indonesian context |

---

## 2. Remote Sync Flow

```
App Opens (≥6h cooldown since last sync)
  │
  ├─ GET /adblock/metadata
  │   └─ Compare rule hashes with locally cached versions
  │
  ├─ For each changed rule:
  │   └─ GET /adblock/rules/{ruleName} → save to device storage
  │
  ├─ GET /adblock/policy?profile={active}
  │   └─ Parse policy → apply to engine (blocking level, features, thresholds)
  │
  └─ Mark sync complete → next sync in 6 hours
```

**Key Classes:**

- [`StrategyApi.kt`](../app/src/main/java/com/yue/browser/data/adblock/remote/StrategyApi.kt) — HTTP client for Strategy Center endpoints
- [`FilterSyncEngine.kt`](../app/src/main/java/com/yue/browser/data/adblock/sync/FilterSyncEngine.kt) — Orchestrates sync with hash-based change detection
- [`StrategyStore.kt`](../app/src/main/java/com/yue/browser/data/adblock/config/StrategyStore.kt) — Persists policy and rule hashes to SharedPreferences
- [`AdBlockEngine.kt`](../app/src/main/java/com/yue/browser/data/adblock/AdBlockEngine.kt) — Facade that initializes sync and applies rules to services

**BE Endpoints:**

| Method | Path | Description |
|--------|------|-------------|
| GET | `/health/live` | Process alive check |
| GET | `/health/ready` | Engine readiness check |
| GET | `/adblock/metadata` | Engine version, rule hashes, profiles list |
| GET | `/adblock/policy?profile={name}` | Get policy profile configuration |
| GET | `/adblock/profiles` | List all available profiles |
| GET | `/adblock/rules/{ruleName}` | Download filter rule file |

---

## 3. Host Blocking

Blocks ad/tracker domains at the network request level by intercepting `shouldInterceptRequest`:

**Key Classes:**

- [`HostBlocker.kt`](../app/src/main/java/com/yue/browser/data/adblock/engine/HostBlocker.kt) — Parses `easylist.txt` and `abpindo.txt` domain patterns (`||domain.com^`, `|https://domain.com/`) into a lookup set
- [`SystemWebViewClient.kt`](../app/src/main/java/com/yue/browser/data/engine/SystemWebViewClient.kt) — Calls `hostBlocker.isHostBlocked(url)` before allowing request

```kotlin
if (adBlockEngine.isHostBlocked(request.url)) {
    return WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream(ByteArray(0)))
}
```

**Features:**
- Pattern parsing for `||domain.com^` (domain anchor) and `|https://domain.com/` (start anchor)
- Whitelist support for user-trusted domains
- Fallback hardcoded domains if list parsing fails
- Sync reloads host patterns from BE-downloaded rule files

---

## 4. Cosmetic Filtering

Hides ads via CSS injection and dynamic JavaScript removal without destroying DOM nodes.

**Key Classes:**

- [`CosmeticService.kt`](../app/src/main/java/com/yue/browser/data/adblock/engine/CosmeticService.kt) — Manages CSS/JS ad hiding lifecycle
- [`AdRemovalScripts.kt`](../app/src/main/java/com/yue/browser/data/adblock/engine/AdRemovalScripts.kt) — JavaScript injection payloads
- [`CookieBannerRemover.kt`](../app/src/main/java/com/yue/browser/data/adblock/engine/CookieBannerRemover.kt) — Specialized cookie consent banner handling

**Approach:**
- CSS-only hiding: `display:none`, `visibility:hidden`, `height:0` (never `el.remove()`)
- TreeWalker + MutationObserver for dynamic content detection
- Periodic rescan (5000ms) for injected ads
- Skip video/canvas/iframe players, cookie consent, and legitimate `<dialog>` elements
- z-index threshold (>500) + viewport coverage (>40%) to distinguish ads from modals

---

## 5. Redirect Bypass

Detects ad-network redirect chains (e.g., `shrinkearn.com → tpi.li → acefile.co`) and extracts the final target URL directly.

**Key Classes:**

- [`RedirectService.kt`](../app/src/main/java/com/yue/browser/data/adblock/engine/RedirectService.kt) — Detects redirect chains and extracts target URLs
- [`SystemWebViewClient.kt`](../app/src/main/java/com/yue/browser/data/engine/SystemWebViewClient.kt) — `shouldOverrideUrlLoading` extracts URLs from query params or base64-encoded redirect chains

**Features:**
- Tracks `isInRedirectChain` flag to avoid blocking legitimate navigation
- Direct extraction of download URLs from intermediate ad pages
- Skip auto-close and ad blocking during redirect chain traversal

---

## 6. Element Picker (User-Driven Blocking)

A developer-style interface that lets users tap any element on a webpage, highlight it, and block it permanently.

**Key Classes:**

- [`SystemWebViewSessionPicker.kt`](../app/src/main/java/com/yue/browser/data/engine/SystemWebViewSessionPicker.kt) — Controls picker session, injects scripts, enables highlighting
- [`SystemWebChromeClient.kt`](../app/src/main/java/com/yue/browser/data/engine/SystemWebChromeClient.kt) — Intercepts element selections via `onJsPrompt`
- [`SettingsRepositoryImpl.kt`](../app/src/main/java/com/yue/browser/data/repository/SettingsRepositoryImpl.kt) — Persists CSS selectors per domain

```kotlin
// Saved as JSON in SharedPreferences
sharedPreferences.getString("blocked_css_selectors", "{}")
```

---

## 7. Strategy Center Backend

**Repository**: [github.com/fazza-abiyyu/Yue-AdBlock](https://github.com/fazza-abiyyu/Yue-AdBlock)

**Stack**: Bun + Elysia (OData v4) + Zod, deployed on Vercel

**Structure** (matches cp-center/elysia pattern):

```
src/
├── app.ts              # App factory with error handler + CORS + module registry
├── index.ts            # Entry point
├── lib/                # OData response builders, endpoint mounting, exceptions
├── infrastructure/     # Zod-validated config
├── modules/            # health, metadata, adblock (controller + service + module)
└── endpoints/          # Route definitions
```

**Deployment**: Vercel with `bunVersion: "1.x"`, no database (file-based rule storage in `public/adblock/`)