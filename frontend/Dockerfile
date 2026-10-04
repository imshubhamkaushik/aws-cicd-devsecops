# Stage 1: Build the React app
FROM node:24-alpine AS build
WORKDIR /app
COPY package*.json ./
# BuildKit cache mount: npm's download cache survives between builds (build-time only,
# never part of the image).
RUN --mount=type=cache,id=catalogix-npm-frontend,target=/root/.npm \
    npm ci
COPY . .
RUN npm run build

# Stage 2: Serve via Nginx (non-root, port 11001)
FROM nginx:stable-alpine3.24
RUN apk update && apk upgrade --no-cache \
    && sed -i '/^user[[:space:]]/d' /etc/nginx/nginx.conf \
    && sed -i 's|^pid[[:space:]].*;|pid /tmp/nginx.pid;|' /etc/nginx/nginx.conf \
    && chown -R 1000:1000 \
       /var/cache/nginx \
       /var/log/nginx \
       /usr/share/nginx/html \
       /etc/nginx/conf.d
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/build /usr/share/nginx/html
RUN chown -R 1000:1000 /usr/share/nginx/html
USER 1000
EXPOSE 11001
HEALTHCHECK --interval=20s --timeout=5s --start-period=10s --retries=3 \
    CMD wget -qO /dev/null http://127.0.0.1:11001/ || exit 1
CMD ["nginx", "-g", "daemon off;"]
