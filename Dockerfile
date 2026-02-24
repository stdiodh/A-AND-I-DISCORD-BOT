FROM eclipse-temurin:21-jdk AS builder
WORKDIR /workspace

COPY gradlew gradlew
COPY gradle gradle
COPY settings.gradle.kts build.gradle.kts ./
RUN chmod +x ./gradlew

COPY src src
RUN ./gradlew --no-daemon clean bootJar
RUN cp "$(ls -1 build/libs/*.jar | grep -v 'plain' | head -n 1)" build/libs/app.jar

FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app

COPY --from=builder /workspace/build/libs/app.jar /app/app.jar

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
