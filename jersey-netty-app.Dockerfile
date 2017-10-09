# The application docker image
FROM jdk-9-alpine-linked
# Add the application jar file
ADD target/jersey-netty-app-1.0-SNAPSHOT.jar /opt/app/jersey-netty-app-1.0-SNAPSHOT.jar
# Add the application's dependent jars obtained from maven
ADD target/dependency /opt/app/dependency
# Set the command to run the application, note the use of the wildcard for
# all dependent jars
# Include experimental flag to obey cgroup memory limits for the heap
CMD java \
  -XX:+UnlockExperimentalVMOptions -XX:+UseCGroupMemoryLimitForHeap \
  -cp /opt/app/jersey-netty-app-1.0-SNAPSHOT.jar:/opt/app/dependency/* \
  app.App

