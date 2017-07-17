#!/bin/bash

build ()
{
  # Step 1
  # Build the dev JDK 9 docker image with java and maven
  # This image could be declared as a wercker box
  # Depends on alpine image, JDK for musl and maven
  # TODO could possibly customize to reduce size of dev image
  docker build \
    -t jdk-9-alpine \
    -f jdk-9-alpine.Dockerfile .


  # Step 2
  # Depends on Step 1
  # Build the application by running maven
  #   compile src, compile tests, run tests, produce jar, copy dependent jars
  # Mounts a local maven repository to avoid downloading from maven central
  docker run --rm \
    --workdir=/jersey-netty-app \
    --volume=$HOME/.m2:/root/.m2 \
    --volume $PWD:/jersey-netty-app \
    jdk-9-alpine \
    mvn package


  # Step 3
  # Depends on Step 1 and indirectly on Step 2 (the application determines what
  # modules should be installed)
  # Build jlink'ed JDK 9 from the dev JDK 9 docker image, this contains only
  # the modules that the application depends on. Could this be a defined wercker
  # step where the set of modules are declared in the wercker.yml?
  rm -fr target/jdk-9-alpine-linked
  docker run --rm \
    --volume $PWD:/jersey-netty-app \
    jdk-9-alpine \
    jlink --module-path /opt/jdk-9/jmods \
      --add-modules java.base,java.logging,java.management,java.xml,jdk.management,jdk.unsupported \
      --strip-debug \
      --compress 2 \
      --no-header-files \
      --output /jersey-netty-app/target/jdk-9-alpine-linked


  # Step 4
  # Depends on Step 3
  # Build the jlink'ed JDK 9 docker image using jersey-netty-app/target/jdk-9-alpine-linked
  # Depends on alpine image
  docker build \
    -t jdk-9-alpine-linked \
    -f jdk-9-alpine-linked.Dockerfile .


  # Step 5
  # Depends on Step 4 and Step 2
  # Build Jersey/Netty app docker image
  # Depends on jdk-9-alpine-linked
  docker build \
    -t jersey-netty-app \
    -f jersey-netty-app.Dockerfile .

}


dev ()
{
  # dev
  # mount maven cache and project
  # compile src, compile tests, run tests, exec
  docker run --tty --interactive --rm \
   --workdir=/jersey-netty-app \
   --volume $PWD:/jersey-netty-app \
   --volume=$HOME/.m2:/root/.m2 \
   --publish 9090:8080 \
   jdk-9-alpine mvn exec:java
}

run ()
{
  # run
  docker run -it --rm \
    --publish 9090:8080 \
    jersey-netty-app
}


case $1 in
"build")
  build
  ;;
"dev")
  dev
  ;;
"run")
  run
  ;;
esac
