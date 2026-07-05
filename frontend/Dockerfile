# ---- Etapa 1: build de la SPA con Node ----
FROM node:22-alpine AS build
WORKDIR /app
COPY package*.json ./
RUN npm install
COPY . .
# URL del API Gateway vista DESDE EL NAVEGADOR del usuario (no desde el contenedor).
ARG VITE_GATEWAY_URL=http://localhost:8080
ENV VITE_GATEWAY_URL=$VITE_GATEWAY_URL
RUN npm run build

# ---- Etapa 2: nginx sirviendo los estáticos ----
FROM nginx:alpine
COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
