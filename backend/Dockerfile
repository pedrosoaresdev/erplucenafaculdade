# Build
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B -DskipTests package

# Execução
FROM eclipse-temurin:21-jre
RUN useradd --system --uid 10001 erp
WORKDIR /app
COPY --from=build /src/target/erp-universitario-*.jar app.jar
USER erp
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
