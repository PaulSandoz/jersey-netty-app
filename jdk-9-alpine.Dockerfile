# The JDK 9 development image for building
FROM alpine:3.6
# Add the musl-based JDK 9 distribution
RUN apk update \
  && apk add wget
RUN mkdir /opt
RUN wget -q http://download.java.net/java/jdk9-alpine/archive/177/binaries/jdk-9-ea+177_linux-x64-musl_bin.tar.gz
RUN tar -x -f jdk-9-ea+177_linux-x64-musl_bin.tar.gz -C /opt
RUN rm jdk-9-ea+177_linux-x64-musl_bin.tar.gz
# Add maven
RUN wget -q http://apache.claz.org/maven/maven-3/3.5.0/binaries/apache-maven-3.5.0-bin.tar.gz
RUN tar -x -f apache-maven-3.5.0-bin.tar.gz -C /opt
RUN rm apache-maven-3.5.0-bin.tar.gz
# Set up env variables
ENV JAVA_HOME=/opt/jdk-9
ENV MAVEN_HOME=/opt/apache-maven-3.5.0
ENV PATH=$PATH:$JAVA_HOME/bin:$MAVEN_HOME/bin
CMD ["/opt/jdk-9/bin/java", "-version"]