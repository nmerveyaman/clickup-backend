# 1. Aşama: Build
FROM node:22-alpine AS builder
WORKDIR /app
RUN npm install -g pnpm
COPY package.json pnpm-lock.yaml* ./
RUN pnpm install --frozen-lockfile || pnpm install
COPY . .
# Docker build'da API_BASE_URL boş bırakılır; nginx proxy yönlendirir
ARG VITE_API_BASE_URL=""
ENV VITE_API_BASE_URL=$VITE_API_BASE_URL
RUN pnpm build

# 2. Aşama: Production (Nginx)
FROM nginx:alpine
COPY --from=builder /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
