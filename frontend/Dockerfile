# syntax=docker/dockerfile:1

# ---- Stage 1: build the Vue SPA ----
FROM node:20-alpine AS build
WORKDIR /app

COPY package.json package-lock.json ./
RUN npm ci

COPY . .
# In production the SPA talks to the backend through the nginx /api proxy.
ENV VITE_API_BASE_URL=/api
RUN npm run build

# ---- Stage 2: serve static assets via nginx ----
FROM nginx:alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html

EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
