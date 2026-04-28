# Stage 1: Build the React app
FROM node:22-alpine AS build
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .
RUN npm run build

# Stage 2: Serve via Nginx (non-root, port 80)
FROM nginx:1.29.8-alpine
RUN apk update && apk upgrade --no-cache \
    && sed -i '/^user /d' /etc/nginx/nginx.conf \
    && chown -R 1000:1000 \
       /var/cache/nginx \
       /var/log/nginx \
       /usr/share/nginx/html \
       /etc/nginx/conf.d
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/build /usr/share/nginx/html
RUN chown -R 1000:1000 /usr/share/nginx/html
USER 1000
EXPOSE 80
HEALTHCHECK --interval=20s --timeout=5s --start-period=10s --retries=3 \
    CMD wget -qO /dev/null http://localhost:80/ || exit 1
CMD ["nginx", "-g", "daemon off;"]
