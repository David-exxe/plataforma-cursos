# Imagen independiente de comprobantes-service (el contexto de build es la RAIZ del repo).
#   docker build -f comprobantes-service/Dockerfile -t comprobantes-service .
# Render: Dockerfile Path = ./comprobantes-service/Dockerfile  |  Docker Context = .
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY . .
RUN mvn -B -q -pl comprobantes-service -am package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S app && adduser -S app -G app
COPY --from=build /app/comprobantes-service/target/comprobantes-service-*.jar app.jar
USER app
# Ajustado para 512 MB (plan free de Render): GC serial, pila chica, arranque mas rapido
ENV JAVA_OPTS="-XX:MaxRAMPercentage=70 -XX:+UseSerialGC -Xss512k -XX:TieredStopAtLevel=1 -Duser.timezone=UTC"
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
