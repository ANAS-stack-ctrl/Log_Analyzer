#!/usr/bin/env python3
"""
Audit LOGS_2: catalogue WORKS + extraction graphe (process, filtre, tâche, action).
Compare les signaux présents dans les logs vs ce qu'un extracteur regex (aligné LogPatternExtractor) produit.
"""
import argparse
import json
import re
import sys
from collections import Counter, defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CATALOG = ROOT / "src/main/resources/works-message-families.json"

WORKFLOW_PREFIXES = (
    "processfilter", "processwebservice", "processrunrules", "process.",
    "processworksinit", "processhyphen",
)

# --- regex alignées LogPatternExtractor (simplifié) ---
RE_FILTER_CODE = re.compile(r"filter code\s*\[\s*([^\]]+?)\s*]", re.I)
RE_FETCH_FILTER = re.compile(r"fetching filter\s*\[\s*([^\]]+?)\s*]", re.I)
RE_TASK_BRACKET = re.compile(r"taskName\s*\[\s*([^\]]+?)\s*]", re.I)
RE_TASK_PARAM = re.compile(r"taskName\s*=\s*([^,}\s]+)", re.I)
RE_ACTION_BRACKET = re.compile(r"actionName\s*\[\s*([^\]]+?)\s*]", re.I)
RE_ACTION_NAME_BRACKET = re.compile(r"ACTION_NAME\s*\[\s*([^\]]+?)\s*]", re.I)
RE_ACTION_COLON = re.compile(r"actionName\s*:\s*([^,|]+?)(?=\s*,\s*-|\s*\||$)", re.I)
RE_TRANSITION = re.compile(r"transition\s*[=:]\s*([^,}\s|]+)", re.I)
RE_PROCESS_BRACKET = re.compile(r"processName\s*\[\s*([^\]]+?)\s*]", re.I)
RE_PROCESS_HYPHEN = re.compile(r"^process[A-Za-z]+-\d+-", re.I)
RE_WS = re.compile(r"\b(WS_[A-Z0-9_]+)\b")


def parse_line(line: str):
    line = line.rstrip("\n\r")
    if not line or line.count("|") < 7:
        return None
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


def middle_column_segment(process: str) -> str | None:
    if not process or "-" not in process:
        return None
    parts = process.split("-")
    if len(parts) >= 3:
        return parts[2].strip() or None
    return None


def last_column_segment(process: str) -> str | None:
    if not process or "-" not in process:
        return None
    parts = process.split("-")
    if len(parts) >= 2:
        return parts[-1].strip() or None
    return None


def process_root(process: str) -> str | None:
    if not process:
        return None
    if process.startswith("process."):
        name = process[8:].split("---")[0].split("-")[0].strip()
        return name or None
    if RE_PROCESS_HYPHEN.match(process):
        return process.split("-")[0]
    return None


def looks_like_filter_code(s: str) -> bool:
    if not s or s.isdigit():
        return False
    u = s.upper()
    if u.startswith("WS_"):
        return False
    if "_TASK" in u or u.endswith("_TASK"):
        return False
    return True


def extract_simulated(process: str, message: str) -> dict:
    """Simulation simplifiée de LogPatternExtractor.extractGraph."""
    out = {"process": None, "filter": None, "task": None, "action": None}
    all_text = f"{message} {process}"

    # Process
    pb = RE_PROCESS_BRACKET.search(all_text)
    if pb:
        out["process"] = pb.group(1).strip()
    elif process.startswith("process."):
        out["process"] = process_root(process)
    elif RE_PROCESS_HYPHEN.match(process):
        out["process"] = process.split("-")[0]

    # Filter
    fc = RE_FILTER_CODE.search(all_text) or RE_FETCH_FILTER.search(all_text)
    if fc:
        out["filter"] = fc.group(1).strip()
    elif process.lower().startswith("processfilter"):
        mid = middle_column_segment(process)
        if mid and looks_like_filter_code(mid):
            out["filter"] = mid

    # Task
    t = RE_TASK_BRACKET.search(all_text) or RE_TASK_PARAM.search(all_text)
    if t:
        out["task"] = t.group(1).strip()
    elif process.lower().startswith("processrunrules"):
        mid = middle_column_segment(process)
        if mid and ("_TASK" in mid.upper() or mid.upper().endswith("_TASK")):
            out["task"] = mid
    elif process.lower().startswith("processwebservice"):
        mid = middle_column_segment(process)
        if mid and mid.upper().startswith("WS_"):
            out["task"] = mid

    # Action
    a = (
        RE_ACTION_BRACKET.search(all_text)
        or RE_ACTION_NAME_BRACKET.search(all_text)
        or RE_ACTION_COLON.search(all_text)
        or RE_TRANSITION.search(all_text)
    )
    if a:
        out["action"] = a.group(1).strip()
    else:
        last = last_column_segment(process)
        if last and not last.isdigit() and RE_PROCESS_HYPHEN.match(process):
            root = process.split("-")[0].lower()
            if root not in ("processfilter",):
                out["action"] = last

    return out


def ground_truth_signals(process: str, message: str) -> dict:
    """Signaux objectifs dans le log indiquant qu'une entité devrait être extractible."""
    sig = {"process": False, "filter": False, "task": False, "action": False}
    all_text = f"{message} {process}"
    pl = process.lower()

    if (
        pl.startswith("process.")
        or pl.startswith("processfilter")
        or pl.startswith("processwebservice")
        or pl.startswith("processrunrules")
        or pl.startswith("processworksinit")
        or RE_PROCESS_HYPHEN.match(process)
        or RE_PROCESS_BRACKET.search(all_text)
        or "running rules|" in message.lower()
    ):
        sig["process"] = True

    if RE_FILTER_CODE.search(all_text) or RE_FETCH_FILTER.search(all_text):
        sig["filter"] = True
    elif pl.startswith("processfilter"):
        mid = middle_column_segment(process)
        if mid and looks_like_filter_code(mid):
            sig["filter"] = True

    if RE_TASK_BRACKET.search(all_text) or RE_TASK_PARAM.search(all_text):
        sig["task"] = True
    elif pl.startswith("processrunrules"):
        mid = middle_column_segment(process)
        if mid and "_TASK" in mid.upper():
            sig["task"] = True
    elif pl.startswith("processwebservice"):
        mid = middle_column_segment(process)
        if mid and mid.upper().startswith("WS_"):
            sig["task"] = True
    elif RE_WS.search(message) and "processwebservice" in pl:
        sig["task"] = True

    if (
        RE_ACTION_BRACKET.search(all_text)
        or RE_ACTION_NAME_BRACKET.search(all_text)
        or RE_ACTION_COLON.search(all_text)
        or RE_TRANSITION.search(all_text)
        or "doAction for" in message
        or "ACTION_NAME_" in message
    ):
        sig["action"] = True
    elif RE_PROCESS_HYPHEN.match(process):
        last = last_column_segment(process)
        if last and not last.isdigit():
            sig["action"] = True

    return sig


def is_workflow_line(process: str, message: str) -> bool:
    if not process and not message:
        return False
    pl = (process or "").lower()
    if any(pl.startswith(p) for p in WORKFLOW_PREFIXES):
        return True
    if RE_PROCESS_HYPHEN.match(process or ""):
        return True
    ml = (message or "").lower()
    return any(
        k in ml
        for k in (
            "filter code",
            "fetching filter",
            "taskname",
            "actionname",
            "running rules|",
            "doaction for",
            "startprocess",
            "processname",
        )
    )


def load_catalog():
    data = json.loads(CATALOG.read_text(encoding="utf-8"))
    families = []
    for f in data["families"]:
        patterns = [re.compile(p, re.I) for p in f.get("patterns", []) if p]
        families.append({"id": f["id"], "column_only": f.get("columnOnly", False), "patterns": patterns})
    return data.get("version", "?"), len(families), families


def classify_catalog(process, message, families):
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


def audit(files, families, max_lines: int | None):
    total = workflow_lines = 0
    catalog_matched = 0
    gt = Counter()
    extracted = Counter()
    missed = defaultdict(Counter)  # entity -> reason counts
    file_types = Counter()

    for path in files:
        file_types[path.name.split("-")[0]] += 1
        line_count = 0
        with path.open(encoding="utf-8", errors="replace") as f:
            for line in f:
                if max_lines and line_count >= max_lines:
                    break
                if not line.strip():
                    continue
                parsed = parse_line(line)
                if not parsed:
                    continue
                process, message = parsed
                total += 1
                line_count += 1

                if not is_workflow_line(process, message):
                    continue
                workflow_lines += 1

                if classify_catalog(process or "", message or "", families):
                    catalog_matched += 1

                truth = ground_truth_signals(process or "", message or "")
                ex = extract_simulated(process or "", message or "")

                for key in ("process", "filter", "task", "action"):
                    if truth[key]:
                        gt[key] += 1
                        if ex[key]:
                            extracted[key] += 1
                        else:
                            missed[key]["not_extracted"] += 1

    return {
        "total": total,
        "workflow_lines": workflow_lines,
        "catalog_matched": catalog_matched,
        "gt": gt,
        "extracted": extracted,
        "missed": missed,
        "file_types": file_types,
    }


def pct(num, den):
    return f"{num * 100.0 / den:.2f}%" if den else "n/a"


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("files", nargs="*", default=[r"C:\Users\Asus\Desktop\LOGS_2"])
    parser.add_argument("--glob", action="append", default=[])
    parser.add_argument("--max-lines-per-file", type=int, default=0, help="0 = unlimited")
    args = parser.parse_args()

    if not args.glob:
        args.glob = ["works-*.log", "process-*.log", "saveLoadLogFile-*.log"]

    files = collect_files(args)
    if not files:
        print("Aucun fichier. Vérifiez le chemin LOGS_2.")
        sys.exit(1)

    version, fam_count, families = load_catalog()
    max_lines = args.max_lines_per_file or None

    print("=" * 60)
    print("AUDIT EXTRACTION — LOGS_2")
    print("=" * 60)
    print(f"Catalogue works-message-families.json v{version} ({fam_count} familles)")
    print(f"Fichiers: {len(files)} | globs: {args.glob}")

    r = audit(files, families, max_lines)

    print(f"\nTypes de fichiers: {dict(r['file_types'].most_common(15))}")
    print(f"\n--- LIGNES ---")
    print(f"Total lignes parsées     : {r['total']:,}")
    print(f"Lignes workflow (ciblées): {r['workflow_lines']:,}")

    wl = r["workflow_lines"]
    print(f"\n--- CATALOGUE (familles message) ---")
    print(f"Reconnues                : {r['catalog_matched']:,} / {wl:,} ({pct(r['catalog_matched'], wl)})")
    print(f"Inconnues                : {wl - r['catalog_matched']:,}")

    print(f"\n--- EXTRACTION GRAPHE (simulation LogPatternExtractor) ---")
    print(f"{'Entité':<10} {'Signaux dataset':>16} {'Extraits':>12} {'Couverture':>12} {'Manqués':>10}")
    print("-" * 62)
    for key, label in [
        ("process", "Process"),
        ("filter", "Filtre"),
        ("task", "Tâche"),
        ("action", "Action"),
    ]:
        g = r["gt"][key]
        e = r["extracted"][key]
        m = g - e
        print(f"{label:<10} {g:>16,} {e:>12,} {pct(e, g):>12} {m:>10,}")

    print(f"\nNote: « Signaux dataset » = lignes où le log contient explicitement")
    print(f"      un marqueur extractible (filter code, taskName, colonne process.*, etc.)")

    print("\n" + "=" * 60)


if __name__ == "__main__":
    main()
