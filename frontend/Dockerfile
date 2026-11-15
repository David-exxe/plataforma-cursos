# Etapa 1: compilar Angular
FROM node:22-alpine AS build
WORKDIR /app
COPY package*.json ./
RUN npm install --no-audit --no-fund
COPY . .
# URL pública del API Gateway que usará el navegador (no el nombre interno de Docker)
ARG API_URL=http://localhost:8080
RUN sed -i "s#https://api.tu-dominio.com#${API_URL}#" src/environments/environment.prod.ts \
 && npx ng build --configuration production

# Etapa 2: servir los archivos estáticos con fallback de SPA
FROM nginx:1.27-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist/academia-web/browser /usr/share/nginx/html
EXPOSE 80
