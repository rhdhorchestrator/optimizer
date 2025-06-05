FROM openjdk:17
ENV OPTIMIZER_CM_NAME=app-config
ENV OPTIMIZER_CM_NAMESPACE=default
COPY ./target/optimizer-1.0-SNAPSHOT-jar-with-dependencies.jar .
COPY ./log4j2.xml /optimizer/
ENTRYPOINT ["java", "-Dlog4j2.configurationFile=file:/optimizer/log4j2.xml", "-jar", "./optimizer-1.0-SNAPSHOT-jar-with-dependencies.jar"]