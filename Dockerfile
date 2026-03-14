FROM gradle:8.14.3-jdk21 AS build
WORKDIR /app
COPY . .
RUN gradle --no-daemon clean buildFatJar

FROM eclipse-temurin:21-jre
WORKDIR /app

COPY --from=build /app/build/libs/*-all.jar /app/app.jar
COPY --from=build /app/visualdiff /app/visualdiff

RUN mkdir -p /data/assets /data/runs

EXPOSE 8080

CMD ["java", "-jar", "/app/app.jar"]
