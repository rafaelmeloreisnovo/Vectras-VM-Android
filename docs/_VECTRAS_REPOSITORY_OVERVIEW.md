# Vectras VM Android - Complete Repository Overview

**Repository**: wojcikiewicz17/Vectras-VM-Android  
**Language**: Java (Android) + C/C++ + Rust  
**Description**: Virtual Machine App for Android based on QEMU  
**Last Updated**: 2026-04-21  

---

## 📋 Executive Summary

This is an **Android-based Virtual Machine application** built on QEMU architecture with a hybrid tech stack:
- **Frontend**: Kotlin/Java (Android UI)
- **Core Runtime**: C/C++ (QEMU integration, native kernel)
- **Performance Layer**: Rust (optimizations)

### Key Features
- **Deterministic Event Processing**: Vectra Core MVP with integrity verification
- **Toroidal Routing**: Advanced deterministic kernel routing system
- **Hybrid Architecture**: Android UI + native acceleration + QEMU backend
- **Formal CI/CD**: Multi-stage pipeline with host + Android validation
- **Comprehensive Documentation**: 3-layer analysis (purpose, structure, files)

---

## 🗂️ Repository Structure (Top Level)

### Root Configuration Files
| File | Purpose |
|------|---------|
| `build.gradle` | Main Gradle build configuration |
| `settings.gradle` | Gradle submodule settings |
| `gradle.properties` | Gradle properties (versions, flags) |
| `CMakeLists.txt` | CMake configuration for native builds |
| `CMakePresets.json` | CMake preset configurations |
| `Makefile` | Top-level build automation |
| `local.properties.example` | Template for local Android SDK configuration |
| `.gitignore` | Git ignore patterns |
| `.java-version` | Java version specification |
| `.mise.toml` | Mise tool configuration |

### Core Application Directories

| Directory | Purpose |
|-----------|---------|
| **`app/`** | Main Android application (Kotlin + JNI) |
| **`engine/`** | Native QEMU kernel integration (C/C++) |
| **`terminal-emulator/`** | Terminal UI component |
| **`terminal-view/`** | Terminal view rendering |
| **`shell-loader/`** | Shell loading mechanism |
| **`runtime/`** | Runtime execution framework |
| **`demo_cli/`** | CLI demonstrations & self-tests |
| **`tools/`** | Build tools and scripts |
| **`docs/`** | Complete documentation system |

### Support Directories

| Directory | Purpose |
|-----------|---------|
| **`web/`** | Web frontend/dashboard |
| **`fastlane/`** | App store automation |
| **`gradle/`** | Gradle wrapper files |
| **`resources/`** | Assets and resources |
| **`reports/`** | CI/CD reports and metrics |
| **`archive/`** | Historical/deprecated code |
| **`3dfx/`** | Legacy 3DFX wrapper artifacts |
| **`_incoming/`** | Inbound code awaiting integration |
| **`security/`** | Security policies and configs |

### Documentation Files (Root)

| File | Content |
|------|---------|
| `README.md` | Main entry point with navigation |
| `VECTRA_CORE.md` | Deterministic core framework specification |
| `PROJECT_STATE.md` | Current project state and roadmap |
| `CHANGELOG.md` | Version history and changes |
| `RELEASE_NOTES.md` | Release-specific notes |
| `BUILDING.md` | Build instructions |
| `TROUBLESHOOTING.md` | Common issues and solutions |
| `VERSION_STABILITY.md` | Stability guarantees and policies |
| `FIXES_SUMMARY.md` | Applied fixes catalog |
| `DOC_INDEX.md` | Complete documentation index |
| `VECTRAS_MEGAPROMPT_DOCS.md` | Extended reference documentation |

---

## 🔧 Core Technical Components

### 1. Vectra Core (MVP - Minimal Viable Product)

**Location**: `app/src/main/java/com/vectras/vm/vectra/`

A deterministic event processing framework implementing:

#### Key Concepts
- **Rho (ρ)**: Information-theoretic approach treating noise as data
  - `syndrome`: Parity differences (bit errors)
  - `event_weight`: Importance factor
  
- **4-Phase Cycle Loop** (10 Hz):
  1. Input (poll event from priority queue)
  2. Process (update state based on event)
  3. Output (log state changes)
  4. Next (prepare for next iteration)

- **Triad Physical Model** (2-of-3 consensus):
  - CPU, RAM, DISK components
  - Detects which component is out-of-sync

- **ECC/Parity System**:
  - 4×4 grid with 8 parity bits
  - Error detection and single-bit correction

#### Configuration
```gradle
buildTypes {
    debug { buildConfigField "boolean", "VECTRA_CORE_ENABLED", "true" }
    release { buildConfigField "boolean", "VECTRA_CORE_ENABLED", "false" }
}