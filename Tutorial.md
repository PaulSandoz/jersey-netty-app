# Kubernetes and minikube tutorial
### Paul Sandoz, version 0.1, the Braque edition

[Kubernetes][kubernetes] is a platform for automating deployment, scaling and
operations of application containers.  Kubernetes is a container control system
that maintains a desired state of container deployments. It is predominantly
used with [Docker][docker] containers but is agnostic to the container
technology.

In this tutorial you will learn how to:

- run a Kubernetes cluster on a local machine using [minikube][minikube], a tool
  that makes it easy to run Kubernetes locally on a laptop.

- deploy a simple Java 9-based web application, contained within a Docker image
  (based off Alpine linux using a custom JDK 9 image), to a running Kubernetes
  cluster.

- understand how Kubernetes interacts with instances of the application, termed
  pods.  For example, such as how Kubernetes determines whether the application
  is alive and ready to receive requests.

- scale the application.

- expose application and JVM metrics that can be consumed by
  [Prometheus][prometheus], a system that records time-series data (telemetry)
  for monitoring and alerting.

- deploy Prometheus to the Kubernetes cluster using [helm][helm], a tool for
  managing pre-configured Kubernetes packages referred to as charts.

- configure the Prometheus application to automatically receive metrics from the
  application pods.

- deploy [Grafana][grafana] to the Kubernetes cluster using helm.  Grafana is
  a tool for querying, rendering and altering on time-series data that can
  accept data stored and exposed by Prometheus.

- connect the Grafana application to the Prometheus application.  Query and
  render metrics from the application pods.

After following this tutorial you should have a good basic understanding of
Kubernetes, how to deploy Java applications and how to monitor those
applications.  More importantly it should give an appreciation of how Java is
used in cloud environments and how it can be improved.

This tutorial will not explain how to set up a continuous delivery pipeline to
automatically build and deploy the application to a Kubernetes cluster.  (See
Oracle's recent acquisition of [Wercker][wercker].)  Nor explain how to perform
rolling updates or rollbacks of the application.

[kubernetes]: https://kubernetes.io/docs/concepts/overview/what-is-kubernetes/
[docker]: https://www.docker.com/what-docker
[minikube]: https://github.com/kubernetes/minikube
[prometheus]: https://prometheus.io/
[helm]: https://github.com/kubernetes/helm
[grafana]: https://grafana.com/
[wercker]: http://blog.wercker.com/oracle

### Acknowledgements

Thanks to Daniel Fuchs for test driving this tutorial and providing early
feedback.

## Prerequisites on Unix-based systems

This tutorial is dependent on running with minikube, if another Kubernetes
execution mechanism is chosen some aspects may differ, especially those around
obtaining IP addresses to interact with the Kubernetes cluster.

### On Mac OS X

It is preferable to use a Mac system with 16GB of memory.  It is possible to use
a Mac system with 8GB of memory but expect much swapping of memory, especially
if an IDE such as IntelliJ is also running.

Minikube will run Kubernetes within a linux-based virtual machine on the host
Mac system.  In turn minikube will require [Docker Machine][libmachine] running
within the same virtual machine to manage it's own execution, and furthermore
this will be required so we can create our Docker images that Kubernetes will
deploy as Docker containers.

We shall choose the [xhyve][xhyve] Hypervisor as our virtual machine platform
and install both Docker Machine and xhyve using the
[libmachine driver plugin][docker-xhyve] for xhyve. (xhyve is considered lighter
weight than VirtualBox.)

(Note: this area is rapidly changing, see the recent announcement of
[LinuxKit][linuxkit], which over time is likely improve the experience of
integrating Kubernetes, Docker and linux-based virtual machines running on a
host system.)

OS X 10.10.3 Yosemite or higher is required for xhyve (it may be possible to use
an older version with VirtualBox but this has not been tested).

Use the [brew][brew] packaging tool (Homebrew) to install the Docker Machine
driver plugin for xhyve:

    brew install docker-machine-driver-xhyve

    # docker-machine-driver-xhyve need root owner and uid
    sudo chown root:wheel $(brew --prefix)/opt/docker-machine-driver-xhyve/bin/docker-machine-driver-xhyve
    sudo chmod u+s $(brew --prefix)/opt/docker-machine-driver-xhyve/bin/docker-machine-driver-xhyve

Use brew to install minikube, the kubernetes command line tool (kubectl), helm,
and wrk (a HTTP benchmarking tool):

    brew cask install minikube
    brew install kubernetes-cli
    brew install kubernetes-helm
    brew wrk

Executables will be placed in `/usr/local/bin`.

Verify that minikube and docker can execute, such as follows:

    $ minikube version
    minikube version: v0.20.0
    $ docker --version
    Docker version 17.06.0-ce, build 02c1d87

[brew]: https://brew.sh/
[libmachine]: https://docs.docker.com/machine/overview/
[xhyve]: https://github.com/mist64/xhyve
[docker-xhyve]: https://github.com/zchee/docker-machine-driver-xhyve
[openconnect]: http://www.infradead.org/openconnect/manual.html
[linuxkit]: https://github.com/linuxkit/linuxkit

## Running Kubernetes with minikube

Start minikube using the xhyve virtual machine:

    minikube start --vm-driver=xhyve

On first execution you should see output like this:

    $ minikube start --vm-driver=xhyve
    Starting local Kubernetes v1.6.4 cluster...
    Starting VM...
    Downloading Minikube ISO
     90.95 MB / 90.95 MB [==============================================] 100.00% 0s
    Moving files into cluster...
    Setting up certs...
    Starting cluster components...
    Connecting to cluster...
    Setting up kubeconfig...
    Kubectl is now configured to use the cluster.

The running Kubernetes cluster will have a unique IP address that may change
on each execution:

    $ minikube ip
    192.168.64.17

This is the IP address that will be used to externally communicate will all
services hosted in the Kubernetes cluster.

It is possible to SSH into the virtual machine and then query the docker images
and running containers:

    $ minikube ssh
    $ docker images
    $ docker images
    REPOSITORY                                             TAG                 IMAGE ID            CREATED             SIZE
    gcr.io/google_containers/kubernetes-dashboard-amd64    v1.6.1              71dfe833ce74        8 weeks ago         134.4 MB
    gcr.io/google_containers/k8s-dns-sidecar-amd64         1.14.2              7c4034e4ffa4        9 weeks ago         44.5 MB
    gcr.io/google_containers/k8s-dns-kube-dns-amd64        1.14.2              ca8759c215c9        9 weeks ago         52.36 MB
    gcr.io/google_containers/k8s-dns-dnsmasq-nanny-amd64   1.14.2              e5c335701995        9 weeks ago         44.84 MB
    gcr.io/google-containers/kube-addon-manager            v6.4-beta.1         85809f318123        4 months ago        127.2 MB
    gcr.io/google_containers/pause-amd64                   3.0                 99e59f495ffa        14 months ago       746.9 kB
    $
    $ docker ps
    CONTAINER ID        IMAGE                                      COMMAND                  CREATED              STATUS              PORTS               NAMES
    c468c84de08b        7c4034e4ffa4                               "/sidecar --v=2 --log"   About a minute ago   Up About a minute                       k8s_sidecar_kube-dns-1301475494-mlh8r_kube-system_014c7004-6a67-11e7-963e-32811a79cf32_0
    d0ad349f5bac        e5c335701995                               "/dnsmasq-nanny -v=2 "   About a minute ago   Up About a minute                       k8s_dnsmasq_kube-dns-1301475494-mlh8r_kube-system_014c7004-6a67-11e7-963e-32811a79cf32_0
    8945eb887982        ca8759c215c9                               "/kube-dns --domain=c"   About a minute ago   Up About a minute                       k8s_kubedns_kube-dns-1301475494-mlh8r_kube-system_014c7004-6a67-11e7-963e-32811a79cf32_0
    3cbffe7017af        71dfe833ce74                               "/dashboard --insecur"   About a minute ago   Up About a minute                       k8s_kubernetes-dashboard_kubernetes-dashboard-qjnw9_kube-system_0082e1ef-6a67-11e7-963e-32811a79cf32_0
    6cf3ffbe7be3        gcr.io/google_containers/pause-amd64:3.0   "/pause"                 About a minute ago   Up About a minute                       k8s_POD_kube-dns-1301475494-mlh8r_kube-system_014c7004-6a67-11e7-963e-32811a79cf32_0
    436153f79835        gcr.io/google_containers/pause-amd64:3.0   "/pause"                 About a minute ago   Up About a minute                       k8s_POD_kubernetes-dashboard-qjnw9_kube-system_0082e1ef-6a67-11e7-963e-32811a79cf32_0
    21e6d429da2d        85809f318123                               "/opt/kube-addons.sh"    About a minute ago   Up About a minute                       k8s_kube-addon-manager_kube-addon-manager-minikube_kube-system_8538d869917f857f9d157e66b059d05b_0
    2ff110d6ceca        gcr.io/google_containers/pause-amd64:3.0   "/pause"                 About a minute ago   Up About a minute                       k8s_POD_kube-addon-manager-minikube_kube-system_8538d869917f857f9d157e66b059d05b_0
    $
    $ exit
    logout

Notice that there are local docker images related to Kubernetes and there are
instances running as containers within the virtual machine.

There is no need to SSH into the virtual machine to utilize docker, it can also
be executed from outside the virtual machine by setting up appropriate
docker-related environment variables.

    $ minikube docker-env
    export DOCKER_TLS_VERIFY="1"
    export DOCKER_HOST="tcp://192.168.64.17:2376"
    export DOCKER_CERT_PATH="/Users/sandoz/.minikube/certs"
    export DOCKER_API_VERSION="1.23"
    # Run this command to configure your shell:
    # eval $(minikube docker-env)

This is easily performed with the command (the last one commented out above):

    eval $(minikube docker-env)

Now the same commands as shown previously can be executed from the host system
(`docker images` and `docker ps`).

### Deploying a simple application on the Kubernetes cluster

To verify the Kubernetes cluster is functioning correctly we shall run a
test application.  Some Kubernetes terminology will be gently introduced but
not explained in detail.  We shall:

- inspect the Kubernetes node representing the single virtual machine in the
  Kubernetes cluster

- create a Kubernetes deployment for a single instance of a simple web
  application that will run within a docker container and be exposed on port
  8080.

- create a Kubernetes service to expose the deployment to the host system.

- inspect the pods and services in the Kubernetes cluster.

- access the web application from the host system.

- delete the service and deployment.

Use the Kubernetes command line tool `kubectl` to inspect the single Kubernetes
node:

    $ kubectl describe nodes
    Name:                   minikube
    Role:
    Labels:                 beta.kubernetes.io/arch=amd64
                            beta.kubernetes.io/os=linux
                            kubernetes.io/hostname=minikube
    Annotations:            node.alpha.kubernetes.io/ttl=0
                            volumes.kubernetes.io/controller-managed-attach-detach=true
    Taints:                 <none>
    CreationTimestamp:      Sun, 16 Jul 2017 13:40:10 -0700
    Conditions:
      Type                  Status  LastHeartbeatTime                       LastTransitionTime                      Reason                          Message
      ----                  ------  -----------------                       ------------------                      ------                          -------
      OutOfDisk             False   Sun, 16 Jul 2017 13:45:13 -0700         Sun, 16 Jul 2017 13:40:10 -0700         KubeletHasSufficientDisk        kubelet has sufficient disk space available
      MemoryPressure        False   Sun, 16 Jul 2017 13:45:13 -0700         Sun, 16 Jul 2017 13:40:10 -0700         KubeletHasSufficientMemory      kubelet has sufficient memory available
      DiskPressure          False   Sun, 16 Jul 2017 13:45:13 -0700         Sun, 16 Jul 2017 13:40:10 -0700         KubeletHasNoDiskPressure        kubelet has no disk pressure
      Ready                 True    Sun, 16 Jul 2017 13:45:13 -0700         Sun, 16 Jul 2017 13:40:10 -0700         KubeletReady                    kubelet is posting ready status
    Addresses:
      LegacyHostIP: 192.168.64.17
      InternalIP:   192.168.64.17
      Hostname:     minikube
    Capacity:
     cpu:           2
     memory:        2048516Ki
     pods:          110
    Allocatable:
     cpu:           2
     memory:        1946116Ki
     pods:          110
    System Info:
     Machine ID:                    b041fd90f92040d98491f49f4c0635d8
     System UUID:                   71754E9A-0000-0000-BA22-DBB51E7CF10B
     Boot ID:                       fd8b9841-cc58-41f7-9135-6440bf4a1fe1
     Kernel Version:                4.9.13
     OS Image:                      Buildroot 2017.02
     Operating System:              linux
     Architecture:                  amd64
     Container Runtime Version:     docker://1.11.1
     Kubelet Version:               v1.6.4
     Kube-Proxy Version:            v1.6.4
    ExternalID:                     minikube
    Non-terminated Pods:            (3 in total)
      Namespace                     Name                                    CPU Requests    CPU Limits      Memory Requests Memory Limits
      ---------                     ----                                    ------------    ----------      --------------- -------------
      kube-system                   kube-addon-manager-minikube             5m (0%)         0 (0%)          50Mi (2%)       0 (0%)
      kube-system                   kube-dns-1301475494-mlh8r               260m (13%)      0 (0%)          110Mi (5%)      170Mi (8%)
      kube-system                   kubernetes-dashboard-qjnw9              0 (0%)          0 (0%)          0 (0%)          0 (0%)
    Allocated resources:
      (Total limits may be over 100 percent, i.e., overcommitted.)
      CPU Requests  CPU Limits      Memory Requests Memory Limits
      ------------  ----------      --------------- -------------
      265m (13%)    0 (0%)          160Mi (8%)      170Mi (8%)
    Events:
      FirstSeen     LastSeen        Count   From                    SubObjectPath   Type            Reason                  Message
      ---------     --------        -----   ----                    -------------   --------        ------                  -------
      5m            5m              1       kubelet, minikube                       Normal          Starting                Starting kubelet.
      5m            5m              1       kubelet, minikube                       Warning         ImageGCFailed           unable to find data for container /
      5m            5m              2       kubelet, minikube                       Normal          NodeHasSufficientDisk   Node minikube status is now: NodeHasSufficientDisk
      5m            5m              2       kubelet, minikube                       Normal          NodeHasSufficientMemory Node minikube status is now: NodeHasSufficientMemory
      5m            5m              2       kubelet, minikube                       Normal          NodeHasNoDiskPressure   Node minikube status is now: NodeHasNoDiskPressure
      5m            5m              1       kubelet, minikube                       Normal          NodeReady               Node minikube status is now: NodeReady
      5m            5m              1       kube-proxy, minikube                    Normal          Starting                Starting kube-proxy.

Create a deployment and service:

    $ # Create a deployment named hello-minikube of the web application
    $ kubectl run hello-minikube \
          --image=gcr.io/google_containers/echoserver:1.6 \
          --port=8080
    deployment "hello-minikube" created

    $ # Create a service to expose the hello-minikube deployment
    $ kubectl expose deployment hello-minikube --type=NodePort
    service "hello-minikube" exposed

    $ # Get the pods (instances of hello-minikube)
    $ kubectl get pods
    NAME                              READY     STATUS    RESTARTS   AGE
    hello-minikube-1134960308-shtdv   1/1       Running   0          35s

    $ # Get the services
    $ kubectl get services
    NAME             CLUSTER-IP   EXTERNAL-IP   PORT(S)          AGE
    hello-minikube   10.0.0.14    <nodes>       8080:30478/TCP   31s
    kubernetes       10.0.0.1     <none>        443/TCP          9m

Notice pods are ephemeral they might come and go (we shall see more of this
later when we deploy the Java application), hence the appending of a unique id
to the deployment name.  Whereas, services are stable and will expose stable
information that an external system can use to interact with the deployment.

Also notice that the docker image `echoserver` was downloaded and Kubernetes
started some related Docker containers:

    $ docker images | grep echoserver
    gcr.io/google_containers/echoserver                    1.6                 a38d57e15d01        4 weeks ago         95.2MB

    $ docker ps | grep hello-minikube
    4948daff2095        a38d57e15d01                               "nginx -g 'daemon ..."   2 minutes ago       Up 2 minutes                            k8s_hello-minikube_hello-minikube-1134960308-shtdv_default_272b19ae-6a68-11e7-963e-32811a79cf32_0
    dfe4fe63c00e        gcr.io/google_containers/pause-amd64:3.0   "/pause"                 2 minutes ago       Up 2 minutes                            k8s_POD_hello-minikube-1134960308-shtdv_default_272b19ae-6a68-11e7-963e-32811a79cf32_0

The first running container is using the echoserver image.  (The second
container is Kubernetes specific and is required to ensure that network
information is retained if the container associated with the echoserver pod is
restarted. It does nothing but sleeps so uses little memory and runtime
resources).

Further details can be obtained by describing the pods and services, such as
with the commands:

    kubectl describe pods

    kubectl describe services

Access the web application by querying minikube for the external URL of service:

    $ minikube service hello-minikube --url
    http://192.168.64.17:30478

    $ curl $(minikube service hello-minikube --url)


    Hostname: hello-minikube-1134960308-shtdv

    Pod Information:
            -no pod information available-

    Server values:
            server_version=nginx: 1.13.1 - lua: 10008

    Request Information:
            client_address=172.17.0.1
            method=GET
            real path=/
            query=
            request_version=1.1
            request_uri=http://192.168.64.17:8080/

    Request Headers:
            accept=*/*
            host=192.168.64.17:30478
            user-agent=curl/7.43.0

    Request Body:
            -no body in request-

View the log from the specific pod showing the application was accessed:

    $ # Obtained the pod name by querying and extracting the information
    $ export POD_NAME=$(kubectl get pods --namespace default -l "run=hello-minikube" -o jsonpath="{.items[0].metadata.name}")
    $ echo $POD_NAME
    hello-minikube-1134960308-shtdv
    $
    $ kubectl logs $POD_NAME
    172.17.0.1 - - [16/Jul/2017:20:53:09 +0000] "GET / HTTP/1.1" 200 426 "-" "curl/7.43.0"

Minikube also provides an easy way to access the Kubernetes dashboard, a service
deployed within the Kubernetes cluster to query the cluster using the web
browser:

    $ # Open up a browser window to the Kubernetes dashboard
    $ minikube dashboard
    Opening kubernetes dashboard in default browser...

The dashboard can be used to query nodes, deployments, services, pods and logs
as previously performed using the command line.

Delete the deployment and service (which will also delete the pod):

    $ kubectl delete service,deployment -l run=hello-minikube
    service "hello-minikube" deleted
    deployment "hello-minikube" deleted

and verify there are no related deployments, services and pods. (Notice that
there are no longer running containers associated with the application, however
the docker image is still present.)

Stop minikube:

    $ minikube stop
    Stopping local Kubernetes cluster...
    Machine stopped.

(Notice running `kubectl` or `docker` will result in an error since there is
no connection to the docker machine.)

If you start minikube note that the IP address to the Kubernetes cluster may be
different.

You can start from scratch by completely deleting the minikube cluster,
specifically the docker images and stopped docker containers:

    $ minikube delete
    Deleting local Kubernetes cluster...

## Cloning, building and testing the Jersey+Netty project

Before cloning, building and testing the Jersey+Netty project ensure that you
have installed on your host system:

- Git (`git`).

- An early access build of [JDK 9][jdk9] of at least build 176 or greater.

[jdk9]: http://jdk.java.net/9/

- Maven (`mvn`) version 3.1.1 or greater.

Clone the project:

    git clone https://github.com/PaulSandoz/jersey-netty-app

Compile and package the project:

    cd jersey-netty-app
    mvn package

(The project may be opened in an IDE such as the IntelliJ.)

The project consists of a simple stateless web application, using Netty as the
HTTP server and Jersey to process HTTP request and produce HTTP responses.  For
such an application there is no need for an application server, nor a servlet
engine, nor the production of a war file (certain other kinds of applications
may require some or all of these).  As we shall see later it's very easy to
package this application within a Docker image and set the runtime command to
start the application.

The HTTP API supported by the application is summarized in the following table:

| Method | Path                 | Description                               |
| ------ |----------------------| ----------------------------------------- |
| GET    | /host                | Get the name of the host running the app  |
| GET    | /work                | Perform some work, rendering a Mandelbrot |
| GET    | /metrics             | Obtain JVM and app metrics                |
| GET    | /probe/liveness      | Probe if the app is alive                 |
| GET    | /probe/readiness     | Probe if the app is ready                 |
| POST   | /probe               | Update the app to be dead, alive or ready |
| GET    | /lifecycle/postStart | Called when the container is started      |
| GET    | /lifecycle/preStop   | Called when the container will be stopped |

The class `app.EndpointResource` implements this HTTP API as JAX-RS resource
classes (POJOs with JAX-RS annotations).

The main class `app.App` is responsible for starting the Netty server and
binding the `app.EndpointResource` resource class to the URL
`http://localhost:8080/`.

Run the application locally using maven:

    $ mvn exec:java
    ...
    [INFO] --- exec-maven-plugin:1.2.1:java (default-cli) @ jersey-netty-app ---
    Jul 16, 2017 2:03:12 PM app.App main
    INFO: Prometheus intializing JVM metrics
    ...
    Jul 16, 2017 2:03:14 PM app.App main
    INFO: Netty starting
    ...
    Jul 16, 2017 2:03:23 PM app.App main
    INFO: Netty started

You may observe warnings that Netty is accessing internal classes of the JDK
and that Jersey detects that some of its services cannot be registered because
they depend on JAXB (it and other EE related modules are not visible by default
on the Java 9 platform).  Netty and Jersey still function.

Access the application using `curl`

    $ # Get the host name
    $ curl http://localhost:8080/host
    Admins-MacBook-Pro-3.local

    $ # Do some work
    $ curl http://localhost:8080/work
                                            *

                                        ****
                                        ****
                                  *  **********
                                  *****************
                                *******************
                               *********************
                     *******  **********************
                    ********* **********************
     *********************************************
                    ********* **********************
                     *******  **********************
                               *********************
                                *******************
                                  *****************
                                  *  **********
                                        ****
                                        ****

Notice that as the application is accessed it will log messages such as:

    Jul 16, 2017 2:03:49 PM app.EndpointResource host
    INFO: /host
    Jul 16, 2017 2:04:15 PM app.EndpointResource work
    INFO: /work

The operations starting with URL path segment '/probe' support querying of
the application to see if it alive and if it is ready to receive requests. The
operations starting with the URL path segment 'lifecycle' support lifecycle
control just after the application is considered alive and before the
application is stopped.  In a further section of this tutorial we shall see how
these operations are integrated with Kubernetes.  For the moment we can get
a basic understanding of the behaviour using curl:

    $ # Test if the app is alive, if so a HTTP 200 status code is returned
    $ curl -sw ' %{http_code} ' http://localhost:8080/probe/liveness
    ALIVE 200

    $ # Test if the app is ready, if so a HTTP 200 status code is returned
    $ curl -sw ' %{http_code} ' http://localhost:8080/probe/readiness
    READY 200

    $ # Change the state of the app from ready to live
    $ curl -sw ' %{http_code} ' --data "state=ALIVE" localhost:8080/probe
    READY 200

    $ # The app is still alive
    $ curl -sw ' %{http_code} ' http://localhost:8080/probe/liveness
    ALIVE 200

    $ # But the app not ready, since a HTTP 500 status code is returned
    $ curl -sw ' %{http_code} ' http://localhost:8080/probe/readiness
    ALIVE 500

The application may also be placed in a "zombie" state where technically it is
not alive or ready and then could be destroyed by acting on the preStop hook:

    $ # Change the app from alive to a zombie
    $ curl -sw ' %{http_code} ' --data "state=ZOMBIE" localhost:8080/probe
    ALIVE 200

    $ # Stop the application (Notice the mvn exec:java process will terminate
    $ curl -sw ' %{http_code} ' http://localhost:8080/lifecycle/preStop
     200

    $ # And the app is no longer running
    $ curl http://localhost:8080/probe/liveness
    curl: (7) Failed to connect to localhost port 8080: Connection refused

Start up the application again and obtain JVM and application metrics:

    $ curl http://localhost:8080/metrics
    # HELP jvm_info JVM version info
    # TYPE jvm_info gauge
    jvm_info{version="9-internal+0-adhoc.sandoz.dev",vendor="Oracle Corporation",} 1.0
    # HELP jvm_classes_loaded The number of classes that are currently loaded in the JVM
    # TYPE jvm_classes_loaded gauge
    jvm_classes_loaded 5998.0
    # HELP jvm_classes_loaded_total The total number of classes that have been loaded since the JVM has started execution
    # TYPE jvm_classes_loaded_total counter
    jvm_classes_loaded_total 5998.0
    ...
    # HELP jvm_gc_collection_seconds Time spent in a given JVM garbage collector in seconds.
    # TYPE jvm_gc_collection_seconds summary
    jvm_gc_collection_seconds_count{gc="G1 Young Generation",} 3.0
    jvm_gc_collection_seconds_sum{gc="G1 Young Generation",} 0.064
    jvm_gc_collection_seconds_count{gc="G1 Old Generation",} 0.0
    jvm_gc_collection_seconds_sum{gc="G1 Old Generation",} 0.0
    ...

The metrics operation returns metrics in a textual format that can be understood
by Prometheus, which can record such metrics in a time series.  In a further
section we will run a Prometheus service and configure it to sample metrics by
performing HTTP requests, at regular intervals, to the metrics URL of each
running instance of the application.  By default JVM metrics are configured and
exposed, in addition application metrics have also been integrated and are
exposed and updated when work is performed.

(Note that the metrics are exposed on the same port as the application itself.
In a production system this is likely undesirable and the metrics should either
be exposed on a different port or written at regular intervals to a rolling log
file.  The metrics would then be consumed by a daemon, possibly responsible for
accumulating metrics from multiple containers on the same host, that is then
accessed by a telemetry service such as Prometheus.)

Create some load on the application by continually creating HTTP requests to
perform work (8 threads with 8 HTTP connections per thread) for 30s:

    $ wrk -t8 -c8 -d30s http://localhost:8080/work
    Running 30s test @ http://localhost:8080/work
      8 threads and 8 connections
      Thread Stats   Avg      Stdev     Max   +/- Stdev
        Latency   203.47ms   28.37ms 489.73ms   94.70%
        Req/Sec     4.40      1.50    10.00     91.96%
      1181 requests in 30.09s, 1.48MB read
    Requests/sec:     39.25
    Transfer/sec:     50.21KB

After the work has completed query the application metrics:

    $ curl -s http://localhost:8080/metrics | grep app_work
    # HELP app_work_requests_latency_seconds Request latency in seconds.
    # TYPE app_work_requests_latency_seconds histogram
    app_work_requests_latency_seconds_bucket{le="0.1",} 0.0
    app_work_requests_latency_seconds_bucket{le="0.2",} 599.0
    app_work_requests_latency_seconds_bucket{le="0.3",} 1091.0
    app_work_requests_latency_seconds_bucket{le="0.4",} 1091.0
    app_work_requests_latency_seconds_bucket{le="0.5",} 1099.0
    app_work_requests_latency_seconds_bucket{le="0.6",} 1099.0
    app_work_requests_latency_seconds_bucket{le="0.8",} 1099.0
    app_work_requests_latency_seconds_bucket{le="1.0",} 1099.0
    app_work_requests_latency_seconds_bucket{le="1.5",} 1099.0
    app_work_requests_latency_seconds_bucket{le="2.0",} 1099.0
    app_work_requests_latency_seconds_bucket{le="3.0",} 1099.0
    app_work_requests_latency_seconds_bucket{le="+Inf",} 1099.0
    app_work_requests_latency_seconds_count 1099.0
    app_work_requests_latency_seconds_sum 222.74719100000013

The application metrics observe and report the number of request and the
accumulated time taken to perform the work operation.  The requests are bucketed
into a histogram based on time thresholds.

In the particular sample above a total of 1099 requests were performed taking a
total of 223 seconds (recall requests were being performed concurrently by the
`wrk` tool, so that value does the actual duration of work which was 30
seconds).  Out of the 1099 requests:

- 599 took less than or equal to 0.2 seconds but greater than 0.1 seconds;
- 492 (1091 - 599) took less than or equal to 0.3 seconds but greater than 0.2
  seconds;
- the remaining 8 requests (1099 - 1091) took less than or equal to 0.5 seconds
  but greater than 0.4 seconds; and
- roughly 60% of requests were returned in less than 0.2 seconds, and taking an
  an estimate guess 90% were returned in less that 0.28%.

From such metric information and given a service like Prometheus that performs
regular sampling it's possible to write many sorts of queries over such
time-series data, render it, detect thresholds and provide alerting when such
thresholds are reached.  That's the general mechanism by which a dev ops team
might monitor a system to ensure say 95% of web requests are under say 10ms over
a sliding window of say 5 minutes.  If that percentage falls then team members
would get notified, who would then use the Prometheus service to query in more
detail to identify the cause. (Similar metrics can, for example, be sampled to
determine the response error rate, another important metric that might be
included in a Service Level Agreement.)

## Building the JRE and Jersey+Netty application docker images

Once the project is built and tested locally we can move on to building and
testing docker images.

Check the following (as previously described), otherwise it will not be possible
to build Docker images and run Docker containers:

- minikube is running; and

- minikube's `docker-env` variables have been exported in the current
  shell/terminal that shall be used to build the images.

We shall be building the project within docker containers in a series of steps:

1. Build a docker image for development.  This image will be derived from Alpine
linux, a very small distribution of approximately 5MB in size that comes with
its own standard library, [`musl` libc][musl], instead of `glibc`.  The image
will contain a recent JDK 9 distribution that is compatible with musl (see the
OpenJDK [Project Portola][portola]).  The image will also contain a recent build
of Maven so the application can be built by executing a docker container.

[musl]: https://www.musl-libc.org/faq.html
[portola]: http://openjdk.java.net/projects/portola/

2. Build the application by running the development image create in step 1 and
execute Maven to build the project.

3. Create a smaller custom JDK 9 image containing only the modules that the
jersey+netty application depends on.

4. Build a docker image, derived from Alpine linux, containing the custom JDK 9
image created in 3.

5. Build the application docker image, derived from the image built in 4.

Note: all the steps described above and in detail below may be performed by
executing the script `docker-pipelines.sh` with the argument `build`.

The docker image for development is built from the docker file named
`jdk-9-alpine.Dockerfile` consisting of:

    # The JDK 9 development image for building
    FROM alpine:3.6
    # Add the musl-based JDK 9 distribution
    RUN apk update \
      && apk add wget
    RUN mkdir /opt
    RUN wget -q http://download.java.net/java/jdk9-alpine/archive/177/binaries/jdk-9-ea+177_linux-x64-musl_bin.tar.gz
    RUN tar -x -v -f jdk-9-ea+177_linux-x64-musl_bin.tar.gz -C /opt
    RUN rm jdk-9-ea+177_linux-x64-musl_bin.tar.gz
    # Add maven
    RUN wget -q http://apache.claz.org/maven/maven-3/3.5.0/binaries/apache-maven-3.5.0-bin.tar.gz
    RUN tar -x -v -f apache-maven-3.5.0-bin.tar.gz -C /opt
    RUN rm apache-maven-3.5.0-bin.tar.gz
    # Set up env variables
    ENV JAVA_HOME=/opt/jdk-9
    ENV MAVEN_HOME=/opt/apache-maven-3.5.0
    ENV PATH=$PATH:$JAVA_HOME/bin:$MAVEN_HOME/bin
    CMD ["/opt/jdk-9/bin/java", "-version"]

Build the docker image for development, named `jdk-9-alpine`:

    $ docker build \
        -t jdk-9-alpine \
        -f jdk-9-alpine.Dockerfile .
    Sending build context to Docker daemon  44.61MB
    Step 1 : FROM alpine:3.6
     ---> 7328f6f8b418
    Step 2 : RUN apk update   && apk add wget
     ---> Running in ff611db50bea
    fetch http://dl-cdn.alpinelinux.org/alpine/v3.6/main/x86_64/APKINDEX.tar.gz
    fetch http://dl-cdn.alpinelinux.org/alpine/v3.6/community/x86_64/APKINDEX.tar.gz
    v3.6.2-33-gc21717b071 [http://dl-cdn.alpinelinux.org/alpine/v3.6/main]
    v3.6.2-32-g6f53cfcccd [http://dl-cdn.alpinelinux.org/alpine/v3.6/community]
    OK: 8436 distinct packages available
    (1/1) Installing wget (1.19.1-r2)
    Executing busybox-1.26.2-r5.trigger
    OK: 4 MiB in 12 packages
     ---> 0b9d97eac94b
    ...
    Step 4 : RUN wget -q http://download.java.net/java/jdk9-alpine/archive/177/binaries/jdk-9-ea+177_linux-x64-musl_bin.tar.gz
     ---> Running in 4cef7cc5d41b
     ---> 79a8c40d9550
    ...
    Step 7 : RUN wget -q http://apache.claz.org/maven/maven-3/3.5.0/binaries/apache-maven-3.5.0-bin.tar.gz
     ---> Running in a00bc3283246
     ---> 0df096faa523
    ...
    Step 13 : CMD /opt/jdk-9/bin/java -version
     ---> Running in 2b3bac729ef5
     ---> fc6bb587eb1e
    Removing intermediate container 2b3bac729ef5
    Successfully built fc6bb587eb1e

Build the application by executing `mvn package` in a running container of image
`jdk-9-alpine`:

    $ docker run --rm \
        --workdir=/jersey-netty-app \
        --volume=$HOME/.m2:/root/.m2 \
        --volume $PWD:/jersey-netty-app \
        jdk-9-alpine \
        mvn package
    [INFO] Scanning for projects...
    [INFO]
    [INFO] ------------------------------------------------------------------------
    [INFO] Building app-jersey-netty 1.0-SNAPSHOT
    [INFO] ------------------------------------------------------------------------
    ...

Note that the maven configuration directory and project directory are mounted as
volumes in the running container (this assumes you are executing in the
top-level directory of the project).  Mounting the maven configuration directory
ensures that any cache of dependencies is reused.  Mounting the project
directory ensures the project configuration and artifacts are available for
maven to build the project, in addition to the output (`target`) where built
artifacts are placed.

Build the application by executing `mvn package` in a running container of image
`jdk-9-alpine`:


Create a smaller custom JDK 9 image by executing [`jlink`][jlink] in a running
container of the image `jdk-9-alpine`:

    docker run --rm \
      --volume $PWD:/jersey-netty-app \
      jdk-9-alpine \
      jlink --module-path /opt/jdk-9/jmods \
        --add-modules java.base,java.logging,java.management,java.xml,jdk.management,jdk.unsupported \
        --strip-debug \
        --compress 2 \
        --no-header-files \
        --output /jersey-netty-app/target/jdk-9-alpine-linked

[jlink]: https://docs.oracle.com/javase/9/tools/jlink.htm

`jlink` can assemble and optimize a set of Java modules and their dependencies
into a custom runtime image.  In this case we are producing a custom JDK 9
image with the modules `java.base`, `java.logging`, `java.management`,
`java.xml`, `jdk.management`, `jdk.unsupported`.

Since `jlink` is executed in the docker container the resulting custom image
will also be compatible with musl libc and therefore will function correctly
on Alpine linux.

The docker image for the custom JDK 9 image is built from the docker file named
`jdk-9-alpine-linked.Dockerfile` consisting of:

    # Make a docker image with a jlink'ed JDK 9
    FROM alpine:3.6
    # Add jlink'ed JDK 9
    ADD target/jdk-9-alpine-linked /opt/jdk-9
    ENV JAVA_HOME=/opt/jdk-9
    ENV PATH=$PATH:$JAVA_HOME/bin
    CMD ["/opt/jdk-9/bin/java", "-version"]

Build the docker image for the custom JDK 9 image, named `jdk-9-alpine-linked`:

    $ docker build \
        -t jdk-9-alpine-linked \
        -f jdk-9-alpine-linked.Dockerfile .
    Sending build context to Docker daemon  44.62MB
    Step 1 : FROM alpine:3.6
    ...
    Successfully built a880f2839516

The docker image for the application is built from the docker file named
`jersey-netty-app.Dockerfile` consisting of:

    # The application docker image
    FROM jdk-9-alpine-linked
    # Add the application jar file
    ADD target/jersey-netty-app-1.0-SNAPSHOT.jar /opt/app/jersey-netty-app-1.0-SNAPSHOT.jar
    # Add the application's dependent jars obtained from maven
    ADD target/dependency /opt/app/dependency
    # Set the command to run the application, note the use of the wildcard for
    # all dependent jars
    CMD java \
      -cp /opt/app/jersey-netty-app-1.0-SNAPSHOT.jar:/opt/app/dependency/* \
      app.App

Build the docker image for the application, named `jdk-9-alpine-linked`:

    $ docker build \
        -t jersey-netty-app \
        -f jersey-netty-app.Dockerfile .
    Sending build context to Docker daemon  44.62MB
    Step 1 : FROM jdk-9-alpine-linked
     ---> a880f2839516
    Step 2 : ADD target/jersey-netty-app-1.0-SNAPSHOT.jar /opt/app/jersey-netty-app-1.0-SNAPSHOT.jar
    ...
    Step 3 : ADD target/dependency /opt/app/dependency
    ...
    Step 4 : CMD java   -cp /opt/app/jersey-netty-app-1.0-SNAPSHOT.jar:/opt/app/dependency/*   app.App
    ...
    Successfully built 9d58de60cf89

The application jar and its dependent jars are copied into the docker image
and the `CMD` uses `java` from the parent image.  Note the use of the little
known wildcard option when declaring the classpath.

It's very easy to create a Docker image for executing a Java application, there
is no need in such cases to create an "uber" jar file or potentially even a
war file (depending on the requirements), since in effect the container is the
*unit of deployment*.

The size of the three images can be compared:

    $ docker images
    REPOSITORY                                             TAG                 IMAGE ID            CREATED              SIZE
    jersey-netty-app                                       latest              9d58de60cf89        About a minute ago   47.2MB
    jdk-9-alpine-linked                                    latest              a880f2839516        About a minute ago   39.5MB
    jdk-9-alpine                                           latest              fc6bb587eb1e        4 minutes ago        587MB
    alpine                                                 3.6                 7328f6f8b418        2 weeks ago          3.96MB
    gcr.io/google_containers/echoserver                    1.6                 a38d57e15d01        4 weeks ago          95.2MB
    gcr.io/google_containers/kubernetes-dashboard-amd64    v1.6.1              71dfe833ce74        2 months ago         134MB
    gcr.io/google_containers/k8s-dns-sidecar-amd64         1.14.2              7c4034e4ffa4        2 months ago         44.5MB
    gcr.io/google_containers/k8s-dns-kube-dns-amd64        1.14.2              ca8759c215c9        2 months ago         52.4MB
    gcr.io/google_containers/k8s-dns-dnsmasq-nanny-amd64   1.14.2              e5c335701995        2 months ago         44.8MB
    gcr.io/google-containers/kube-addon-manager            v6.4-beta.1         85809f318123        4 months ago         127MB
    gcr.io/google_containers/pause-amd64                   3.0                 99e59f495ffa        14 months ago        747kB

Observe how `jlink` can be utilized to reduce the size of a JDK image by an
order of magnitude (in this case from 587MB to 39.5MB).

For development and test purposes the application could be run from within the
docker container for development (which is just a further step beyond building
the application):

    $ docker run --tty --interactive --rm \
       --workdir=/jersey-netty-app \
       --volume $PWD:/jersey-netty-app \
       --volume=$HOME/.m2:/root/.m2 \
       --publish 9090:8080 \
       jdk-9-alpine mvn exec:java
    ...
    INFO: Netty started

Note: the step described above and in detail below may be performed by
executing the script `docker-pipelines.sh` with the argument `dev`.

In this case the local port `8080` is remapped to port `9090` on the host
system.  The application may be tested in a similar manner as when run using
`mvn exec:java` directly, but the URL will now be `http://$(minikube ip):9090`.
For example, some load on the application may be created using the `wrk` tool
that was previously installed:

    wrk -t1 -c1 -d10s http://$(minikube ip):9090/work

The application can also be run by executing the application image:

    $ docker run -it --rm \
        --publish 9090:8080 \
        jersey-netty-app
    ...
    INFO: Netty started

Note: the step described above may be performed by executing the script
`docker-pipelines.sh` with the argument `run`.

## Deploying the Jersey+Netty application to the Kubernetes cluster

Now that the JDK and application docker images have been built we can start
deploying and testing the application on the local kubernetes cluster (instead
of directly controlling the life-cycle of docker containers).

### Creating the deployment

First we need to declare a Kubernetes [deployment][deployment] for the
application by writing a deployment document in the YAML format.  Once written
will shall tell Kubernetes to create a deployment object from the deployment
document.  Kubernetes will in turn create other objects (such as replica sets
and pods, explained later) and control the deployment so that its state will
reach that specified in deployment document.  As we shall see later on we can
update the specification of the deployment and Kubernetes will react to the new
conditions controlling the deployment to reach the new desired state.

A pre-written deployment document is located in the project file
`jersey-netty-deployment.yml`.  We shall go through contiguous sections of the
document in order explaining further details.

     1   # This document represents a Deployment
     2   kind: Deployment
     3   # The version of this Deployment
     4   apiVersion: extensions/v1beta1
     5   metadata:
     6     # Unique key of this Deployment instance
     7     name: jersey-netty-deployment

The initial part of the document declares that is a representation of a
[deployment][deployment-api] of a particular version.  In the `metadata` a
unique name  is declared, a label whose key is `name` and whose value is the
name of the deployment. (Note: it's possible to have one document declare
multiple Kubernetes objects of the same and different kinds.)

     8   # Specification of the desired behavior of this Deployment
     9   spec:
    10     # Number of desired pods to be managed
    11     replicas: 1
    12     # Template describes the pods associated with this deployment
    13     template:
    14       metadata:
    15         labels:
    16           # Label (key/value pair) associated with this pod template
    17           app: jersey-netty

The deployment consists of a [deployment specification][deployment-spec] that
declares how many pods (`replicas`) should be created and managed (in this case
initially just one), and a [pod template specification][pod-template-spec] that
describes the pods.  A important aspect of the pod template specification is the
label, name/value pair, that is associated with any pods created from this
template.  The label will be used to connect the deployment to the service (as
further explained later on).

    18      # Specification of the desired behavior of a pod
    19      spec:
    20        # List of containers belonging to the pod
    21        containers:
    22          # Name of the container, specified as a DNS_LABEL
    23        - name: jersey-netty
    24          # Docker image name
    25          image: jersey-netty-app
    26          # Image pull policy, "IfNotPresent" == pull if not in local repository
    27          imagePullPolicy: IfNotPresent
    28          # List of ports to expose from the container
    29          ports:
    30          - containerPort: 8080

The pod template specification consists of a [pod specification][pod-spec] that
defines the behaviour of pods.  The pod specification may consist of one or
more [containers][container].  When kubernetes creates an instance of a pod it
will create the specified containers such that they are co-located on the same
node (in this case our singular the virtual machine) associated with the pod.
We just have one container for our application, named `jersey-netty`, whose
Docker image is that we created in a previous section, named `jersey-netty-app`,
and where port 8080 is exposed from the container.

    31          # Periodic probe of container liveness.
    32          livenessProbe:
    33            httpGet:
    34              path: probe/liveness
    35              port: 8080
    36          # Periodic probe of container service readiness. Container will be removed from service endpoints if the probe fails
    37          readinessProbe:
    38            httpGet:
    39              path: probe/readiness
    40              port: 8080

The container declares two kinds of probes for
[controlling the life-cycle][pod-lifecycle] of pods.  Kubernetes will interact
with a running container at periodic intervals to determine if it is alive
(`livenessProbe`), and if it is ready to receive requests (`readinessProbe`).
In both cases the probes are specify that Kubernetes perform HTTP requests, the
URL `path`s of which are implemented in the class `app.EndpointResource`, as
previously described.

If the liveness probe fails to respond or returns a 5xx status code then
Kubernetes will restart the container.  Note that a liveness probe is not
required if the application can crash or terminate gracefully, resulting in
stopping of the container.  In such cases the same restart policy will be
applied as if a liveness probe failed.

If the readiness probe fails to respond or returns a 5xx status code (but the
container is alive as per the liveness probe) then Kubernetes will ensure that
no requests are directed to the container in the pod.  This can be useful if
certain maintenance is required.  For example, if a Java process knew it was
going to perform a full GC request that could take a long time and potentially
violate an SLA then the process could temporarily declare that it is not ready
to receive requests until the full GC has completed.)

    42          lifecycle:
    43  #          postStart:
    44  #            httpGet:
    45  #              path: lifecycle/postStart
    46  #              port: 8080
    47            # PreStop is called immediately before a container is terminated
    48            # The container is terminated after the handler completes. The reason for termination is passed to the handler. Regardless of the outcome of the handler, the container is eventually terminated. Other management of the container blocks until the hook completes
    49            preStop:
    50              httpGet:
    51                path: lifecycle/preStop
    52                port: 8080

The container can declare two kinds of life-cycle actions to be invoked after
the container has started (`postStart`) and before the container is terminated
(`preStop`).  In this case only the pre-stop action is declared.  When the
application receives the pre-stop action it will gracefully shut down the Netty
service.

Note: as of writing there appear to be timing issues with the pre-start action.
Kubernetes will send the post-start request immediately after the container is
created but at this point the jersey+netty application is not fully initialized
and not ready to respond to HTTP requests.  Thus Kubernetes observes a
"connection refused" and attempts to restart the container rather than retrying
the post-start action with a retry policy.

Create the deployment:

    $ kubectl create -f jersey-netty-deployment.yml
    deployment "jersey-netty-deployment" created

Inspect the pods:

    $ kubectl get pods
    NAME                                       READY     STATUS    RESTARTS   AGE
    jersey-netty-deployment-4144015705-phhs9   1/1       Running   0          18m

Inspect the log of the created pod using the `app=jersey-netty` label (the same
label in the pod template):

    $ kubectl logs -l app=jersey-netty
    Jul 16, 2017 11:27:12 PM app.EndpointResource$Probe readiness
    INFO: /probe/readiness
    Jul 16, 2017 11:27:22 PM app.EndpointResource$Probe liveness
    INFO: /probe/liveness
    Jul 16, 2017 11:27:22 PM app.EndpointResource$Probe readiness
    INFO: /probe/readiness
    Jul 16, 2017 11:27:32 PM app.EndpointResource$Probe liveness
    INFO: /probe/liveness
    Jul 16, 2017 11:27:32 PM app.EndpointResource$Probe readiness
    INFO: /probe/readiness

Notice that Kubernetes, by default, probes for liveness and readiness every 10s.

Obtain the IP address of the single pod and interact with it by SSH'ing into the
node:

    $ export POD_IP=$(kubectl get pods --namespace default -l app=jersey-netty -o jsonpath="{.items[0].status.podIP}")

    $ minikube ssh "curl -s http://$POD_IP:8080/host"
    jersey-netty-deployment-4144015705-phhs9

Kill the application and notice that Kubernetes will restart the container:

    $ # Send a request to stop the application which will terminate the container
    $ minikube ssh "curl -s http://$POD_IP:8080/lifecycle/preStop"

    $ # Logs show the application has shut down
    $ kubectl logs -l app=jersey-netty
    Jul 16, 2017 11:29:52 PM app.EndpointResource$Probe readiness
    INFO: /probe/readiness
    Jul 16, 2017 11:30:02 PM app.EndpointResource$Probe liveness
    INFO: /probe/liveness
    Jul 16, 2017 11:30:02 PM app.EndpointResource$Probe readiness
    INFO: /probe/readiness
    Jul 16, 2017 11:30:05 PM app.EndpointResource$Lifecycle preStop
    INFO: /liveness/preStop
    Jul 16, 2017 11:30:05 PM app.App main
    INFO: Netty shutting down

    ...

    $ # Some time later the pod is up and running again with indication it restarted
    $ kubectl get pods
    NAME                                       READY     STATUS    RESTARTS   AGE
    jersey-netty-deployment-4144015705-phhs9   1/1       Running   1          20m

Notice that the pod name and pod IP address does not change if the container
restarts.

Set the application to be unresponsive and notice that Kubernetes will restart
the container:

    $ # Make the application return 500 status code on GET /probe/liveness
    $ minikube ssh "curl -s --data "state=ZOMBIE" http://$POD_IP:8080/probe"
    READY

    ...

    $ # Some time later the pod is up and running again with indication it restarted
    $ kubectl get pods
    NAME                                       READY     STATUS    RESTARTS   AGE
    jersey-netty-deployment-4144015705-phhs9   1/1       Running   2          22m

Showing details of the pod present messages indicated actions performed on the
container as a result of previous operations:

    kubectl describe pods -l app=jersey-netty

### Creating the service

After Kubernetes has created the deployment object (and ancillary objects) we
need to declare a Kubernetes [service][service] for the application by writing a
service document in the YAML format.  Once written will shall tell Kubernetes to
create a service object from the service document.  The service will expose the
deployment in a stable manner such that it can be interacted with without
knowing the particulars about pods associated with the deployment, such as how
many pods are active and their IP addresses/ports, both of which are not stable
and may change over time.

A pre-written deployment document is located in the project file
`jersey-netty-service.yml`.  We shall go through contiguous sections of the
document in order explaining further details.

     1  # This document represents a Service
     2  kind: Service
     3  # The version of this Service
     4  apiVersion: v1
     5  metadata:
     6    # Unique key of this Service instance
     7    name: jersey-netty-service

The initial part of the document declares that is a representation of a
[service][service-api] of a particular version.  In the `metadata` a
unique name  is declared, a label whose key is `name` and whose value is the
name of the service.

     8  # Specification of the designed behaviour of the Service
     9  spec:
    10    # Route service traffic to pods with label keys and values matching this selector.
    11    selector:
    12      # The label declared in the deployment specification
    13      # Requests will be routed to pods matching this label
    14      app: jersey-netty

The service consists of a [service specification][service-spec] that
declares a label within a selector, `app: jersey-netty`.  This is the same label
that was declared in the pod template specification of the deployment document.
All pods that match the label will be selected to be part of the service, which
means the service can redirect an HTTP request to the IP address and
port, referred to as an endpoint, of a matching pod.

    15    # Accept traffic sent to port 8080 and mapped to port 8080 on the matched pods
    16    ports:
    17      - protocol: TCP
    18        port: 8080
    19        # The target port can refer to a name so is not hard coded
    20        targetPort: 8080

The service specification declares the port that is exposed by the service
and the mapped target port exposed by a container in a matching pod.

    21    # Expose this Service running in the virtual machine (the single node) at
    22    # a stable port. The node will proxy that static port and redirect to pods.
    23    # A pod is chosen at random
    24    type: NodePort

Finally the service specification declares how the service is exposed.  For a
service type of `NodePort` Kubernetes will allocate a stable port on each node
such that the host system can interact with the service.  `NodePort` builds
on the `ClusterIP` type that defines an internal and virtual IP for load
balancing to endpoints.  On each node Kubernetes will run a proxy that will
perform Layer 4 load balancing, which means the proxy is responsible for
configuring the iptable rules to redirect TCP/IP traffic.  As a result an
endpoint will be selected at random independently of the kind of traffic.
The `LoadBalancer` type builds upon `NodePort` for integration with an external
load balancer that may perform Layer 7 load balancing that can also make more
informed decisions on how to balance traffic, for example by analysing the
traffic contents.

Create the service:

    $ kubectl create -f jersey-netty-service.yml
    service "jersey-netty-service" created

Inspect the deployments, pods, services and endpoints, and show details of the
service:

    $ kubectl get deployment,pods,services,endpoints
    NAME                             DESIRED   CURRENT   UP-TO-DATE   AVAILABLE   AGE
    deploy/jersey-netty-deployment   1         1         1            1           25m

    NAME                                          READY     STATUS    RESTARTS   AGE
    po/jersey-netty-deployment-4144015705-phhs9   1/1       Running   2          25m

    NAME                       CLUSTER-IP   EXTERNAL-IP   PORT(S)          AGE
    svc/jersey-netty-service   10.0.0.58    <nodes>       8080:32207/TCP   16m
    svc/kubernetes             10.0.0.1     <none>        443/TCP          3h

    NAME                      ENDPOINTS            AGE
    ep/jersey-netty-service   172.17.0.4:8080      16m
    ep/kubernetes             192.168.64.17:8443   3h

    $ kubectl describe services jersey-netty-service
    Name:                   jersey-netty-service
    Namespace:              default
    Labels:                 <none>
    Annotations:            <none>
    Selector:               app=jersey-netty
    Type:                   NodePort
    IP:                     10.0.0.58
    Port:                   <unset> 8080/TCP
    NodePort:               <unset> 32207/TCP
    Endpoints:              172.17.0.4:8080
    Session Affinity:       None
    Events:                 <none>

Notice that a fixed port has been created, in this case port 32207.  The
external URL to interact with the service can be obtained from minikube:

    $ export JN_SERVICE=$(minikube service jersey-netty-service --url)

    $ curl $JN_SERVICE/host
    jersey-netty-deployment-4144015705-phhs9

Notice that the host of the running container name corresponds to the pod name.

For the extra curious SSH into the virtual machine and observe the NAT iptables
using:

    sudo iptables --table nat --list

### Scaling the deployment

So far we only have one instance of our application running (specifically one
pod).  Let's scale the deployment so there are four pods and observe the newly
created pods:

    $ kubectl scale deployment jersey-netty-deployment --replicas 4
    deployment "jersey-netty-deployment" scaled

    $ kubectl get pods
    NAME                                       READY     STATUS              RESTARTS   AGE
    jersey-netty-deployment-4144015705-4g6g9   0/1       ContainerCreating   0          16m
    jersey-netty-deployment-4144015705-d2vmb   0/1       ContainerCreating   0          16m
    jersey-netty-deployment-4144015705-fpt4q   0/1       ContainerCreating   0          16m
    jersey-netty-deployment-4144015705-phhs9   1/1       Running             2          28m

    ...

    $ # After a little time all four pods are live and are ready
    $ kubectl get pods
    NAME                                       READY     STATUS    RESTARTS   AGE
    jersey-netty-deployment-4144015705-4g6g9   1/1       Running   0          16m
    jersey-netty-deployment-4144015705-d2vmb   1/1       Running   0          16m
    jersey-netty-deployment-4144015705-fpt4q   1/1       Running   0          16m
    jersey-netty-deployment-4144015705-phhs9   1/1       Running   2          28m

    $ # The service now knows about the new endpoints
    $ kubectl describe service jersey-netty-service
    Name:                   jersey-netty-service
    Namespace:              default
    Labels:                 <none>
    Annotations:            <none>
    Selector:               app=jersey-netty
    Type:                   NodePort
    IP:                     10.0.0.58
    Port:                   <unset> 8080/TCP
    NodePort:               <unset> 32207/TCP
    Endpoints:              172.17.0.4:8080,172.17.0.5:8080,172.17.0.6:8080 + 1 more...
    Session Affinity:       None
    Events:                 <none>

(For the extra curious SSH into the virtual machine and observe the NAT
iptables.)

Verify that load balancing is working across all the pods by performing 100
requests and creating a histogram of number of requests to each host:

     $ { for i in {1..100}; do curl -sw "\n" $JN_SERVICE/host; done } | sort | uniq -c
      35 jersey-netty-deployment-4144015705-4g6g9
      20 jersey-netty-deployment-4144015705-d2vmb
      20 jersey-netty-deployment-4144015705-fpt4q
      25 jersey-netty-deployment-4144015705-phhs9

The log of each pod can be inspected to observe the received work requests.
Note that the command `kubectl logs` can only log the output from one selected
pod.  However, a utility written by the Wercker team, [`stern`][stern] can
combine the logs from multiple pods into one unified stream.

[stern][https://github.com/wercker/stern]

Make "unready" a random pod and verify only three pods are active:

    $ # This request will get directed to a random pod
    $ curl --data "state=ALIVE" $JN_SERVICE/probe
    READY

    ...

    $ # Wait until one pod is no longer ready to receive requests
    $ kubectl get pods
    jersey-netty-deployment-4144015705-4g6g9   0/1       Running   0          19m
    jersey-netty-deployment-4144015705-d2vmb   1/1       Running   0          19m
    jersey-netty-deployment-4144015705-fpt4q   1/1       Running   0          19m
    jersey-netty-deployment-4144015705-phhs9   1/1       Running   2          31m

    $ # Only three endpoints are now associated with the service
    $ kubectl describe services jersey-netty-service
    Name:                   jersey-netty-service
    Namespace:              default
    Labels:                 <none>
    Annotations:            <none>
    Selector:               app=jersey-netty
    Type:                   NodePort
    IP:                     10.0.0.58
    Port:                   <unset> 8080/TCP
    NodePort:               <unset> 32207/TCP
    Endpoints:              172.17.0.4:8080,172.17.0.5:8080,172.17.0.6:8080
    Session Affinity:       None
    Events:                 <none>

    $ # Load is now distributed across three endpoints
    $ { for i in {1..100}; do curl -sw "\n" $JN_SERVICE/host; done } | sort | uniq -c
      27 jersey-netty-deployment-4144015705-d2vmb
      33 jersey-netty-deployment-4144015705-fpt4q
      40 jersey-netty-deployment-4144015705-phhs9

(For the extra curious SSH into the virtual machine and observe the NAT
iptables.)

Scale down the service:

    $ kubectl scale deployment jersey-netty-deployment --replicas 1
    deployment "jersey-netty-deployment" scaled

    $ kubectl get pods
    NAME                                       READY     STATUS        RESTARTS   AGE
    jersey-netty-deployment-4144015705-4g6g9   0/1       Terminating   0          20m
    jersey-netty-deployment-4144015705-d2vmb   1/1       Terminating   0          20m
    jersey-netty-deployment-4144015705-fpt4q   1/1       Terminating   0          20m
    jersey-netty-deployment-4144015705-phhs9   1/1       Running       2          32m

    ...

    $ kubectl get pods
    NAME                                       READY     STATUS    RESTARTS   AGE
    jersey-netty-deployment-4144015705-phhs9   1/1       Running   2          32m

Notice that Kubernetes will terminate an "unready" pod in preference to a ready
pod (and presumably a younger pod in preference to an older pod).

This section only presented the basics of scaling a deployment.  Other aspects
including rolling upgrades, downgrades and canary'ing  which involve more
complex interactions and preferably involve a CD pipeline to help orchestrate
such activity, such as that supported by [Wercker][wercker].

[deployment]: https://kubernetes.io/docs/concepts/workloads/controllers/deployment/
[service]: https://kubernetes.io/docs/concepts/services-networking/service/
[deployment-api]: https://kubernetes.io/docs/api-reference/v1.6/#deployment-v1beta1-apps
[deployment-spec]: https://kubernetes.io/docs/api-reference/v1.6/#deploymentspec-v1beta1-apps
[pod-template-spec]: https://kubernetes.io/docs/api-reference/v1.6/#podtemplatespec-v1-core
[pod-spec]: https://kubernetes.io/docs/api-reference/v1.6/#podspec-v1-core
[container]: https://kubernetes.io/docs/api-reference/v1.6/#container-v1-core
[pod-lifecycle]: https://kubernetes.io/docs/concepts/workloads/pods/pod-lifecycle
[service-api]: https://kubernetes.io/docs/api-reference/v1.6/#service-v1-core
[service-spec]: https://kubernetes.io/docs/api-reference/v1.6/#servicespec-v1-core

## Intializing helm to deploy pre-defined services on the Kubernetes cluster

We shall use helm to install a Prometheus and Grafana service in the following
sections.  Helm is a tool for managing pre-configured Kubernetes packages
referred to as charts.  In a sense charts define a template for Kubernetes
documents and configuration options that gets filled with appropriate values
when helm installs a package.  Helm can easily manage the life-cycle of a
service withouy having to directl use the kubectl command.

Before initializing helm ensure the Kubernetes cluster is running.

Initialize helm:

    $ helm init
    Creating /Users/sandoz/.helm
    Creating /Users/sandoz/.helm/repository
    Creating /Users/sandoz/.helm/repository/cache
    Creating /Users/sandoz/.helm/repository/local
    Creating /Users/sandoz/.helm/plugins
    Creating /Users/sandoz/.helm/starters
    Creating /Users/sandoz/.helm/repository/repositories.yaml
    $HELM_HOME has been configured at /Users/sandoz/.helm.

    Tiller (the helm server side component) has been installed into your Kubernetes Cluster.
    Happy Helming!

On initialization helm will configure the default repository for obtaining
charts and install a special service, named Tiller, on the cluster to manage the
life cycle of installed helm packages.

    $ kubectl get services --namespace kube-system
    NAME                   CLUSTER-IP   EXTERNAL-IP   PORT(S)         AGE
    kube-dns               10.0.0.10    <none>        53/UDP,53/TCP   3h
    kubernetes-dashboard   10.0.0.40    <nodes>       80:30000/TCP    3h
    tiller-deploy          10.0.0.11    <none>        44134/TCP       16m

Let's add an incubating repository just in case you want to experiment with
interesting incubating packages that are not quite stable, and while we are at
it ensure we are up to date with the repositories:

    $ helm repo add incubator http://storage.googleapis.com/kubernetes-charts-incubator
    "incubator" has been added to your repositories

    $ helm repo update
    Hang tight while we grab the latest from your chart repositories...
    ...Skip local chart repository
    ...Successfully got an update from the "incubator" chart repository
    ...Successfully got an update from the "stable" chart repository
    Update Complete. ⎈ Happy Helming!⎈

Searching the repository will show some interesting packages (for brevity some
some packages are not shown):

    $ helm search
    NAME                          	VERSION	DESCRIPTION
    incubator/cassandra             0.1.1   Apache Cassandra is a free and open-source dist...
    ...
    incubator/elasticsearch         0.1.6   Flexible and powerful open source, distributed ...
    ...
    incubator/kafka                 0.1.2   Apache Kafka is publish-subscribe messaging ret...
    ...
    incubator/zookeeper             0.3.0   Centralized service for maintaining configurati...
    ...
    stable/grafana                  0.3.7   The leading tool for querying and visualizing t...
    ...
    stable/influxdb                 0.4.2   Scalable datastore for metrics, events, and rea...
    ...
    stable/prometheus               3.1.0   Prometheus is a monitoring system and time seri...
    ...
    stable/redis                    0.8.0   Open source, advanced key-value store. It is of...
    ...
    stable/spark                    0.1.4   Fast and general-purpose cluster computing system.
    ...
    stable/spinnaker                0.3.0   Open source, multi-cloud continuous delivery pl...

## Deploying a Prometheus service to monitor the Jersey+Netty application

Now that helm is initialized we can install Prometheus on the Kubernetes
cluster.  The Prometheus service will be configured to *scrape* metrics from
running pods who have a label `app: jersey-netty`.  For each pod Prometheus will
sample the metrics of the application at regular intervals by performing an
HTTP GET request to the URL http://<pod-ip>:<pod-port>/metrics.

Prometheus will react in the same manner as the application service, and thus
will know when pods come and go due to new scaling requirements or because a pod
has become inactive.

By default the Prometheus helm chart is configured to monitor the Kubernetes
cluster as declared in the default
[values.yml configuration][prometheus-default-values-config] file which can
also be obtained with the command:

    helm inspect stable/prometheus

We shall change the default configuration to remove the scraping and just focus
on scraping the application to reduce the number of metrics that are available.

A pre-written configuration document is located in the project file
`prometheus-values.yml`.  Here is the relevant section to scrape the
application:

       410	    scrape_configs:
       411	      # A scrape configuration for the application
       412	      - job_name: ‘jersey-netty’
       413	        # Scrape every 10s (the default is 1m)
       414	        scrape_interval: 10s
       415
       416	        # Scrape pods in the kubernetes cluster
       417	        # By default Prometheus will access the URL with an HTTP GET request
       418	        #    http://<pod-ip>:<pod-port>/metrics
       419	        #
       420	        kubernetes_sd_configs:
       421	          - role: pod
       422
       423	        # Declare which pods should be scraped
       424	        relabel_configs:
       425	          # Idenitify pods from their app label whose value matches jersye-netty
       426	          - source_labels: [__meta_kubernetes_pod_label_app]
       427	            regex: jersey-netty
       428	            action: keep

(See the [scrape configuration documentation][prometheus-scrape-config] for more
details.)

Prometheus is configured to scrape each application pod at 10 second intervals.
In practice this may be too high a frequency and will place unnecessary load on
the application.  The default rate is 1 minute.  However, for demonstration
purposes the rate is appropriate as it will be easier to observe changes.

Install Prometheus:

    $ helm install stable/prometheus --name prometheus -f prometheus-values.yml
    NAME:   prometheus
    LAST DEPLOYED: Sun Jul 16 16:53:18 2017
    NAMESPACE: default
    STATUS: DEPLOYED

    RESOURCES:
    ==> v1/ConfigMap
    NAME                                DATA  AGE
    prometheus-prometheus-alertmanager  1     4s
    prometheus-prometheus-server        3     4s

    ==> v1/PersistentVolumeClaim
    NAME                                STATUS   VOLUME                                    CAPACITY  ACCESSMODES  STORAGECLASS  AGE
    prometheus-prometheus-alertmanager  Bound    pvc-f1905116-6a81-11e7-963e-32811a79cf32  2Gi       RWO          standard      4s
    prometheus-prometheus-server        Pending  standard                                  4s

    ==> v1/Service
    NAME                                      CLUSTER-IP  EXTERNAL-IP  PORT(S)   AGE
    prometheus-prometheus-node-exporter       None        <none>       9100/TCP  4s
    prometheus-prometheus-alertmanager        10.0.0.207  <none>       80/TCP    3s
    prometheus-prometheus-server              10.0.0.13   <none>       80/TCP    3s
    prometheus-prometheus-kube-state-metrics  None        <none>       80/TCP    2s

    ==> v1beta1/DaemonSet
    NAME                                 DESIRED  CURRENT  READY  UP-TO-DATE  AVAILABLE  NODE-SELECTOR  AGE
    prometheus-prometheus-node-exporter  1        1        0      1           0          <none>         2s

    ==> v1beta1/Deployment
    NAME                                      DESIRED  CURRENT  UP-TO-DATE  AVAILABLE  AGE
    prometheus-prometheus-server              1        0        0           0          2s
    prometheus-prometheus-kube-state-metrics  1        0        0           0          2s
    prometheus-prometheus-alertmanager        1        0        0           0          2s


    NOTES:
    The Prometheus server can be accessed via port 80 on the following DNS name from within your cluster:
    prometheus-prometheus-server.default.svc.cluster.local


    Get the Prometheus server URL by running these commands in the same shell:
      export POD_NAME=$(kubectl get pods --namespace default -l "app=prometheus,component=server" -o jsonpath="{.items[0].metadata.name}")
      kubectl --namespace default port-forward $POD_NAME 9090


    The Prometheus alertmanager can be accessed via port 80 on the following DNS name from within your cluster:
    prometheus-prometheus-alertmanager.default.svc.cluster.local


    Get the Alertmanager URL by running these commands in the same shell:
      export POD_NAME=$(kubectl get pods --namespace default -l "app=prometheus,component=alertmanager" -o jsonpath="{.items[0].metadata.name}")
      kubectl --namespace default port-forward $POD_NAME 9093

    For more information on running Prometheus, visit:
    https://prometheus.io/

Observe that helm created a number of related services and deployments, and also
created persistent volumes where Prometheus will store time-series data.  It
also provides instructions on how to access Prometheus, which we shall follow
so we can access via the browser.

List the helm installed packages:

    $ helm list
    NAME      	REVISION	UPDATED                 	STATUS  	CHART           	NAMESPACE
    prometheus      1               Sun Jul 16 16:53:18 2017        DEPLOYED        prometheus-3.1.0        default

Since the Prometheus service is, by default, configured using `ClusterIP` there
is no external and stable port or IP address to access the service from the host
system (it may be possible to configure to use `NodePort` or specify an external
IP but as of writing this has not been investigated).  We shall work around this
by following the access instructions to forward network traffic to/from the host
machine, on port 9090, and the Prometheus pod hosting the service (on it's
port):

    export PROMETHEUS_POD_NAME=$(kubectl get pods --namespace default -l "app=prometheus,component=server" -o jsonpath="{.items[0].metadata.name}")
    kubectl --namespace default port-forward $PROMETHEUS_POD_NAME 9090

You may want to run the port forward process in a separate shell or in the
background. (This approach is obviously far from ideal for a production system,
where the Prometheus service should have a stable URL.)

In the browser go to the URL
[http://localhost:9090/graph](http://localhost:9090/graph).  You should see
the main Prometheus page for executing query expressions over time-series data
to obtain results and render graphs.  The "insert metric at cursor"
drop down menu will present a number of JVM metrics.

Goto the Status drop down menu at the top and select the Targets item, which
will take you to the targets page.  This page shows the pods to scrape.  In this
case there should only be one such pod (if linearly following the tutorial.)

Go back to the graph page and type the following expression into the expression
text field and execute the query:

    jvm_memory_pool_bytes_used

The metric `jvm_memory_pool_bytes_used` is a guage reports the size on bytes
of various memory pools of the Java Virtual Machine, such as the spaces used by
the G1 garbage collector.

Nothing interesting is happening at the moment so lets create some
load then re-execute the query (keep re-executing while the work is being
performed):

    $ export JN_SERVICE=$(minikube service jersey-netty-service --url); \
        wrk -t1 -c1 -d30s $JN_SERVICE/work; \
        sleep 30; \
        wrk -t2 -c2 -d30s $JN_SERVICE/work; \
        sleep 30; \
        wrk -t4 -c4 -d30s $JN_SERVICE/work

Observe the change in pool values usage when the work was performed.

Create another graph that execute the following query:

    rate(jvm_gc_collection_seconds_count{gc="G1 Young Generation"}[20s])

This second plots the rate at which GC collections occur .  Notice the increase
in GC activity when work is performed.

So far we only have metrics for one pod.  Let's scale up the application:

    $ kubectl scale deployment jersey-netty-deployment --replicas 4
    deployment "jersey-netty-deployment" scaled

Notice that Prometheus now recognizes the additional pods at
[http://localhost:9090/targets](http://localhost:9090/targets).

Re-executing the previous queries will now overlay results for the three
additional pods.

Now we shall focus on the metrics of the work itself, perform some heavy work:

    $ wrk -t8 -c8 -d10m $JN_SERVICE/work

and execute the following query that will show the average request latency of
work for each pod:

    rate(app_work_requests_latency_seconds_sum[1m]) / rate(app_work_requests_latency_seconds_count[1m])

To sum over all pods execute the following query to get the overall average
request latency for work:

    sum by(job) (rate(app_work_requests_latency_seconds_sum[1m])) / sum by(job) (rate(app_work_requests_latency_seconds_count[1m]))

Note that pods will receive requests concurrently from the worker so while the
number of requests per second may be considered reasonable the latency of each
request may not.  As such the average request latency is likely to be
misleading.  What's more important is the latency for say 95% of the requests.

The metric `app_work_requests_latency_seconds_bucket` represents a histogram (as
previously explored) and this metric may be used to calculate the 95th
percentile with the following query:

    histogram_quantile(0.95,  sum (rate(app_work_requests_latency_seconds_bucket[1m])) by (job, le))

There should be a noticeable difference between the aggregated average request
latency and that for the 95th percentile, which is a more realistic indication
of what a customer is experiencing in terms of quality of service.  For example,
the aggregated average latency might be 0.75s per request, but the 95th
percentile might be that 95% of requests were returned in 1.4s or less.  Often
a service will set a latency goal expressed in terms of percentiles.

For a more detailed explanation of examples see how to query Prometheus
[part 1][prometheus-query1] and [part 2][prometheus-query2].

Prometheus will keep a history of the time-series data so it's possible to
experiment further querying metrics previously produced from work.  The
time-series data generating from producing work may be deleted with the
following curl commands:

    curl -XDELETE 'http://localhost:9090/api/v1/series?match\[\]=app_work_requests_latency_seconds_bucket'
    curl -XDELETE 'http://localhost:9090/api/v1/series?match\[\]=app_work_requests_latency_seconds_count'
    curl -XDELETE 'http://localhost:9090/api/v1/series?match\[\]=app_work_requests_latency_seconds_sum'

As you may have already discovered the browser-based service is only useful
for ad-hoc queries and testing/debugging.  For persistent storage of queries,
the displaying of graphs, and the grouping of graphs into a dashboard, another
service is required, which is the subject of the final section in this tutorial.

[prometheus-default-values-config]: https://github.com/kubernetes/charts/blob/master/stable/prometheus/values.yaml#L414
[prometheus-scrape-config]: https://prometheus.io/docs/operating/configuration/#<scrape_config>
[prometheus-query1]: https://www.digitalocean.com/community/tutorials/how-to-query-prometheus-on-ubuntu-14-04-part-1
[prometheus-query2]: https://www.digitalocean.com/community/tutorials/how-to-query-prometheus-on-ubuntu-14-04-part-2

## Deploying a Grafana service to query and render time-series data supplied by Prometheus

Grafana is a service to query, visualize, alert on and understand metrics.  It
can support a variety of data sources, one such source being data from a
Prometheus service.

Install Grafana:

    $ helm install stable/grafana --name grafana \
        --set server.adminUser=admin,server.adminPassword=password
    NAME:   grafana
    LAST DEPLOYED: Sun Jul 16 17:20:04 2017
    NAMESPACE: default
    STATUS: DEPLOYED

    RESOURCES:
    ==> v1/Service
    NAME             CLUSTER-IP  EXTERNAL-IP  PORT(S)  AGE
    grafana-grafana  10.0.0.155  <none>       80/TCP   1s

    ==> v1beta1/Deployment
    NAME             DESIRED  CURRENT  UP-TO-DATE  AVAILABLE  AGE
    grafana-grafana  1        1        1           0          1s

    ==> v1/Secret
    NAME             TYPE    DATA  AGE
    grafana-grafana  Opaque  2     2s

    ==> v1/ConfigMap
    NAME                    DATA  AGE
    grafana-grafana-config  1     2s
    grafana-grafana-dashs   0     2s

    ==> v1/PersistentVolumeClaim
    NAME             STATUS   VOLUME    CAPACITY  ACCESSMODES  STORAGECLASS  AGE
    grafana-grafana  Pending  standard  1s


    NOTES:
    1. Get your 'admin' user password by running:

       kubectl get secret --namespace default grafana-grafana -o jsonpath="{.data.grafana-admin-password}" | base64 --decode ; echo

    2. The Grafana server can be accessed via port 80 on the following DNS name from within your cluster:

       grafana-grafana.default.svc.cluster.local

       Get the Grafana URL to visit by running these commands in the same shell:

         export POD_NAME=$(kubectl get pods --namespace default -l "app=grafana-grafana,component=grafana" -o jsonpath="{.items[0].metadata.name}")
         kubectl --namespace default port-forward $POD_NAME 3000

    3. Login with the password from step 1 and the username: admin

Observe that helm created a deployment and service for the Grafana application,
and also created persistent volumes where Grafana will store data.  It
also provides instructions on how to access Grafana, which we shall follow
so we can access via the browser.

List the helm installed packages:

    $ helm list
    NAME      	REVISION	UPDATED                 	STATUS  	CHART           	NAMESPACE
    grafana         1               Sun Jul 16 17:20:04 2017        DEPLOYED        grafana-0.3.7           default
    prometheus      1               Sun Jul 16 16:53:18 2017        DEPLOYED        prometheus-3.1.0        default

Since the Grafana service is, by default, configured using `ClusterIP` there
is no external and stable port or IP address to access the service from the host
system (it may be possible to configure to use `NodePort` or specify an external
IP but as of writing this has not been investigated).  We shall work around this
by following the access instructions to forward network traffic to/from the host
machine, on port 3000 (note Prometheus will be using port 9090 as in the
previous section), and the Grafana pod hosting the service (on it's port):

    export GRAFANA_POD_NAME=$(kubectl get pods --namespace default -l "app=grafana-grafana,component=grafana" -o jsonpath="{.items[0].metadata.name}")
    kubectl --namespace default port-forward $GRAFANA_POD_NAME 3000

You may want to run the port forward process in a separate shell or in the
background. (This approach is obviously far from ideal for a production system,
where the Grafana service should have a stable URL.)

In the browser go to the URL
[http://localhost:3000/login](http://localhost:3000/login).  You should see
the Grafana log in page.  Log in the the user name `admin` and password
`password` that were configured when installing.

Click on the `Add data source` button to configure a new data source then:

- enter "Prometheus" in the `Name` text field type;

- select "Prometheus" in the `Type` dropdown box;

- enter the URL "http://localhost:9090" to the Prometheus service in the `Url`
  text field type;

- select "direct" in the Access in the `Access` dropdown box; and

- click on the `Add` button.

From the Home Dashboard click on `New dashboard` to create a new dashboard then:

- select the `Graph` button to create a new graph panel;

- click on the panel title (named "Panel Title") above and outside of the graph
  area to view the panel pop up menu; and

- select the `edit` button in the panel pop up menu.

You should observe a `Metrics` tab.  In the `Query` field enter the query:

    rate(process_cpu_seconds_total[1m])

This was the first query we used with Prometheus.  Create another graph panel
this time with the query (select `AddPanel` in the collapsible menu identified
by the the 3 vertical dots located to the left of the first panel):

    histogram_quantile(0.95,  sum (rate(app_work_requests_latency_seconds_bucket[1m])) by (job, le))

Now perform some work and observe changes in the two graphs:

    $ export JN_SERVICE=$(minikube service jersey-netty-service --url); \
        wrk -t8 -c8 -d10m $JN_SERVICE/work

Grafana renders both queries with far high quality and with more configurability
than Prometheus, and of course the newly created dashboard can be saved.  It is
anticipated that significant effort may be spent fabricating dashboards for
services as the information they expose and monitor is critical to understanding
if a service is functioning normally with an SLA and if not be notified quickly
to ascertain why not.  (Note dashboard can also be shared or be created from
templates.)
