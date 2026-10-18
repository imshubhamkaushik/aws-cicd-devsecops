FROM nginx:stable-alpine3.24

# Run nginx unprivileged. The Helm deployment mounts writable emptyDir volumes
# for nginx cache and tmp paths because the container root filesystem is
# read-only in EKS. /var/log/nginx is deliberately NOT mounted: the image links
# access.log/error.log to stdout/stderr, which is how logs reach `kubectl logs`.
RUN apk update && apk upgrade --no-cache \
    && sed -i '/^user /d' /etc/nginx/nginx.conf \
    && sed -i 's|^pid .*|pid /tmp/nginx.pid;|' /etc/nginx/nginx.conf \
    && chown -R 1000:1000 \
       /var/cache/nginx \
       /var/log/nginx \
       /etc/nginx/conf.d
COPY nginx.conf /etc/nginx/conf.d/default.conf
USER 1000
EXPOSE 11000 8081
HEALTHCHECK --interval=20s --timeout=5s --start-period=10s --retries=3 \
    CMD wget -qO /dev/null http://127.0.0.1:11000/gateway/health || exit 1
CMD ["nginx", "-g", "daemon off;"]
