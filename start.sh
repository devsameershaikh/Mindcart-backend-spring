#!/bin/sh
set -e

# The host (Render/Railway/etc.) tells us which port is actually public.
# nginx binds to that one; the Spring Boot app itself always uses fixed
# internal ports that nothing outside the container ever talks to directly.
PUBLIC_PORT=${PORT:-4000}

export PORT=4000
export SOCKETIO_PORT=4001
export SOCKETIO_ENABLED=true

java -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -jar app.jar &

PORT=$PUBLIC_PORT envsubst '${PORT}' < /etc/nginx/nginx.conf.template > /etc/nginx/nginx.conf
exec nginx -g 'daemon off;'
