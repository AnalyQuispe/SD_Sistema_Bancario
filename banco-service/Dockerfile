# ---- Etapa 1: build con Maven + JDK 21 ----
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
# Cachea dependencias: primero el pom y el wrapper, luego el código.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN ./mvnw -B dependency:go-offline
COPY src/ src/
RUN ./mvnw -B clean package -DskipTests

# ---- Etapa 2: imagen de ejecución (solo JRE) ----
FROM eclipse-temurin:21-jre
WORKDIR /app
# El perfil (bancoA/B/C) se inyecta con SPRING_PROFILES_ACTIVE desde docker-compose.
COPY --from=build /app/target/*.jar app.jar
# Carpeta de datos persistente del banco (montada como volumen).
ENV BANCO_DATA_DIR=/app/data
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
