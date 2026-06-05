FROM maven:3.9.7-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
COPY call-common/pom.xml call-common/pom.xml
COPY call-iam/pom.xml call-iam/pom.xml
COPY call-common/src call-common/src
COPY call-iam/src call-iam/src
RUN mvn -pl call-iam -am clean package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/call-iam/target/call-iam-1.0.0-SNAPSHOT.jar app.jar
EXPOSE 8085
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
