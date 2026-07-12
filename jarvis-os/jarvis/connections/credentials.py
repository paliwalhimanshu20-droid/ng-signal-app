"""
jarvis.connections.credentials

Secrets live here and ONLY here — never in a Connection object, never in
an audit detail dict, never in a health report, never printed by any
`__repr__` this module doesn't explicitly control.

Loaded from environment variables exclusively (same pattern already
established for UPSTOX_ACCESS_TOKEN in the trading app: read from
Secrets/env, never hardcoded, error clearly if missing — never silently
substitute a fake value). This module has no knowledge of Streamlit
secrets specifically; `from_env()` is the one place a future sprint
adding a different secrets backend (e.g. a real secrets manager) would
change, without touching any adapter code that consumes
ConnectionCredentials.
"""

from __future__ import annotations

import os
from dataclasses import dataclass
from typing import Optional


class MissingCredentialsError(Exception):
    """Raised when a required credential environment variable is not set. Never raised with the (absent) value included in the message."""


@dataclass(frozen=True)
class ConnectionCredentials:
    """
    Holds exactly what one adapter instance needs to authenticate, for
    the lifetime of that instance only. `__repr__` is deliberately
    overridden below so that logging, debugging, or an accidental
    `print(credentials)` can never leak the key — this is a structural
    guarantee, not a documentation promise.
    """

    api_key: str
    organization_id: Optional[str] = None

    def __repr__(self) -> str:
        masked = f"{self.api_key[:4]}...{self.api_key[-2:]}" if len(self.api_key) > 8 else "***"
        return f"ConnectionCredentials(api_key={masked!r}, organization_id={self.organization_id!r})"


def from_env(env_var: str, organization_env_var: Optional[str] = None) -> ConnectionCredentials:
    """Read a credential from an environment variable. Raises MissingCredentialsError (naming only the variable, never any value) if it isn't set or is blank."""
    api_key = os.environ.get(env_var, "").strip()
    if not api_key:
        raise MissingCredentialsError(
            f"Environment variable '{env_var}' is not set. This connection cannot be "
            f"established without it. Per this project's existing secrets pattern, set it "
            f"via your platform's secrets manager or environment — never hardcode it."
        )
    organization_id = None
    if organization_env_var is not None:
        organization_id = os.environ.get(organization_env_var, "").strip() or None
    return ConnectionCredentials(api_key=api_key, organization_id=organization_id)
