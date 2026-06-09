FROM maven:3.9.7-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
COPY call-persistence/pom.xml call-persistence/pom.xml
COPY call-ingestion/pom.xml call-ingestion/pom.xml
COPY call-search/pom.xml call-search/pom.xml
COPY call-task/pom.xml call-task/pom.xml
COPY call-persistence/src call-persistence/src
COPY call-ingestion/src call-ingestion/src
COPY call-search/src call-search/src
COPY call-task/src call-task/src
RUN mvn -pl call-task -am clean package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/call-task/target/call-task-1.0.0-SNAPSHOT.jar app.jar
EXPOSE 8084
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
