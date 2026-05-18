FROM maven:3.9.6-eclipse-temurin-21 AS build

WORKDIR /healthcare-backend

COPY . .

RUN mvn clean package -DskipTests

FROM eclipse-temurin:21

WORKDIR /healthcare-backend

COPY --from=build /healthcare-backend/target/*.jar healthcare-backend-1.0.0.jar

EXPOSE 8080

ENTRYPOINT ["java","-jar","healthcare-backend-1.0.0.jar"]