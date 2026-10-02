# Single image: builds the PWA, bundles it into the Spring Boot jar, serves both on port 8080.
#   docker build -t math-mission . && docker run -p 8080:8080 math-mission   →  http://localhost:8080
FROM node:22-alpine AS web
WORKDIR /web
COPY frontend/package*.json ./
RUN npm install --no-audit --no-fund
COPY frontend/ ./
RUN npm run build

FROM maven:3.9-eclipse-temurin-21 AS api
WORKDIR /src
COPY backend/pom.xml .
RUN mvn -q -B dependency:go-offline
COPY backend/src src
COPY --from=web /web/dist src/main/resources/static
RUN mvn -q -B package -DskipTests

FROM eclipse-temurin:21-jre
RUN useradd --system --uid 10001 app && mkdir /data && chown app /data
USER app
WORKDIR /data
COPY --from=api /src/target/math-mission-backend-*.jar /app/app.jar
# Default: zero-setup demo (H2 file DB in /data, demo content + demo accounts).
# For real use set SPRING_PROFILES_ACTIVE=postgres and DB_URL/DB_USER/DB_PASSWORD.
ENV SPRING_PROFILES_ACTIVE=dev PORT=8080
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java -XX:MaxRAMPercentage=75 -Dserver.port=${PORT} -jar /app/app.jar"]
