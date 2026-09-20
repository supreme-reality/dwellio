# Build context: repository root
# docker build -f apps/worker/Dockerfile -t dwellio-worker .
# Set WORKER_ROLE=billing|invoice at runtime (ECS task definition).

FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace

COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle ./gradle
COPY apps/api ./apps/api
COPY apps/worker ./apps/worker

RUN chmod +x gradlew \
  && ./gradlew :apps:worker:bootJar -x test --no-daemon \
  && find apps/worker/build/libs -name '*.jar' ! -name '*-plain.jar' -exec cp {} /workspace/app.jar \;

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S dwellio && adduser -S dwellio -G dwellio
USER dwellio

COPY --from=build /workspace/app.jar /app/app.jar

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
