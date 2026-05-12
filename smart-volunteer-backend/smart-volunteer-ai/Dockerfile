FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY target/smart-volunteer-ai-1.0-SNAPSHOT.jar app.jar
EXPOSE 9093
ENTRYPOINT ["java", "-jar", "app.jar"]
