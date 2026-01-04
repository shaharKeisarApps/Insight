# Circuit Expert Agent Setup Guide

## Skill vs Subagent: When to Use Which

### Use a **Skill** when:
- You need **reference material** that Claude Code reads on-demand
- The knowledge applies across many different tasks
- You want Claude to **automatically trigger** based on context (e.g., "create a Circuit screen")
- The expertise is **passive** - informing how Claude works rather than delegating work

### Use a **Subagent** when:
- You need to **delegate complex parallel tasks** to separate Claude instances
- The task requires **different context or tools** than the main agent
- You want **explicit invocation** for specific workflows
- You're orchestrating **multi-step pipelines** with handoffs

### Recommendation for Circuit

**For your use case, use a Skill.** Here's why:

1. Circuit expertise should **automatically apply** whenever you're working on presenters, screens, or state management
2. It's **reference knowledge** that enhances all Circuit-related code generation
3. You don't need parallel execution - you need Claude Code to be consistently excellent at Circuit

A subagent would be overkill - you'd have to explicitly invoke it each time, and it wouldn't benefit from the conversation context of your main session.

---

## Setup Option 1: Claude Code Skill (Recommended)

### Installation

1. **Copy the skill folder** to your Claude Code skills directory:

```bash
# For user-specific skills
cp -r circuit-expert-skill ~/.claude/skills/user/circuit-expert/

# Or add to your project's .claude directory
cp -r circuit-expert-skill .claude/skills/circuit-expert/
```

2. **The skill auto-triggers** when Claude Code detects Circuit-related requests:
   - Creating screens/presenters/UIs
   - State management questions
   - Navigation patterns
   - Testing Circuit code
   - Metro DI integration

### How Claude Code Uses It

When you say things like:
- "Create a profile screen with Circuit"
- "How should I manage this state in my presenter?"
- "Write a test for this presenter"

Claude Code will automatically read the `SKILL.md` and apply the patterns.

---

## Setup Option 2: Project-Level Agent Prompt

If you want more **explicit control** or to combine with other instructions, create a `.claude/agents/circuit.md`:

```markdown
# Circuit Development Agent

You are an elite Circuit framework expert. When working on this project, always follow these principles:

## Automatic Behaviors

1. **For any new Screen**: Create the complete pattern (Screen + State + Events + Presenter + UI)
2. **For state management**: Default to `rememberRetained` family, explain trade-offs
3. **For testing**: Always suggest Turbine-based presenter tests
4. **For DI**: Generate Metro-compatible `@CircuitInject` annotations

## Reference Skill

Always consult `/mnt/skills/user/circuit-expert/SKILL.md` for detailed patterns.

## Code Generation Defaults

- Use function-based presenters unless class-based is needed for complex dependencies
- Always include `modifier: Modifier = Modifier` parameter on UI functions
- Use nested State/Event classes inside Screen
- Default to `@Parcelize data object` for screens without parameters
```

Then invoke with: `claude --agent circuit "Create a settings screen"`

---

## Setup Option 3: CLAUDE.md Project Instructions

Add Circuit expertise to your project's `CLAUDE.md`:

```markdown
# Project: MyApp

## Architecture

This project uses **Circuit** (Slack's Compose architecture) with **Metro DI**.

### Circuit Patterns

When creating new features:

1. Read `/mnt/skills/user/circuit-expert/SKILL.md` for complete patterns
2. Follow the Screen → Presenter → UI structure
3. Use `rememberRetained` / `collectAsRetainedState` for state
4. Write presenter tests with Turbine

### File Structure

```
features/
└── profile/
    ├── ProfileScreen.kt      # Screen + State + Events
    ├── ProfilePresenter.kt   # @CircuitInject presenter
    ├── ProfileUi.kt          # @CircuitInject UI
    └── ProfilePresenterTest.kt
```
```

---

## Quick Reference Card

### State Retention Decision Tree

```
Need to retain state?
├─ No → use remember { }
└─ Yes → Across config changes only?
         ├─ No → rememberSaveable { }
         └─ Yes → Across back stack too?
                  ├─ No → remember { }
                  └─ Yes → Must survive process death?
                           ├─ No → rememberRetained { }
                           └─ Yes → rememberRetainedSaveable { }

Loading data from Flow?
├─ StateFlow → .collectAsRetainedState()
└─ Regular Flow → .collectAsRetainedState(initial = ...)

One-shot async load?
└─ produceRetainedState(initial) { value = fetchData() }
```

### File Naming Conventions

| Type | File Name | Annotation |
|------|-----------|------------|
| Screen | `{Feature}Screen.kt` | `@Parcelize` |
| Presenter (function) | `{Feature}Presenter.kt` | `@CircuitInject` + `@Composable` |
| Presenter (class) | `{Feature}Presenter.kt` | `@CircuitInject` on factory |
| UI | `{Feature}Ui.kt` or `{Feature}.kt` | `@CircuitInject` + `@Composable` |
| Test | `{Feature}PresenterTest.kt` | - |

### Common Imports

```kotlin
// Core Circuit
import com.slack.circuit.runtime.CircuitUiState
import com.slack.circuit.runtime.CircuitUiEvent
import com.slack.circuit.runtime.screen.Screen
import com.slack.circuit.runtime.presenter.Presenter
import com.slack.circuit.runtime.Navigator

// State Retention
import com.slack.circuit.retained.rememberRetained
import com.slack.circuit.retained.collectAsRetainedState
import com.slack.circuit.retained.produceRetainedState

// Code Gen
import com.slack.circuit.codegen.annotations.CircuitInject

// Navigation
import com.slack.circuit.foundation.rememberCircuitNavigator
import com.slack.circuit.backstack.rememberSaveableBackStack
import com.slack.circuit.foundation.NavigableCircuitContent

// Testing
import com.slack.circuit.test.FakeNavigator
import com.slack.circuit.test.test
```

---

## Reference Resources

### Documentation
- **Circuit Docs**: https://slackhq.github.io/circuit/
- **Circuit Tutorial**: https://slackhq.github.io/circuit/tutorial/
- **States & Events Guide**: https://slackhq.github.io/circuit/states-and-events/
- **Navigation Guide**: https://slackhq.github.io/circuit/navigation/
- **Testing Guide**: https://slackhq.github.io/circuit/testing/
- **Code Generation**: https://slackhq.github.io/circuit/code-gen/

### Source Code References
- **Circuit GitHub**: https://github.com/slackhq/circuit
- **CatchUp App** (real-world example): https://github.com/ZacSweers/CatchUp
- **Metro DI**: https://github.com/ZacSweers/metro
- **Tivi App** (another Circuit example): https://github.com/chrisbanes/tivi

### API Reference
- **circuit-retained APIs**: https://slackhq.github.io/circuit/api/0.x/circuit-retained/com.slack.circuit.retained/
- **circuit-test APIs**: https://slackhq.github.io/circuit/api/0.x/circuit-test/com.slack.circuit.test/
- **Turbine** (test helper): https://github.com/cashapp/turbine

### Talks & Articles
- **Modern Compose Architecture with Circuit** (Zac Sweers): https://speakerdeck.com/zacsweers/modern-compose-architecture-with-circuit
- **Catching Up on CatchUp 2023**: https://www.zacsweers.dev/catching-up-on-catchup-2023/

---

## Included Files

```
circuit-expert-skill/
├── SKILL.md                    # Main skill file (install this)
└── references/
    ├── advanced-patterns.md    # Complex patterns & edge cases
    └── migration-guide.md      # Migrating from ViewModel/other architectures
```
