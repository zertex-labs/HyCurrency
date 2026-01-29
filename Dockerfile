# HyCurrency Service Dockerfile
# Build: docker build -t hycurrency-service .
# Run: docker run -p 50051:50051 hycurrency-service

FROM eclipse-temurin:21-jre-alpine

# Install netcat for healthcheck
RUN apk add --no-cache netcat-openbsd

WORKDIR /app

# Copy the pre-built shadow JAR
# Build it first with: ./gradlew :service:shadowJar
COPY service/build/libs/hycurrency-service-*-all.jar /app/service.jar

# Copy currency configuration
COPY currencies.json /app/currencies.json

# Expose gRPC port
EXPOSE 50051

# Run the service
ENTRYPOINT ["java", "-jar", "/app/service.jar"]
