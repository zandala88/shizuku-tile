#!/usr/bin/env python3
"""Create a dedicated local release key; never include it in distributable source."""
import os
from pathlib import Path
import secrets
import shutil
import subprocess

root = Path(__file__).resolve().parent.parent
config = root / "signing.properties"
if config.exists():
    print("Release signing is already configured.")
    raise SystemExit(0)
key_dir = Path.home() / ".local/share/shizuku-tile"
key_dir.mkdir(parents=True, exist_ok=True, mode=0o700)
key = key_dir / "release.jks"
password_file = key_dir / "password"
if key.exists():
    if not password_file.exists():
        raise SystemExit("Existing key has no password file; restore its original signing.properties.")
    password = password_file.read_text().strip()
else:
    password = secrets.token_urlsafe(32)
    java_root = Path(os.environ.get("JAVA_HOME", str(Path.home() / ".cache/my-funds-tools/jdk/Contents/Home")))
    keytool = str(java_root / "bin/keytool") if (java_root / "bin/keytool").exists() else shutil.which("keytool")
    if not keytool:
        raise SystemExit("Install JDK 17 or set JAVA_HOME.")
    env = os.environ.copy()
    env["SHIZUKU_KEY_PASSWORD"] = password
    subprocess.run([keytool, "-genkeypair", "-keystore", str(key), "-alias", "shizuku-tile",
                    "-storetype", "PKCS12", "-keyalg", "RSA", "-keysize", "3072", "-validity", "10000",
                    "-dname", "CN=Shizuku Tile Local, O=Personal",
                    "-storepass:env", "SHIZUKU_KEY_PASSWORD", "-keypass:env", "SHIZUKU_KEY_PASSWORD"],
                   check=True, env=env)
    key.chmod(0o600)
    password_file.write_text(password + "\n")
    password_file.chmod(0o600)
config.write_text(f"storeFile={key}\nstorePassword={password}\nkeyAlias=shizuku-tile\nkeyPassword={password}\n")
config.chmod(0o600)
print("Dedicated release signing key prepared.")
