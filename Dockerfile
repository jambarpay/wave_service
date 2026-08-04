FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /app

COPY pom.xml .
COPY src ./src

RUN mvn -B -ntp -DskipTests package

FROM eclipse-temurin:17-jre
WORKDIR /app

COPY --from=build /app/target/wave-service-3.4.3.jar app.jar

EXPOSE 8088

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
