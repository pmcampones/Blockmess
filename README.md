# Blockmess β
Blockmess is a scalable and modular Distributed Ledger used as an application agnostic software module.

This repository holds the Java (not yet) open source library with the Distributed Ledger logic.

This package contains the source code (src/), jar file (target/BlockmessLib.jar), running scripts (scripts/), and configuration files (config/) for the project. Blockmess requires the Java Runtime Environment version 16 or higher.

### β Considerations
This is still a work in progress, so all feedback is appreciated.

There is room for improvement, both in the interaction with the applications and in the internal operation of Blockmess.
Especially in the first front, I may be missing some crucial quality of life features that would massively simplify the end developer's application, while being simple to implement in Blockmess. 

Finally, as much as it pains me to say. There might be some bugs here and there.

Every improvement suggestion and error detected will benefit this project and provide a better product to all who use it.

***

## Quick Start Example



***

## Highlight Features

### Totally Ordered Operations
As a Distributed Ledger, Blockmess aggregates operations in blocks that are eventually delivered to any application replica in a total order.

By default, application content is not delivered to the application upon receival of the content, or when such content is placed in blocks. The content is only delivered to the application when the blocks they are placed are confirmed/finalized.

Following this behaviour, we guarantee that all replicas execute operations in the same order and no duplicate operations are delivered.

#### Liberty to have lower consistency guarantees
Altough the default behaviour of Blockmess ensures a total order of operations, our system allows the application to extract content during its ordering process; reducing the latency in the delivery of content, at the cost of having lower consistency guarantees.

We must emphasise that as of this release, the application interface for the retrieval of content following these mechanisms is not as clean as with totally ordered operations.

##### FIFO Content Retrieval:
As soon as content is received in a replica during its dissemination, it can be extracted and processed. This ensures a FIFO delivery of content guaranteeing application operations are processed as soon as they are available.

##### Speculative Total Order:
The application has access to blocks as soon as they are received by the replicas. The application can use the content on these blocks as soon as they are received or wait for some further blocks, processing them with no guarantees the content in them will actually be delivered; however being aware that the probability the content is delivered is high.

We emphasise that depending on the parameterization of Blockmess, the delivery of content not yet finalized can open some attack vectors to the application, that would otherwise be detected by simple Blockchain implementations.
One such example is the Double Spend attack found in cryptocurrency applications.

### Application oblivious
Blockmess was designed as a replication module able to service any application.
The system does not require specific types of content and gives the application the freedom to process its information however it sees fit.

As long as the application content can be serialized it is accepted by Blockmess.

### Plugable Modularity
Despite being a functional and scalable Distributed Ledger implementation out of the box, Blockmess its components to be swapped by other implementations granting unprecedented freedom to the developer beyond what can be achieved through simple parameterization.

The Blockmess was designed with modularity as one of its core tenets from the ground up, facilitating the plugability of these modules.
	
### PoW abstraction
Blockmess developers hate harming the environment, and so our default block proposal protocol is not a PoW variant.

However, it mimics the functionality of a PoW system, providing the same distribution as these mechanisms, without requiring an high computational load on the replicas.

**Disclaimer:**
The default implementation simulating PoW is not resistant against an adversary that modifies Blockmess' code to give itself an advantage.

If using Blockmess for the deployment of a real application, please modify the implementation of the Sybil Resistant Election protocol (and to do so keep reading untill we mention the modularity features).

### High Dynamic Throughput
Incorporating performance enhancing mechanisms from the Parallel Chains approach to Distributed Ledger scalability, Blockmess is able to achieve a very high throughput.

The use of Parallel Chain solutions by themselves has drawbacks, such that their use with incorrect parameterizations may lead to a deteriorating performance.
This problem is exacerbated by the variability in application load exerted over Blockmess. What is an optimal parameterization at a given point, may prove suboptimal in periods of higher load.

The great innovation of Blockmess is that it modifies its internal structure to adapt to the application requirements, thus achieving a nearly optimal balance between throughput and latency.

### Extensive Configuration Potential
Blockmess is highly configurable, allowing tweaks to all software modules.

This parameterization is simple and well documented.
The properties on the configurations file can be overwritten upon launching any replica, allowing great flexibility when running more than one replica on a single machine, while simultaneously not hindering simpler launch processes of a single replica per host.

***

## Application Layer Extensions:

### ApplicationInterface
Blockmess is suited to interact with any application by extending a set of classes.

However, the only mandatory class to be extended is the **applicationInterface.ApplicationInterface**

#### Instancing
- public ApplicationInterface(String[] blockmessProperties)

When creating an instance of this class, the Blockmess system is launched. The argument *blockmessProperties* contains a list of properties that are to override those in the configuration file.

There can only be a single *ApplicationInterface* instance in the program.

#### Operation Submission
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

#### Operation Processing
Being application agnostic, Blockmess does not know how to process the operations submitted by the application.
As a result, it is the application responsibility to process them.

The *ApplicationInterface* provides the following abstract method to allow the processing of operations.

- public abstract byte[] processOperation(byte[] operation)

This method is executed by all correct replicas, not only the replica that has submitted the operation.
The received as argument represents an *operation* submitted by a replica (using either of the operation submission methods presented earlier).
This method is executed sequentially according to the delivery order of the operations, ensuring all replicas process the operations in a total order.

The return value of this method is the left-hand side of the invocation response that will be delivered to the replica that issued the operation.

#### Block Monitoring
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

### Secondary Extensions
Beyond the core functionalities required by the application and covered by the *ApplicationInterface* class, there are other functionalities that may benefit from an interaction with the application.

These functionalities have a default simple behaviour managed by Blockmess, but can be overridden by the application by replacing the implementation of a set of key classes. 

#### CMuxIdMapper
Blockmess deterministically maps application content to specific chains using a data structure we designated **Content Multiplexer (CMux)**.

The submission of content to the *CMux* requires it to be associated with two byte array non-unique values.

These values are not disseminated and must instead be derived from the operations themselves.
The mapping of content to these *CMux* related numbers are performed by implementations of the **cmux.CMuxIdMapper**.

This interface has two functionalities:
- byte[] mapToCmuxId1(byte[] operation)
- byte[] mapToCmuxId2(byte[] operation)

Each of these methods maps the operation to be submitted by a replica and which is received as argument, to a value that determining the placement of the content in the Blockmess.

#### FixedCMuxIdMapper
The class **cmux.FixedCMuxMapper** implements the *CMuxIdMapper* interface and is used by Blockmess to map the operation content to the values used by the *CMux*.

Besides the functionalities provided by *CMuxIdMapper*, this class provides the following operation:

- public void setCustomMapper(CMuxIdMapper mapper)

With this method, the application can override the
default behaviour of Blockmess by providing the *CMuxIdMapper* implementation received as parameter.

#### DefaultCMuxMapper
The default *CMuxIdMapper* behaviour provided by Blockmess is implemented in the class **cmux.DefaultCMuxMapper**.

This implementation ensures that the values used in the *CMux* are uniformly distributed, which in turn ensures the content associated with the several parallel chains is balanced.

Because the *DefaultCMuxMapper* is agnostic to application content it is oblivious to possible patterns in the content that may increase the computational efficiency of the mapping operations.

Additionally, the application may benefit from ensuring specific operations are placed in the same chain, or otherwise ensure some operations are placed in different chains.

If either of these aspects proves beneficial to the application, it should replace the *DefaultCMuxMapper* used by the *FixedCMuxMapper*.

***

#### ApplicationAwareValidator
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

#### FixedApplicationAwareValidator
The class **validators.FixedApplicationAwareValidator** implements the *ApplicationAwareValidator* interface and is used by Blockmess to validate the operations and blocks as they are broadcast.

Besides the functionalities provided by the *ApplicationAwareValidator*, this class provides the following operation:

- public void setCustomValidator(ApplicationAwareValidator validator)

With this method, the application can override the default behaviour of Blockmess by providing the *ApplicationAwareValidator* implementation received as parameter.

#### DefaultApplicationAwareValidator
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

#### TODO: OperationStorage
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

## Parameters:
Blockmess provides a series of parameters to configure both how each replica will run and global parameters influencing the performance and security of the replicas.

These parameters are placed in the **config/confg.properties** file.

### Global:
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

###	Instance specific:
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

Operation:

	- Overlay Network:
		All communications are done with the aid of the Babel distributed protocol framework.
		- Replicas of the system communicate through a P2P overlay network;
		- The overlay network topology follows a Random graph approximation provided by running the Hyparview protocol, dynamically adapting the topology as replicas are added to the system;
		- The peer sampling protocol reserves a port to manage the network topology.
		- Replicas are added to the network by contacting a set of contact nodes;
		- Our program does not provide a mechanism to attain this set of nodes, it must be established through some external means;
		- The exception to this rule is the first node in the system, who is its own contact node;
		- The Hyparview protocol highly tolerates crash faults and nodes leaving the network;
		- Byzantine faults are tolerated by increasing the redundancy in the communications, further study should be made regarding how to improve this approach;
		- To account for all byzantine replicas supressing content/block dissemination, the nodes should have a neighboorhood at least 4 times the minimum recomended in the original paper;
		
	- Broadcast protocols:
		Two broadcast protocols are used:
			- Eager push protocol for dissemination of small content;
			- Lazy push protocol for dissemination of large content;
			
		It's expected that standalone application content is disseminated through the eager push protocol, however this can be parameterized;
		As of this release, all blocks are disseminated through the Lazy push protocol;
		Broadcast protocols use a different port than the Peer Sampling protocol;
		The port used follows an offset (To be changed) in relation to the Peer Sampling port;
		
		Lazy push intermediate validation:
		At each hop during the dissemination with the lazy push protocol, the block being disseminated is validated and is only propagated if deemed valid;
		This protects the network against DoS attacks, where an adversarial entity publishes erroneous content or blocks.
	
	- Application content:
		- Application content is submited to the ApplicationInterface as a byte array and the Blockmess is agnostic to its content;
		- The content is then submited to the broadcast protocols (Eager push by default) and disseminated through the network;
		- Each replica, upon receiving the content can validate it using the ApplicationAwareValidator;
		- By default Blockmess assumes all content is valid;
		- Upon validated, the received content is associated with some Blockmess required metadata;
		- The 
		 each replica submits the content to the ApplicationAwareContentStorage, where it can 
		
	
	The application submits content to the Blockmess;
	The content is then disseminated through an eager push broadcast protocol; 
	
Modularity:

	Peer Sampling Protocol

	Lazy/Eager push Broadcast protocol

	Inner Ledger -Blockchain
	
	Sybil Resistant Election
	
	Block Indirection Layer
	
	Application Oblivious Validator
	
  - It should be noted that the validation process is tightly coupled with the remaining protocols, and protocols that diverge significantly from those presented may require a deep knowledge of Blockmess operation and awareness of the internal functionalities provided. 


To use create class extending ApplicationInterface.
Comments in that abstract class explain how to use it.
Configurations are in config/config.properties. In that file it's explained what each configuration does.
These configurations can be overloaded when running the program: [property_name=value]

##### Disclaimer
All claims presented in this document assume that the use of the replicas follow the parameterized Fault and Network moddels. 

Furthermore, Distributed Ledgers' properties are probabilistic, as such all assertions done in this document are implicitly prefaced with the notice that properties are achieved with high probability.
