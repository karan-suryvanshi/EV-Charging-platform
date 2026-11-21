"""Restore a SQL dump into an empty Compose MySQL database only.

This deliberately refuses to import over any tables. Start only the MySQL
service first; then pass the exact database name shown in the dump as the
confirmation value. It never drops a volume or existing database objects.
"""

from __future__ import annotations

import argparse
import hashlib
import re
import subprocess
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[3]
DATABASE_DDL = re.compile(
    rb"CREATE DATABASE(?:\s+/\*![0-9]+[^*]*\*/)?\s+`([^`]+)`",
    re.IGNORECASE,
)


def docker_mysql(sql: str) -> subprocess.CompletedProcess[str]:
    command = [
        "docker", "compose", "exec", "-T", "mysql", "sh", "-c",
        'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot '
        '--batch --skip-column-names -e "$1"',
        "restore-check", sql,
    ]
    return subprocess.run(
        command, cwd=ROOT, capture_output=True, text=True, check=False
    )


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("dump", type=Path, help="verified SQL dump file")
    parser.add_argument(
        "--confirm-empty-db", required=True,
        help="must exactly match the database name encoded in the dump",
    )
    args = parser.parse_args()

    dump = args.dump.resolve()
    if not dump.is_file():
        print(f"Dump file not found: {dump}", file=sys.stderr)
        return 2

    with dump.open("rb") as source:
        header = source.read(1024 * 1024)
    match = DATABASE_DDL.search(header)
    if not match:
        print("Could not find a database declaration in the dump header.", file=sys.stderr)
        return 2
    database = match.group(1).decode("utf-8")
    if args.confirm_empty_db != database:
        print("Confirmation does not match the dump database; no data was sent.", file=sys.stderr)
        return 2

    literal = database.replace("'", "''")
    check = docker_mysql(
        "SELECT COUNT(*) FROM information_schema.tables "
        f"WHERE table_schema='{literal}'"
    )
    if check.returncode:
        print("Could not inspect the Compose database. Start its MySQL service first.", file=sys.stderr)
        print(check.stderr.strip(), file=sys.stderr)
        return check.returncode
    try:
        table_count = int(check.stdout.strip())
    except ValueError:
        print("MySQL returned an unreadable table count; no data was sent.", file=sys.stderr)
        return 2
    if table_count:
        print(
            f"Refusing restore: {database} already has {table_count} tables. "
            "No dump data was sent.",
            file=sys.stderr,
        )
        return 2

    digest = hashlib.sha256(dump.read_bytes()).hexdigest()
    print(f"Restoring verified dump into empty database {database}; SHA256={digest}")
    with dump.open("rb") as source:
        restore = subprocess.run(
            [
                "docker", "compose", "exec", "-T", "mysql", "sh", "-c",
                'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot',
            ],
            cwd=ROOT,
            stdin=source,
            capture_output=True,
            check=False,
        )
    if restore.returncode:
        print(
            "Restore failed after streaming began. The destination may be partial; "
            "do not retry over it. Preserve logs and inspect this disposable database.",
            file=sys.stderr,
        )
        print(restore.stderr.decode(errors="replace")[-4000:], file=sys.stderr)
        return restore.returncode
    print("Restore command completed. Verify row counts before starting the backend.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
