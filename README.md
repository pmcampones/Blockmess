# Blockmess β
Blockmess is a scalable and modular Distributed Ledger used as an application agnostic software module.

This repository holds the Java (not yet) open source library with the Distributed Ledger logic.

This package contains the source code (src/), jar file (target/BlockmessLib.jar), running scripts (scripts/), and configuration files (config/) for the project. Blockmess requires the Java Runtime Environment version 16 or higher.

## Highlight Features

### Totally Ordered Operations
As a Distributed Ledger, Blockmess aggregates operations in blocks that are eventually delivered to the any application replica in a total order.

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

##### Disclaimer
The default implementation simulating PoW is not resistant against an adversary that modifies Blockmess' code to give itself an advantage.

If using Blockmess for the deployment of a real application, please modify the implementation of the Sybil Resistant Election protocol (and to do so keep reading untill we mention the modularity features).
	
### Parameterizable and dynamic throughput

### High Configuration Potential

Application Layer Extentions:

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
