# ---------- build stage ----------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# сначала зависимости
COPY pom.xml .
RUN mvn -q -DskipTests dependency:go-offline

# потом исходники
COPY src ./src
RUN mvn -q -DskipTests package

# ---------- run stage ----------
FROM eclipse-temurin:21-jre
WORKDIR /app

# приложение
COPY --from=build /app/target/*.jar /app/app.jar

CMD ["java","-jar","/app/app.jar"]