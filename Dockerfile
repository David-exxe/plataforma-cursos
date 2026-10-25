# Dockerfile unico para todos los microservicios:
#   docker build --build-arg MODULO=matricula-service -t matricula-service .
FROM maven:3.9-eclipse-temurin-21 AS build
ARG MODULO
WORKDIR /app
COPY . .
RUN --mount=type=cache,target=/root/.m2 mvn -B -q -pl ${MODULO} -am package -DskipTests

FROM eclipse-temurin:21-jre-alpine
ARG MODULO
WORKDIR /app
RUN addgroup -S app && adduser -S app -G app
COPY --from=build /app/${MODULO}/target/${MODULO}-*.jar app.jar
USER app
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -Duser.timezone=UTC"
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
