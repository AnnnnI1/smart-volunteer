FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY target/smart-volunteer-credit-1.0-SNAPSHOT.jar app.jar
EXPOSE 9095
ENTRYPOINT ["java", "-jar", "app.jar"]
