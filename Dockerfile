FROM eclipse-temurin:17-jdk AS build
WORKDIR /app

ARG GPR_USER
ARG GPR_TOKEN

COPY gradlew .
COPY gradle gradle
COPY build.gradle .
COPY settings.gradle .

RUN chmod +x gradlew

RUN ./gradlew dependencies --no-daemon \
    -PGPR_USER=${GPR_USER} \
    -PGPR_TOKEN=${GPR_TOKEN} || true

COPY src src

RUN ./gradlew bootJar -x test --no-daemon \
    -PGPR_USER=${GPR_USER} \
    -PGPR_TOKEN=${GPR_TOKEN}

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]