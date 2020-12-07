# Akka/HTTP-based - Chord Simulator
##### Team: Amedeo Baragiola (Leader), Amna Irfan, Matteo Pelosi, Georgia Fiscaletti 
##### You can obtain this Git repo using the command ```git clone https://{bitbucket_username}/abarag4/amedeo_baragiola_project.git```.


## Introduction:
This project is an implementation of a cloud simulator in Scala. 
This simulator uses Akka-based actors to implement client and server nodes and the communication between them.
We use consistent hashing to generate server and client entity ids and place them on our 
network. Once initialized, we use Chord again to put and retrieve files across the network. 
To simulate failed requests, our auto simulator uses R to generate random values based on a 
chosen distribution. 

##  Instructions:
1. Install [IntelliJ](https://www.jetbrains.com/student/), JDK, Scala runtime, IntelliJ Scala plugin and the [Simple Build Toolkit (SBT)](https://www.scala-sbt.org/1.x/docs/index.html) and make sure that you can run Java monitoring tools.
2. Install [R-QuickStart](https://rstudio.com/products/quickstart/) to allow our Chord simulator to run with an x distribution.
3. Please set up your account with [Dockerhub](https://hub.docker.com/) so that you can pull the docket image for this project and run locally.
4. Open the project in IntelliJ and build the project. This may take some time as the Library Dependencies mentioned in the build.sbt file will be downloaded and added to the classpath of the project.
5. Alternatively, you can run the project using command line. Open the terminal and `cd` into the project directory. 

This project includes a single main task that exposes the REST API to our Akka-based Chord simulator.

There are two ways to run the simulation;

### Run Simulation Locally:
In order to start the REST API, run the following command, ```sbt clean compile run```. 

Now you have access to the simulation via the following endpoints.

#### Request
`GET /start-simulation`


    curl -XGET http://localhost:8080/start-simulation

```<state><response>Success</response></state>```
###### Disclaimer: To see the output, check server logs.

#### Request
`GET /stop-simulation`


    curl -XGET http://localhost:8080/stop-simulation

```<state><response>Success</response></state>```
###### Disclaimer: To see the output, check server logs.

#### Request
`GET /simulation-state`


    curl -XGET http://localhost:8080/simulation-state

```<state><response>Data saved to file dump.xml</response></state>```
###### Disclaimer: To see the output, check dump.xml in root directory.

#### Request
`GET /movie`


    curl -XGET curl http://localhost:8080/movie?name=sample&initialClientId=0&initialServerId=0

```<state><response>Success</response></state>```
###### Disclaimer: To see the output, check server logs.

#### Request
`POST /movie`


    curl -XPOST curl http://localhost:8080/movie?name=sample&initialClientId=0&initialServerId=0

```<state><response>Success</response></state>```
###### Disclaimer: To see the output, check server logs.

#### Test

`sbt clean compile test` - We have five unit tests, they all should pass. 

### Run Simulation on Docker:
In order to run the simulation using our [docker image](https://hub.docker.com/repository/docker/airfan5/akka-chord/general), run the following commands

1. `docker pull airfan5/akka-chord:latest` - Pull the image from Docker hub
2. `docker images` -  Ensure the image airfan5/akka-chord is pulled.
3. `docker run airfan5/akka-chord` - Run the image 

In order to create a docker image, use our Docker configuration file and run the following commands to build the image and deploy on Docker hub.

1. `docker build -t chord-simulator:v1 .` - Build image using the Docker Config file: 
2. `docker images` - Ensure the image chord-simulator is built
3. `docker run chord-simulator:v1` - Run the docker image
4. `docker login` - Login to your Docker account
5. `docker tag {image_id} {repo}` - Get image id and run
6. `docker push {repo}` - Push to the Docker Hub


## Chord Implementation
The Chord algorithm was implemented in Scala following the paper _Chord: A Scalable Peer-to-peer Lookup Protocol for Internet Applications_ from I. Stoika et al. (the one uploaded on Piazza), and was integrated with the **Akka** toolkit in order to build a concurrent, distributed and message-driven application for our simulator. 
### Functions
The main functions of the algorithm (see the pseudocode shown at pp. 5-6 of the paper) are implemented in the ServerActor.scala file. 

- `findSuccessor()`: one of the most important functions of the algorithm. It is used to find an eligible successor of a newly joined node. It takes as parameter the `ActorRefs` of the picked node in the ring and its successor's, and the one of the newly joined node. As showed in the paper, it checks whether the _id_ of the new node is included in the segment of the Chord ring obtained by moving clockwise from (but not including) the selected node in the ring until reaching (and including) its successor, using the function `belongs()` (explained below). If that is the case, the function returns the `ActorRef` of the successor of the selected node in the ring. Otherwise, the function `closestPrecedingNode()` (explained below) is called, and the `findSuccessor()` function is forwarded to the closest node that precedes the selected node in the ring. If the `ActorRef` returned by `closestPrecedingNode()` is the same as the selected node in the ring, we assume that the node selected is the right successor of the new node and we return its `ActorRef`. Otherwise, the request is forwarded via a `Future[ActorRef]` to the returned node, calling remotely `findSuccessor()` recursively until the eligible successor is found. We then wait for the remote calls to return and return the final `ActorRef`.
- `findSuccessorID()`: this function works exactly as `findSuccessor()`, but takes the _id_ of the nodes (`int`) instead of their `ActorRef`. It is used in the calls contained inside the `fixFingers()` function (explained below), since we need to find a successor starting from the _hops_, that most of the times do not correspond to any node in the ring. Since we are using ids instead of `ActorRef`, instead of calling the main `closestPrecedingNode()` function we call the variation `closestPrecedingNodeID()`, which returns an id (`int`) instead of an `ActorRef`. When an eligible successor is found, the `ActorRef` is obtained using `ActorSelection`. 
- `closestPrecedingNode()`: this function performs a reverse visit of the caller's finger table and checks with the function `belongs()` if the successor contained in the given finger table entry is included in the segment of the Chord ring obtained by moving clockwise from (but not including) the caller node until reaching (and not including) the newly joined node. If the condition is satisfied, the function returns the `ActorRef` of the node corresponding to the selected entry of the finger table, otherwise if no correspondence is found the caller's `ActorRef` is returned. 
- `closestPrecedingNodeID()`: as `findSuccessorID()`, this function works as `closestPrecedingNode()` but takes the _id_ of the nodes (`int`) instead of their `ActorRef`. It is called only by `findSuccessorID()`. 
- `getPredecessor()`: getter function that returns the `ActorRef` of the caller node. 
- `updateFinger()`: function that updates an entry of the finger table, called by `FixFingers()`. It looks for the eligible successor for the hop corresponding to the selected entry of the finger table using `findSuccessorID()` and updates the finger table of the given node with the `ActorRef` of the new node (`succN`). 
- `belongs()`: utility function to check if a node is included in the segment of the Chord ring obtained by moving clockwise from one node (`lowerBound`) to another (`upperBound`). It takes the flag `inFindSuccessor` as a parameter to distinguish if the function is called in the `findSuccessor()` or `findSuccessorID()` functions - where the `upperBound` of the segment must be included - or if it is called anywhere else (i.e. `closestPrecedingNode()`, `stabilize()`, `notify()`) - where the `upperBound` of the segment is excluded in the condition.
- `receive()`: override of the Akka framework function to handle the reception of messages in the actor.
- `active()`: callback function to handle every action performed by the actor. Every time the state of the actor is updated, `context become active()` is called to perform the update. All the attributes of the state are passed as a parameter to the `active()` function in order to update them without the need of storing `var` inside the state of a single actor. In this way, we have a fully functional implementation. The actions handled by `active()` are listed below: 

	- `Create()`: function fired by the first server in _ChordSimulator.scala_ to create the Chord ring with the first node. It takes as parameters the number of nodes to spawn in the server (`queue`) and the reference to the server that sent the request (`replyTo`). It creates the first node setting its `successor` to itself and its `predecessor` to null, updates the state and sends an asynchronous message back to its sender containing the call to `CreateRespond()` which spawns a number of nodes equal to `queue` and joins them in the ring. 
	- `Join()`: function fired by a node to join the Chord ring. It takes as a parameter the node in the ring selected for the join (`existingNode`), the number of nodes in the queue (`queue`) and the reference to the new node to be added in the ring (`replyTo`). It sets the `predecessor` of the new node to null. Its `successor` is asked using a `Future[ActorRef]`, which calls `findSuccessor()` on the node in the ring selected for the join. When the successor is returned, the state is updated and a `JoinRespond()` is sent back to the sender.
	- `FindSuccessor()`: remote call for `findSuccessor()` to forward the function call to a node different from the actor itself.
	- `FindSuccessorID()`: remote call for `findSuccessorID()` to forward the function call to a node different from the actor itself. 
	- `Stabilize()`: function called periodically to stabilize the Chord ring after new nodes joined the network. If the `successor` of the actor is the actor itself (i.e. when `stabilize()` is called for the first time on the first node added to the ring), we simply set `x` to the `predecessor` of the actor. If the `x` is not `null`, we check if it is included in the segment of the Chord ring obtained by moving clockwise from (but not including) the caller node until reaching (and not including) its `successor`. If the condition is satisfied, we set `x` as the `predecessor` of the actor and update its state and call `StabilizeRespond()` on the actor. Otherwise, we just call `StabilizeRespond()` on the actor. If the call is not local (i.e. if the `successor` of the caller node is not the node itself), we forward the `GetPredecessor()` call to its `successor`. In this case, as explained below, `GetPredecessorRespond()` will handle the state update, the eventual update of the actor's successor and the call to `StabilizeRespond()`. 
	- `GetPredecessor()`: calls the `getPredecessor()` function on the caller node, whose reference is passed as parameter (`replyTo`), and calls `GetPredecessorRespond()` on the caller node passing the wrapped `ActorRef` of the node returned by `GetPredecessor()`.
	- `GetPredecessorRespond()`: takes as parameter the wrapped `ActorRef` of the predecessor returned in `GetPredecessor()` (`wrapper`). If the node in the wrapper is not null, we check if it is included in the segment of the Chord ring obtained by moving clockwise from (but not including) the caller node until reaching (and not including) its `successor`. If the condition is satisfied, we set it as the `predecessor` of the actor and update its state and call `StabilizeRespond()` on the actor. Otherwise, we just call `StabilizeRespond()` on the actor. 
	- `StabilizeRespond()`: simply forwards a call to `Notify()` to the actor's `successor`, passing `self` as parameter.
	- `Notify()`: takes the reference to the node which forwarded the `Notify()` request to its `successor` as a parameter (`n1`). If the `predecessor` of the caller is not `null`, it checks whether `n1` is is included in the segment of the Chord ring obtained by moving clockwise from (but not including) the `predecessor` of the caller node until reaching (and not including) the caller node. If the condition is satisfied, it sets `n1` as the `predecessor` of the caller node and updates its state. Otherwise nothing happens. If the `predecessor` of the caller was `null` at the beginning of the function call, it is simply set to `n1` and the state of the caller is updated.
	- `FixFingers()`: function called periodically to update the finger tables after new nodes joined the Chord ring. `next` stores the index of the next finger to fix and is a parameter to the `active()` callback. Its value is increased by 1 at the beginning of `FixFingers()`. If its value exceeds `m` it is reset to 1, the call to `updateFinger()` is performed on the first entry of the finger table and the state of the actor is updated. Otherwise, we update the entry of the finger table corresponding to `next1` (the updated value of `next`) calling `updateFinger()` on the selected entry and update the state of the actor. After the updates are performed, we send a call to `FixFingersRespond()` to the actor.
	- `FixFingersRespond()`: simply prints the updated finger table of the caller.
	- `CheckPredecessor()`: function called periodically to check if the `predecessor` of the called has failed. The eventual failure is checked by pinging the `predecessor` and storing the result of the ping in a `Future[Boolean]`. In case of failure the `predecessor` is set to null allowing the node to eventually accept a new `predecessor`. 
	- `Ping()`: function to ping the `predecessor` of a node in `CheckPredecessor()`. 
	- `Get()`:
	- `Put()`:

### Fault tolerance

As explained in the paper, the correctness of the Chord algorithm relies on the fact that each node knows its successor. This condition might not hold if one (or more) nodes fail. In our implementation, fault tolerance is handled by the `Stabilize()`, `FixFingers()` and `CheckPredecessor()` functions, called periodically to check the status of the ring and fix eventual incongruences in finger tables and in the nodes' successors and predecessors. If a node fails, its failure will be caught as soon as `CheckPredecessor()` is called on its successor in the ring. `CheckPredecessor()` will clear the predecessor field of the node if the predecessor has failed, and the node will be ready to accept a new predecessor - as soon as new calls to `Stabilize()` will be performed by the node itself and the nodes in its neighborhood. When a node fails, it is removed from the ring, and consecutive calls to `FixFingers()` on the other nodes in the ring will progressively remove the reference to the failed node from the finger tables. The time for recovery increases as the number of nodes that fail simultaneously increases, as shown in the simulation presented below. This happens because the size of the hops increases exponentially, and the further we jump, the higher is the probability that there are other nodes between two hops that are not "seen" by the node we are jumping from. If more than one node fails, the successor of our node might be one of those "unseen" nodes, but the node will only see the closest node to the hop corresponding to the first valid entry of the finger table. In this case, joins and lookups might take more time to find a specific location in the ring, and we will need more calls to `Stabilize()` and `FixFingers()` before the saved state of the Chord ring converges to the actual state.

### Simulations

In this section, we present the variations of the system's behavior with different parameters.

### System stabilization

First off, we ran different simulations changing the number of nodes in the ring. Depending on the number of nodes, the system needs a different number of calls to `Stabilize()` before the ring converges to the correct state. The number of calls needed to stabilize the system appears to be proportional to the number of nodes added to the system. The number of calls to `FixFingers()` needed is instead always equal to the number of entries in the finger table, i.e. equal to `m` - once the nodes are stabilized. 
