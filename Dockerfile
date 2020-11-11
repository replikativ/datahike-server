FROM openjdk:16-alpine

RUN mkdir -p /opt/datahike-server
WORKDIR /opt/datahike-server
COPY target/datahike-server-standalone.jar .

EXPOSE 3000

ENTRYPOINT ["java", "-jar", "datahike-server-standalone.jar"]
