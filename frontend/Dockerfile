# Stage 1: Build
FROM node:20-alpine AS build
WORKDIR /app

COPY package.json package-lock.json ./
RUN npm ci --frozen-lockfile --network-timeout=120000 --maxsockets=5 || \
    (sleep 5 && npm ci --frozen-lockfile --network-timeout=120000 --maxsockets=5)

COPY . .
RUN npm run build

# Stage 2: Serve
FROM nginx:1.25-alpine
COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx/default.conf /etc/nginx/conf.d/default.conf

EXPOSE 80

# Health = static SPA AND the gateway upstream (a 502 from a stale gateway IP must
# turn this container UNHEALTHY — before 2026-07-12 it stayed "healthy" while every
# /api call failed). /healthz/upstream proxies to the gateway's REAL health path
# (/actuator/health, not /api/actuator/health — the gateway does not serve under
# /api itself) through the same resolver+variable proxy_pass as /api/, so a dead
# upstream IP fails this probe exactly like it fails real traffic.
HEALTHCHECK --interval=30s --timeout=5s \
  CMD wget -qO- http://127.0.0.1/index.html >/dev/null 2>&1 && \
      wget -q --spider http://127.0.0.1/healthz/upstream || exit 1

CMD ["nginx", "-g", "daemon off;"]
