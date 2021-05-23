FROM gcr.io/distroless/java:11

COPY target/datahike-server-standalone.jar /

EXPOSE 3000

CMD ["/datahike-server-standalone.jar"]
