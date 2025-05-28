# Fase 1: Build
FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn clean package -DskipTests

# Fase 2: Imagem Final
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
ARG JAR_FILE=target/*.jar
COPY --from=builder /app/${JAR_FILE} app.jar
EXPOSE 8080
# Habilitar Virtual Threads para o Tomcat embutido (Spring Boot >= 3.2)
ENV SPRING_THREADS_VIRTUAL_ENABLED=true
ENTRYPOINT ["java", "-jar", "app.jar"]