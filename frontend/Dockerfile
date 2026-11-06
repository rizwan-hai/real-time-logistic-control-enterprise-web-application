FROM node:22-alpine AS build
WORKDIR /app
COPY package*.json ./
RUN npm install --no-audit --no-fund
COPY . .
ARG API_SCHEMA_URL
ENV API_SCHEMA_URL=$API_SCHEMA_URL
ARG API_SCHEMA_FILE
ENV API_SCHEMA_FILE=$API_SCHEMA_FILE
RUN npm run build

FROM nginx:1.30.6-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
