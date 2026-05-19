FROM maven:3.9.7-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
COPY call-common/pom.xml call-common/pom.xml
COPY call-ingestion/pom.xml call-ingestion/pom.xml
COPY call-search/pom.xml call-search/pom.xml
COPY call-ops/pom.xml call-ops/pom.xml
COPY call-common/src call-common/src
COPY call-ingestion/src call-ingestion/src
COPY call-search/src call-search/src
COPY call-ops/src call-ops/src
RUN mvn -pl call-ingestion -am clean package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/call-ingestion/target/call-ingestion-1.0.0-SNAPSHOT.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
