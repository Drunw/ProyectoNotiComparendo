FROM registry.access.redhat.com/ubi8/openjdk-17:1.13 AS build
USER root
WORKDIR /app
COPY . .
RUN mvn clean package -Dquarkus.package.type=uber-jar

FROM registry.access.redhat.com/ubi8/openjdk-17:1.13
WORKDIR /app
COPY --from=build /app/target/*-runner.jar /app/app.jar
CMD ["java","-Dquarkus.http.port=8080" ,"-jar", "app.jar"]