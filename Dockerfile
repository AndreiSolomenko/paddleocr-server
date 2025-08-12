FROM maven:3.8.3-openjdk-17 AS build
COPY . .
RUN mvn clean package -DskipTests

FROM openjdk:17.0.2-jdk-slim

# Install the necessary dependencies for Tesseract
RUN apt-get update && apt-get install -y \
    tesseract-ocr \
    && rm -rf /var/lib/apt/lists/*

COPY --from=build /target/highlight-server-spring-0.0.1-SNAPSHOT.jar highlightserverspring.jar

RUN mkdir -p /usr/share/tesseract-ocr/4.00/tessdata
COPY --from=build /target/classes/tessdata /usr/share/tesseract-ocr/4.00/tessdata

EXPOSE 8080
ENTRYPOINT ["java","-jar","highlightserverspring.jar"]
