package mempoolManager;

import cmux.AppOperation;
import lombok.Getter;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static java.util.stream.Collectors.toSet;

@Getter
public class MempoolChunk {

	private final UUID id;

	private final Set<UUID> previousIds;

	private final Set<UUID> usedIds;

	private final List<AppOperation> addedContent;

	private final int weight;

	public MempoolChunk(UUID id, Set<UUID> previousIds, List<AppOperation> addedContent) {
		this.id = id;
		this.previousIds = previousIds;
		this.usedIds = addedContent.stream().map(AppOperation::getId).collect(toSet());
		this.addedContent = addedContent;
		this.weight = 1;
	}

}
