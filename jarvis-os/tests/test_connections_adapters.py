"""
Sprint-6 Part 13 — OpenAI Adapter, Anthropic Adapter, Conversation
Support, Provider Failure tests. Every test injects a fake transport —
no real network call is ever made by this suite, per the module
docstrings' testing-hygiene note.
"""

from __future__ import annotations

import json

import pytest

from jarvis.ai_coordination.models import Capability, ProviderRequest
from jarvis.connections.adapters.anthropic_adapter import AnthropicAdapter
from jarvis.connections.adapters.openai_adapter import OpenAIAdapter
from jarvis.connections.credentials import ConnectionCredentials
from jarvis.connections.provider_adapter import AdapterHealthStatus, HTTPResponseSpec, ProviderAdapterError


def _request(prompt_summary="Do the thing", constraints=()) -> ProviderRequest:
    return ProviderRequest(
        request_id="req-1",
        session_id="session-1",
        capability=Capability.IMPLEMENTATION,
        prompt_summary=prompt_summary,
        context_references=(),
        constraints=constraints,
        prepared_at="now",
    )


def _openai_success_transport(content="Looks good.", finish_reason="stop"):
    def transport(spec):
        if spec.url.endswith("/models"):
            return HTTPResponseSpec(200, {}, b"{}")
        body = json.dumps(
            {"choices": [{"message": {"content": content}, "finish_reason": finish_reason}]}
        ).encode("utf-8")
        return HTTPResponseSpec(200, {}, body)

    return transport


def _anthropic_success_transport(content="Looks good.", stop_reason="end_turn"):
    def transport(spec):
        body = json.dumps({"content": [{"text": content}], "stop_reason": stop_reason}).encode("utf-8")
        return HTTPResponseSpec(200, {}, body)

    return transport


# --- OpenAI Adapter ------------------------------------------------------------------


def test_openai_connect_succeeds_with_valid_response():
    adapter = OpenAIAdapter(transport=_openai_success_transport())
    adapter.connect(ConnectionCredentials(api_key="sk-test"))
    assert adapter.health() == AdapterHealthStatus.HEALTHY


def test_openai_connect_fails_on_http_error():
    def failing_transport(spec):
        return HTTPResponseSpec(401, {}, b'{"error": "invalid api key"}')

    adapter = OpenAIAdapter(transport=failing_transport)
    with pytest.raises(ProviderAdapterError):
        adapter.connect(ConnectionCredentials(api_key="bad-key"))


def test_openai_send_prompt_requires_connect_first():
    adapter = OpenAIAdapter(transport=_openai_success_transport())
    with pytest.raises(ProviderAdapterError):
        adapter.send_prompt(_request())


def test_openai_send_prompt_parses_real_response_shape():
    adapter = OpenAIAdapter(transport=_openai_success_transport(content="Proceed with the refactor."))
    adapter.connect(ConnectionCredentials(api_key="sk-test"))
    response = adapter.send_prompt(_request())

    assert response.content == "Proceed with the refactor."
    assert response.recommended_action == "proceed"
    assert response.completeness == 1.0
    assert response.provider_id == "provider-openai"


def test_openai_send_prompt_reflects_truncated_completeness():
    adapter = OpenAIAdapter(transport=_openai_success_transport(finish_reason="length"))
    adapter.connect(ConnectionCredentials(api_key="sk-test"))
    response = adapter.send_prompt(_request())
    assert response.completeness == 0.6
    assert response.recommended_action == "retry"


def test_openai_send_prompt_includes_constraints_in_request():
    captured = {}

    def transport(spec):
        if spec.url.endswith("/models"):
            return HTTPResponseSpec(200, {}, b"{}")
        captured["body"] = json.loads(spec.body)
        return HTTPResponseSpec(
            200, {}, json.dumps({"choices": [{"message": {"content": "ok"}, "finish_reason": "stop"}]}).encode()
        )

    adapter = OpenAIAdapter(transport=transport)
    adapter.connect(ConnectionCredentials(api_key="sk-test"))
    adapter.send_prompt(_request(constraints=("Never execute directly.",)))

    messages = captured["body"]["messages"]
    assert any("Never execute directly." in m["content"] for m in messages if m["role"] == "system")


def test_openai_send_prompt_failure_raises_provider_adapter_error():
    def failing_transport(spec):
        if spec.url.endswith("/models"):
            return HTTPResponseSpec(200, {}, b"{}")
        return HTTPResponseSpec(500, {}, b"internal error")

    adapter = OpenAIAdapter(transport=failing_transport)
    adapter.connect(ConnectionCredentials(api_key="sk-test"))
    with pytest.raises(ProviderAdapterError):
        adapter.send_prompt(_request())


def test_openai_malformed_response_raises_provider_adapter_error():
    def transport(spec):
        if spec.url.endswith("/models"):
            return HTTPResponseSpec(200, {}, b"{}")
        return HTTPResponseSpec(200, {}, b"not json at all")

    adapter = OpenAIAdapter(transport=transport)
    adapter.connect(ConnectionCredentials(api_key="sk-test"))
    with pytest.raises(ProviderAdapterError):
        adapter.send_prompt(_request())


def test_openai_capabilities_and_conversation_support():
    adapter = OpenAIAdapter(transport=_openai_success_transport())
    assert Capability.IMPLEMENTATION in adapter.capabilities()
    assert adapter.conversation_support() is True


def test_openai_disconnect_clears_credentials():
    adapter = OpenAIAdapter(transport=_openai_success_transport())
    adapter.connect(ConnectionCredentials(api_key="sk-test"))
    adapter.disconnect()
    with pytest.raises(ProviderAdapterError):
        adapter.send_prompt(_request())


def test_openai_credentials_never_appear_in_repr():
    credentials = ConnectionCredentials(api_key="sk-supersecretkeyvalue1234")
    assert "supersecretkeyvalue" not in repr(credentials)


# --- Anthropic Adapter -----------------------------------------------------------------


def test_anthropic_connect_succeeds_with_valid_response():
    adapter = AnthropicAdapter(transport=_anthropic_success_transport())
    adapter.connect(ConnectionCredentials(api_key="sk-ant-test"))
    assert adapter.health() == AdapterHealthStatus.HEALTHY


def test_anthropic_connect_fails_on_http_error():
    def failing_transport(spec):
        return HTTPResponseSpec(401, {}, b'{"error": "invalid api key"}')

    adapter = AnthropicAdapter(transport=failing_transport)
    with pytest.raises(ProviderAdapterError):
        adapter.connect(ConnectionCredentials(api_key="bad-key"))


def test_anthropic_send_prompt_parses_real_response_shape():
    adapter = AnthropicAdapter(transport=_anthropic_success_transport(content="Agreed, proceed."))
    adapter.connect(ConnectionCredentials(api_key="sk-ant-test"))
    response = adapter.send_prompt(_request())

    assert response.content == "Agreed, proceed."
    assert response.recommended_action == "proceed"
    assert response.completeness == 1.0
    assert response.provider_id == "provider-anthropic"


def test_anthropic_send_prompt_reflects_truncated_completeness():
    adapter = AnthropicAdapter(transport=_anthropic_success_transport(stop_reason="max_tokens"))
    adapter.connect(ConnectionCredentials(api_key="sk-ant-test"))
    response = adapter.send_prompt(_request())
    assert response.completeness == 0.6


def test_anthropic_send_prompt_failure_raises_provider_adapter_error():
    def failing_transport(spec):
        return HTTPResponseSpec(500, {}, b"internal error")

    adapter = AnthropicAdapter(transport=failing_transport)
    with pytest.raises(ProviderAdapterError):
        adapter.connect(ConnectionCredentials(api_key="sk-ant-test"))


def test_anthropic_capabilities_and_conversation_support():
    adapter = AnthropicAdapter(transport=_anthropic_success_transport())
    assert Capability.RESEARCH in adapter.capabilities()
    assert adapter.conversation_support() is True


def test_anthropic_health_degraded_on_rate_limit():
    def transport(spec):
        return HTTPResponseSpec(429, {}, b"{}")

    adapter = AnthropicAdapter(transport=_anthropic_success_transport())
    adapter.connect(ConnectionCredentials(api_key="sk-ant-test"))
    adapter._transport = transport  # simulate a later rate-limit on a subsequent health check
    assert adapter.health() == AdapterHealthStatus.DEGRADED


# Every adapter test above passes an explicit fake transport; the real
# default_http_transport (a genuine network call) is never exercised by
# this suite, by design — see the adapter modules' own docstrings.
