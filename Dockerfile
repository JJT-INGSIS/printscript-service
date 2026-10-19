# syntax=docker/dockerfile:1

FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

COPY gradlew build.gradle.kts settings.gradle.kts ./
COPY gradle ./gradle
COPY src/main ./src/main

RUN --mount=type=cache,target=/root/.gradle \
    --mount=type=secret,id=github_actor,env=GITHUB_ACTOR,required=true \
    --mount=type=secret,id=github_token,env=GITHUB_TOKEN,required=true \
    sh ./gradlew bootJar --no-daemon

FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app

COPY --from=build /app/build/libs/app.jar app.jar

USER 10001:10001
EXPOSE 8080
# SNI-25 dev-only rollback regression drill. Revert after testing v0.5.1.
ENTRYPOINT ["/bin/sh", "-c", "echo 'SNI-25 controlled startup failure for dev rollback verification' >&2; exit 42"]
