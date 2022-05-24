# Blockmess β
Blockmess is a scalable and modular Distributed Ledger used as an application agnostic software module.

This repository holds the Java (not yet) open source library with the Distributed Ledger logic.

This package contains the source code (src/), jar file (target/BlockmessLib.jar), running scripts (scripts/), and configuration files (config/) for the project. Blockmess requires the Java Runtime Environment version 11 or higher.

### β Considerations
This is still a work in progress, so all feedback is appreciated.

We consider the package ready for use in the context of the integration with projects in the CSD course (DI/FCT/UNL, 2021/2022).

There is room for improvement, both in the interaction with the applications and in the internal operation of Blockmess.
Especially in the first front, I may be missing some crucial quality of life features that would massively simplify the end developer's application, while being simple to implement in Blockmess.
The case for the above integration on CSD projects will be also a relevant testbench for this purpose, being certainly an interesting research task.

Finally, as much as it pains me to say. There might be some bugs here and there.
Should this be the case we will help to overcome or to help with any issue in the context of the CSD course.

Every improvement suggestion and error detected will benefit this project and provide a better product to all who use it.

***

## 1 - Quick Start Example

As a library, Blockmess should be incorporated into other programs rather than running as a standalone program.

For example, concerning the requirements of CSD project, the provided library allows for an integration model similar to the architectural model initially developed.

Section 3.1 and its subsections explain the **ApplicationInterface** class.
It is through this class that the developed applications can access Blockmess' functionalities.

Sections 4.1 and 4.2 present a detailed description of all the configuration properties that can be found in the file **config/config.properties**.
Section 4.3 shows how to override the properties in the configuration file and finally section 4.5 suggests the minimum configurations that should be modified to complete the CSD project.

Section 5 presents a walkthrough of the AsyncCounter demo application.
In this demo we put in practice the information presented in the previously mentioned sections.

Finally, section 7 explains the scripts provided to run Blockmess.
Note that when changing from baremetal executions to containerized executions, some knowledge of the parameters shown in section 4 is required.

***

## 2 - Highlight Features

### 2.1 - Totally Ordered Operations
As a Distributed Ledger, Blockmess aggregates operations in blocks that are eventually delivered to any application replica in a total order.

By default, application content is not delivered to the application upon receival of the content, or when such content is placed in blocks. The content is only delivered to the application when the blocks they are placed are confirmed/finalized.

Following this behaviour, we guarantee that all replicas execute operations in the same order and no duplicate operations are delivered.

#### 2.1.1 - Liberty to have lower consistency guarantees
Altough the default behaviour of Blockmess ensures a total order of operations, our system allows the application to extract content during its ordering process; reducing the latency in the delivery of content, at the cost of having lower consistency guarantees.

We must emphasise that as of this release, the application interface for the retrieval of content following these mechanisms is not as clean as with totally ordered operations.

##### 2.1.2 - FIFO Content Retrieval:
As soon as content is received in a replica during its dissemination, it can be extracted and processed. This ensures a FIFO delivery of content guaranteeing application operations are processed as soon as they are available.

##### 2.1.3 - Speculative Total Order:
The application has access to blocks as soon as they are received by the replicas. The application can use the content on these blocks as soon as they are received or wait for some further blocks, processing them with no guarantees the content in them will actually be delivered; however being aware that the probability the content is delivered is high.

We emphasise that depending on the parameterization of Blockmess, the delivery of content not yet finalized can open some attack vectors to the application, that would otherwise be detected by simple Blockchain implementations.
One such example is the Double Spend attack found in cryptocurrency applications.

### 2.2 - Application oblivious
Blockmess was designed as a replication module able to service any application.
The system does not require specific types of content and gives the application the freedom to process its information however it sees fit.

As long as the application content can be serialized it is accepted by Blockmess.

### 2.3 - Plugable Modularity
Despite being a functional and scalable Distributed Ledger implementation out of the box, Blockmess its components to be swapped by other implementations granting unprecedented freedom to the developer beyond what can be achieved through simple parameterization.

The Blockmess was designed with modularity as one of its core tenets from the ground up, facilitating the plugability of these modules.
	
### 2.4 - PoW abstraction
Blockmess developers hate harming the environment, and so our default block proposal protocol is not a PoW variant.

However, it mimics the functionality of a PoW system, providing the same distribution as these mechanisms, without requiring an high computational load on the replicas.

**Disclaimer:**
The default implementation simulating PoW is not resistant against an adversary that modifies Blockmess' code to give itself an advantage.

If using Blockmess for the deployment of a real application, please modify the implementation of the Sybil Resistant Election protocol (and to do so keep reading untill we mention the modularity features).

### 2.5 - High Dynamic Throughput
Incorporating performance enhancing mechanisms from the Parallel Chains approach to Distributed Ledger scalability, Blockmess is able to achieve a very high throughput.

The use of Parallel Chain solutions by themselves has drawbacks, such that their use with incorrect parameterizations may lead to a deteriorating performance.
This problem is exacerbated by the variability in application load exerted over Blockmess. What is an optimal parameterization at a given point, may prove suboptimal in periods of higher load.

The great innovation of Blockmess is that it modifies its internal structure to adapt to the application requirements, thus achieving a nearly optimal balance between throughput and latency.

### 2.6 - Extensive Configuration Potential
Blockmess is highly configurable, allowing tweaks to all software modules.

This parameterization is simple and well documented.
The properties on the configurations file can be overwritten upon launching any replica, allowing great flexibility when running more than one replica on a single machine, while simultaneously not hindering simpler launch processes of a single replica per host.

***

## 3 - Application Layer Extensions:

### 3.1 - ApplicationInterface
Blockmess is suited to interact with any application by extending a set of classes.

However, the only mandatory class to be extended is the **applicationInterface.ApplicationInterface**

#### 3.1.1 - Instancing
- public ApplicationInterface(String[] blockmessProperties)

When creating an instance of this class, the Blockmess system is launched. The argument *blockmessProperties* contains a list of properties that are to override those in the configuration file.

There can only be a single *ApplicationInterface* instance in the program.

#### 3.1.2 - Operation Submission
Operations are submitted to Blockmess in an application agnostic manner.

To do so, the *ApplicationInterface* will receive all operations as byte arrays.

- public Pair<byte[], Long> invokeSyncOperation(byte[] operation)

This instruction allows a replica to submit the **operation** in the parameter to the Blockmess.
After this operation is ordered with the remaining operations it is delivered to every correct replica, which then process it in a total order.

The return value **Pair<byte[],Long>** contains the response to the operation submitted in the left-hand side and the global operation index on the right-hand side.  

This method blocks the calling software thread until the operation is processed and the return value is delivered.

- public void invokeAsyncOperation(byte[] operation, ReplyListener listener)

This operation also allows a replica to submit an operation to be processed by all replicas.

In contrast with *invokeSyncOperation*, the *invokeAsyncOperation* does not block the calling thread allowing it to advance while Blockmess handles the processing of the operation.

The result of the operation is returned to the replica which has submitted the operation through the use of a **applicationInterface.ReplyListener**, implemented by the application and passed as the second argument.

This interface has a single operation:
- void processReply(Pair<byte[], Long> operationResult);

The *processReply* argument parameter corresponds to the return value of *invokeSyncOperation*.

The processing of the responses in a replica by the *ReplyListener* is sequential, following the order operations were processed.

#### 3.1.3 - Operation Processing
Being application agnostic, Blockmess does not know how to process the operations submitted by the application.
As a result, it is the application responsibility to process them.

The *ApplicationInterface* provides the following abstract method to allow the processing of operations.

- public abstract byte[] processOperation(byte[] operation)

This method is executed by all correct replicas, not only the replica that has submitted the operation.
The received as argument represents an *operation* submitted by a replica (using either of the operation submission methods presented earlier).
This method is executed sequentially according to the delivery order of the operations, ensuring all replicas process the operations in a total order.

The return value of this method is the left-hand side of the invocation response that will be delivered to the replica that issued the operation.

#### 3.1.4 - Block Monitoring
While not required for the correct execution of the system, the application can access blocks as they are received by the application and is notified which blocks are finalized and which are discarded.

To access these functionalities, the application class extending *ApplicationInterface* must override the following methods:

- public void notifyNonFinalizedBlock(BlockmessBlock block)

The argument of in this operation contains a validated block that was just received by this replica, and that is yet to be ordered.

From this block several important metrics can be extracted; such as:
- The block identifier;
- The content placed in the block;
- The parallel chain where the block was placed;
- The block proposer;
- The parallel chains in use from the point of the block proposer;
- Other blocks this one references;

In extremis, with this information, the application can create an accurate representation of the Blockmess internal state and even build its own block ordering mechanism replacing Blockmess.

It cannot however, propose the creation or merge of parallel chains.

The other method the application can override is:

- public void notifyFinalizedBlocks(List\<UUID> finalized, Set\<UUID> discarded)

This method notifies the application which blocks have been finalized and which have been discarded.

The arguments received are not the blocks themselves, but rather the identifiers of the blocks.
If the application desires to access the block content from these identifiers, it must make use of the *notifyNonFinalizedBlock* method previously described.

The first argument provides a list of finalized block identifiers in the order they are finalized. The second argument provide a set of blocks that were discarded because they forked the longest chains.

***

### 3.2 - Secondary Extensions
Beyond the core functionalities required by the application and covered by the *ApplicationInterface* class, there are other functionalities that may benefit from an interaction with the application.

These functionalities have a default simple behaviour managed by Blockmess, but can be overridden by the application by replacing the implementation of a set of key classes. 

#### 3.2.1 - CMuxIdMapper
Blockmess deterministically maps application content to specific chains using a data structure we designated **Content Multiplexer (CMux)**.

The submission of content to the *CMux* requires it to be associated with two byte array non-unique values.

These values are not disseminated and must instead be derived from the operations themselves.
The mapping of content to these *CMux* related numbers are performed by implementations of the **cmux.CMuxIdMapper**.

This interface has two functionalities:
- byte[] mapToCmuxId1(byte[] operation)
- byte[] mapToCmuxId2(byte[] operation)

Each of these methods maps the operation to be submitted by a replica and which is received as argument, to a value that determining the placement of the content in the Blockmess.

#### 3.2.2 - FixedCMuxIdMapper
The class **cmux.FixedCMuxMapper** implements the *CMuxIdMapper* interface and is used by Blockmess to map the operation content to the values used by the *CMux*.

Besides the functionalities provided by *CMuxIdMapper*, this class provides the following operation:

- public void setCustomMapper(CMuxIdMapper mapper)

With this method, the application can override the
default behaviour of Blockmess by providing the *CMuxIdMapper* implementation received as parameter.

#### 3.2.3 - DefaultCMuxMapper
The default *CMuxIdMapper* behaviour provided by Blockmess is implemented in the class **cmux.DefaultCMuxMapper**.

This implementation ensures that the values used in the *CMux* are uniformly distributed, which in turn ensures the content associated with the several parallel chains is balanced.

Because the *DefaultCMuxMapper* is agnostic to application content it is oblivious to possible patterns in the content that may increase the computational efficiency of the mapping operations.

Additionally, the application may benefit from ensuring specific operations are placed in the same chain, or otherwise ensure some operations are placed in different chains.

If either of these aspects proves beneficial to the application, it should replace the *DefaultCMuxMapper* used by the *FixedCMuxMapper*.

***

#### 3.2.4 - ApplicationAwareValidator
Blockmess validates the structure of blocks to ensure that no invalid block is delivered.
However, being application agnostic, the system is unable to determine the validity of the operations submitted.

Nevertheless, Blockmess provides the application the opportunity to validate application content by using the **validators.ApplicationAwareValidator** interface.

This interface provides the following functionalities:

- Pair<Boolean, byte[]> validateReceivedOperation(byte[] operation)
- boolean validateBlockContent(BlockmessBlock block)

The **validateReceivedOperation** method is called whenever a replica receives an operation from the broadcast protocol. This operation is the argument received.

The return value of the operation is a pair whose left-hand side represents whether the operation is valid or not.

If the operation is valid, the right-hand side of the return value is ignored.

Otherwise, the operation is discarded, its dissemination is halted, and the right-hand value is the response to the replica that issued the operation.

The **validateBlockContent** method is called whenever a block is received by a replica. As argument the method receives the block to be validated itself.

The return value indicates whether all content in the block is valid.
Should this value be false the block is discarded, and it's dissemination ceased.

The operations of the block in of themselves are valid, according to the *validateReceivedOperation* execution. It is only the placement of the operations in the received block that is invalid. For this reason, the replica issuing the operation is not notified of the operation failure, as it will be eventually be placed in another block.

#### 3.2.5 - FixedApplicationAwareValidator
The class **validators.FixedApplicationAwareValidator** implements the *ApplicationAwareValidator* interface and is used by Blockmess to validate the operations and blocks as they are broadcast.

Besides the functionalities provided by the *ApplicationAwareValidator*, this class provides the following operation:

- public void setCustomValidator(ApplicationAwareValidator validator)

With this method, the application can override the default behaviour of Blockmess by providing the *ApplicationAwareValidator* implementation received as parameter.

#### 3.2.6 - DefaultApplicationAwareValidator
The default *ApplicationAwareValidator* behaviour provided by Blockmess is implemented in the class **validators.DefaultApplicationAwareValidator**.

In this implementation, all operations and blocks are deemed as valid.
While considering all operations valid may appear unsafe to the point that the application should be forced to provide a more strict implementation for the *ApplicationAwareValidator*, we note that all the harm to the system's safety these operations can cause is done during the processing of the operation, after having been totally ordered.

At such point, during the processing of the operation, the application can validate them.
This approach is equal to that used in other operation ordering protocols besides Distributed Ledgers and has two advantages:

- The validation of the content is agnostic to the inner workings of the operation ordering system. Validating in the *validateBlockContent* method implicitly forces the application to be aware of the inner workings of the underlying system;
- During the validation of a block, the validation of the block content is (probably) the most time-consuming step, and thus may significantly increase the dissemination time of a block, which in turn significantly lowers the performance of Blockmess.

The only disadvantage of validating only when operations are processed is the bandwidth waste caused by invalid operations and blocks.

A compromise can be reached by implementing validation logic only for the *validateReceivedOperation* method.
If this is done, the validation must be repeated when the operations are processed, otherwise, a byzantine replica that placed an invalid operation in a block could attack the state of correct replicas.

***

#### 3.2.7 - TODO: OperationStorage
Currently, Blockmess stores operations submitted by the application in memory until they are delivered to the application.
If the application load is very high in relation to the throughput of Blockmess, maintaining these operations in memory may prove too expensive.

By allowing the application to store the content in disk however it saw fit, this problem could be obviated.

This mechanism is to be implemented, and the interface should be similar to what appears in the previous sections.

the *OperationStorage* would have the methods:

- public void storeOperation(UUID opId, byte[] operation)
- public void getOperations(Collection\<UUID> opIds)

The first would request the storage of an operation with an identifier generated by Blockmess, while the second method requests the retrieval of content based on the identifiers received as arguments of the first method.

Besides this interface there would be two classes with functionalities analogous with their corresponding classes in the previous sections:

- FixedOperationStorage
- DefaultOperationStorage

***

## 4 - Parameters:
Blockmess provides a series of parameters to configure both how each replica will run and global parameters influencing the performance and security of the replicas.

These parameters are placed in the **config/confg.properties** file.

### 4.1 - Global:
The global properties define the general behaviour of the system.
These properties must be equal among all correct replicas, otherwise they will not be able to communicate correctly.  

#### contact=[IP:port]
The contact property indicates the IP address and port of the contact node. 
The contact node answers connections of joining nodes to form the P2P overlay network.

When running the Blockmess, one must ensure that the contact node is initialized before adding other nodes. 

***Example:*** contact=127.0.0.1:6000

#### minNumChains=[Val]
This property indicates the minimum number of chains to be used by Blockmess.
Independently of the application load, the number of chains in use will never fall bellow the value attributed to *minNumChains*.

By keeping this value low, Blockmess can optimize the latency of operation delivery when the application throughput demands are low.
However, if there is a spike in application load, Blockmess will take a longer time to adapt the number of chains in use to the application needs.

***Example:*** minNumChains=1 

#### maxNumChains=[Val]
This property indicates the maximum allowed number of parallel chains in use.
Independently of application load, the number of chains in use will never exceed the value attributed to *maxNumChains*.

The higher the number of parallel chains, the greater the amount of metadata sent in blocks.
At a given point, the amount of metadata transmitted ensures that increasing the number of parallel chains reduces the achievable throughput.

The value in this parameter should be the cutoff point beyond which the throughput deteriorates with an increase in the number of chains.

***Note:*** The greater the maximum allowed block size, the higher the number of parallel chains that can be employed before deteriorating the throughput.

***Example:*** maxNumChains=85

#### initialNumChains=[Val]
This property indicates the number of parallel chains employed when a replica is launched.
The value attributed to *initialNumChains* should not be lower than the value of *minNumChains* nor higher than the value associated with *maxNumChains*.

The hierarchical structure of the generated chains follows the expected theoretical model for the chain tree when the content is uniformly distributed.
To maintain the structure balanced, the number of initial chains should belong to the following succession:

<img src="https://latex.codecogs.com/svg.image?\large&space;\bg{black}chains\_epoch(epoch)=&space;\left\{\begin{array}{ll}&space;&space;&space;&space;&space;&space;1&space;&&space;epoch&space;=&space;0&space;\\&space;&space;&space;&space;&space;&space;3&space;&&space;epoch&space;=&space;1\\&space;&space;&space;&space;&space;&space;2&space;chains\_epoch(epoch&space;-&space;2)&space;&plus;&space;chains\_epoch(epoch&space;-&space;1)&space;&&space;epoch&space;\geq&space;2&space;\\\end{array}&space;\right." title="https://latex.codecogs.com/svg.image?\large \bg{black}chains\_epoch(epoch)= \left\{\begin{array}{ll} 1 & epoch = 0 \\ 3 & epoch = 1\\ 2 chains\_epoch(epoch - 2) + chains\_epoch(epoch - 1) & epoch \geq 2 \\\end{array} \right."  alt=""/>

***Example:*** initialNumChains=11

#### finalizedWeight=[Val]
This parameter indicates how deep a block must be within the longest chain of a blockchain to be finalized in it.
It should be noted that a block is not delivered to the application as soon as it is finalized within its own blockchain.
It must first be ordered against the finalized blocks of all other parallel chains.

Its value depends on the adversary presence and the ratio between the block dissemination time and block proposal time.

***Example:*** finalizedWeight=6

#### genesisUUID=[UUID String Representation]
This property defines the identifier of the original chain in the Blockmess system.
Currently, there is no reason to change the default value, however, should in the future the system evolve to having several Blockmesses in parallel, these need to have different ids.

The chain identifiers are UUID instances that have a specific String representation.

***Example:*** genesisUUID=00000000-0000-0000-0000-000000000000

#### expectedNumNodes=[Val]
In PoW based Distributed Ledgers, the difficulty of the cryptographic puzzles dictating the proposal of new blocks are dependent on the total computational power of the system replicas.

Similarly, Blockmess requires an estimate of the number of replicas in order to determine the difficulty of the cryptographic puzzles of the algorithm simulating PoW.

This property indicates an estimate of how many replicas are active in the system.

***Example:*** expectedNumNodes=200

#### timeBetweenQueries=[Milliseconds]
The algorithm employed to simulate PoW also consists on having replicas attempt to find verifiable solutions to a cryptographic puzzle in order to propose blocks.

Unlike PoW solutions where the process to identify these solutions is resource intensive, in Blockmess replicas wait a given amount of time between every attempt to find a valid solution to the cryptographic puzzle.

The value in this property indicates the amount of time replicas wait between attempts to find a valid solution.

***Note:*** This value should be low when there are few nodes and the average time between block proposals is short.

#### expectedTimeBetweenBlocks=[Milliseconds]
In Distributed Ledgers, the safety properties and performance metrics of the system are maintained by guaranteeing the average time between block proposals follows a certain amount of time.

This property indicates the average time interval between block proposals.

***Example:*** expectedTimeBetweenBlocks=20000

#### maxBlockSize=[Bytes]
In Distributed Ledgers, blocks have a maximum size beyond which they are deemed invalid.
This property indicates the maximum allowed size of a valid block.

The size of a block is a very important and nuanced factor in the performance of a Distributed Ledger.
It both determines the amount of application content that can be delivered in each block, but also the time a block requires to be disseminated throughout the network.

***Example:*** maxBlockSize=100000

#### delayedValueTimer=[Miliseconds]
In *Lazy-Push* broadcast protocols, a node can request a value from another upon having received a notification that content is available to be disseminated.

This property determines the amount of time a node waits for the response of a content request before attempting to contact another node to retrieve the content.

***Note:*** Our implementation of the *Lazy-Push* broadcast is greedy in the sense that when it receives a block, a replica disseminates to its neighboors that it has a valid block, and only then validates it. If the block validation is a slow process, the value of this timer should be considerable. 

***Example:*** delayedValueTimer=2000

***

###	4.2 - Instance specific:
The instance specific properties refer only to a single replica.
Different replicas must have some values different in these configurations, otherwise they'd be indistinguishable to the system and other replicas.

When running several replicas in the same machine or through a script, these are the properties that will be overridden while launching the replica.

#### interface=[InterfaceName]
This property indicates the network interface being used by the replica in the communications of the program.

***Note:*** In Linux distros, the interfaces available can be seen running the command *ip addr*.
When running locally, it's recommended that the interface *lo* is used.
This interface routes the messages back to the machine that sent them.
When running this program on containers or in a distributed deployment, use other interfaces.

***Example:*** interface=lo

#### address=[IP]
This property indicates the IP address of used by this replica in the interface provided in the previous property.

***Warning:*** Program has not been tested with IPV6 addresses.  

***Example:*** address=localhost

#### port=[Val]
This property indicates the port where this node opens connections in the *Peer Sampling Protocol*.

***Example:*** port=6000

#### redirectFile=[Pathname]
Blockmess redirects output logs to a file for future processing.
This property indicates the pathname of the file the logs will be redirected to.

***Example:*** redirectFile=outputLogs/redirectLog.log

#### (Deprecated) isBootstraped=[T/F]
This property indicates whether Blockmess should load content initial content from a file.

The responsibility to load this initial content is from the application, Blockmess should not be involved.

***Example:*** isBootstraped=F

#### (Deprecated) bootstrapFile=[Pathname]
This property indicates the file Blockmess should load the content from.
The content in this file must contain data specific to how Blockmess handles unfinalized content, and thus it should not be generated outside previous runs of Blockmess.

***Example:*** bootstrapFile=./bootstrapContent/bootstrapFile.txt

#### generateKeys=[T/F]
This property indicates whether this replica should generate an ECDSA key pair and use it, or load it from a file.

***Example:*** generateKeys=F

#### myPublic=[Pathname]
If this replica is not generating its own ECDSA keys, as defined by the former configuration, this property indicates the pathname of the file containing the public key of the replica.

***Example:*** myPublic=./keys/public.pem

#### mySecret=[Pathname]
If this replica is not generating its own ECDSA keys, as defined by the former configuration, this property indicates the pathname of the file containing the private key of the replica.

***Example:*** mySecret=./keys/secret.pem

#### initializationTime=[Miliseconds]
Currently, Blockmess does not have a mechanism to retrieve proposed blocks for a replica entering the system.
Instead, all replicas must join the system before the first blocks are proposed.

The value in this property indicates the interval of time between the launch of a replica until it starts to propose blocks.

***Example:*** initializationTime=10000

***

### 4.3 - Overriding Configuration File
Having all configurations in a single static file restrains a developer deploying several replicas on a same machine or through a script.

In order to avoid this problem, the properties in the configuration file can be overridden when launching a replica.
This is done by placing an argument with the name of the property followed by the value that will override it, with an equals sign separating them.

The following example shows a potential script running 5 replicas of the AsyncCounter demo by overriding the properties in the configuration file. 

```
for I in {1..5}
do
    java -cp target/BlockmessLib.jar demo.counter.AsyncCounter $I 1000 port=$(( 6000 + I )) redirectFile=./outputLogs/replica$I.log myPublic=./keys/public$I.pem mySecret=./keys/secret$I.pem
done
```

In the example we replaced the instance specific properties:
- port;
- redirectFile;
- myPublic;
- mySecret.

Notice that not all instance specific properties need to be overridden.
Replicas being launched from the same host can use the same network interface and IP address.
Even the overridden values of the keys pathname could be obviated if the **generateKeys** configuration is set to its default value of true.

Careful considerations of what should be placed in the configuration file and what should be overridden at launch significantly simplifies the deployment process.

### 4.4 - Logs
Blockmess uses a logging library for debug and monitoring purposes.
The configurations of the logging library are found in **config/log4j2.xml**.

#### 4.4.1 - Increase Logging Output

By default, Blockmess is only logging error messages (which hopefully will not be seen).
To increase the logging of messages, the **log4j2.xml** file must be modified by adding or modifying a line in the **Loggers** scope in the file

***Example:***<Logger level=[Level] name=[Pathname to Package or File]/>

the *Level* fields can be:
- error
- debug
- info

***Note:*** The logging library has more levels but Blockmess does not use them.

#### 4.4.2 - Redirect Logging Output
As stated in the *redirectFile* parameter in section 4.2, the outputs of the logging library will be redirected to a file.

The file where the logger used will redirect the output will be given by the aforementioned parameter.

### 4.5 - Minimal Configuration Tweaks
In the CSD project, there are few configurations that need to be adapted in order to complete the project.
The configurations that need to be adapted depend on whether the replicas are run locally in the bare-metal host or are containerized.

#### 4.5.1 - contact, port, address, and interface
If the replicas are running in the bare-metal host, they must have the same IP address and consequently, different ports.

With this in consideration, when running several replicas the port used by each must override the configuration file default.

When running the replicas in a containerized environment, they can have different IP addresses while using the same ports.
The default configuration for Blockmess considers the replicas are being run in the bare-metal machine, and thus the **contact** and **interface** parameters must be modified in the configuration file.

Additionally, in containerized deployments, the **address** of each replica must be overridden.

#### 4.5.2 - redirectFile
The *redirectFile* indicates where the logging information will be redirected to.
When running the replicas in the bare-metal host the value of this property should be overridden.

When deploying replicas in a containerized environment, the files across containers will be isolated and thus there is no need to modify the configurations.

#### 4.5.3 - expectedTimeBetweenBlocks
The default time interval between block proposals is fairly low.
The reason for this parameterization is that we configured Blockmess to be run in a bare-metal host which we assume is unable to run a large amount of replicas concurrently.

If the number of replicas increases considerably and if the latency between replicas is high or the bandwidth low, the value of this parameter should be modified.

***

## 5 - Demo AsyncCounter
Several demo applications are made available to exemplify the functionalities and application interface of Blockmess.
One such demo application is a distributed counter which is consistent across all correct replicas in the Blockmess network.

### 5.1 - Code Guide
The **AsyncCounter** is an application example where replicas update the value of a distributed counter by adding some value to its previous content.

The *main* of this program received two mandatory arguments:
The operation over the counter to be executed and the number of operations the replica will execute.

Initially, an instance of **Counter** will be created:

```
Counter counterServer = new Counter(blockmessProperties);
```

The *Counter* is a class that extends **ApplicationInterface**, which, as seen in section 3.1 allows the application to communicate with Blockmess and is the endpoint where the underlying system sends the ordered operations to be processed.

#### 5.1.1 - Operation Submission

After having initialized Blockmess through the creation of the *Counter*, the program will update the value in the counter by invoking the operation *invokeAsyncOperation* in the *Counter*:

```
counterServer.invokeAsyncOperation(changeBytes, operationResult -> {...});
```

The arguments in this operation are in order, the operation to be submitted to Blockmess and an implementation of the *ReplyListener* to process the operation response.
This last argument will be discussed later.

The value in *changeBytes* is represented as a byte array instead of a number.
The reason beyond this conversion is the fact that Blockmess is application agnostic and only receives byte arrays, leaving the handling of content representation to the application.

#### 5.1.2 - Operation Processing
The *Counter* class has an implementation of the *processOperation* method declared in the *ApplicationInterface*.
It is in this method that the processing of the operations submitted by all replicas takes place.

```
public byte[] processOperation(byte[] operation) {
    int change = bytesToInt(operation);
    counter += change;
    return numToBytes(counter);
}
```

The processing of the operation is very simple.
The method receives the operation itself as an argument in the form of a byte array.
Because *Counter* knows the format of the operation, it knows the argument must be converted to a numerical value to update the local.

Each replica updates its local counter knowing that other correct replicas will have the same value in their when concluding the processing of the operation.

An important aspect to notice is that the local counters of the replicas are not thread safe.
Because operations are executed sequentially, the application can be sure that no two threads will be processing an operation at the same time.

The result of the operation is the updated value of the replica's counter, which is transformed into a byte array representation.

#### 5.1.3 - Process Response
When an operation is processed, the replica that issued the operation is notified and given the answer to the operation request.

With an async operation, the response to the operation is received in the *ReplyListener* instance passed as argument in the *invokeAsyncOperation* call.

*AsyncCounter*'s implementation of the *ReplyListener* is the following:

```
operationResult -> {
            byte[] currCounterBytes = operationResult.getLeft();
            long opIdx = operationResult.getRight();
            int currCounter = Counter.bytesToInt(currCounterBytes);
            System.out.printf("Counter with value %d on local update %d and global operation %d%n", currCounter, i, opIdx);
        }
```

The *ReplyListener* only method receives the **Pair<byte[], Long> operationResult** as argument.
This argument indicates the value in the distributed counter when the invoked operation was processed and the number of the operation index globally.

The *ReplyListener* simply extracts this information and prints it to the default output stream.
Additionally, it adds the number of the local operation submission to the output.

### 5.2 - Deploy Single Instance
The *main* of the demo is located in the class **demo.counter.AsyncCounter.java**.
To run the application with a single replica a few configurations must be modified from their default value.

```
numExpectedReplicas=1
timeBetweenQueries=100
initializationTime=0
```

The change to **numExpectedReplicas** reflects that there is a single replica being run.
Because there is a single replica, using a large **timeBetweenQueries** may reduce the statistical accuracy of the emulation of the PoW protocol; and thus we choose a lower value for this parameter.

Finally, the **initializationTime** is set to 0 because no other replica needs to join the system before this starts proposing blocks.

Another parameter that could be changed is the **expectedTimeBetweenBlocks**.
Given that a single replica is in use, the interval between block proposals could be as low as the time required to process a block.
With this said, lowering this value to such a point would not comply with the spirit of the experiment.
In extremis, with a single replica, Blockmess could be ignored altogether.

With the jar containing Blockmess in the target directory, the instance of *AsyncCounter* is launched by running the following command from the project root:

```
java -cp BlockmessLib.jar demo.counter.AsyncCounter 2 100
```

This command launches the code in **BlockmessLib.jar** from the **main** in *demo.counter.AsyncCounter*.
The command also receives the arguments 2 and 10.

The first of these arguments indicates the operations executed on the distributed counter; 
in this particular instance, the replica will add 2 to the value in the counter.

The last argument indicates how many repetitions of the operation the replica will execute; 
in this particular instance the replica will add 2 to the counter 10 times, which will result in a counter value of 20.

Upon running the command and waiting for the operations to be processed the output will be the following:

```
Counter with value 2 on local update 6 and global operation 0
Counter with value 4 on local update 1 and global operation 1
Counter with value 6 on local update 9 and global operation 2
Counter with value 8 on local update 8 and global operation 3
Counter with value 10 on local update 3 and global operation 4
Counter with value 12 on local update 0 and global operation 5
Counter with value 14 on local update 5 and global operation 6
Counter with value 16 on local update 4 and global operation 7
Counter with value 18 on local update 2 and global operation 8
Counter with value 20 on local update 7 and global operation 9
```

Observing this output we can see that in each operation executed the value of the counter increases by two, starting with the value 2 after the first operation, and terminated with the expected value of 20.

The *global operations* increase monotonically as the program executes.
This information logs the order in which Blockmess processes the operations.

The remaining information we can extract from the output are the *local updates*.
This information indicates the order operations were submitted to Blockmess by the replica.
The first operation submitted is the *local update 0*, while the last is the *local update 9*.

Because this demo does not block the thread invoking the operations, they are submitted to Blockmess at similar times.
The underlying system makes no guarantees about maintaining an order of submitted operations, merely that the operations are delivered for processing in a total order.

### 5.3 - Deploy Several Instances
When running the demo with several instances, some configurations from the *config* file must be modified.

```
numExpectedReplicas=5
timeBetweenQueries=250
initializationTime=1000
```

The first modified parameter indicates the system expects 5 replicas, which is accurate given that is number of replicas that will be launched.

With 5 replicas being used, these need not perform PoW solution attempts as frequently as in the single replica example in order to provide an accurate execution; 
and so the value in *timeBetweenQueries* has been increased to *250* milliseconds.

Finally, because now there are several replicas in the system, it must be ensured that the last replica is launched before a valid block is proposed.
For this reason the *initializationTime* was set to *1* second.

This value is very conservative, given that the replicas will be launched from a script and take very little time to be initialized.
Nevertheless, it's better to be safe than sorry.

There are other two important parameters that should be modified, namely the **port** and the **redirectionFile**.
However, these configurations are specific for each replica, and thus will be overridden during the launch of the instances in the deployment script.

When running a large number of replicas (hundreds rather than *5*), another parameter which is a candidate to be overridden is the aforementioned *initializationTime*.
The last replicas to be launched should have a lower initialization time than the first.
This is however an optimization that should not be considered when running only *5* replicas.

To deploy the *5* replicas, the following script can be run:

```
FILE_LOC="demo.counter.AsyncCounter"
CONTACT_PORT=6000
OPS_PER_REPLICA=10

for I in {1..5}
do
        PORT=$(( CONTACT_PORT + I - 1 ))
        eval "java -cp BlockmessLib.jar $FILE_LOC $I $OPS_PER_REPLICA port=$PORT redirectFile=./outputLogs/node$I.txt 2>&1 | sed 's/^/[replica$I] /' &"
done
```

In this script *5* replicas will be launched, and each will execute *10* operations.
The operations that each replica will execute is to add its index value to the distributed counter.
Replica *1* will add *1* ten times while replica *5* will add *5* ten times.

Like in the previous deployment, the *BlockmessLib* jar will be run on the *main* in *demo.counter.AsyncCounter* and receiving as arguments the operations each replica will execute, as well as how many times the operation will be executed.

Additionally, the commands also receive override values for the *port* and *redirectFile* properties.
We ensure that the first replica's port is the same as the contact node.

The final part of the command does not pertain to Blockmess and simply indicates the name of the replica that has produced a given output during the program's execution.

Running this script we obtain the following output:

```
[replica1] Counter with value 1 on local update 3 and global operation 0
[replica1] Counter with value 2 on local update 0 and global operation 1
[replica1] Counter with value 3 on local update 1 and global operation 2
[replica1] Counter with value 4 on local update 8 and global operation 3
[replica1] Counter with value 5 on local update 5 and global operation 4
[replica1] Counter with value 6 on local update 4 and global operation 5
[replica1] Counter with value 7 on local update 9 and global operation 6
[replica1] Counter with value 8 on local update 2 and global operation 7
[replica1] Counter with value 9 on local update 6 and global operation 8
[replica1] Counter with value 10 on local update 7 and global operation 9
[replica5] Counter with value 55 on local update 7 and global operation 20
[replica4] Counter with value 14 on local update 6 and global operation 10
[replica4] Counter with value 18 on local update 7 and global operation 11
[replica4] Counter with value 22 on local update 9 and global operation 12
[replica5] Counter with value 60 on local update 2 and global operation 21
[replica5] Counter with value 65 on local update 3 and global operation 22
[replica5] Counter with value 70 on local update 8 and global operation 23
[replica5] Counter with value 75 on local update 5 and global operation 24
[replica5] Counter with value 80 on local update 1 and global operation 25
[replica5] Counter with value 85 on local update 4 and global operation 26
[replica4] Counter with value 26 on local update 3 and global operation 13
[replica5] Counter with value 90 on local update 9 and global operation 27
[replica5] Counter with value 95 on local update 6 and global operation 28
[replica4] Counter with value 30 on local update 5 and global operation 14
[replica4] Counter with value 34 on local update 0 and global operation 15
[replica4] Counter with value 38 on local update 8 and global operation 16
[replica5] Counter with value 100 on local update 0 and global operation 29
[replica4] Counter with value 42 on local update 4 and global operation 17
[replica4] Counter with value 46 on local update 1 and global operation 18
[replica4] Counter with value 50 on local update 2 and global operation 19
[replica2] Counter with value 102 on local update 8 and global operation 30
[replica2] Counter with value 104 on local update 9 and global operation 31
[replica2] Counter with value 106 on local update 6 and global operation 32
[replica2] Counter with value 108 on local update 4 and global operation 33
[replica2] Counter with value 110 on local update 7 and global operation 34
[replica2] Counter with value 112 on local update 2 and global operation 35
[replica2] Counter with value 114 on local update 0 and global operation 36
[replica2] Counter with value 116 on local update 1 and global operation 37
[replica2] Counter with value 118 on local update 3 and global operation 38
[replica2] Counter with value 120 on local update 5 and global operation 39
[replica3] Counter with value 123 on local update 2 and global operation 40
[replica3] Counter with value 126 on local update 5 and global operation 41
[replica3] Counter with value 129 on local update 1 and global operation 42
[replica3] Counter with value 132 on local update 8 and global operation 43
[replica3] Counter with value 135 on local update 3 and global operation 44
[replica3] Counter with value 138 on local update 9 and global operation 45
[replica3] Counter with value 141 on local update 4 and global operation 46
[replica3] Counter with value 144 on local update 7 and global operation 47
[replica3] Counter with value 147 on local update 6 and global operation 48
[replica3] Counter with value 150 on local update 0 and global operation 49
```

Observing this output we can see that *50* operations were executed globally, all replicas executed *10* operations, and the counter has reached its expected value of *150* upon the completion of the operations.

An aspect that differs from the execution with a single replica is that some *global operation* indexes do not follow the expected sequence from output line to output line.
Is Blockmess broken?

No (at least not from this evidence).
Because each thread is processing the results of the operations in its own separate OS thread, the output is subject to the scheduling of the operating system, which may cause discrepancies in the expected behaviour.

Nevertheless, the correct execution of the program can be observed by noticing that within each replica the global operation index increases monotonically.

***

## 6 - Container Execution (Docker)
To ease the execution of Blockmess we make available a simple docker image to run the tests.

The image can be built by running the following command in the project root:

    docker build -t blockmess_csd .

**Note** The name of the image is set to in the provided command is set to *blockmess_csd*, however, any choice of name is valid.
With this said, the scripts provided to run the containers assume the image is called *blockmess_csd*.

Some configurations to beware when changing from a bare-metal execution to a containerized one are:

- interface: Modify from *lo* to *eth0*. I don't know if this will work for every setup. *It works on my machine*.
- address: Modify from *localhost* to comply with the address range of the network where the nodes are running. In the scripts we create a network with the prefix *192.168.0.0/24*. Note that every container should have a different address, unlike in the bare-metal deployments.
- contact: Change the address of the contact node in accordance with the previous config.

## 7 - Launch Scripts
A series of scripts were made available to help run Blockmess.

All scripts are located in the *scripts/* directory and subdirectories but must be run from the project root directory.

For each example demo we provide scripts to run replicas both in the bare-metal machine and in a containerized environment.

### 7.1 - Counter
As seen in a previous section, the *Counter* demo consists on maintaining a shared distributed *MRMW* counter between several replicas. 

The scripts to run the demo are *scripts/counter/baremetal/sync.sh*, *scripts/counter/container/sync.sh*, *scripts/counter/baremetal/async.sh*  and *scripts/counter/container/async.sh*, the first two runs the *Counter* demo which issues operations with the blocking *ApplicationInterface.invokeSync*, while the latter two issue operations with the non-blocking method *ApplicationInterface.invokeAsync*.

The two scripts take two arguments.
- The number of replicas in operation;
- The number of operations to be performed by each replica.

In both scripts, each replica will add its index to the distributed *counter* several times.

The scripts can be run thus:
    
    ./scripts/counter/container/async.sh 3 100

### 7.2 - Register
In the *Counter* demo, all operations are commutative, and therefore the order operations take place has no bearing in the final value of the counter when all operations are executed.

In the *Register* demo, a distributed *MRMW* register is shared across all replicas.
On this register, the replicas can execute operations of addition and multiplication, which are not commutative between themselves.

Analogous to the previous section, four scripts are made available: *scripts/register/baremetal/sync.sh*, *scripts/register/container/sync.sh*, *scripts/register/baremetal/async.sh* and *scripts/register/container/async.sh*.

The scripts take the same arguments as those presented in the previous section.

In these scripts, the replicas with an even index will perform addition operations, while those with an odd index perform multiplications.

Like in the previous scripts the index of the replicas will determine the changes executed to the shared *register*.

*Replica 1* will add *1* to the *register* value and *replica 2* will multiply it by *2*.

The scripts can be run thus:

    ./scripts/register/container/async.sh 3 100

### 7.3 - YCSB
The *Yahoo Cloud Serving Benchmark (YCSB)* is a benchmarking tool widely used to test the efficiency of databased (mostly No-SQL) under different workloads.

Using a uniform benchmarking tool, biases caused by the choice of workloads and internal processing of the benchmarks are mitigated, thus leaving the database tests more representative of the expected performance of the databases in real deployments and allowing a more accurate comparison between database systems, across a variety of workloads.

Blockmess is not a database, however, the same principles can still apply.
Often distributed operation ordering mechanisms (Paxos, PBFT, BFT-SMaRT, HotStuff) use the *YCSB* benchmark to compare their throughput and latency.

The nature of the *YCSB* tests require a synchronous execution calling the *ApplicationInterface.invokeSync* operation.
As such, the metrics retrieved significantly downplay Blockmess' performance, as the threads issuing the operations will be blocked.

Nevertheless, we provide the scripts *scripts/ycsb/baremetal.sh* and *scripts/ycsb/container.sh* to run a *YCSB* test.

This scripts receives no arguments and run a single replica.
The operations executed follow the workload defined in *config/workloads/workloada*.

The configurations used follow the description found in:

-https://github.com/brianfrankcooper/YCSB/wiki/Running-a-Workload

-https://github.com/brianfrankcooper/YCSB/wiki/Core-Properties

The script can be run thus:

    ./scripts/run_ycsb.sh

### 7.4 - Stopping Replicas

Other than in the *YCSB* tests, the replicas will not stop their execution when the operations proposed are finished. 
Instead, more blocks are made and the system keeps evolving.

#### Baremetal
To stop the replicas in a baremetal deployment we must identify their *process id (pid)* and manually terminate the execution.

Running the command:
    
    pidof java

We can see the *pid* values of all active processes running *java*.
Among those will be the replicas.
The *pid* of the replicas should be close to one another.

To stop the replicas copy their *pid* values and paste them following the command:

    kill -9

Alternatively, if the replicas are the only *java* processes in execution, the following command is recomended:

    killall java

#### Container

The containers created using the provided scripts can be stopped running the command:

    docker container stop $(docker container ls -q -f name='replica')

After having stopped the containers and extracted all useful information, the containers can be deleted by issuing the command:

    docker container prune

Alternatively, for convenience it's possible to delete the containers without stopping them:

    docker container rm -f $(docker container ls -q -f name='replica')

Unlike with the solution presented to stop the replicas in the baremetal scenario, where the commands presented can stop any replica execution, these only work when the replicas have a *name* attribute starting with *replica*.
This behaviour is the default in the provided scripts, but beware when running something that is not in the scripts.

## 8 - Research Fronts/Possible Thesis
There are several aspects of Blockmess that can be improved and that represent interesting research challenges.
Some of these challenges would be good projects for a master's thesis, providing a rich state of the art to study, requiring considerable implementation effort, and needing validation through experimental evaluation.

### 8.1 - Efficient lazy-push broadcast with Invertible Bloom Filters (or equivalent)

Bandwidth use and block dissemination latency are some of the most important aspects limiting modern Distributed Ledgers architectures.

An important research front is how to accelerate the dissemination of a block while lowering (or not aggravating) bandwidth waste.

A way to do this is to limit the redundant information transmitted when disseminating a block.
This can be done by avoiding the transmission of operations that other replicas already own.

An approach for doing this is using Invertible Bloom Lookup Tables (IBLT) to determine which operation content needs to be transmitted on a node by node basis during the lazy-push broadcast of a block.

The potential thesis would entail the study of broadcast protocols and the study and implementation of data structures with lookup properties on summarized data.

The performance properties of the several solutions would then be tested under different network conditions and adversarial presence.

Finally, the resulting broadcast protocols could be trivially be integrated with Blockmess.

### 8.2 - Fast transaction settlement with application specific content allocation
Blockmess allows applications using this platform to define, to some extent, the chain where content is placed.
The algorithm designed for content allocation was initially based on transactions for cryptocurrency applications, and it allows some optimizations specific to it.
In particular, it would allow the delivery of some transactions to the application before the block they were placed in was finalized.

As Blockmess evolved to be more application agnostic, this research front was temporarily placed aside.
However, the potential of this approach is still present and is something we want to explore.

A thesis covering this front would entail a study of transaction settlement strategies in parallel chain distributed ledgers and how Blockmess works internally.

As an implementation onus, you'd have to delve into the internals of Blockmess to add the functionality of delivering content based on rules that go beyond those dictating the delivery order of blocks, and which are application specific.

In the best case scenario which would greatly improve the potential of the thesis, new content allocation rules could be proposed that improve even further the transaction settlement benefits.

Finally, the benefits of this solution should be tested when compared with other solutions.

### 8.3 - Multi-purpose chains in Blockmess
In Blockmess the use of parallel chains is restricted to the improvement of throughput by having the network propose blocks at a faster rate.
However, there are several use cases for parallel chains in this area's literature.
Some works use parallel chains to improve throughput (like ours), other improve latency in block delivery, and finally other improve transaction settlement without providing total order for operation processing.

As far as we know, no work developed uses the parallel chains for more than one purpose.
In Blockmess we already addapt the number of chains in use to correspond to the throughput application demands. We can extend the idea further and have different chain functionalities that optimize the performance metrics of the system depending on the application demands.

This work would entail the study in general of parallel chain solutions and the implementation of deep modifications on the Blockmess platform in order to create new types of chains.

The experimental work on this thesis would show the effects of the use of the different kinds of parallel chains under varying application loads, amount of adversarial presence, and rules for the instantiation of different types of chains.

### 8.4 - Integration of parallel chains with other scalability solutions
This proposal is admittedly more open-ended than the previous.
There exist many scalability proposals and research fronts over the original Blockchain design introduced in Bitcoin.
Each of them has a set of upsides and downsides (yes, even Blockmess).

In this thesis we would study the integration of several scalability solutions to optimize the system and extract some synergy from their concurrent use.

We have no predefined plan for this research front.
However, if you are interested in the area and want to study some alternative scalability solutions to parallel chains, a thesis in this area provides boundless space for innovation and implementation freedom.

On a personal recommendation, it was following this general aimless: "Let's see what exists in Distributed Ledger scalability papers", that Blockmess was thought of.

***
##### Disclaimer
All claims presented in this document assume that the use of the replicas follow the parameterized Fault and Network moddels. 

Furthermore, Distributed Ledgers' properties are probabilistic, as such all assertions done in this document are implicitly prefaced with the notice that properties are achieved with high probability.
