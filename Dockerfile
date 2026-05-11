FROM gradle:8.12-jdk17 AS build

WORKDIR /app

ARG GPR_USER
ARG GPR_TOKEN

COPY build.gradle settings.gradle ./
COPY gradle ./gradle

RUN GPR_USER=${GPR_USER} GPR_TOKEN=${GPR_TOKEN} gradle dependencies --no-daemon || true

COPY src ./src

RUN GPR_USER=${GPR_USER} GPR_TOKEN=${GPR_TOKEN} gradle bootJar --no-daemon -x test

FROM eclipse-temurin:17-jre

WORKDIR /app

COPY --from=build /app/build/libs/*.jar app.jar

ENTRYPOINT ["java", "-jar", "app.jar"]