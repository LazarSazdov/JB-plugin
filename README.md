<p align="center">
  <img src="https://img.shields.io/badge/IntelliJ%20IDEA-000000?style=for-the-badge&logo=intellij-idea&logoColor=white" alt="IntelliJ IDEA"/>
</p>

<h1 align="center">🚀 Auto Code Walker</h1>

<p align="center">
  <strong>Stop reading code. Start experiencing it.</strong>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Java-21-ED8B00?style=flat&logo=openjdk&logoColor=white" alt="Java 21"/>
  <img src="https://img.shields.io/badge/Gradle-9.2.1-02303A?style=flat&logo=gradle&logoColor=white" alt="Gradle"/>
  <img src="https://img.shields.io/badge/IntelliJ%20Platform-2025.2-000000?style=flat&logo=intellij-idea&logoColor=white" alt="IntelliJ Platform"/>
  <img src="https://img.shields.io/badge/OpenAI-Powered-412991?style=flat&logo=openai&logoColor=white" alt="OpenAI"/>
  <img src="https://img.shields.io/badge/License-MIT-blue?style=flat" alt="License"/>
</p>

<p align="center">
  <a href="https://drive.google.com/file/d/1MWms4ZZ_6HX4UhAfkhjnpxAZiJHqKdT5/view?usp=drive_link">
    <img src="https://img.shields.io/badge/🎬%20Watch%20Demo-Video-red?style=for-the-badge" alt="Watch Demo Video"/>
  </a>
</p>

---

## 📑 Table of Contents

- [The Problem](#-the-problem)
- [The Solution](#-the-solution)
- [Key Features](#-key-features)
- [Architecture & Tech Stack](#-architecture--tech-stack)
- [How It Works (The Flow)](#-how-it-works-the-flow)
- [Getting Started](#-getting-started)
- [The Authors](#-the-authors)
- [License](#-license)

---

## 😫 The Problem

**Onboarding to a new codebase is painful.**

Every developer knows the struggle:
- 📚 Hours spent jumping between files trying to understand the flow
- 🤯 Outdated or missing documentation that leaves you guessing
- 💬 Constantly interrupting teammates with "What does this function do?"
- 🔍 Reading code line by line without understanding the bigger picture
- ⏰ Weeks of ramp-up time before becoming productive

Traditional documentation is static, disconnected from the code, and often out of date. Code comments are scattered and lack context. README files give you the 10,000-foot view but not the ground-level understanding you need.

**There has to be a better way.**

---

## 💡 The Solution

**Auto Code Walker** transforms code comprehension from a passive reading experience into an **interactive, guided journey**.

Instead of reading code, you **experience** it. Our IntelliJ IDEA plugin creates immersive, AI-powered tours that:
- 🎯 **Focus your attention** on what matters with intelligent highlighting
- 🤖 **Explain code automatically** using OpenAI's advanced language models
- 📖 **Create persistent documentation** that lives alongside your code
- 🔗 **Connect concepts** with smart navigation and contextual links
- ✨ **Blur out distractions** so you can concentrate on one piece at a time

Think of it as a **personal tour guide** for any codebase — created by experts, enhanced by AI.

---

## ✨ Key Features

### 🎨 For Tour Creators

| Feature | Description |
|---------|-------------|
| **Visual Selection Mode** | Right-click → "Create Tour" to enter selection mode. Click on functions, classes, or code blocks to add them to your tour |
| **Smart Highlighting** | Selected code gets visually highlighted in the editor so you always know what's included |
| **Author Notes** | Add your own explanations and context to shape the AI-generated content |
| **AI-Powered Explanations** | OpenAI generates clear, concise explanations with usage examples automatically |
| **One-Click Export** | Generate a portable `tour.json` file that can be version-controlled and shared |

### 🚶 For Tour Users

| Feature | Description |
|---------|-------------|
| **Immersive Walkthrough** | Press "Start Tour" and the plugin guides you step-by-step through the code |
| **Focus Mode** | Surrounding code is blurred while the current function is highlighted in a spotlight effect |
| **Rich Explanation Panel** | A sleek floating panel shows AI explanations, author notes, and navigation controls |
| **Keyboard Navigation** | Use arrow keys or Enter to navigate between steps. Press Escape to exit |
| **Integrated JavaDoc** | Tour explanations are automatically integrated into IntelliJ's Quick Documentation (Ctrl+Q) |
| **Smart Links** | Explanations can include links to other functions, external documentation, or Kotlin/Java references |

### 📚 Documentation Integration

| Feature | Description |
|---------|-------------|
| **Enhanced Hover Docs** | Hover over any toured function and see the AI explanation right in the JavaDoc popup |
| **Usage Examples** | Every explanation includes practical calling examples |
| **Switchable Views** | Arrow buttons let you toggle between tour documentation and standard JavaDoc |

---

## 🏗 Architecture & Tech Stack

```
┌─────────────────────────────────────────────────────────────────────┐
│                        Auto Code Walker                             │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  ┌─────────────┐   ┌─────────────┐   ┌─────────────────────────┐    │
│  │   Actions   │   │   Services  │   │          UI             │    │
│  ├─────────────┤   ├─────────────┤   ├─────────────────────────┤    │
│  │ CreateTour  │   │ TourState   │   │ TourOverlayManager      │    │
│  │ AddStep     │   │ Selection   │   │ TourToolWindow          │    │
│  │ StartTour   │   │ EditorNav   │   │ StepCreationDialog      │    │
│  │ FinalizeTour│   │             │   │                         │    │
│  │ GenerateDoc │   │             │   │                         │    │
│  └─────────────┘   └─────────────┘   └─────────────────────────┘    │
│         │                 │                      │                  │
│         └─────────────────┼──────────────────────┘                  │
│                           │                                         │
│  ┌────────────────────────┴────────────────────────┐                │
│  │                    Models                       │                │
│  ├─────────────────────────────────────────────────┤                │
│  │  Tour { title, steps[] }                        │                │
│  │  TourStep { filePath, lineNum, endLine,         │                │
│  │             codeSnippet, authorNote,            │                │
│  │             aiExplanation, symbolName, type }   │                │
│  └─────────────────────────────────────────────────┘                │
│                           │                                         │
│  ┌────────────────────────┴────────────────────────┐                │
│  │              OpenAI Integration                 │                │
│  ├─────────────────────────────────────────────────┤                │
│  │  • Async HTTP/2 client                          │                │
│  │  • JSON response parsing                        │                │
│  │  • LRU caching (256 entries)                    │                │
│  │  • Automatic retry with backoff                 │                │
│  └─────────────────────────────────────────────────┘                │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

### Tech Stack

| Component | Technology |
|-----------|------------|
| **Platform** | IntelliJ Platform SDK 2025.2 |
| **Language** | Java 21 |
| **Build Tool** | Gradle 9.2.1 with Kotlin DSL |
| **AI Engine** | OpenAI GPT API |
| **JSON Handling** | Gson 2.10.1 |
| **HTTP Client** | Java HttpClient (HTTP/2) |
| **UI Framework** | Swing + IntelliJ UI Components |

---

## 🔄 How It Works (The Flow)

### 👨‍💻 Creator Flow

```
1️⃣  ENTER SELECTION MODE
    └─ Right-click → "Create Tour (Select Functions)"
    └─ Editor enters selection mode with visual indicator

2️⃣  SELECT CODE
    └─ Click on functions, classes, or code blocks
    └─ Add optional author notes to guide AI explanations
    └─ Selected items are highlighted in the editor

3️⃣  GENERATE TOUR
    └─ Right-click → "Create Tour (Generate JSON)"
    └─ AI analyzes each selection
    └─ Generates explanations with usage examples
    └─ Creates `tour.json` in `.codewalker/` directory

4️⃣  SHARE
    └─ Commit `tour.json` to version control
    └─ Team members can now experience the tour
```

### 👥 User Flow

```
1️⃣  START TOUR
    └─ Right-click → "Start Tour"
    └─ Plugin loads `tour.json`
    └─ Opens Tour Tool Window on the right

2️⃣  EXPERIENCE THE WALKTHROUGH
    └─ Current step is highlighted with spotlight effect
    └─ Surrounding code is blurred for focus
    └─ Explanation panel shows AI summary + author notes

3️⃣  NAVIGATE
    └─ Click "Next" / "Previous" or use arrow keys
    └─ Press Enter to advance, Escape to exit
    └─ Jump to any step from the tool window

4️⃣  EXPLORE DOCUMENTATION
    └─ Hover over any toured function
    └─ Press Ctrl+Q for Quick Documentation
    └─ See tour explanation integrated with JavaDoc
```

---

## 🚀 Getting Started

### Prerequisites

- **IntelliJ IDEA** 2025.2 or later
- **Java 21** or later
- **OpenAI API Key** (optional, for AI-powered explanations)

### Installation

#### From JetBrains Marketplace (Coming Soon)

```
Settings → Plugins → Marketplace → Search "Auto Code Walker" → Install
```

#### Manual Installation

1. Download the latest release from [GitHub Releases](https://github.com/LazarSazdov/JB-plugin/releases/latest)
2. In IntelliJ: `Settings → Plugins → ⚙️ → Install plugin from disk...`
3. Select the downloaded `.zip` file
4. Restart IntelliJ IDEA

### Configuration

1. **Set your OpenAI API Key** (optional but recommended):
   - Go to `Settings → Tools → Auto Code Walker`
   - Enter your API key
   - Select your preferred model (default: `gpt-4o-mini`)

2. **Create your first tour**:
   - Open a project
   - Right-click in the editor → `Create Tour (Select Functions)`
   - Click on functions you want to explain
   - Right-click → `Create Tour (Generate JSON)`

3. **Experience a tour**:
   - Open a project with a `tour.json` file
   - Right-click → `Start Tour`
   - Follow the guided walkthrough!

### Building from Source

```bash
# Clone the repository
git clone https://github.com/LazarSazdov/JB-plugin.git
cd JB-plugin

# Build the plugin
./gradlew buildPlugin

# Run in a sandbox IDE
./gradlew runIde
```

---

## 👨‍💻 The Authors

<table>
  <tr>
    <td align="center">
      <a href="https://github.com/MilanSazdov">
        <img src="https://github.com/MilanSazdov.png" width="100px;" alt="Milan Sazdov"/>
        <br />
        <sub><b>Milan Sazdov</b></sub>
      </a>
    </td>
    <td align="center">
      <a href="https://github.com/LazarSazdov">
        <img src="https://github.com/LazarSazdov.png" width="100px;" alt="Lazar Sazdov"/>
        <br />
        <sub><b>Lazar Sazdov</b></sub>
      </a>
    </td>
    <td align="center">
      <a href="https://github.com/vedranbajic4">
        <img src="https://github.com/vedranbajic4.png" width="100px;" alt="Vedran Bajic"/>
        <br />
        <sub><b>Vedran Bajic</b></sub>
      </a>
    </td>
  </tr>
</table>

---

## 📄 License

This project is licensed under the **MIT License** — see the [LICENSE](LICENSE) file for details.

```
MIT License

Copyright (c) 2024 Milan Sazdov, Lazar Sazdov, Vedran Bajic

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

---

<p align="center">
  <strong>Built with ❤️ for the JetBrains Plugin Hackathon</strong>
</p>

<p align="center">
  <a href="https://plugins.jetbrains.com/">
    <img src="https://img.shields.io/badge/JetBrains-Plugin-000000?style=for-the-badge&logo=jetbrains&logoColor=white" alt="JetBrains Plugin"/>
  </a>
</p>

<!-- Plugin description -->
**Auto Code Walker** transforms how developers understand code. Instead of passively reading through unfamiliar codebases, experience guided, AI-powered tours that highlight key functions, provide intelligent explanations, and integrate seamlessly with IntelliJ's documentation system.

**Key Features:**
- 🎯 Create interactive code tours with visual selection
- 🤖 AI-powered explanations via OpenAI integration
- 🔍 Immersive focus mode with spotlight highlighting
- 📚 Integrated JavaDoc documentation with tour explanations
- ⌨️ Full keyboard navigation support
- 📦 Portable JSON format for sharing tours

Perfect for onboarding new team members, documenting complex systems, or creating educational content.
<!-- Plugin description end -->

