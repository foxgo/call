FROM node:20-alpine AS build
WORKDIR /app
COPY call-iam-web/package.json call-iam-web/package-lock.json ./
RUN npm ci
COPY call-iam-web/index.html .
COPY call-iam-web/tsconfig.json .
COPY call-iam-web/vite.config.ts .
COPY call-iam-web/src ./src
RUN npm run build

FROM nginx:1.27-alpine
COPY call-iam-web/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
