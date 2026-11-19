FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY . .
RUN chmod +x gradlew && ./gradlew bootJar --no-daemon

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN groupadd --system murise && useradd --system --gid murise murise
COPY --from=build /workspace/build/libs/*-SNAPSHOT.jar /app/app.jar
USER murise
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
