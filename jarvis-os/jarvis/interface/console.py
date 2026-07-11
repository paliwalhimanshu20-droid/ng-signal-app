"""
jarvis.interface.console

ConsoleInterface: the interactive command loop (Part 8) tying together
Session Manager, Command Parser, Approval Interface, Response Renderer,
and System Dashboard — all talking to the system exclusively through
jarvis.kernel.ExecutiveKernel.

I/O is injected (`input_fn`, `output_fn`) rather than hardcoded to
`input()`/`print()` — this is what makes the full interactive loop
testable without a real terminal: tests pass a scripted iterator of
inputs and a list-capturing output function, and drive the exact same
code path `main.py` uses for real.
"""

from __future__ import annotations

from typing import Callable

from jarvis.core.bootstrap import JarvisCore
from jarvis.intake.models import TaskStatus
from jarvis.interface.approval_interface import ApprovalInterface
from jarvis.interface.command_parser import CommandParser, CommandType
from jarvis.interface.dashboard import SystemDashboard
from jarvis.interface.health import run_interface_health_check
from jarvis.interface.renderer import ResponseRenderer
from jarvis.interface.session import SessionManager
from jarvis.kernel import ExecutiveKernel
from jarvis.logging_ import get_logger
from jarvis.orchestrator.task_planner import Tier

logger = get_logger(__name__)

HELP_TEXT = """\
Available commands:
  <any request>   e.g. "Analyze GitHub repository"
  help            show this message
  status          show the system dashboard
  history         show recent audit events
  clear           clear the screen
  exit            close the session and quit

When an approval is pending:
  yes / approve / confirm    approve the action
  no / reject / cancel       reject the action

When a Tier 3 confirmation is pending, type the exact phrase shown."""


class ConsoleInterface:
    """
    The Console Interface. Constructed once, run once (`run()` is the
    full interactive loop, returning a process exit code).
    """

    def __init__(
        self,
        core: JarvisCore,
        kernel: ExecutiveKernel,
        session_manager: SessionManager,
        command_parser: CommandParser,
        renderer: ResponseRenderer,
        dashboard: SystemDashboard,
        approval_interface: ApprovalInterface,
        input_fn: Callable[[str], str] = input,
        output_fn: Callable[[str], None] = print,
    ) -> None:
        self._core = core
        self._kernel = kernel
        self._session_manager = session_manager
        self._command_parser = command_parser
        self._renderer = renderer
        self._dashboard = dashboard
        self._approval_interface = approval_interface
        self._input_fn = input_fn
        self._output_fn = output_fn

    def run(self) -> int:
        session = self._session_manager.open_session()
        self._kernel.audit_ledger.record(
            event_type="interface.owner_connected",
            message="Owner connected.",
            details={"session_id": session.session_id},
        )

        self._output_fn(self._render_dashboard())
        self._output_fn("\nReady.")

        while True:
            try:
                raw = self._input_fn("> ")
            except (EOFError, StopIteration):
                break

            self._kernel.audit_ledger.record(
                event_type="interface.command_received",
                message="Command received.",
                details={"raw_input": raw},
            )
            self._session_manager.touch()
            self._session_manager.check_idle()

            if self._dispatch(raw) is False:
                break

        if self._session_manager.current is not None and self._session_manager.current.status.value != "closed":
            self._close_session()

        return 0

    def _dispatch(self, raw: str) -> bool:
        """Handle one command. Returns False to signal the loop should stop."""
        task = self._session_manager.get_current_task()
        awaiting_approval = task is not None and task.status is TaskStatus.WAITING_APPROVAL
        awaiting_confirmation = awaiting_approval and task.tier is Tier.TIER_3_IRREVERSIBLE_OR_HIGH_STAKES
        confirmation_phrase = (
            self._approval_interface.confirmation_phrase_for(task) if awaiting_confirmation else None
        )

        parsed = self._command_parser.parse(
            raw,
            awaiting_approval=awaiting_approval and not awaiting_confirmation,
            awaiting_confirmation=awaiting_confirmation,
            confirmation_phrase=confirmation_phrase,
        )

        if parsed.command_type is CommandType.EXIT:
            self._close_session()
            self._output_fn("Goodbye.")
            return False

        if parsed.command_type is CommandType.HELP:
            self._output_fn(HELP_TEXT)
            return True

        if parsed.command_type is CommandType.STATUS:
            self._output_fn(self._render_dashboard())
            return True

        if parsed.command_type is CommandType.HISTORY:
            entries = self._kernel.audit_ledger.read_all()[-10:]
            self._output_fn(self._renderer.render_audit_summary(entries))
            return True

        if parsed.command_type is CommandType.CLEAR:
            self._output_fn("\n" * 40)
            return True

        if parsed.command_type in (CommandType.APPROVAL_RESPONSE, CommandType.CONFIRMATION_RESPONSE):
            self._handle_approval_response(task, parsed.approved)
            return True

        if parsed.command_type is CommandType.NORMAL_REQUEST:
            self._handle_normal_request(raw)
            return True

        # UNKNOWN — never guess.
        self._output_fn(
            self._renderer.render_warning(
                "I didn't understand that. Type 'help' for available commands."
            )
        )
        return True

    def _handle_normal_request(self, raw: str) -> None:
        task = self._kernel.submit_request(raw)

        if task.status is TaskStatus.WAITING_APPROVAL:
            self._session_manager.set_current_task(task)
            prompt = self._approval_interface.build_prompt(task)
            self._kernel.audit_ledger.record(
                event_type="interface.approval_requested",
                message="Approval prompt displayed to owner.",
                details={"task_id": task.task_id, "approval_id": prompt.approval_id},
            )
            self._output_fn(self._renderer.render_approval_prompt(prompt))
            return

        self._session_manager.set_current_task(None)
        self._output_fn(self._renderer.render_task_result(task))
        self._output_fn("\nReady for next command.")

    def _handle_approval_response(self, task, approved) -> None:
        if task is None or approved is None:
            self._output_fn(self._renderer.render_warning("There is no pending approval to respond to."))
            return

        approval_id = task.metadata.get("approval_request", {}).get("approval_id", "")
        result_task = self._kernel.resume(task, approval_id=approval_id, approved=approved, approved_by="owner")

        self._kernel.audit_ledger.record(
            event_type="interface.approval_granted" if approved else "interface.approval_rejected",
            message=f"Owner {'approved' if approved else 'rejected'} the pending action.",
            details={"task_id": task.task_id, "approval_id": approval_id},
        )
        self._kernel.audit_ledger.record(
            event_type="interface.workflow_resumed",
            message="Workflow resumed after owner response.",
            details={"task_id": task.task_id},
        )

        self._session_manager.set_current_task(None)
        self._output_fn(self._renderer.render_task_result(result_task))
        self._output_fn("\nReady for next command.")

    def _close_session(self) -> None:
        session = self._session_manager.current
        self._session_manager.close_session()
        if session is not None:
            self._kernel.audit_ledger.record(
                event_type="interface.session_closed",
                message="Session closed.",
                details={"session_id": session.session_id},
            )

    def _render_dashboard(self) -> str:
        interface_health = run_interface_health_check(self._session_manager, self._command_parser)
        snapshot = self._dashboard.snapshot(self._core, self._kernel, interface_health, self._session_manager)
        return self._dashboard.render(snapshot)
