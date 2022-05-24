FROM openjdk:11
RUN mkdir outputLogs
COPY keys /keys
COPY scripts /scripts
COPY target/BlockmessLib.jar /target/BlockmessLib.jar
COPY config /config
ENTRYPOINT ["java", "-cp", "target/BlockmessLib.jar"]
CMD ["demo.counter.AsyncCounter", "10", "100"]
