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

## Application Layer Extensions:

### ApplicationInterface
Blockmess is suited to interact with any application by extending a set of classes.

However, the only mandatory class to be extended is the **ApplicationInterface**.

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

The result of the operation is returned to the replica which has submitted the operation through the use of a **ReplyListener**, implemented by the application and passed as the second argument.

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

### Secondary Extensions





    - ApplicationInterface
	
    - CMuxIdMapper
	
    - ApplicationAwareValidator
	
    - ApplicationAwareContentStorage

Parameters:
	
	Global:
	
	Instance specific:
	
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
