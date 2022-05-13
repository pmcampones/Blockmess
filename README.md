# Blockmess_Simple_PoET
Blockmess Distributed Ledger as a software module, employing a simple leader election mechanism mimicking a Sybil Resistant alternative (like PoW)


Functionalities provided:
	- Totally ordered operations
	
	- Bounded staleness
	
	- Application oblivious
	
	- PoW abstraction
	
	- Parameterizable and dynamic throughput

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
