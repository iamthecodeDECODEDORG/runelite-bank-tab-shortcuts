#!/usr/bin/env python3
import pathlib
import tempfile
import zipfile

from security_scan import scan


def write_jar(path: pathlib.Path, classes: set[str]) -> None:
    with zipfile.ZipFile(path, "w") as archive:
        for name in classes:
            archive.writestr(name, b"test")


with tempfile.TemporaryDirectory() as temp:
    root = pathlib.Path(temp)
    source = root / "src"
    source.mkdir()
    java = source / "Safe.java"
    java.write_text("final class Safe {}", encoding="utf-8")
    jar = root / "plugin.jar"
    expected = {
        "io/github/iamthecodedecoded/banktabshortcuts/BankTabNavigator.class",
        "io/github/iamthecodedecoded/banktabshortcuts/BankTabShortcutsPlugin.class",
    }
    write_jar(jar, expected)
    assert not scan(source, jar)

    java.write_text("import java.net.Socket; final class Unsafe {}", encoding="utf-8")
    assert any("BTS_SECURITY_NETWORK" in finding for finding in scan(source, jar))

    java.write_text("final class Safe {}", encoding="utf-8")
    write_jar(jar, expected | {"unexpected/Extra.class"})
    assert any("BTS_SECURITY_CLASS_SET" in finding for finding in scan(source, jar))

print("BTS_SECURITY_MUTATION_OK")
