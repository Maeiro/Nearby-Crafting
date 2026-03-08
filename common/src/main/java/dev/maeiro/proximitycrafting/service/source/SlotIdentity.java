package dev.maeiro.proximitycrafting.service.source;

public record SlotIdentity(int ownerIdentityHash, int slotIndex) {
	public static SlotIdentity of(Object owner, int slotIndex) {
		return new SlotIdentity(System.identityHashCode(owner), slotIndex);
	}
}
