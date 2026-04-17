FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY target/smart-volunteer-user-1.0-SNAPSHOT.jar app.jar
EXPOSE 9091
ENTRYPOINT ["java", "-jar", "app.jar"]
