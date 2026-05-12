FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY target/smart-volunteer-activity-1.0-SNAPSHOT.jar app.jar
EXPOSE 9092
ENTRYPOINT ["java", "-jar", "app.jar"]
