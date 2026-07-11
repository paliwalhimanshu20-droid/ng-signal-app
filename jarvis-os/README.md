# JARVIS AI Operating System — Sprint-0: Foundation Bootstrap

This repository implements **only** the Foundation Bootstrap scope defined
against the JARVIS architecture series:

1. `JARVIS OS Constitution & Master Architecture Blueprint v1.0`
2. `JARVIS-001 — Foundation & Core Architecture`
3. `JARVIS-002 — Intelligence & Agent Framework`
4. `JARVIS-003 — Specialist Agents & User Experience`
5. `JARVIS-004 — Engineering, Operations & Lifecycle Handbook`

## What Sprint-0 is

A clean, bootable framework that:

- Loads and structurally validates a Constitution reference (all seven
  Articles present) before anything else runs — halts otherwise.
- Establishes Audit Ledger connectivity before any auditable event can occur.
- Loads configuration across the three constitutionally-required classes
  (operational / structural / constitutional-reference), kept separate.
- Configures operational logging explicitly, once, distinct from the Audit
  Ledger — see `jarvis/logging_/__init__.py` for why this separation is
  treated as non-negotiable.
- Provides a working Agent Registry with the full five-state lifecycle
  (proposed → reviewed → provisioned → active → deprecated).
- Provides an Orchestrator skeleton with all five request-lifecycle
  components (Intent Processor, Task Planner, Workflow Engine, Agent
  Router, Context Manager) present as real, separately-wired classes.
- Runs a Core self-health check and reports it.
- Boots and shuts down cleanly via `main.py`.

## What Sprint-0 is explicitly NOT

Per the Sprint-0 task scope, this repository contains **no**:

- AI reasoning, evidence grading, or confidence computation
- Memory or Knowledge Store implementation
- Permission Engine or Approval Engine
- Any external integration (GitHub, Upstox, Streamlit, etc.)
- Any concrete specialist agent (Engineering, Research, Trading, ...)
- Any business logic whatsoever

Every module that stops short of real behavior says so explicitly in its
own docstring, including *why* stopping there is the architecturally
correct choice for this sprint rather than an oversight — see especially
`jarvis/orchestrator/intent_processor.py` and `jarvis/agents/base.py`.

## Running it

```bash
pip install -r requirements.txt
python main.py
```

Expected output: a Bootstrap log trail, a Core self-health report showing
all checks passing, and a clean shutdown.

## Running the tests

```bash
pip install -r requirements.txt
pytest
```

## Repository structure

```
jarvis/
  __init__.py
  constitution/       # Constitution loading & structural validation
  config/              # Three-class configuration (JARVIS-001 §17)
  logging_/            # Operational logging (JARVIS-001 §20)
  audit/                # Audit Ledger (JARVIS-001 §16, Article IV)
  registry/             # Agent Registry & lifecycle (JARVIS-002 §16-17)
  orchestrator/         # Orchestrator skeleton (JARVIS-001 §4, §9-14)
  agents/               # Base Agent Specification (JARVIS-002 §15)
  health/               # Core self-health check (JARVIS-001 §22)
  core/                 # Bootstrap sequence (JARVIS-001 §7, §26-27)
data/
  constitution.json     # The Constitution reference this Core targets
tests/                  # pytest suite covering every subsystem above
main.py                 # Entry point: boot, report health, shut down
```

## Next steps (explicitly out of scope for this sprint)

See each module's docstring for its own specific "what a later sprint
must add" notes. At a system level, the next architecturally significant
milestones are the Permission Engine and Approval Engine — per
`jarvis/core/bootstrap.py`'s note on Bootstrap Step 3, these must exist
before any sprint introduces real, consequential task execution.
