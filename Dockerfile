FROM openjdk:14-alpine

RUN mkdir -p /opt/datahike-server
WORKDIR /opt/datahike-server
COPY target/datahike-server-standalone.jar .

ENTRYPOINT ["java", "-jar", "datahike-server-standalone.jar"]
