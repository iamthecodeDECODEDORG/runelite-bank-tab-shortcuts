#!/usr/bin/env python3
import argparse
import pathlib
import re
import sys
import zipfile


FORBIDDEN_SOURCE = {
    "network": re.compile(r"java\\.net|okhttp|HttpClient|Socket|WebSocket", re.IGNORECASE),
    "filesystem": re.compile(r"java\\.io|java\\.nio\\.file|RuneLite\\.RUNELITE_DIR|FileOutputStream"),
    "process": re.compile(r"ProcessBuilder|Runtime\\.getRuntime|java\\.lang\\.reflect"),
    "clipboard_or_robot": re.compile(r"Clipboard|Toolkit\\.getDefaultToolkit|java\\.awt\\.Robot"),
    "gameplay_action": re.compile(r"menuAction|invokeMenuAction|MenuAction|Packet", re.IGNORECASE),
    "credentials": re.compile(r"credential|session|accountHash|profile", re.IGNORECASE),
}

EXPECTED_CLASSES = {
    "io/github/iamthecodedecoded/banktabshortcuts/BankTabNavigator.class",
    "io/github/iamthecodedecoded/banktabshortcuts/BankTabShortcutsPlugin.class",
}


def scan(source: pathlib.Path, jar: pathlib.Path) -> list[str]:
    findings: list[str] = []
    for path in sorted(source.rglob("*.java")):
        text = path.read_text(encoding="utf-8")
        for code, pattern in FORBIDDEN_SOURCE.items():
            if pattern.search(text):
                findings.append(f"BTS_SECURITY_{code.upper()}: {path}")

    with zipfile.ZipFile(jar) as archive:
        classes = {name for name in archive.namelist() if name.endswith(".class")}
    if classes != EXPECTED_CLASSES:
        findings.append(
            "BTS_SECURITY_CLASS_SET: expected "
            f"{sorted(EXPECTED_CLASSES)}, found {sorted(classes)}"
        )
    return findings


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source", type=pathlib.Path, required=True)
    parser.add_argument("--jar", type=pathlib.Path, required=True)
    args = parser.parse_args()
    findings = scan(args.source, args.jar)
    if findings:
        print("\n".join(findings), file=sys.stderr)
        return 1
    print("BTS_SECURITY_OK")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
