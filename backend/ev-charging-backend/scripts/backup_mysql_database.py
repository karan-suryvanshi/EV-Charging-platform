"""Create a binary-safe MySQL dump using DB_* environment variables."""

from __future__ import annotations

import argparse
import hashlib
import os
import re
import subprocess
import sys
from pathlib import Path


JDBC_URL = re.compile(r"^jdbc:mysql://([^:/?]+)(?::(\d+))?/([^?]+)")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("output", type=Path, help="new .sql dump path")
    args = parser.parse_args()

    url = os.environ.get("DB_URL", "")
    match = JDBC_URL.match(url)
    username = os.environ.get("DB_USERNAME")
    password = os.environ.get("DB_PASSWORD")
    if not match or not username or not password:
        print("Set DB_URL, DB_USERNAME and DB_PASSWORD in the environment.", file=sys.stderr)
        return 2

    output = args.output.resolve()
    if output.exists():
        print(f"Refusing to overwrite existing dump: {output}", file=sys.stderr)
        return 2
    output.parent.mkdir(parents=True, exist_ok=True)

    env = os.environ.copy()
    env["MYSQL_PWD"] = password
    command = [
        "mysqldump", "--single-transaction", "--routines", "--triggers",
        "--databases", match.group(3), "--host", match.group(1),
        "--port", match.group(2) or "3306", "--user", username,
    ]
    created = False
    try:
        with output.open("xb") as destination:
            created = True
            result = subprocess.run(
                command, stdout=destination, stderr=subprocess.PIPE,
                env=env, check=False,
            )
        if result.returncode:
            raise RuntimeError(result.stderr.decode(errors="replace")[-3000:])
        digest = hashlib.sha256()
        size = 0
        with output.open("rb") as source:
            while chunk := source.read(1024 * 1024):
                digest.update(chunk)
                size += len(chunk)
            source.seek(max(0, size - 8192))
            tail = source.read().decode("utf-8", errors="replace")
        if not size or "Dump completed" not in tail:
            raise RuntimeError("Dump footer missing; backup integrity check failed.")
    except (OSError, RuntimeError) as error:
        if created:
            output.unlink(missing_ok=True)
        print(f"Backup failed: {error}", file=sys.stderr)
        return 1

    digest_hex = digest.hexdigest()
    print(f"Backup completed: {output}")
    print(f"Bytes: {size}")
    print(f"SHA256: {digest_hex}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
