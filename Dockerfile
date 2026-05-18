FROM eclipse-temurin:21

WORKDIR /healthcare-backend

COPY target/*.jar healthcare-backend-1.0.0.jar

EXPOSE 8080

ENTRYPOINT ["java","-jar","healthcare-backend-1.0.0.jar"]