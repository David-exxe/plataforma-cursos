# Imagen independiente de matricula-service (el contexto de build es la RAIZ del repo).
#   docker build -f matricula-service/Dockerfile -t matricula-service .
# Render: Dockerfile Path = ./matricula-service/Dockerfile  |  Docker Context = .
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY . .
RUN mvn -B -q -pl matricula-service -am package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S app && adduser -S app -G app
COPY --from=build /app/matricula-service/target/matricula-service-*.jar app.jar
USER app
# Ajustado para 512 MB (plan free de Render): GC serial, pila chica, arranque mas rapido
ENV JAVA_OPTS="-XX:MaxRAMPercentage=70 -XX:+UseSerialGC -Xss512k -XX:TieredStopAtLevel=1 -Duser.timezone=UTC"
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
