FROM eclipse-temurin:21-jdk-jammy AS build

WORKDIR /workspace

COPY .mvn .mvn
COPY mvnw pom.xml ./

RUN chmod +x mvnw \
    && ./mvnw -B -ntp dependency:go-offline

COPY src src

RUN ./mvnw -B -ntp clean package -DskipTests


FROM eclipse-temurin:21-jre-jammy

WORKDIR /app

RUN groupadd --system app \
    && useradd \
        --system \
        --gid app \
        --no-create-home \
        app

COPY --from=build \
    --chown=app:app \
    /workspace/target/*.jar \
    app.jar

USER app

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
