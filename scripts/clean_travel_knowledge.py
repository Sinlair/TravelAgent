import argparse
import json
import re
from collections import Counter, defaultdict
from pathlib import Path
from typing import Any

DEFAULT_INPUT = Path("travel-agent-infrastructure/src/main/resources/travel-knowledge.collected.json")
DEFAULT_OUTPUT = Path("travel-agent-infrastructure/src/main/resources/travel-knowledge.cleaned.json")
CITY_ALIAS_SOURCE = Path("scripts/travel-attraction-targets.json")
KEEP_TOPICS = {"scenic", "activity", "food", "nightlife", "transit", "hotel"}
TRANSIT_BANNED_PREFIXES = (
    "routes ",
    "route ",
    "ticket ",
    "tickets ",
    "fare ",
    "fares ",
    "bus ",
)
TRANSIT_KEEP_HINTS = ("airport", "ferry", "metro", "subway", "rail", "train", "station", "tram")
HOTEL_LOCATION_HINTS = (
    "district",
    "area",
    "metro",
    "subway",
    "old town",
    "west lake",
    "bund",
    "near ",
    "downtown",
    "station",
    "airport",
    "walking distance",
    "close to",
    "business district",
    "shopping district",
    "lake",
    "park",
    "beach",
    "resort",
)
HOTEL_TYPE_HINTS = (
    "hotel",
    "hostel",
    "inn",
    "guest house",
    "guesthouse",
    "resort",
    "lodge",
    "suite",
    "homestay",
    "youth hostel",
    "backpack",
)
HOTEL_AREA_HINTS = (
    "where to stay",
    "best area",
    "best areas",
    "good base",
    "base yourself",
    "stay in",
    "district",
    "districts",
    "neighborhood",
    "neighbourhood",
    "area",
    "areas",
    "downtown",
    "city center",
    "city centre",
    "close to",
    "convenient for",
    "recommended area",
)
HOTEL_AREA_GUIDANCE_HINTS = (
    "where to stay",
    "best area",
    "best areas",
    "good base",
    "base yourself",
    "stay in",
    "district",
    "districts",
    "neighborhood",
    "neighbourhood",
    "recommended area",
)
TRANSIT_ARRIVAL_HINTS = (
    "airport",
    "railway station",
    "train station",
    "south station",
    "north station",
    "east station",
    "west station",
    "ferry terminal",
    "arrive",
    "arrival",
    "from airport",
    "from the airport",
    "from railway",
    "from the train station",
)
TRANSIT_HUB_HINTS = (
    "terminal",
    "wharf",
    "port",
    "interchange",
    "transfer",
    "hub",
    "bus station",
    "ferry",
    "metro station",
    "subway station",
)
TRANSIT_ROUTE_HINTS = (
    "line",
    "route",
    "north-south",
    "west-east",
    "district",
    "center",
    "centre",
    "linking",
    "passing through",
    "connect",
    "bus",
    "metro",
    "subway",
)
FOOD_CLUSTER_HINTS = (
    "cluster",
    "street",
    "lane",
    "night market",
    "food court",
    "quarter",
    "around",
    "near",
    "district",
)
TRIP_STYLE_KEYWORDS = {
    "relaxed": ("relaxed", "slow pace", "easy pace", "leisurely", "轻松", "慢节奏", "休闲"),
    "family": ("family", "kids", "children", "child-friendly", "亲子", "家庭", "小朋友"),
    "nightlife": ("nightlife", "bar", "pub", "late night", "夜生活", "酒吧", "夜景"),
    "museum": ("museum", "gallery", "history museum", "博物馆", "美术馆", "展览"),
    "shopping": ("shopping", "mall", "market", "walk street", "购物", "商场", "步行街"),
    "foodie": ("foodie", "food", "eat", "restaurant", "snack", "美食", "小吃", "吃"),
    "heritage": ("heritage", "historic", "old town", "temple", "古迹", "历史", "寺", "古镇"),
    "outdoors": ("outdoors", "hike", "park", "lake", "mountain", "户外", "徒步", "公园", "湖", "山"),
    "budget": ("budget", "cheap", "affordable", "hostel", "low cost", "预算", "便宜", "实惠"),
}
SUMMARY_DROP_PATTERNS = (
    re.compile(r"\b(?:open|opening hours?)\b.*", re.IGNORECASE),
    re.compile(r"\b(?:updated|last updated)\b.*", re.IGNORECASE),
    re.compile(r"\b(?:tickets?|admission|fare|fares|price|prices?)\b.*", re.IGNORECASE),
    re.compile(r"\b(?:tel|telephone|email|website|web|booking)\b.*", re.IGNORECASE),
)
SENTENCE_SPLIT_RE = re.compile(r"(?<=[.!?。！？])\s+")
MAX_PER_CITY_TOPIC = {
    "scenic": 12,
    "activity": 8,
    "food": 8,
    "nightlife": 6,
    "transit": 4,
    "hotel": 6,
}
PHONE_RE = re.compile(r"\+?\d[\d\s\-()]{7,}")
EMAIL_RE = re.compile(r"[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}")
PRICE_RE = re.compile(r"\bcny\s*\d{2,5}\b", re.IGNORECASE)
COORD_PREFIX_RE = re.compile(r"^\d+(?:\.\d+)?\s+\d+(?:\.\d+)?\s+\d+\s+")
CSS_ARTIFACT_RE = re.compile(r"(?:\.mw-parser-output[^{}]*\{[^}]*\})+")
CSS_TITLE_RE = re.compile(r"^(?:\.|#|@media\b|error-|listi\b|mw-)", re.IGNORECASE)
CSS_STYLE_RE = re.compile(r"(?:display\s*:\s*none|background\s*:\s*yellow|color\s*:\s*red|white-space\s*:\s*nowrap)", re.IGNORECASE)
TRACEBACK_RE = re.compile(r"(?:traceback \(most recent call last\)|unicode(?:encode|decode)error|file \"<stdin>\"|line \d+, in <module>)", re.IGNORECASE)
MOJIBAKE_RE = re.compile(r"(?:Â¥|Ã[^\s]{0,3}|â[^\s]{0,4}|鈽\?|锟|�)")
DINGBAT_RE = re.compile(r"[☎☏✆✉✂✈]")
EMAIL_OR_PHONE_EDGE_RE = re.compile(r"(?:,\s*)?(?:" + EMAIL_RE.pattern + r"|" + PHONE_RE.pattern + r")(?:\s*,)?", re.IGNORECASE)
HIGH_LATIN_HINT_RE = re.compile(r"[ÃÂâæåçéèêëìíîïðñòóôõöøùúûüýþÿœžŸ]")


def normalize_spaces(value: str) -> str:
    value = value.replace("\xa0", " ")
    value = value.replace("\u200b", "")
    value = value.replace("\ufeff", "")
    value = re.sub(r"\s+", " ", value)
    return value.strip()


def suspicious_score(value: str) -> int:
    score = len(MOJIBAKE_RE.findall(value)) * 3
    score += len(HIGH_LATIN_HINT_RE.findall(value))
    score += sum(1 for ch in value if 128 <= ord(ch) <= 159) * 2
    return score


def has_han(value: str) -> bool:
    return any("\u4e00" <= ch <= "\u9fff" for ch in value)


def maybe_repair_latin1_chunk(chunk: str) -> str:
    if sum(1 for ch in chunk if ord(ch) >= 128) < 2:
        return chunk
    try:
        fixed = chunk.encode("latin1").decode("utf-8")
    except UnicodeError:
        return chunk

    improvement = suspicious_score(chunk) - suspicious_score(fixed)
    if has_han(fixed) and not has_han(chunk):
        improvement += 4
    if improvement <= 0:
        return chunk
    return fixed


def repair_mojibake(value: str) -> str:
    parts: list[str] = []
    chunk: list[str] = []

    def flush() -> None:
        nonlocal chunk
        if not chunk:
            return
        parts.append(maybe_repair_latin1_chunk("".join(chunk)))
        chunk = []

    for ch in value:
        if ord(ch) <= 255:
            chunk.append(ch)
        else:
            flush()
            parts.append(ch)
    flush()
    return "".join(parts)


def normalize_text(value: str) -> str:
    value = repair_mojibake(normalize_spaces(value))
    replacements = {
        "–": "-",
        "—": "-",
        "’": "'",
        "“": '"',
        "”": '"',
        "楼": "CNY ",
        "妤": "CNY ",
        "Â¥": "CNY ",
        "¥": "CNY ",
        "â˜Ž": " ",
        "âœ†": " ",
        "âœ‰": " ",
        "Â": " ",
    }
    for src, dst in replacements.items():
        value = value.replace(src, dst)
    return value


def strip_markup_artifacts(value: str) -> str:
    value = CSS_ARTIFACT_RE.sub(" ", value)
    value = value.replace(".mw-parser-output", " ")
    value = DINGBAT_RE.sub(" ", value)
    value = COORD_PREFIX_RE.sub("", value)
    value = EMAIL_OR_PHONE_EDGE_RE.sub(" ", value)
    value = EMAIL_RE.sub(" ", value)
    value = PHONE_RE.sub(" ", value)
    value = re.sub(r"\b(check-in|check-out)\b\s*\d{1,2}:\d{2}", " ", value, flags=re.IGNORECASE)
    value = re.sub(r"\b(?:fax|tel|telephone)\b[: ]*", " ", value, flags=re.IGNORECASE)
    value = re.sub(r"\s*[,;]\s*[,;]+", ", ", value)
    value = re.sub(r"\(\s*\)", " ", value)
    value = re.sub(r"\s+", " ", value)
    return value.strip()


def title_key(record: dict[str, Any]) -> tuple[str, str, str]:
    return (
        record["city"].strip().lower(),
        record["topic"].strip().lower(),
        record["title"].strip().lower(),
    )


def sanitize_record(record: dict[str, Any]) -> dict[str, Any]:
    city = normalize_text(record.get("city", ""))
    topic = normalize_text(record.get("topic", "")).lower()
    title = strip_markup_artifacts(normalize_text(record.get("title", "")))
    content = strip_markup_artifacts(normalize_text(record.get("content", "")))
    source = normalize_text(record.get("source", ""))
    tags = []
    for tag in record.get("tags", []):
        tag = normalize_text(str(tag)).lower()
        if tag and tag not in tags:
            tags.append(tag)
    return {
        "city": city,
        "topic": topic,
        "title": title,
        "content": content,
        "tags": tags,
        "source": source,
    }


def searchable_text(record: dict[str, Any]) -> str:
    values = [record.get("title", ""), record.get("content", ""), *record.get("tags", [])]
    return normalize_text(" ".join(str(value) for value in values)).lower()


def split_sentences(value: str) -> list[str]:
    if not value:
        return []
    return [item.strip(" .") for item in SENTENCE_SPLIT_RE.split(value) if item and item.strip(" .")]


def normalize_sentence(sentence: str) -> str:
    sentence = normalize_text(sentence)
    for pattern in SUMMARY_DROP_PATTERNS:
        sentence = pattern.sub(" ", sentence)
    sentence = sentence.strip(" ,;.-")
    sentence = re.sub(r"\s+", " ", sentence)
    return sentence.strip()


def is_useful_sentence(sentence: str) -> bool:
    if len(sentence) < 24:
        return False
    lowered = sentence.lower()
    if CSS_STYLE_RE.search(lowered):
        return False
    if TRACEBACK_RE.search(lowered):
        return False
    if sentence.count(",") >= 8 and len(sentence) < 160:
        return False
    return True


def sentence_score(sentence: str, topic: str, schema_subtype: str) -> int:
    lowered = sentence.lower()
    score = min(len(sentence) // 24, 10)
    if topic == "scenic" and any(word in lowered for word in ("historic", "view", "lake", "temple", "museum", "walk", "causeway", "iconic")):
        score += 5
    if topic == "food" and any(word in lowered for word in ("special", "signature", "local", "street", "market", "bbq", "snack", "tea")):
        score += 5
    if topic == "hotel" and schema_subtype == "hotel_area" and any(word in lowered for word in ("stay", "base", "district", "near", "easy", "access", "convenient", "walk")):
        score += 8
    if topic == "hotel" and schema_subtype == "hotel_listing" and any(word in lowered for word in ("hostel", "hotel", "rooms", "location", "staff", "central")):
        score += 5
    if topic == "transit" and schema_subtype == "transit_arrival" and any(word in lowered for word in ("airport", "station", "take", "transfer", "downtown", "city centre", "city center")):
        score += 8
    if topic == "transit" and schema_subtype == "transit_hub" and any(word in lowered for word in ("terminal", "wharf", "port", "bus station", "hub", "interchange")):
        score += 7
    if topic == "transit" and schema_subtype == "transit_district" and any(word in lowered for word in ("connect", "line", "district", "route", "through", "link")):
        score += 5
    return score


def planner_summary(record: dict[str, Any]) -> str:
    topic = record["topic"]
    schema_subtype = record.get("schemaSubtype") or infer_schema_subtype(record)
    raw_sentences = [normalize_sentence(sentence) for sentence in split_sentences(record["content"])]
    raw_sentences = [sentence for sentence in raw_sentences if is_useful_sentence(sentence)]
    if not raw_sentences:
        return record["content"]

    ranked = sorted(
        raw_sentences,
        key=lambda sentence: sentence_score(sentence, topic, schema_subtype),
        reverse=True
    )

    kept: list[str] = []
    for sentence in ranked:
        lowered = sentence.lower()
        if any(existing.lower() == lowered for existing in kept):
            continue
        kept.append(sentence)
        if len(kept) == 2:
            break

    if not kept:
        return record["content"]

    if topic == "hotel" and schema_subtype == "hotel_area":
        prefix = "Best used as a stay area because"
    elif topic == "hotel":
        prefix = "Representative stay option:"
    elif topic == "transit" and schema_subtype == "transit_arrival":
        prefix = "Arrival advice:"
    elif topic == "transit" and schema_subtype == "transit_hub":
        prefix = "Transfer hub hint:"
    elif topic == "transit":
        prefix = "District movement hint:"
    elif topic == "food":
        prefix = "Food planning hint:"
    elif topic == "scenic":
        prefix = "Visit planning hint:"
    else:
        prefix = "Planning hint:"

    summary = f"{prefix} {' '.join(kept)}"
    summary = re.sub(r"\s+", " ", summary).strip()
    return summary


def infer_trip_style_tags(record: dict[str, Any]) -> list[str]:
    text = searchable_text(record)
    styles: list[str] = []
    for style, keywords in TRIP_STYLE_KEYWORDS.items():
        if any(keyword in text for keyword in keywords) and style not in styles:
            styles.append(style)
    if record["topic"] == "nightlife" and "nightlife" not in styles:
        styles.append("nightlife")
    if record["topic"] == "food" and "foodie" not in styles:
        styles.append("foodie")
    if record["topic"] == "scenic" and any(keyword in text for keyword in ("museum", "history", "gallery", "博物馆")) and "museum" not in styles:
        styles.append("museum")
    if record["topic"] == "hotel" and record.get("schemaSubtype") == "hotel_listing" and any(keyword in text for keyword in ("hostel", "budget", "cheap", "affordable", "青年旅舍")) and "budget" not in styles:
        styles.append("budget")
    return styles


def infer_schema_subtype(record: dict[str, Any]) -> str:
    topic = record["topic"]
    text = searchable_text(record)
    if topic == "hotel":
        has_area_guidance = any(hint in text for hint in HOTEL_AREA_GUIDANCE_HINTS)
        return "hotel_area" if has_area_guidance else "hotel_listing"
    if topic == "transit":
        has_explicit_arrival_advice = any(hint in text for hint in (
            "arrive",
            "arrival",
            "from airport",
            "from the airport",
            "from railway",
            "from the railway",
            "from the train station",
            "from the station",
            "to downtown",
            "to the city",
        ))
        has_arrival_node = any(hint in text for hint in (
            "airport",
            "railway station",
            "train station",
            "south station",
            "north station",
            "east station",
            "west station",
            "ferry terminal",
        ))
        if has_explicit_arrival_advice or (has_arrival_node and any(hint in text for hint in ("take", "get off", "depart", "terminates"))):
            return "transit_arrival"
        if any(hint in text for hint in TRANSIT_HUB_HINTS):
            return "transit_hub"
        return "transit_district"
    return topic


def is_noise(record: dict[str, Any]) -> bool:
    title = record["title"]
    content = record["content"]
    combined = f"{title}\n{content}"
    if not title or not content:
        return True
    if len(content) < 80:
        return True
    if title.lower().startswith("traceback"):
        return True
    if TRACEBACK_RE.search(combined):
        return True
    if CSS_TITLE_RE.search(title) or CSS_STYLE_RE.search(combined):
        return True
    if title.count("{") >= 1 and title.count(":") >= 1 and title.count(";") >= 1:
        return True
    if len(MOJIBAKE_RE.findall(combined)) >= 2:
        return True
    if content.count("?") >= 12:
        return True
    return False


def should_drop_transit(record: dict[str, Any]) -> bool:
    title = record["title"].lower()
    content = record["content"].lower()
    if title.startswith(TRANSIT_BANNED_PREFIXES):
        return True
    if any(hint in title for hint in TRANSIT_KEEP_HINTS):
        return False
    if "taxi" in title and "airport" not in content:
        return True
    if re.search(r"\bcny\s*\d+", content) and "station" not in content and "airport" not in content:
        return True
    return False


def should_drop_hotel(record: dict[str, Any]) -> bool:
    title = record["title"].lower()
    content = record["content"].lower()
    has_location_hint = any(hint in content or hint in title for hint in HOTEL_LOCATION_HINTS)
    has_hotel_type = any(hint in content or hint in title for hint in HOTEL_TYPE_HINTS)
    contact_count = int(bool(EMAIL_RE.search(content))) + len(PHONE_RE.findall(content)) + len(PRICE_RE.findall(content))

    if not has_hotel_type and not has_location_hint:
        return True
    if len(content) < 110 and not has_location_hint:
        return True
    if contact_count >= 3 and not has_location_hint:
        return True
    return False


def quality_score(record: dict[str, Any]) -> int:
    score = 0
    title = record["title"].lower()
    content = record["content"].lower()
    tags = set(record["tags"])
    schema_subtype = record.get("schemaSubtype") or infer_schema_subtype(record)
    score += min(len(content) // 40, 12)
    score += min(len(tags), 6)
    if record["topic"] == "scenic":
        score += 8
    if record["topic"] == "activity":
        score += 6
    if record["topic"] == "food":
        score += 5
    if record["topic"] == "hotel":
        score += 5
        if any(hint in content or hint in title for hint in HOTEL_LOCATION_HINTS):
            score += 6
        if any(hint in title for hint in ("hostel", "guest house", "guesthouse", "resort", "old town")):
            score += 2
    if schema_subtype == "hotel_area":
        score += 10
    if schema_subtype in {"transit_arrival", "transit_hub"}:
        score += 8
    if any(hint in content or hint in title for hint in TRANSIT_ROUTE_HINTS):
        score += 5
    if any(hint in content or hint in title for hint in FOOD_CLUSTER_HINTS):
        score += 4
    if any(word in title for word in ["museum", "temple", "lake", "mountain", "street", "park", "old town", "night market"]):
        score += 4
    if any(word in content for word in ["best", "worth", "view", "historic", "walking", "local", "popular", "traditional"]):
        score += 3
    if len(content) <= 360:
        score += 4
    return score


def clean(records: list[dict[str, Any]], city_alias_map: dict[str, list[str]]) -> tuple[list[dict[str, Any]], dict[str, int]]:
    stats = Counter()
    deduped: dict[tuple[str, str, str], dict[str, Any]] = {}
    for raw in records:
        record = sanitize_record(raw)
        if record["topic"] not in KEEP_TOPICS:
            stats["dropped_topic"] += 1
            continue
        if is_noise(record):
            stats["dropped_noise"] += 1
            continue
        if record["topic"] == "transit" and should_drop_transit(record):
            stats["dropped_transit"] += 1
            continue
        if record["topic"] == "hotel" and should_drop_hotel(record):
            stats["dropped_hotel"] += 1
            continue
        record["cityAliases"] = city_alias_map.get(record["city"], [])
        record["schemaSubtype"] = infer_schema_subtype(record)
        record["tripStyleTags"] = infer_trip_style_tags(record)
        record["content"] = planner_summary(record)
        record["qualityScore"] = quality_score(record)
        key = title_key(record)
        current = deduped.get(key)
        if current is None or record["qualityScore"] > current["qualityScore"]:
            deduped[key] = record

    grouped: dict[tuple[str, str], list[dict[str, Any]]] = defaultdict(list)
    for record in deduped.values():
        grouped[(record["city"], record["topic"])].append(record)

    cleaned: list[dict[str, Any]] = []
    for (_city, topic), items in grouped.items():
        items.sort(key=lambda item: item["qualityScore"], reverse=True)
        kept = items[: MAX_PER_CITY_TOPIC.get(topic, 6)]
        cleaned.extend(kept)
        stats[f"kept_{topic}"] += len(kept)
        stats[f"trimmed_{topic}"] += max(0, len(items) - len(kept))

    cleaned.sort(key=lambda item: (item["city"], item["topic"], item["title"]))
    stats["final_records"] = len(cleaned)
    return cleaned, dict(stats)


def load_city_alias_map(path: Path) -> dict[str, list[str]]:
    if not path.exists():
        return {}
    items = json.loads(path.read_text(encoding="utf-8"))
    alias_map: dict[str, list[str]] = {}
    for item in items:
        city = normalize_text(item.get("city", ""))
        aliases = []
        for alias in item.get("aliases", []):
            alias = normalize_text(str(alias))
            if alias and alias != city and alias not in aliases:
                aliases.append(alias)
        alias_map[city] = aliases
    return alias_map


def main() -> None:
    parser = argparse.ArgumentParser(description="Clean collected travel knowledge records.")
    parser.add_argument("--input", type=Path, default=DEFAULT_INPUT)
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT)
    args = parser.parse_args()

    repo_root = Path(__file__).resolve().parents[1]
    input_path = repo_root / args.input
    output_path = repo_root / args.output
    city_alias_path = repo_root / CITY_ALIAS_SOURCE

    records = json.loads(input_path.read_text(encoding="utf-8"))
    city_alias_map = load_city_alias_map(city_alias_path)
    cleaned, stats = clean(records, city_alias_map)
    output_path.write_text(json.dumps(cleaned, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps(stats, ensure_ascii=False, indent=2))
    print(f"Wrote {len(cleaned)} cleaned records to {output_path}")


if __name__ == "__main__":
    main()
