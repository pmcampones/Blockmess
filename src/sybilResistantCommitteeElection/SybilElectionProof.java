package sybilResistantCommitteeElection;

import ledger.blocks.SizeAccountable;
import main.ProtoPojo;

import java.io.IOException;
import java.security.PublicKey;

/**
 * Proof specific of the Sybil Resistant Committee Election that differs from application to application.
 * Other types of proofs could be issued. And blocks could even have several proofs (although we are not contemplating it)
 *
 * IMPORTANT: The Application must verify what is the proof being used.
 *              Supposing there are several implementations of Proofs but only one of them is being used.
 *              The Adversary could provide the block with a proof type different than that which is being used.
 *              This alternative proof would be valid, while the correct proof wouldn't be.
 */
public interface SybilElectionProof extends ProtoPojo, SizeAccountable { }
