#!/bin/sh
set -e

# /app/data is a declared volume, so on a bind mount it arrives owned by whoever owns the
# host directory - typically not the unprivileged account this image runs as. Only root can
# repair that, so the container starts as root, fixes the ownership (recursively - a failed
# earlier run can leave files behind) and immediately drops to the service account. Anyone
# who pins a user themselves (docker run --user) skips to the exec below and keeps it.
if [ "$(id -u)" = "0" ]; then
    chown -R prioritize:prioritize /app/data
    exec setpriv --reuid=prioritize --regid=prioritize --init-groups "$@"
fi

exec "$@"
