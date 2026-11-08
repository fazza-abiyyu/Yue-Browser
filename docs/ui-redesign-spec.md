# Yue Browser — Spesifikasi Redesain UI

> **Status: v1.0 — FINAL** (2026-11-07)
> Arah visual: **standar premium** (ala Google/One UI) — krom netral-rapi, bar ramping melekat, **aksen pink dipertahankan**, **dark mode AMOLED murni**. Semua keputusan terkunci; implementasi berjalan per §9.
> App: `com.yue.browser` — UI sepenuhnya Jetpack Compose + Material 3.

---

## 1. Tujuan

Redesain menyeluruh (seluruh surface) dengan dua sasaran:

1. **Identitas visual yang kohesif** — satu bahasa desain di semua layar, termasuk private mode.
2. **Sistem token, bukan nilai hardcoded** — hari ini warna/radius/ukuran tersebar inline di ~30 file; redesain harus menghasilkan satu sumber kebenaran (`Color.kt`, `Type.kt`, `Shape.kt`, `Dimens`).

Non-goal: mengubah arsitektur navigasi (tanpa nav library, screen = overlay boolean di atas `MainBrowserScreen`) — di luar scope redesain visual.

## 2. Prinsip desain

1. **Konten web dulu** — chrome (bar, overlay, sheet) seminimal mungkin visualnya, menyusut saat tidak dipakai. Browser bukan bintangnya; konten websitelah bintangnya.
2. **Dinilai di realcase, bukan prototype** — setiap keputusan visual diuji di atas konten nyata: artikel terang, situs gelap, halaman ramai. Chrome harus terbaca di atas warna apa pun; keindahan empty-state (new tab home) bukan tolok ukur.
3. **Satu token, satu makna** — dilarang hardcoded hex/dp/sp di call site. Private mode adalah *varian token*, bukan cabang `if` warna di tiap file.
4. **Konsistensi radius & ukuran** — semua pill/kartu/dialog mengikuti skala bentuk (§5.3).
5. **Touch target ≥ 48dp**, kontras WCAG AA (§8).
6. **AMOLED dark tetap jadi identitas** (bg murni `#000000`) kecuali referensi visual mengarah lain.

## 3. Inventaris surface (current state)

| # | Surface | Entry / file utama | Struktur saat ini |
|---|---------|--------------------|-------------------|
| S1 | Browser utama (phone) | `MainBrowserWebsitesLayout.kt`, `BrowserBottomBar.kt` | Progress bar 2dp top → WebView → bottom bar 56dp (URL pill 36dp r18, tab-count box, menu) + FAB tab baru 48dp |
| S2 | Browser utama (tablet ≥600dp) | `BrowserTopBar.kt` | Tab strip 40dp (tab 36dp, maks 160dp) + nav row 48dp (URL pill 32dp r16) |
| S3 | Split screen | `MainBrowserWebsitesLayout.kt` | Row 2 pane, divider drag 8dp (rasio 0.2–0.8), dashboard pane (grid thumbnail / frequently-visited), border pane aktif 1dp |
| S4 | New tab home | `NewTabHomeScreen.kt`, `SpeedDialHelpers.kt` | Wordmark "Yue" 40sp Light + tagline → search pill statis 48dp r24 → speed dial grid 4 kolom (6 di >600dp), tile lingkaran 52dp huruf berwarna |
| S5 | Search overlay | `SearchOverlay.kt` | Full-screen (phone) / kartu 640dp (tablet); bar 56dp; saran: autocomplete Google 6 + history 5 + bookmark 3; input via `AppCompatEditText` (AndroidView) |
| S6 | Tab switcher | `TabSwitcherScreen.kt`, `TabSwitcherComponents.kt` | Grid 2 kolom kartu 1:1.2 r16, segmented pill header, search, banner tab tidak aktif (>21 hari), drag&drop long-press → grup, multi-select + toolbar bawah |
| S7 | Grup tab | `GroupDetailOverlay.kt`, `TabGroupDialogs.kt` | Panel modal 92%×70% r24, grid kartu, reorder drag, dialog warna grup (8 palet) |
| S8 | Menu drawer | `MenuDrawerSheet.kt` | Bottom sheet custom (bukan M3) r32-top, `HorizontalPager` 2 halaman × grid ikon lingkaran 56dp; tablet: dropdown 260dp; ~15 ikon Canvas custom |
| S9 | Settings | `SettingsScreen.kt` (1379 baris) | Scaffold + M3 TopAppBar, search, sub-halaman (Umum/Personalisasi/Privacy/Backup) via enum |
| S10 | Layar list sekunder | History, Bookmarks, OfflinePages, AdblockFilters, SitePermissions, Playback, PasswordManager, IpDnsChecker, LockedWebsites | Pola seragam: TopAppBar + search + list; IpDnsChecker pakai 3 kartu; LockedWebsites pakai PIN + numpad |
| S11 | Downloads | `DownloadsScreen.kt`, `DownloadItemComponents.kt` | List per status, ikon file 40dp r10, progress linear 6dp + **chunk progress bar** (16 sel) |
| S12 | Lock & private | `WebLockOverlay.kt`, `IncognitoLockScreen.kt` | PIN full-screen: ikon kunci pulsing, 6 dot, numpad 3×4 tombol 72dp; incognito lock: lingkaran 90dp + tombol pill |
| S13 | Welcome (first run) | `WelcomeScreen.kt` | "Yue" 80sp Light + judul 36sp + tombol r28, animasi spring |
| S14 | Scanner | `ScannerActivity.kt` | Kamera full-black, bracket sudut putih, overlay izin |

## 4. Temuan audit (masalah yang wajib dibetulkan redesain)

1. **Aksen private mode `#FF002C` hardcoded di ~10 file**, plus surface private `#000000`/`#F5F5F5` dan border `#1A1A1A`/`#D8D8DC` duplikat di top/bottom bar → harus jadi slot token.
2. **Literal pink brand** (`#EC4899`/`#DB2777`) diulang di translate bar, undo banner, tab switcher, welcome, about — padahal sudah ada `PinkPrimary` di Theme.
3. **Palet WebLock terpisah & tidak nyambung**: aksen `#FF69B4`, error `#FF4444`, grays `#1E1E2E…`.
4. **Tab switcher full hardcoded neutral** (`#121212`, `#1A1A1C`, `#0F0F11`, `#EDEEEF`, …) — tidak ikut tema.
5. **Radius liar**: pill 16/18/20/22/24dp, kartu 12/14/16dp, tombol 8/10/28dp — tanpa skala.
6. **Tidak ada `Typography`**: semua `fontSize = X.sp` inline, tanpa keluarga font; tidak ada `Dimens`.
7. `SearchEngineIcon` merender ikon generik — padahal drawable brand (`ic_google`, `ic_bing`, `ic_duckduckgo`, `ic_yahoo`) sudah ada.
8. String UI tersebar hardcoded (OfflinePages Indonesia, Scanner/SitePermissions Inggris) — normalkan ke `strings.xml` (mengingat ada `values-in`).
9. Launcher icon PNG polos tanpa adaptive-icon XML.

## 5. Design tokens

> Nilai bertanda `TBD` diisi setelah referensi visual diterima (§10). Nama token sudah final.

### 5.1 Warna (`Color.kt` → `YueColors` extension di `MaterialTheme`)

| Token | Peran | Nilai |
|---|---|---|
| `brand.primary` | Aksen utama, CTA, progress | `#EC4899` (**dipertahankan** — keputusan 2026-11-07) |
| `brand.primaryDark` | Varian gelap brand | `#DB2777` |
| `brand.container` | Latar tonal brand | overlay `primary` 12% (mengganti pola `primary.copy(0.12)`) |
| `private.accent` | Aksen mode privat | `#FF002C` (dinormalisasi dari ~10 hardcoded) |
| `private.surface` / `private.border` | Surface & border privat | `#1A1A1C`/`#F5F5F5` · border `#1A1A1A`/`#D8D8DC` |
| `neutral.bg/surface/surfaceVariant` | Neutrals light | `#F8F9FA` · `#FFFFFF` · `#F0F1F2` |
| `neutral.darkBg/darkSurface…` | Neutrals dark (**AMOLED murni dipertahankan**) | bg/surface `#000000` · pill/surfaceVariant `#1A1A1C` · outlineVariant `#1A1A1A` · onBackground `#E3E3E3` |
| `semantic.success` / `warning` | Status (download sukses, picker HOLD) | `#4CAF50` · `#F59E0B` |
| `url.scheme` | Warna URL di saran | `#D93025` (light) · `#F28B82` (dark) |
| `dialPalette` + `groupPalette` | Warna kategoris — **digabung satu palet** | `3B82F6 EF4444 F59E0B 10B981 8B5CF6 EC4899 06B6D4 F97316` |

Aturan: **satu palet warna kategori** dipakai bersama speed dial, grup, dan tempat lain yang butuh warna kategoris. `Color.Red` di tombol delete diganti `semantic.error`.

### 5.2 Tipografi (`Type.kt`) — `TBD` keluarga font dari referensi

Skala (nama final, ukuran mengikuti referensi; baseline sekarang di kurung):

| Token | Baseline |
|---|---|
| `displayWordmark` | 80sp Light (welcome), 40sp (home) |
| `titleLarge` | 17sp SemiBold (judul layar) |
| `titleMedium` | 15sp (judul item settings/list) |
| `bodyLarge` | 16sp (isi saran, input) |
| `bodyMedium` | 14sp (teks list) |
| `bodySmall` | 13sp |
| `labelSmall` | 11–12sp (label sekunder, caption) |

### 5.3 Bentuk (`Shape.kt`) — final, ala One UI

| Token | Nilai | Dipakai untuk |
|---|---|---|
| `shape.pill` | full (setengah tinggi) | URL pill 38dp, search pill, chip |
| `shape.card` | 16dp | kartu tab, kartu pengaturan, kartu IP |
| `shape.sheet` | 28dp top | drawer, overlay grup |
| `shape.dialog` | 24dp | semua `AlertDialog` |
| `shape.tile` | 12dp | ikon file, quick-setting tile, input |

### 5.4 Spacing & ukuran (`Dimens`)

Skala tetap: **4 / 8 / 12 / 16 / 24 / 32dp** (ganti nilai liar 14/10/7dp dgn tetangga terdekat).
Tetap (bukan TBD): bar tinggi **48dp**, URL pill tinggi **38dp**, touch target min **48dp**, ikon bar **24dp** (aksesibel 20dp), lebar maks konten **600dp** (home) / **720dp** (bottom bar).

### 5.5 Motion

| Token | Nilai | Dipakai |
|---|---|---|
| `motion.fast` | 150ms | scrim sheet (sudah 150ms), fade kecil |
| `motion.normal` | 250ms | expand/collapse bar, visibilitas overlay |
| `motion.slow` | 400ms | transisi antar-state besar |
| `motion.spring` | spring medium (welcome screen sudah pakai) | entrance, drag&drop kartu |
| Easing | standar M3 (`EmphasizedEasing`) | semua transisi |

## 6. Spek per surface

> Format tiap surface: **tetap** (keputusan final) / **TBD** (menunggu referensi visual).

### S1–S3 Browser utama
- **Tetap**: progress bar top (2dp), satu URL pill komponen tunggal dipakai bersama top bar (tablet) & bottom bar (phone), split screen dengan divider drag (0.2–0.8).
- **Tetap (keputusan 2026-11-07, feedback user)**: FAB "+" **dihapus** — tab baru via floating + kanan-bawah di tab switcher; bar dirampingkan (~48dp); **tombol forward KEMBALI di bar** (revisi user — sempat dihapus, ternyata dirindukan); long-press back = forward tetap sebagai shortcut; back saat habis history → home.
- **Tetap**: URL pill panjang-klik tetap membuka `SiteSettingsDialog`; tab-count box tetap portal ke tab switcher.
- **Tetap (final — mockup v2 opsi 1)**: bar ramping melekat ~48dp, pill abu flat **terisi** (bukan outline, bukan kaca); di situs gelap bar pakai surface gelap sendiri dengan pill lebih terang.
- **TBD**: gaya toolbar element-picker (mode HOLD `semantic.warning`), border pane aktif split screen.

### S4 New tab home
- **Tetap**: hierarki wordmark → search pill → speed dial; maks lebar 600dp; grid 4 kolom (6 di tablet); tile lingkaran.
- **Tetap**: search pill statis membuka Search overlay (S5). **Ketuk ikon engine di pill → dropdown pilih engine terdaftar** (defaultSearchEngines; checkmark di aktif; tanpa input URL — custom engine tetap hanya via Settings).
- **Tetap (final, implementasi 2026-11-07)**: shortcut bisa diatur user — tile **"+"** (dialog URL + nama opsional), **long-press** dial = hapus (milik user) atau pin (auto-fill). **Grup settingan di Settings → Personalisasi ("New tab shortcuts")**: toggle **tampil/tidak** (`speedDialShow` — off = seluruh section hilang), toggle **"fill empty slots with top sites"** (`speedDialFillFrequent`, hanya tampil saat show ON), **slider jumlah maksimal 0–12** (`speedDialLimit` — menentukan banyaknya user dials + auto-fill yang tampil; SettingsEntry.Slider baru). User dials tampil duluan, auto-fill melengkapi sampai limit. `rememberCombinedSpeedDials` mengembalikan `CombinedSpeedDials(dials, autoUrls)`. **Tanpa section shortcut → wordmark + search di-posisi "sedikit di atas tengah"** (spacer atas 1f / bawah 1.3f — optical centering, feedback user).
- **TBD**: gaya wordmark/tagline, gaya tile (letter avatar vs favicon), palet avatar (→ `dialPalette`), apakah tagline dipertahankan.

### S5 Search overlay
- **Tetap**: pola saran dipertahankan (autocomplete 6 + history 5 + bookmark 3, saat kosong: 4 history terakhir + bookmark).
- **Tetap**: pakai drawable brand engine asli di bar dan saran (betulkan temuan #7). **Ketuk ikon engine di bar → dropdown pilih engine terdaftar** (sama seperti pill home; dulu klik-nya dismiss overlay — diganti menu).
- **TBD**: gaya bar (56dp Brave-style vs referensi), styling URL di baris saran (`url.scheme`).

### S6–S7 Tab switcher & grup
- **Tetap**: grid 2 kolom, kartu 1:1.2, segmented pill normal/incognito, banner tab tidak aktif (>21 hari) collapsible, drag&drop long-press (ke kartu = buat grup, ke grup = tambah), multi-select dengan toolbar bawah, grup detail = panel modal dengan drop zone.
- **Tetap**: **semua neutral hardcoded diganti token** (temuan #4) — tab switcher harus mengikuti tema.
- **TBD**: gaya kartu (header, border aktif), gaya ghost drag, palet grup (→ `groupPalette`).

### S8 Menu drawer
- **Tetap**: bottom sheet (phone) / dropdown (tablet); ikon set custom `Canvas` dipertahankan sebagai satu icon set 24dp stroke 2dp — dikonsolidasikan ke satu file icon.
- **Tetap (final, implementasi 2026-11-07)**: pager 2 halaman DIGANTI satu grid scrollable; header "Menu" + tombol **Edit**; mode edit = **drag & drop** ala launcher (long-press → ghost mengikuti jari → drop ke slot dengan highlight) + **pilih susunan 5×2 / 4×2 / 4×3 / 5×4** (kolom×baris; menentukan kolom grid & tinggi viewport) + tap item = tampil/sembunyi (item tersembunyi di section bawah). Tersimpan: `settings.drawerItems` (urutan & visibilitas) + `settings.drawerGrid` (susunan). Menu tablet masih hardcoded (kandidat menyusul pakai list yang sama).

### S9 Settings
- **Tetap**: struktur sub-halaman enum + search + tipe baris (Toggle/Clickable/Header).
- **Tetap**: ikon baris 20dp; section header 11sp uppercase.
- **TBD**: warna ikon (sekarang semua `primary` — mungkin netral dgn aksen hanya saat aktif), gaya about/branding.

### S10 Layar list sekunder
- **Tetap**: satu komponen list generik baru (row ikon + title + subtitle + aksi, empty state 48dp icon + teks) dipakai semua layar ini — hapus duplikasi pola.
- **Tetap**: aksi destruktif = `semantic.error`, bukan `Color.Red`.
- **TBD**: gaya kartu IpDnsChecker, layout PIN/LockedWebsites.

### S11 Downloads
- **Tetap**: chunk progress bar dipertahankan (fitur khas), legenda 3 warna (aktif/sukses/pending).
- **TBD**: gaya baris & status color mapping (→ `semantic.success` dll).

### S12–S14 Lock, Private, Welcome, Scanner
- **Tetap**: struktur WebLock (kunci pulsing, 6 dot, numpad 3×4, shake+haptic error, lockout countdown) — **palet WebLock dilebur ke token utama** (temuan #3): aksen `#FF69B4` → `brand.primary` / `private.accent`, error → `semantic.error`.
- **Tetap**: IncognitoLock memakai `private.accent` + `private.surface`.
- **TBD**: gaya kunci/numpad, gaya welcome screen, bracket scanner (kemungkinan hanya re-warna).

## 7. Motion & transisi (per interaksi)

| Interaksi | Animasi |
|---|---|
| Bottom bar muncul/hilang saat scroll | `expandVertically` + fade, `motion.normal` (sudah ada) |
| Menu drawer | scrim `motion.fast` + slide-up sheet, handle drag |
| Search overlay | fade+scale kecil dari pill home; scrim `motion.fast` |
| Tab switcher bukti/tutup | fade-through, kartu masuk dengan stagger kecil (≤40ms/item) |
| Drag kartu tab | ghost mengikuti jari + spring release (`motion.spring`) |
| Undo banner | slide-up + auto-dismiss, tombol Undo menonjol |
| Progress bar halaman | indeterminate→determinate halus, hilang fade |

## 8. Aksesibilitas & edge case

- Touch target ≥ 48dp (ikan: beberapa `IconButton` 30–36dp harus dinaikkan minimum area sentuh via `minimumInteractiveComponentSize`).
- Kontras WCAG AA untuk teks di atas `brand.primary`/`private.accent`.
- Uji font scale 1.3× (semua teks `sp`) — khususnya bar 56dp & URL pill 36dp.
- TalkBack label untuk ikon bar (back/forward/menu/tab-count).
- Dark mode AMOLED + light; **private mode harus punya afordansi selain warna** (mis. ikon/badge) supaya tidak bergantung warna saja.
- Tablet ≥600dp & split screen & PiP tetap jadi jalur uji wajib tiap perubahan.
- Launcher icon → migrasi adaptive-icon (foreground/background, dark & themed icon).

## 9. Rencana implementasi bertahap

1. **Fondasi token** — buat `Color.kt`/`Type.kt`/`Shape.kt`/`Dimens` + `YueColors` extension; pasang di `YueTheme`.
2. **Sapu hardcoded** — ganti semua literal (temuan #1–#5, per-file, perangkat diuji via `run_debug.sh`; ingat: verifikasi instal dengan `dumpsys package com.yue.browser | grep version`).
3. **Rework per surface** sesuai §6 (urutan: S1–S5 dulu — surface harian; lalu S6–S8; lalu S9–S11; terakhir S12–S14).
4. **Motion pass** (§7) + **a11y pass** (§8) + pembersihan string ke `strings.xml`.

## 10. Checklist — yang harus dijawab referensi visual

- [x] Palet brand (primary + varian) & aksen private mode → pink dipertahankan (§5.1)
- [x] Perlakuan dark mode → AMOLED murni dipertahankan (§5.1)
- [ ] Keluarga font + bobot wordmark → minor: Roboto default; wordmark Light dipertahankan (opsional dieksplorasi saat implementasi)
- [x] Skala radius → final di §5.3 (ala One UI)
- [x] Gaya URL pill & bottom bar → bar ramping melekat, pill terisi (mockup v2 opsi 1)
- [ ] Gaya tile speed dial & kartu tab switcher
- [ ] Gaya sheet/drawer (paginasi vs scroll)
- [ ] Warna semantic (success/warning/error) & palet kategoris (dial/grup)
- [ ] Ilustrasi/gaya empty state
- [x] Contoh app/produk yang jadi acuan → Google/One UI (standar premium)
