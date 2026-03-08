package dev.maeiro.proximitycrafting.service.scan;

import dev.maeiro.proximitycrafting.networking.payload.RecipeBookSourceEntry;
import dev.maeiro.proximitycrafting.service.source.ItemSourceRef;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class RecipeBookSourceAggregator {
	private RecipeBookSourceAggregator() {
	}

	public static List<RecipeBookSourceEntry> aggregateEntries(List<ItemSourceRef> sourceRefs) {
		Map<String, ItemStack> exemplarStacks = new LinkedHashMap<>();
		Map<String, Integer> totalCounts = new LinkedHashMap<>();

		for (ItemSourceRef sourceRef : sourceRefs) {
			ItemStack stack = sourceRef.slotRef().peekStack();
			if (stack.isEmpty()) {
				continue;
			}

			String stackKey = buildStackKey(stack);
			exemplarStacks.computeIfAbsent(stackKey, ignored -> {
				ItemStack exemplar = stack.copy();
				exemplar.setCount(1);
				return exemplar;
			});
			totalCounts.merge(stackKey, stack.getCount(), Integer::sum);
		}

		List<RecipeBookSourceEntry> entries = new ArrayList<>(exemplarStacks.size());
		for (Map.Entry<String, ItemStack> exemplarEntry : exemplarStacks.entrySet()) {
			int count = totalCounts.getOrDefault(exemplarEntry.getKey(), 0);
			if (count <= 0) {
				continue;
			}
			entries.add(new RecipeBookSourceEntry(exemplarEntry.getValue(), count));
		}
		return entries;
	}

	private static String buildStackKey(ItemStack stack) {
		CompoundTag serialized = new CompoundTag();
		stack.save(serialized);
		serialized.remove("Count");
		return serialized.toString();
	}
}
