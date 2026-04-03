import argparse
import concurrent.futures
import html
import json
import re
import threading
import time
from dataclasses import dataclass
from html.parser import HTMLParser
from pathlib import Path
from typing import Any
from urllib.error import HTTPError, URLError
from urllib.parse import urlencode
from urllib.request import Request, urlopen

API_URL = "https://en.wikivoyage.org/w/api.php"
DEFAULT_TARGETS = Path("scripts/travel-attraction-targets.json")
DEFAULT_OUTPUT = Path("travel-agent-infrastructure/src/main/resources/travel-knowledge.collected.json")
SECTION_TOPIC_MAP = {
    "see": "scenic",
    "do": "activity",
    "eat": "food",
    "drink": "nightlife",
    "sleep": "hotel",
    "get around": "transit",
}
DEFAULT_MAX_WORKERS = 6
DEFAULT_PER_SECTION_LIMIT = 6
USER_AGENT = "TravelAgentKnowledgeCollector/2.0 (+https://example.local)"
PRINT_LOCK = threading.Lock()


class ListItemParser(HTMLParser):
    def __init__(self) -> None:
        super().__init__()
        self.items: list[dict[str, str]] = []
        self._li_depth = 0
        self._capture_text: list[str] = []
        self._capture_title: list[str] = []
        self._in_title = False
        self._in_sup = False

    def handle_starttag(self, tag: str, attrs: list[tuple[str, str | None]]) -> None:
        if tag == "li":
            self._li_depth += 1
            if self._li_depth == 1:
                self._capture_text = []
                self._capture_title = []
        elif tag in {"b", "strong"} and self._li_depth >= 1:
            self._in_title = True
        elif tag == "sup" and self._li_depth >= 1:
            self._in_sup = True

    def handle_endtag(self, tag: str) -> None:
        if tag == "li" and self._li_depth >= 1:
            if self._li_depth == 1:
                text = normalize_text(" ".join(self._capture_text))
                title = normalize_text(" ".join(self._capture_title))
                if text:
                    self.items.append({"title": title or guess_title(text), "text": text})
            self._li_depth -= 1
        elif tag in {"b", "strong"}:
            self._in_title = False
        elif tag == "sup":
            self._in_sup = False

    def handle_data(self, data: str) -> None:
        if self._li_depth < 1 or self._in_sup:
            return
        cleaned = normalize_text(data)
        if not cleaned:
            return
        self._capture_text.append(cleaned)
        if self._in_title:
            self._capture_title.append(cleaned)


@dataclass
class CityResult:
    city: str
    page: str
    records: list[dict[str, Any]]
    errors: list[str]


def log(message: str) -> None:
    with PRINT_LOCK:
        print(message, flush=True)


def normalize_text(value: str) -> str:
    value = html.unescape(value)
    value = value.replace("\xa0", " ")
    value = re.sub(r"\[[^\]]*\]", " ", value)
    value = re.sub(r"\s+", " ", value)
    return value.strip(" -:\u2014\u2013\t\r\n")


def looks_noisy(text: str) -> bool:
    if text.count("?") >= 10:
        return True
    if len(re.findall(r"[\u4e00-\u9fff]", text)) > 0 and len(re.findall(r"[A-Za-z]", text)) > len(text) * 0.7:
        return True
    return False


def guess_title(text: str) -> str:
    for separator in [" - ", " – ", ": ", ". ", ", "]:
        if separator in text:
            head = text.split(separator, 1)[0].strip()
            if 4 <= len(head) <= 90:
                return head
    return text[:90].strip()


def keyword_tokens(text: str) -> list[str]:
    lowered = text.lower()
    tokens = re.split(r"[^a-z0-9\u4e00-\u9fff']+", lowered)
    picked: list[str] = []
    for token in tokens:
        if len(token) < 3:
            continue
        if token in {"city", "area", "street", "park", "temple", "museum", "district", "scenic", "spot", "hotel", "route"}:
            continue
        if token not in picked:
            picked.append(token)
        if len(picked) >= 6:
            break
    return picked


def fetch_json(params: dict[str, Any], attempts: int = 4) -> dict[str, Any]:
    query = urlencode(params)
    request = Request(f"{API_URL}?{query}", headers={"User-Agent": USER_AGENT})
    last_error: Exception | None = None
    for attempt in range(1, attempts + 1):
        try:
            with urlopen(request, timeout=30) as response:
                return json.loads(response.read().decode("utf-8"))
        except (HTTPError, URLError, TimeoutError, ConnectionError) as error:
            last_error = error
            if attempt == attempts:
                break
            time.sleep(min(1.5 * attempt, 5))
    raise RuntimeError(f"Request failed after {attempts} attempts: {last_error}")


def fetch_sections(page: str) -> list[dict[str, str]]:
    payload = fetch_json(
        {
            "action": "parse",
            "page": page,
            "prop": "sections",
            "format": "json",
            "formatversion": 2,
            "redirects": 1,
        }
    )
    return payload.get("parse", {}).get("sections", [])


def fetch_section_html(page: str, section_index: str) -> str:
    payload = fetch_json(
        {
            "action": "parse",
            "page": page,
            "prop": "text",
            "section": section_index,
            "format": "json",
            "formatversion": 2,
            "disablelimitreport": 1,
            "disableeditsection": 1,
            "redirects": 1,
        }
    )
    return payload.get("parse", {}).get("text", "")


def is_candidate(title: str, text: str, topic: str) -> bool:
    if len(text) < 60 or len(text) > 900:
        return False
    if looks_noisy(title) or looks_noisy(text):
        return False
    lowered_title = title.lower()
    if topic == "transit" and lowered_title.startswith(("routes ", "route ", "by ", "ticket ")):
        return False
    if topic == "hotel" and len(re.findall(r"[0-9]", text)) > 25:
        return False
    return True


def collect_city(city_record: dict[str, Any], per_section_limit: int) -> CityResult:
    page = city_record["page"]
    city = city_record["city"]
    aliases = city_record.get("aliases", [])
    errors: list[str] = []
    try:
        sections = fetch_sections(page)
    except Exception as error:
        return CityResult(city=city, page=page, records=[], errors=[str(error)])

    records: list[dict[str, Any]] = []
    for section in sections:
        line = normalize_text(section.get("line", ""))
        topic = SECTION_TOPIC_MAP.get(line.lower())
        if topic is None:
            continue
        try:
            html_text = fetch_section_html(page, section.get("index", ""))
        except Exception as error:
            errors.append(f"section {line}: {error}")
            continue

        parser = ListItemParser()
        parser.feed(html_text)
        count = 0
        for item in parser.items:
            title = item["title"]
            content = item["text"]
            if not is_candidate(title, content, topic):
                continue
            tags = [city.lower(), topic, line.lower(), *[alias.lower() for alias in aliases], *keyword_tokens(title)]
            deduped_tags: list[str] = []
            for tag in tags:
                if tag and tag not in deduped_tags:
                    deduped_tags.append(tag)
            records.append(
                {
                    "city": city,
                    "topic": topic,
                    "title": title,
                    "content": content,
                    "tags": deduped_tags,
                    "source": f"wikivoyage:{page}#{line.replace(' ', '_')}",
                }
            )
            count += 1
            if count >= per_section_limit:
                break
    return CityResult(city=city, page=page, records=records, errors=errors)


def load_targets(path: Path) -> list[dict[str, Any]]:
    return json.loads(path.read_text(encoding="utf-8"))


def load_existing(path: Path) -> list[dict[str, Any]]:
    if not path.exists():
        return []
    return json.loads(path.read_text(encoding="utf-8"))


def dedupe_records(records: list[dict[str, Any]]) -> list[dict[str, Any]]:
    seen: dict[tuple[str, str, str], dict[str, Any]] = {}
    for record in records:
        key = (
            normalize_text(record.get("city", "")).lower(),
            normalize_text(record.get("topic", "")).lower(),
            normalize_text(record.get("title", "")).lower(),
        )
        seen[key] = record
    return list(seen.values())


def persist(output_path: Path, base_records: list[dict[str, Any]], collected_records: list[dict[str, Any]]) -> int:
    merged = dedupe_records(base_records + collected_records)
    output_path.parent.mkdir(parents=True, exist_ok=True)
    output_path.write_text(json.dumps(merged, ensure_ascii=False, indent=2), encoding="utf-8")
    return len(merged)


def main() -> None:
    parser = argparse.ArgumentParser(description="Collect travel attraction knowledge from Wikivoyage.")
    parser.add_argument("--targets", type=Path, default=DEFAULT_TARGETS)
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT)
    parser.add_argument("--per-section-limit", type=int, default=DEFAULT_PER_SECTION_LIMIT)
    parser.add_argument("--max-workers", type=int, default=DEFAULT_MAX_WORKERS)
    args = parser.parse_args()

    repo_root = Path(__file__).resolve().parents[1]
    targets = load_targets(repo_root / args.targets)
    output_path = repo_root / args.output
    existing = load_existing(output_path)

    collected: list[dict[str, Any]] = []
    log(f"Starting collection for {len(targets)} cities with max_workers={args.max_workers}")
    with concurrent.futures.ThreadPoolExecutor(max_workers=args.max_workers) as executor:
        futures = {
            executor.submit(collect_city, city, args.per_section_limit): city
            for city in targets
        }
        for index, future in enumerate(concurrent.futures.as_completed(futures), start=1):
            city = futures[future]
            try:
                result = future.result()
            except Exception as error:
                log(f"[{index}/{len(targets)}] {city['city']}: failed with unexpected error: {error}")
                continue
            collected.extend(result.records)
            total = persist(output_path, existing, collected)
            error_suffix = f" errors={len(result.errors)}" if result.errors else ""
            log(f"[{index}/{len(targets)}] {result.city}: +{len(result.records)} records, total={total}{error_suffix}")
            if result.errors:
                for entry in result.errors[:3]:
                    log(f"  - {entry}")

    final_total = persist(output_path, existing, collected)
    log(f"Finished. Wrote {final_total} records to {output_path}")


if __name__ == "__main__":
    main()