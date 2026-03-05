#!/bin/bash
set -e

# Fix ownership for mounted volumes
chown -R appuser:appgroup /app/uploads

# Drop privileges and run as appuser
exec gosu appuser java ${JAVA_OPTS:-} -jar app.jar "$@"
