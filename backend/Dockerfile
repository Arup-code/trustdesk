# backend/Dockerfile
#
# NOTE: the project pins Gradle 9.5.1 via gradle/wrapper/gradle-wrapper.properties,
# not the 8.x bundled with the `gradle:8-jdk21` image. We build with a plain JDK 21
# image and invoke the project's own Gradle wrapper (./gradlew) so the correct
# Gradle version (9.5.1) is downloaded by the wrapper at build time.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY . .
RUN chmod +x ./gradlew && ./gradlew bootJar --no-daemon -x test

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
