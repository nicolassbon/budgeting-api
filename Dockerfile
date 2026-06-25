# syntax=docker/dockerfile:1.7

FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace

COPY gradlew settings.gradle build.gradle ./
COPY gradle/ gradle/
RUN chmod +x gradlew

COPY src/ src/
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew bootJar -x test --no-daemon

FROM eclipse-temurin:25-jre AS layers
WORKDIR /workspace
COPY --from=build /workspace/build/libs/*.jar app.jar
RUN java -Djarmode=layertools -jar app.jar extract --destination extracted

FROM eclipse-temurin:25-jre
WORKDIR /app

RUN groupadd --system java && useradd --system --gid java appuser

COPY --from=layers /workspace/extracted/dependencies/ ./
COPY --from=layers /workspace/extracted/spring-boot-loader/ ./
COPY --from=layers /workspace/extracted/snapshot-dependencies/ ./
COPY --from=layers /workspace/extracted/application/ ./

USER appuser

EXPOSE 8080

ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]