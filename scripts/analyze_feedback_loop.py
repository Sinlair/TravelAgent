#!/usr/bin/env python3
"""
Generate an offline feedback-loop report from the local SQLite database.

The report mirrors the backend feedback summary logic closely enough for local
analysis, but writes JSON and Markdown files so the output can be reviewed
without running the application server.
"""

from __future__ import annotations

import argparse
import json
import sqlite3
import sys
from collections import defaultdict
from dataclasses import asdict, dataclass
from datetime import datetime, timezone
from decimal import Decimal, ROUND_HALF_UP
from pathlib import Path
from typing import Any, Callable


SCRIPT_DIR = Path(__file__).resolve().parent
REPO_ROOT = SCRIPT_DIR.parent
DEFAULT_DB_PATH = REPO_ROOT / "data" / "travel-agent.db"
DEFAULT_OUTPUT_DIR = REPO_ROOT / "data" / "exports"


@dataclass(frozen=True)
class FeedbackRecord:
    conversation_id: str
    label: str
    reason_code: str | None
    note: str | None
    agent_type: str | None
    destination: str | None
    days: int | None
    budget: str | None
    has_travel_plan: bool
    metadata: dict[str, Any]
    created_at: str
    updated_at: str


@dataclass(frozen=True)
class BreakdownItem:
    key: str
    total_count: int
    accepted_count: int
    partial_count: int
    rejected_count: int
    accepted_rate_pct: float
    usable_rate_pct: float


@dataclass(frozen=True)
class Finding:
    type: str
    key: str
    total_count: int
    accepted_count: int
    partial_count: int
    rejected_count: int
    usable_rate_pct: float
    recommendation: str


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Analyze conversation feedback records from the local SQLite database."
    )
    parser.add_argument(
        "--db",
        type=Path,
        default=DEFAULT_DB_PATH,
        help=f"SQLite database path. Default: {DEFAULT_DB_PATH}",
    )
    parser.add_argument(
        "--outdir",
        type=Path,
        default=DEFAULT_OUTPUT_DIR,
        help=f"Directory for generated reports. Default: {DEFAULT_OUTPUT_DIR}",
    )
    parser.add_argument(
        "--limit",
        type=int,
        default=200,
        help="Maximum number of most-recent feedback rows to analyze. Default: 200",
    )
    parser.add_argument(
        "--print-markdown",
        action="store_true",
        help="Also print the generated Markdown report to stdout.",
    )
    return parser.parse_args()


def round_pct(numerator: int, denominator: int) -> float:
    if denominator <= 0:
        return 0.0
    value = (Decimal(numerator) * Decimal("100")) / Decimal(denominator)
    return float(value.quantize(Decimal("0.01"), rounding=ROUND_HALF_UP))


def normalize_limit(limit: int) -> int:
    if limit <= 0:
        return 200
    return min(limit, 1000)


def parse_metadata(raw: str | None) -> dict[str, Any]:
    if not raw:
        return {}
    try:
        value = json.loads(raw)
    except json.JSONDecodeError:
        return {}
    return value if isinstance(value, dict) else {}


def bool_metadata(record: FeedbackRecord, key: str) -> bool:
    value = record.metadata.get(key)
    if isinstance(value, bool):
        return value
    if isinstance(value, (int, float)):
        return value != 0
    if isinstance(value, str):
        return value.strip().lower() == "true"
    return False


def long_metadata(record: FeedbackRecord, key: str) -> int:
    value = record.metadata.get(key)
    if isinstance(value, bool):
        return int(value)
    if isinstance(value, (int, float)):
        return int(value)
    if isinstance(value, str):
        try:
            return int(value.strip())
        except ValueError:
            return 0
    return 0


def is_accepted(record: FeedbackRecord) -> bool:
    return record.label == "ACCEPTED"


def is_partial(record: FeedbackRecord) -> bool:
    return record.label == "PARTIAL"


def is_rejected(record: FeedbackRecord) -> bool:
    return record.label == "REJECTED"


def is_negative(record: FeedbackRecord) -> bool:
    return not is_accepted(record)


def normalize_bucket(value: str | None, fallback: str) -> str:
    if value is None:
        return fallback
    text = value.strip()
    return text if text else fallback


def build_breakdown(
    records: list[FeedbackRecord],
    classifier: Callable[[FeedbackRecord], str],
    max_items: int,
) -> list[BreakdownItem]:
    groups: dict[str, list[FeedbackRecord]] = defaultdict(list)
    for record in records:
        groups[classifier(record)].append(record)

    items = [
        to_breakdown(key, grouped_records)
        for key, grouped_records in groups.items()
    ]
    items.sort(key=lambda item: (-item.total_count, item.key))
    return items[:max_items]


def to_breakdown(key: str, records: list[FeedbackRecord]) -> BreakdownItem:
    accepted_count = sum(1 for record in records if is_accepted(record))
    partial_count = sum(1 for record in records if is_partial(record))
    rejected_count = sum(1 for record in records if is_rejected(record))
    total_count = len(records)
    return BreakdownItem(
        key=key,
        total_count=total_count,
        accepted_count=accepted_count,
        partial_count=partial_count,
        rejected_count=rejected_count,
        accepted_rate_pct=round_pct(accepted_count, total_count),
        usable_rate_pct=round_pct(accepted_count + partial_count, total_count),
    )


def to_finding(
    finding_type: str,
    key: str,
    records: list[FeedbackRecord],
    recommendation: str,
) -> Finding:
    accepted_count = sum(1 for record in records if is_accepted(record))
    partial_count = sum(1 for record in records if is_partial(record))
    rejected_count = sum(1 for record in records if is_rejected(record))
    total_count = len(records)
    return Finding(
        type=finding_type,
        key=key,
        total_count=total_count,
        accepted_count=accepted_count,
        partial_count=partial_count,
        rejected_count=rejected_count,
        usable_rate_pct=round_pct(accepted_count + partial_count, total_count),
        recommendation=recommendation,
    )


def add_finding(
    findings: list[Finding],
    finding_type: str,
    key: str,
    records: list[FeedbackRecord],
    predicate: Callable[[FeedbackRecord], bool],
    recommendation: str,
) -> None:
    matches = [record for record in records if predicate(record)]
    if matches:
        findings.append(to_finding(finding_type, key, matches, recommendation))


def recommendation_for_reason_code(reason_code: str) -> str:
    if reason_code == "edited_before_use":
        return "Compare partial accepts against accepted plans to see which fields users edit most often."
    if reason_code == "not_useful":
        return "Inspect rejected conversations and route, POI, or budget assumptions before changing prompts globally."
    if reason_code == "UNSPECIFIED":
        return "Ask for a short analyst note on negative feedback so future batches are easier to diagnose."
    return "Review this reason bucket directly and decide whether routing, retrieval, validation, or repair should change."


def build_negative_reason_findings(records: list[FeedbackRecord]) -> list[Finding]:
    breakdown = build_breakdown(
        [record for record in records if is_negative(record)],
        lambda record: normalize_bucket(record.reason_code, "UNSPECIFIED"),
        2,
    )
    return [
        Finding(
            type="REASON_CODE",
            key=item.key,
            total_count=item.total_count,
            accepted_count=item.accepted_count,
            partial_count=item.partial_count,
            rejected_count=item.rejected_count,
            usable_rate_pct=item.usable_rate_pct,
            recommendation=recommendation_for_reason_code(item.key),
        )
        for item in breakdown
    ]


def build_findings(records: list[FeedbackRecord]) -> list[Finding]:
    findings: list[Finding] = []
    add_finding(
        findings,
        "TRAVEL_PLAN_COVERAGE",
        "no_structured_plan",
        records,
        lambda record: not record.has_travel_plan,
        "Inspect planner fallbacks and capture why the flow failed to return a structured plan.",
    )
    add_finding(
        findings,
        "VALIDATION_FAIL",
        "validationFailCount>0",
        records,
        lambda record: long_metadata(record, "validationFailCount") > 0,
        "Review failing constraint checks first; these plans are disproportionately likely to be rejected.",
    )
    add_finding(
        findings,
        "HIGH_WARNING_LOAD",
        "validationWarnCount>=2",
        records,
        lambda record: long_metadata(record, "validationWarnCount") >= 2,
        "Tighten repair prompts when the planner returns multiple warnings instead of letting them accumulate.",
    )
    add_finding(
        findings,
        "CONSTRAINT_RELAXATION",
        "constraintRelaxed=true",
        records,
        lambda record: bool_metadata(record, "constraintRelaxed"),
        "Track which constraints were relaxed and decide whether routing or repair should be adjusted earlier.",
    )
    add_finding(
        findings,
        "LOW_KNOWLEDGE_COVERAGE",
        "knowledgeHintCount=0",
        records,
        lambda record: record.has_travel_plan and long_metadata(record, "knowledgeHintCount") == 0,
        "Check whether retrieval missed city-specific hints before the planner generated the itinerary.",
    )
    findings.extend(build_negative_reason_findings(records))
    findings.sort(
        key=lambda item: (
            -item.rejected_count,
            -item.partial_count,
            -item.total_count,
            item.type,
            item.key,
        )
    )
    return findings[:5]


def load_feedback_records(db_path: Path, limit: int) -> list[FeedbackRecord]:
    if not db_path.exists():
        raise FileNotFoundError(f"SQLite database not found: {db_path}")

    with sqlite3.connect(db_path) as connection:
        cursor = connection.cursor()
        cursor.execute(
            """
            SELECT conversation_id, label, reason_code, note, agent_type, destination, days, budget,
                   has_travel_plan, metadata_json, created_at, updated_at
            FROM conversation_feedback
            ORDER BY updated_at DESC
            LIMIT ?
            """,
            (limit,),
        )
        rows = cursor.fetchall()

    return [
        FeedbackRecord(
            conversation_id=row[0],
            label=row[1],
            reason_code=row[2],
            note=row[3],
            agent_type=row[4],
            destination=row[5],
            days=row[6],
            budget=row[7],
            has_travel_plan=bool(row[8]),
            metadata=parse_metadata(row[9]),
            created_at=row[10],
            updated_at=row[11],
        )
        for row in rows
    ]


def build_summary(records: list[FeedbackRecord], limit_applied: int, db_path: Path) -> dict[str, Any]:
    accepted_count = sum(1 for record in records if is_accepted(record))
    partial_count = sum(1 for record in records if is_partial(record))
    rejected_count = sum(1 for record in records if is_rejected(record))
    structured_plan_count = sum(1 for record in records if record.has_travel_plan)

    return {
        "generatedAt": datetime.now(timezone.utc).isoformat(),
        "sourceDbPath": str(db_path),
        "limitApplied": limit_applied,
        "sampleCount": len(records),
        "acceptedCount": accepted_count,
        "partialCount": partial_count,
        "rejectedCount": rejected_count,
        "acceptedRatePct": round_pct(accepted_count, len(records)),
        "usableRatePct": round_pct(accepted_count + partial_count, len(records)),
        "structuredPlanCount": structured_plan_count,
        "structuredPlanCoveragePct": round_pct(structured_plan_count, len(records)),
        "topReasons": [
            asdict(item)
            for item in build_breakdown(
                records,
                lambda record: normalize_bucket(record.reason_code, "UNSPECIFIED"),
                5,
            )
        ],
        "topDestinations": [
            asdict(item)
            for item in build_breakdown(
                records,
                lambda record: normalize_bucket(record.destination, "UNKNOWN"),
                5,
            )
        ],
        "topAgents": [
            asdict(item)
            for item in build_breakdown(
                records,
                lambda record: record.agent_type if record.agent_type else "UNKNOWN",
                5,
            )
        ],
        "keyFindings": [asdict(item) for item in build_findings(records)],
    }


def render_breakdown_table(items: list[dict[str, Any]]) -> str:
    if not items:
        return "_No data_\n"

    lines = [
        "| Key | Total | Accepted | Partial | Rejected | Accepted % | Usable % |",
        "| --- | ---: | ---: | ---: | ---: | ---: | ---: |",
    ]
    for item in items:
        lines.append(
            "| {key} | {total_count} | {accepted_count} | {partial_count} | {rejected_count} | {accepted_rate_pct:.2f} | {usable_rate_pct:.2f} |".format(
                **item
            )
        )
    return "\n".join(lines) + "\n"


def render_findings(items: list[dict[str, Any]]) -> str:
    if not items:
        return "_No findings_\n"

    lines: list[str] = []
    for item in items:
        lines.append(
            "- [{type}] `{key}`: total={total_count}, partial={partial_count}, rejected={rejected_count}, usable={usable_rate_pct:.2f}%".format(
                **item
            )
        )
        lines.append(f"  Recommendation: {item['recommendation']}")
    return "\n".join(lines) + "\n"


def render_markdown(summary: dict[str, Any]) -> str:
    sample_count = summary["sampleCount"]
    lines = [
        "# Feedback Loop Report",
        "",
        f"- Generated at: `{summary['generatedAt']}`",
        f"- Source DB: `{summary['sourceDbPath']}`",
        f"- Limit applied: `{summary['limitApplied']}`",
        f"- Sample count: `{sample_count}`",
        "",
        "## Key Metrics",
        "",
        f"- Accepted: `{summary['acceptedCount']}` (`{summary['acceptedRatePct']:.2f}%`)",
        f"- Partial: `{summary['partialCount']}`",
        f"- Rejected: `{summary['rejectedCount']}`",
        f"- Usable rate: `{summary['usableRatePct']:.2f}%`",
        f"- Structured plan coverage: `{summary['structuredPlanCount']}` (`{summary['structuredPlanCoveragePct']:.2f}%`)",
        "",
    ]

    if sample_count == 0:
        lines.extend(
            [
                "## Status",
                "",
                "No feedback rows matched the selected limit. Generate or import conversation feedback first, then rerun this script.",
                "",
            ]
        )

    lines.extend(
        [
            "## Top Reasons",
            "",
            render_breakdown_table(summary["topReasons"]).rstrip(),
            "",
            "## Top Destinations",
            "",
            render_breakdown_table(summary["topDestinations"]).rstrip(),
            "",
            "## Top Agents",
            "",
            render_breakdown_table(summary["topAgents"]).rstrip(),
            "",
            "## Key Findings",
            "",
            render_findings(summary["keyFindings"]).rstrip(),
            "",
        ]
    )
    return "\n".join(lines).strip() + "\n"


def write_reports(summary: dict[str, Any], outdir: Path) -> tuple[Path, Path]:
    outdir.mkdir(parents=True, exist_ok=True)
    stamp = datetime.now().strftime("%Y%m%d-%H%M%S")
    json_path = outdir / f"feedback-loop-report-{stamp}.json"
    markdown_path = outdir / f"feedback-loop-report-{stamp}.md"

    json_path.write_text(json.dumps(summary, indent=2, ensure_ascii=True) + "\n", encoding="utf-8")
    markdown_path.write_text(render_markdown(summary), encoding="utf-8")
    return json_path, markdown_path


def main() -> int:
    args = parse_args()
    limit_applied = normalize_limit(args.limit)
    try:
        records = load_feedback_records(args.db.resolve(), limit_applied)
    except FileNotFoundError as exc:
        print(f"Error: {exc}", file=sys.stderr)
        return 1
    except sqlite3.Error as exc:
        print(f"Error: failed to read feedback data from SQLite: {exc}", file=sys.stderr)
        return 1

    summary = build_summary(records, limit_applied, args.db.resolve())
    json_path, markdown_path = write_reports(summary, args.outdir.resolve())

    print(f"Generated JSON report: {json_path}")
    print(f"Generated Markdown report: {markdown_path}")
    print(
        "Sample count={sampleCount}, accepted={acceptedCount}, partial={partialCount}, rejected={rejectedCount}, usableRatePct={usableRatePct:.2f}".format(
            **summary
        )
    )
    if args.print_markdown:
        print()
        print(render_markdown(summary))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
