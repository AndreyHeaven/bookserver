FROM openjdk:17-slim-buster
LABEL maintainer=butt
WORKDIR /app
COPY build/libs/bookserver-*.jar ./app.jar
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
EXPOSE 8080
