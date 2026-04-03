FROM maven:3.9.9-eclipse-temurin-21 AS build

WORKDIR /workspace
ENV TRAVEL_AGENT_SKIP_PROJECT_JDK=true

COPY .mvn .mvn
COPY mvnw mvnw.cmd pom.xml ./
COPY travel-agent-types/pom.xml travel-agent-types/pom.xml
COPY travel-agent-domain/pom.xml travel-agent-domain/pom.xml
COPY travel-agent-amap/pom.xml travel-agent-amap/pom.xml
COPY travel-agent-infrastructure/pom.xml travel-agent-infrastructure/pom.xml
COPY travel-agent-app/pom.xml travel-agent-app/pom.xml
COPY travel-agent-amap-mcp-server/pom.xml travel-agent-amap-mcp-server/pom.xml

COPY travel-agent-types/src travel-agent-types/src
COPY travel-agent-domain/src travel-agent-domain/src
COPY travel-agent-amap/src travel-agent-amap/src
COPY travel-agent-infrastructure/src travel-agent-infrastructure/src
COPY travel-agent-app/src travel-agent-app/src

RUN chmod +x mvnw
RUN ./mvnw -pl travel-agent-app -am -DskipTests package

FROM eclipse-temurin:21-jre

WORKDIR /app
COPY --from=build /workspace/travel-agent-app/target/travel-agent-app.jar /app/app.jar
RUN mkdir -p /app/data

EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/app.jar"]
