# Development stage
FROM node:20-alpine AS development

WORKDIR /app

# Copy package files
COPY package.json package-lock.json ./

# Install dependencies
RUN npm ci

# Copy source code
COPY . .

# Expose port
EXPOSE 4000

# Start dev server
CMD ["npm", "run", "dev", "--", "--host", "0.0.0.0", "--port", "4000"]

# Production build stage
FROM node:20-alpine AS build

WORKDIR /app

COPY package.json package-lock.json ./
RUN npm ci

COPY . .
RUN npm run build

# Production serve stage
FROM nginx:alpine AS production

COPY --from=build /app/dist /usr/share/nginx/html

# Nginx config for SPA routing with resilient upstream resolution
RUN echo 'server { \
    listen 4000; \
    root /usr/share/nginx/html; \
    index index.html; \
    resolver 127.0.0.11 valid=30s ipv6=off; \
    set $backend_url http://backend:8000; \
    location /api { \
        proxy_pass $backend_url; \
        proxy_set_header Host $host; \
        proxy_set_header X-Real-IP $remote_addr; \
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for; \
        proxy_set_header X-Forwarded-Proto $scheme; \
    } \
    location / { \
        try_files $uri $uri/ /index.html; \
    } \
}' > /etc/nginx/conf.d/default.conf

EXPOSE 4000
CMD ["nginx", "-g", "daemon off;"]
