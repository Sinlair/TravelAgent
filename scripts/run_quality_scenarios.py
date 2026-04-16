#!/usr/bin/env python3
import argparse
import json
import pathlib
import sys
import urllib.error
import urllib.request
from datetime import datetime, timezone


def read_json(path: pathlib.Path):
    with path.open("r", encoding="utf-8") as handle:
        return json.load(handle)


def post_json(base_url: str, path: str, payload: dict):
    body = json.dumps(payload).encode("utf-8")
    request = urllib.request.Request(
        f"{base_url.rstrip('/')}{path}",
        data=body,
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    with urllib.request.urlopen(request, timeout=120) as response:
        wrapped = json.load(response)
    if wrapped.get("code") != "0000":
        raise RuntimeError(wrapped.get("info", "Unknown API error"))
    return wrapped["data"]


def classify_result(result: dict) -> str:
    if result.get("travelPlan"):
        constraint_summary = result.get("constraintSummary") or {}
        if constraint_summary.get("hasRisk"):
            return "high_risk"
        return "structured_plan"
    return "fallback"


def run_scenario(base_url: str, scenario: dict) -> dict:
    first_result = post_json(base_url, "/api/conversations/chat", scenario["request"])
    final_result = first_result
    if scenario.get("followup"):
        followup_payload = dict(scenario["followup"])
        followup_payload["conversationId"] = first_result["conversationId"]
        final_result = post_json(base_url, "/api/conversations/chat", followup_payload)

    return {
        "id": scenario["id"],
        "title": scenario["title"],
        "conversationId": final_result.get("conversationId"),
        "outcome": classify_result(final_result),
        "agentType": final_result.get("agentType"),
        "hasTravelPlan": bool(final_result.get("travelPlan")),
        "issues": [item.get("code") for item in final_result.get("issues", [])],
        "constraintStatus": (final_result.get("constraintSummary") or {}).get("status"),
        "planVersion": (final_result.get("feedbackTarget") or {}).get("planVersion"),
    }


def write_markdown(output_path: pathlib.Path, generated_at: str, results: list[dict]):
    lines = [
        "# Quality Scenario Report",
        "",
        f"Generated: {generated_at}",
        "",
        "| Scenario | Outcome | Agent | Issues | Plan Version |",
        "| --- | --- | --- | --- | --- |",
    ]
    for item in results:
        issues = ", ".join(item["issues"]) if item["issues"] else "-"
        lines.append(
            f"| {item['id']} | {item['outcome']} | {item.get('agentType') or '-'} | {issues} | {item.get('planVersion') or '-'} |"
        )
    output_path.write_text("\n".join(lines) + "\n", encoding="utf-8")


def main() -> int:
    parser = argparse.ArgumentParser(description="Replay internal-beta quality scenarios against the local TravelAgent API.")
    parser.add_argument("--base-url", default="http://localhost:8080", help="Base URL for the running TravelAgent app")
    parser.add_argument(
        "--scenarios",
        default=str(pathlib.Path(__file__).with_name("quality-scenarios.json")),
        help="Path to the scenario fixture file",
    )
    parser.add_argument(
        "--output-dir",
        default=str(pathlib.Path("quality-reports")),
        help="Directory for JSON and Markdown reports",
    )
    args = parser.parse_args()

    scenarios_path = pathlib.Path(args.scenarios).resolve()
    output_dir = pathlib.Path(args.output_dir).resolve()
    output_dir.mkdir(parents=True, exist_ok=True)

    try:
        scenarios = read_json(scenarios_path)
        results = [run_scenario(args.base_url, scenario) for scenario in scenarios]
    except urllib.error.URLError as exc:
        print(f"Failed to reach {args.base_url}: {exc}", file=sys.stderr)
        return 2
    except Exception as exc:
        print(f"Scenario run failed: {exc}", file=sys.stderr)
        return 1

    generated_at = datetime.now(timezone.utc).isoformat()
    json_path = output_dir / "quality-scenarios.latest.json"
    md_path = output_dir / "quality-scenarios.latest.md"
    json_path.write_text(json.dumps({"generatedAt": generated_at, "results": results}, indent=2), encoding="utf-8")
    write_markdown(md_path, generated_at, results)

    print(f"Wrote {json_path}")
    print(f"Wrote {md_path}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
