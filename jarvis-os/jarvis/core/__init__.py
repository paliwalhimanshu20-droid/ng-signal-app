"""
jarvis.core

JARVIS Core: the Bootstrap sequence and runtime state that ties every
other Sprint-0 subsystem together.

Design reference: JARVIS-001 §7 (Bootstrap Process), §26 (Startup
Sequence — the canonical restatement of §7), §27 (Shutdown Sequence).
"""

from jarvis.core.bootstrap import BootstrapError, JarvisCore, boot

__all__ = ["BootstrapError", "JarvisCore", "boot"]
