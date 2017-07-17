# Make a docker image with a jlink'ed JDK 9
FROM alpine:3.6
# Add jlink'ed JDK 9
ADD target/jdk-9-alpine-linked /opt/jdk-9
ENV JAVA_HOME=/opt/jdk-9
ENV PATH=$PATH:$JAVA_HOME/bin
CMD ["/opt/jdk-9/bin/java", "-version"]