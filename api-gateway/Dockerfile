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
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
