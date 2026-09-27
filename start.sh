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
APP_PID=$!

# Don't let nginx start accepting public traffic until BOTH internal ports
# are actually accepting connections. Without this, nginx binds to $PORT
# almost instantly while Spring Boot + JPA + the embedded netty-socketio
# server are still booting (worse after a cold start on Render's free
# tier), so early requests -- especially the app's socket.io reconnect --
# get proxied to nothing and come back as 502.
echo "Waiting for app to be ready on :4000 and :4001..."
for i in $(seq 1 300); do
    if curl -fsS http://127.0.0.1:4000/health >/dev/null 2>&1 && \
       (curl -s -o /dev/null -w '%{http_code}' http://127.0.0.1:4001/socket.io/ | grep -qE '^(200|400)$'); then
        echo "App is ready."
        break
    fi
    # If the JVM died during startup, fail fast instead of looping for 300s.
    if ! kill -0 "$APP_PID" 2>/dev/null; then
        echo "App process exited during startup." >&2
        exit 1
    fi
    sleep 1
done

if ! curl -fsS http://127.0.0.1:4000/health >/dev/null 2>&1; then
    echo "WARNING: app still not confirmed ready after timeout -- starting nginx anyway." >&2
fi

PORT=$PUBLIC_PORT envsubst '${PORT}' < /etc/nginx/nginx.conf.template > /etc/nginx/nginx.conf
exec nginx -g 'daemon off;'
