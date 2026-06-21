FROM eclipse-temurin:17-jre

WORKDIR /app

COPY target/my-app-1.0.jar app.jar

ENTRYPOINT ["top", "-b"]