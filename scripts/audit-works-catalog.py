#!/usr/bin/env python3
"""Audit WORKS log lines against works-message-families.json patterns."""
import argparse
import json
import re
import sys
from collections import Counter, defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CATALOG = ROOT / "src/main/resources/works-message-families.json"


def parse_line(line: str):
    line = line.rstrip("\n\r")
    if not line or line.count("|") < 7:
        return None, line[:200]
    parts = line.split("|")
    process = parts[5].strip() if len(parts) > 5 else ""
    msg_parts = []
    i = 6
    while i < len(parts):
        p = parts[i]
        if p.isdigit() and i + 3 < len(parts):
            break
        msg_parts.append(p)
        i += 1
    message = "|".join(msg_parts).strip()
    if message.count("|") > 0:
        segs = message.split("|")
        if segs and segs[-1].isdigit():
            message = "|".join(segs[:-1])
    return process, message


def load_catalog():
    data = json.loads(CATALOG.read_text(encoding="utf-8"))
    families = []
    for f in data["families"]:
        patterns = [re.compile(p, re.IGNORECASE) for p in f.get("patterns", []) if p]
        families.append({
            "id": f["id"],
            "column_only": f.get("columnOnly", False),
            "patterns": patterns,
        })
    return families


def classify(process, message, families):
    for fam in families:
        if fam["column_only"]:
            continue
        if message:
            for pat in fam["patterns"]:
                if pat.search(message):
                    return fam["id"]
    for fam in families:
        if process:
            for pat in fam["patterns"]:
                if pat.search(process):
                    return fam["id"]
    return None


def normalize_unknown(message: str, max_len=90) -> str:
    m = message.strip()
    m = re.sub(r"^uuid\s*\[[^\]]+\]\s*", "", m, flags=re.I)
    m = re.sub(r"^filter code\s*\[[^\]]+\]\s*uuid\s*\[[^\]]+\]\s*", "", m, flags=re.I)
    m = re.sub(r"\d+", "#", m)
    m = re.sub(r"\s+", " ", m)
    return m[:max_len]


def collect_files(args) -> list[Path]:
    files: list[Path] = []
    for raw in args.files:
        p = Path(raw)
        if p.is_dir():
            for pattern in args.glob:
                files.extend(sorted(p.glob(pattern)))
        elif p.exists():
            files.append(p)
    return files


def audit_files(files, families, top_n=100, max_lines_per_file: int | None = None):
    total = matched = 0
    unknown_by_norm = Counter()
    unknown_samples = defaultdict(list)
    family_hits = Counter()

    for path in files:
        line_count = 0
        with path.open(encoding="utf-8", errors="replace") as f:
            for line in f:
                if max_lines_per_file and line_count >= max_lines_per_file:
                    break
                if not line.strip():
                    continue
                process, message = parse_line(line)
                if not message and not process:
                    continue
                total += 1
                line_count += 1
                fam_id = classify(process or "", message or "", families)
                if fam_id:
                    matched += 1
                    family_hits[fam_id] += 1
                else:
                    norm = normalize_unknown(message or process or "")
                    unknown_by_norm[norm] += 1
                    if len(unknown_samples[norm]) < 1:
                        sample = f"[{process[:50]}] {message[:180]}" if process else message[:200]
                        unknown_samples[norm].append(sample)

    return total, matched, unknown_by_norm, unknown_samples, family_hits


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("files", nargs="*", help="Log files or directories")
    parser.add_argument("--glob", action="append", default=["works-*.log"],
                        help="Glob when path is a directory (repeatable)")
    parser.add_argument("--top", type=int, default=80)
    parser.add_argument(
        "--max-lines-per-file",
        type=int,
        default=0,
        help="Limite par fichier (0 = tout le dataset, peut prendre ~1h sur LOGS_2)",
    )
    parser.add_argument(
        "--quick",
        action="store_true",
        help="Échantillon rapide : 2000 lignes/fichier (vérif locale en quelques secondes)",
    )
    args = parser.parse_args()

    max_lines = args.max_lines_per_file or (2000 if args.quick else None)

    files = collect_files(args)
    if not files:
        print("No files. Usage: audit-works-catalog.py C:\\path\\LOGS_2 --glob works-*.log")
        sys.exit(1)

    families = load_catalog()
    mode = f"max {max_lines} lignes/fichier" if max_lines else "complet"
    print(f"FILES={len(files)} CATALOG_FAMILIES={len(families)} MODE={mode}")
    total, matched, unknown_by_norm, unknown_samples, family_hits = audit_files(
        files, families, max_lines_per_file=max_lines
    )

    print(f"TOTAL_LINES={total}")
    print(f"MATCHED={matched}")
    print(f"UNKNOWN={total - matched}")
    print(f"COVERAGE={matched * 100.0 / total:.4f}%")
    print(f"\n=== TOP UNKNOWN PATTERNS (normalized, top {args.top}) ===")
    for norm, cnt in unknown_by_norm.most_common(args.top):
        print(f"\n[{cnt:8d}] {norm}")
        for s in unknown_samples[norm][:1]:
            print(f"           ex: {s[:240]}")

    print("\n=== TOP FAMILY HITS ===")
    for fid, cnt in family_hits.most_common(25):
        print(f"  {fid}: {cnt}")


if __name__ == "__main__":
    main()
