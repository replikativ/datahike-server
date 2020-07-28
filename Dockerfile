FROM clojure:openjdk-14-lein AS builder

WORKDIR /opt
COPY . .

RUN lein uberjar


FROM openjdk:15-alpine

RUN mkdir -p /opt/datahike-server/resources
WORKDIR /opt/datahike-server
COPY --from=builder /opt/target/datahike-server-standalone.jar .

ENTRYPOINT ["java", "-jar", "datahike-server-standalone.jar"]
