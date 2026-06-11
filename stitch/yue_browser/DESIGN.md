---
name: Yue Browser
colors:
  surface: '#f8f9fa'
  surface-dim: '#d9dadb'
  surface-bright: '#f8f9fa'
  surface-container-lowest: '#ffffff'
  surface-container-low: '#f3f4f5'
  surface-container: '#edeeef'
  surface-container-high: '#e7e8e9'
  surface-container-highest: '#e1e3e4'
  on-surface: '#191c1d'
  on-surface-variant: '#43474c'
  inverse-surface: '#2e3132'
  inverse-on-surface: '#f0f1f2'
  outline: '#73777d'
  outline-variant: '#c3c7cd'
  surface-tint: '#496177'
  primary: '#324a5f'
  on-primary: '#ffffff'
  primary-container: '#4a6278'
  on-primary-container: '#c4ddf7'
  inverse-primary: '#b0c9e3'
  secondary: '#4d6172'
  on-secondary: '#ffffff'
  secondary-container: '#cee2f6'
  on-secondary-container: '#516576'
  tertiary: '#5d4320'
  on-tertiary: '#ffffff'
  tertiary-container: '#775a35'
  on-tertiary-container: '#fbd3a5'
  error: '#ba1a1a'
  on-error: '#ffffff'
  error-container: '#ffdad6'
  on-error-container: '#93000a'
  primary-fixed: '#cce5ff'
  primary-fixed-dim: '#b0c9e3'
  on-primary-fixed: '#011d31'
  on-primary-fixed-variant: '#31495e'
  secondary-fixed: '#d0e5f9'
  secondary-fixed-dim: '#b5c9dd'
  on-secondary-fixed: '#081d2c'
  on-secondary-fixed-variant: '#364959'
  tertiary-fixed: '#ffddb6'
  tertiary-fixed-dim: '#e6c093'
  on-tertiary-fixed: '#2a1800'
  on-tertiary-fixed-variant: '#5c421f'
  background: '#f8f9fa'
  on-background: '#191c1d'
  surface-variant: '#e1e3e4'
typography:
  display-lg:
    fontFamily: Geist
    fontSize: 40px
    fontWeight: '600'
    lineHeight: 48px
    letterSpacing: -0.02em
  display-lg-mobile:
    fontFamily: Geist
    fontSize: 32px
    fontWeight: '600'
    lineHeight: 38px
    letterSpacing: -0.02em
  headline-md:
    fontFamily: Geist
    fontSize: 24px
    fontWeight: '500'
    lineHeight: 32px
    letterSpacing: -0.01em
  body-lg:
    fontFamily: Geist
    fontSize: 18px
    fontWeight: '400'
    lineHeight: 28px
    letterSpacing: 0.01em
  body-md:
    fontFamily: Geist
    fontSize: 16px
    fontWeight: '400'
    lineHeight: 24px
    letterSpacing: 0.01em
  label-md:
    fontFamily: Geist
    fontSize: 14px
    fontWeight: '500'
    lineHeight: 20px
    letterSpacing: 0.05em
  caption:
    fontFamily: Geist
    fontSize: 12px
    fontWeight: '400'
    lineHeight: 16px
    letterSpacing: 0.02em
rounded:
  sm: 0.25rem
  DEFAULT: 0.5rem
  md: 0.75rem
  lg: 1rem
  xl: 1.5rem
  full: 9999px
spacing:
  unit: 4px
  xs: 4px
  sm: 8px
  md: 16px
  lg: 24px
  xl: 40px
  gutter: 16px
  margin-mobile: 20px
  margin-desktop: 64px
---

## Brand & Style

The design system is centered on the concept of "Atmospheric Clarity." It targets a sophisticated audience that values focus and digital well-being over the cluttered, ad-heavy experience of traditional browsers. The UI should feel like a breath of fresh air—expansive, quiet, and premium.

The design style is **Modern Minimalism with Glassmorphic accents**. It prioritizes heavy whitespace and a reduction of visual noise to ensure the user's content remains the hero. Every interaction should evoke a sense of calm efficiency through soft transitions and a lightweight aesthetic.

## Colors

The palette is anchored by a sophisticated Slate Blue, used sparingly for primary actions and active states. 

- **Primary**: A muted, professional slate that provides enough contrast for accessibility without being aggressive.
- **Secondary**: A desaturated sky blue used for subtle backgrounds, secondary buttons, and decorative elements.
- **Neutral**: A range of cool greys starting from pure white (#FFFFFF) for the main canvas to a light grey (#F8F9FA) for grouping elements.
- **Glass**: Translucent white layers are used for floating navigation bars and modal overlays to maintain a sense of context and depth.

## Typography

This design system utilizes **Geist** for its technical precision and clean, open counters. The typographic hierarchy is designed to be airy, with increased letter spacing on labels and captions to enhance legibility at small sizes.

Headlines should be set with tight tracking to feel modern and "locked-in," while body text and labels use wider tracking to promote scanning. Use `FontWeight: 400` for the majority of interface text to maintain the lightweight brand feel, reserving `600` only for critical navigation items or titles.

## Layout & Spacing

The layout philosophy follows a **Fluid Grid with Generous Margins**. For a mobile-first browser, we use a 4-column grid on mobile and a 12-column grid on desktop.

Spacing is governed by an 8pt rhythm, but with an emphasis on "Macro-spacing." Between major sections (like the URL bar and the Speed Dial), use `xl` (40px) spacing to create a sense of premium openness. Gutters are kept narrow at 16px to allow content cards to maximize screen real estate while horizontal safe-area margins are kept wide (20px) to prevent the UI from feeling cramped.

## Elevation & Depth

Visual hierarchy is achieved through **Tonal Layering and Glassmorphism** rather than traditional heavy shadows.

1.  **Base Level (0)**: Pure white (#FFFFFF) background.
2.  **Surface Level (1)**: Subtle 1px borders in a soft grey (#E9ECEF) to define card boundaries.
3.  **Floating Level (2)**: Elements like the URL bar or bottom navigation use a `backdrop-filter: blur(20px)` with a 70% opaque white background.
4.  **Shadows**: When used for high-priority modals, shadows should be extremely diffused (e.g., `0 20px 40px rgba(0,0,0,0.04)`), creating an ambient lift rather than a harsh drop.

## Shapes

The shape language is characterized by **large, friendly radii**. This counteracts the "cold" feeling often associated with minimalism.

- Standard buttons and input fields use `0.5rem` (8px).
- Content cards and speed-dial icons use `rounded-lg` (16px).
- The URL bar and primary navigation containers use `rounded-xl` (24px) or fully pill-shaped profiles to make them feel comfortable for touch interaction.

## Components

### Buttons & Inputs
Buttons should be primarily "Ghost" or "Filled-Neutral" styles. The primary button uses the Slate Blue with white text. Input fields, specifically the URL bar, should be pill-shaped with a subtle 1px border that darkens slightly on focus. Avoid heavy fills.

### Navigation Bar
The bottom navigation bar is the centerpiece. It should be a floating glassmorphic container, detached from the bottom of the screen with a `24px` margin. Icons should be 1.5pt line weight, using the Geist font's minimalist aesthetic.

### Cards
Cards for tabs or news feeds should have no shadow by default—only a soft border. On tap or hover, they can lift slightly using the ambient shadow defined in Elevation.

### Chips & Tags
Use for categories or history filters. These should be low-contrast (light grey background with slate text) and use the `label-md` typographic style.

### Tab Switcher
The tab switcher uses a "Stack" metaphor. Tabs are rendered as cards with large corner radii, utilizing a vertical scroll with significant whitespace between items to prevent accidental taps.