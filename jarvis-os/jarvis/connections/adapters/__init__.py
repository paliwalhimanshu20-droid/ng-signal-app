"""
jarvis.connections.adapters

Sprint-6 Parts 7-9: concrete ProviderAdapter implementations. Each
adapter is real, provider-specific integration code — imported here only
for convenience; nothing outside jarvis.connections should import an
adapter class directly (see jarvis.connections.provider_registry, the
one place adapters are looked up by provider_id).
"""

from __future__ import annotations

from jarvis.connections.adapters.anthropic_adapter import AnthropicAdapter
from jarvis.connections.adapters.openai_adapter import OpenAIAdapter

__all__ = ["AnthropicAdapter", "OpenAIAdapter"]
