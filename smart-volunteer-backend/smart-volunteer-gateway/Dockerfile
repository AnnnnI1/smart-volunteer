FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY target/smart-volunteer-gateway-1.0-SNAPSHOT.jar app.jar
EXPOSE 9090
ENTRYPOINT ["java", "-jar", "app.jar"]
