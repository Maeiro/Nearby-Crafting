package dev.maeiro.proximitycrafting.client.recipebook;

import dev.maeiro.proximitycrafting.client.recipebook.mixin.RecipeBookComponentAccessor;
import dev.maeiro.proximitycrafting.client.recipebook.mixin.RecipeBookPageAccessor;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeBookPage;
import net.minecraft.client.gui.screens.recipebook.RecipeButton;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

public final class NeoForgeRecipeBookRuntimeBridge implements RecipeBookRuntimeBridge {
	private final Supplier<Level> levelSupplier;

	public NeoForgeRecipeBookRuntimeBridge(Supplier<Level> levelSupplier) {
		this.levelSupplier = levelSupplier;
	}

	@Override
	@Nullable
	public ResourceLocation resolveHoveredRecipeId(RecipeBookComponent component) {
		if (!component.isVisible()) {
			return null;
		}

		ResourceLocation accessorResolvedId = tryResolveRecipeBookHoverViaAccessor(component);
		if (accessorResolvedId != null) {
			return accessorResolvedId;
		}

		try {
			Object recipeBookPage = getFieldValue(component, "recipeBookPage");
			if (recipeBookPage == null) {
				Field recipeBookPageField = findFieldByTypeNameContains(component.getClass(), "RecipeBookPage");
				if (recipeBookPageField != null) {
					recipeBookPage = recipeBookPageField.get(component);
				}
			}
			if (recipeBookPage == null) {
				return null;
			}

			Object hoveredButton = getFieldValue(recipeBookPage, "hoveredButton");
			if (hoveredButton == null) {
				Field hoveredButtonField = findFieldByTypeNameContains(recipeBookPage.getClass(), "RecipeButton");
				if (hoveredButtonField != null) {
					hoveredButton = hoveredButtonField.get(recipeBookPage);
				}
			}
			if (hoveredButton == null) {
				return null;
			}

			Method getRecipeMethod = findMethod(hoveredButton.getClass(), "getRecipe", 0);
			if (getRecipeMethod == null) {
				getRecipeMethod = findNoArgMethodReturningTypeNameContains(hoveredButton.getClass(), "RecipeHolder");
			}
			if (getRecipeMethod == null) {
				return tryResolveRecipeIdFromRecipeButtonFields(hoveredButton);
			}

			Object recipeHolder = getRecipeMethod.invoke(hoveredButton);
			if (recipeHolder == null) {
				return null;
			}
			if (recipeHolder.getClass().getSimpleName().contains("RecipeCollection")
					|| recipeHolder.getClass().getName().contains("recipebook.RecipeCollection")) {
				ResourceLocation collectionId = tryResolveRecipeIdFromRecipeCollection(recipeHolder, hoveredButton);
				if (collectionId != null) {
					return collectionId;
				}
			}

			Method idMethod = findMethod(recipeHolder.getClass(), "id", 0);
			if (idMethod == null) {
				idMethod = findNoArgMethodReturningTypeNameContains(recipeHolder.getClass(), "ResourceLocation");
			}
			if (idMethod != null) {
				Object recipeId = idMethod.invoke(recipeHolder);
				if (recipeId instanceof ResourceLocation resourceLocation) {
					return resourceLocation;
				}
			}

			Object recipeIdFieldValue = getFieldValue(recipeHolder, "id");
			if (recipeIdFieldValue instanceof ResourceLocation resourceLocation) {
				return resourceLocation;
			}
		} catch (ReflectiveOperationException | RuntimeException ignored) {
			return null;
		}
		return null;
	}

	@Override
	public void onSlotClicked(RecipeBookComponent component, Slot slot) {
		component.slotClicked(slot);
	}

	@Override
	public void onRecipesUpdated(RecipeBookComponent component) {
		component.recipesUpdated();
	}

	@Nullable
	private ResourceLocation tryResolveRecipeBookHoverViaAccessor(RecipeBookComponent component) {
		if (!(component instanceof RecipeBookComponentAccessor componentAccessor)) {
			return null;
		}

		RecipeBookPage recipeBookPage = componentAccessor.proximitycrafting$getRecipeBookPage();
		if (recipeBookPage == null || !(recipeBookPage instanceof RecipeBookPageAccessor pageAccessor)) {
			return null;
		}

		RecipeButton hoveredButton = pageAccessor.proximitycrafting$getHoveredButton();
		if (hoveredButton == null) {
			return null;
		}

		Object recipe = hoveredButton.getRecipe();
		ResourceLocation directId = tryExtractRecipeId(recipe);
		if (directId != null) {
			return directId;
		}
		return tryResolveRecipeIdFromRecipeButtonFields(hoveredButton);
	}

	@Nullable
	private ResourceLocation tryResolveRecipeIdFromRecipeButtonFields(Object recipeButton) {
		Integer currentIndex = tryGetRecipeButtonCurrentIndex(recipeButton);
		for (Field field : getAllFields(recipeButton.getClass())) {
			field.setAccessible(true);
			Object value;
			try {
				value = field.get(recipeButton);
			} catch (IllegalAccessException ignored) {
				continue;
			}
			ResourceLocation directId = tryExtractRecipeId(value);
			if (directId != null) {
				return directId;
			}
			if (value != null && (value.getClass().getSimpleName().contains("RecipeCollection")
					|| value.getClass().getName().contains("recipebook.RecipeCollection"))) {
				ResourceLocation collectionId = tryResolveRecipeIdFromRecipeCollection(value, recipeButton);
				if (collectionId != null) {
					return collectionId;
				}
			}
			if (value instanceof List<?> listValue && !listValue.isEmpty()) {
				int index = currentIndex != null ? currentIndex : 0;
				if (index < 0) {
					index = 0;
				}
				if (index >= listValue.size()) {
					index = listValue.size() - 1;
				}
				ResourceLocation listId = tryExtractRecipeId(listValue.get(index));
				if (listId != null) {
					return listId;
				}
				for (Object listEntry : listValue) {
					ResourceLocation candidate = tryExtractRecipeId(listEntry);
					if (candidate != null) {
						return candidate;
					}
				}
			}
		}
		return null;
	}

	@Nullable
	private ResourceLocation tryResolveRecipeIdFromRecipeCollection(Object recipeCollection, @Nullable Object recipeButton) {
		Integer currentIndex = recipeButton == null ? null : tryGetRecipeButtonCurrentIndex(recipeButton);
		int preferredIndex = currentIndex == null ? 0 : Math.max(0, currentIndex);
		for (Method method : recipeCollection.getClass().getMethods()) {
			if (!List.class.isAssignableFrom(method.getReturnType())) {
				continue;
			}
			if (Modifier.isStatic(method.getModifiers())) {
				continue;
			}
			Object rawList = null;
			try {
				if (method.getParameterCount() == 0) {
					rawList = method.invoke(recipeCollection);
				} else if (method.getParameterCount() == 1 && method.getParameterTypes()[0] == boolean.class) {
					rawList = method.invoke(recipeCollection, true);
					if (!(rawList instanceof List<?> list && !list.isEmpty())) {
						rawList = method.invoke(recipeCollection, false);
					}
				}
			} catch (ReflectiveOperationException | RuntimeException ignored) {
				continue;
			}
			if (!(rawList instanceof List<?> listValue) || listValue.isEmpty()) {
				continue;
			}
			int index = Math.min(preferredIndex, listValue.size() - 1);
			ResourceLocation indexedId = tryExtractRecipeId(listValue.get(index));
			if (indexedId != null) {
				return indexedId;
			}
			for (Object entry : listValue) {
				ResourceLocation candidate = tryExtractRecipeId(entry);
				if (candidate != null) {
					return candidate;
				}
			}
		}
		return null;
	}

	@Nullable
	private Integer tryGetRecipeButtonCurrentIndex(Object recipeButton) {
		Object namedIndex = getFieldValue(recipeButton, "currentIndex");
		if (namedIndex instanceof Integer indexValue) {
			return indexValue;
		}
		for (Field field : getAllFields(recipeButton.getClass())) {
			if (field.getType() != int.class && field.getType() != Integer.class) {
				continue;
			}
			if (Modifier.isStatic(field.getModifiers())) {
				continue;
			}
			String fieldName = field.getName();
			if (!fieldName.toLowerCase().contains("index")) {
				continue;
			}
			field.setAccessible(true);
			try {
				Object value = field.get(recipeButton);
				if (value instanceof Integer indexValue) {
					return indexValue;
				}
			} catch (IllegalAccessException ignored) {
			}
		}
		return null;
	}

	@Nullable
	private ResourceLocation tryExtractRecipeId(@Nullable Object maybeRecipeLike) {
		if (maybeRecipeLike == null) {
			return null;
		}
		if (maybeRecipeLike instanceof ResourceLocation directResourceLocation) {
			return isValidScrollRecipeId(directResourceLocation) ? directResourceLocation : null;
		}

		Class<?> valueClass = maybeRecipeLike.getClass();
		Method idMethod = findMethod(valueClass, "id", 0);
		if (idMethod == null) {
			idMethod = findNoArgMethodReturningTypeNameContains(valueClass, "ResourceLocation");
		}
		if (idMethod != null) {
			try {
				Object idValue = idMethod.invoke(maybeRecipeLike);
				if (idValue instanceof ResourceLocation recipeId) {
					return isValidScrollRecipeId(recipeId) ? recipeId : null;
				}
			} catch (ReflectiveOperationException ignored) {
			}
		}

		Object namedFieldId = getFieldValue(maybeRecipeLike, "id");
		if (namedFieldId instanceof ResourceLocation recipeId) {
			return isValidScrollRecipeId(recipeId) ? recipeId : null;
		}
		for (Field field : getAllFields(valueClass)) {
			if (field.getType() != ResourceLocation.class) {
				continue;
			}
			if (Modifier.isStatic(field.getModifiers())) {
				continue;
			}
			field.setAccessible(true);
			try {
				Object fieldValue = field.get(maybeRecipeLike);
				if (fieldValue instanceof ResourceLocation recipeId && isValidScrollRecipeId(recipeId)) {
					return recipeId;
				}
			} catch (IllegalAccessException ignored) {
			}
		}
		return null;
	}

	private boolean isValidScrollRecipeId(@Nullable ResourceLocation recipeId) {
		Level level = this.levelSupplier.get();
		if (recipeId == null || level == null) {
			return false;
		}
		Optional<?> recipeOptional = level.getRecipeManager().byKey(recipeId);
		if (recipeOptional.isEmpty()) {
			return false;
		}
		return recipeOptional.get() instanceof CraftingRecipe;
	}

	@Nullable
	private static Method findMethod(Class<?> ownerClass, String methodName, int parameterCount) {
		for (Method method : ownerClass.getMethods()) {
			if (method.getName().equals(methodName) && method.getParameterCount() == parameterCount) {
				return method;
			}
		}
		return null;
	}

	@Nullable
	private static Field findField(Class<?> ownerClass, String fieldName) {
		Class<?> currentClass = ownerClass;
		while (currentClass != null) {
			try {
				Field field = currentClass.getDeclaredField(fieldName);
				field.setAccessible(true);
				return field;
			} catch (NoSuchFieldException ignored) {
				currentClass = currentClass.getSuperclass();
			}
		}
		return null;
	}

	@Nullable
	private static Field findFieldByTypeNameContains(Class<?> ownerClass, String typeNameFragment) {
		Class<?> currentClass = ownerClass;
		while (currentClass != null) {
			for (Field field : currentClass.getDeclaredFields()) {
				String fieldTypeName = field.getType().getName();
				String fieldTypeSimpleName = field.getType().getSimpleName();
				if (fieldTypeName.contains(typeNameFragment) || fieldTypeSimpleName.contains(typeNameFragment)) {
					field.setAccessible(true);
					return field;
				}
			}
			currentClass = currentClass.getSuperclass();
		}
		return null;
	}

	private static List<Field> getAllFields(Class<?> ownerClass) {
		List<Field> fields = new ArrayList<>();
		Class<?> currentClass = ownerClass;
		while (currentClass != null) {
			for (Field field : currentClass.getDeclaredFields()) {
				fields.add(field);
			}
			currentClass = currentClass.getSuperclass();
		}
		return fields;
	}

	@Nullable
	private static Method findNoArgMethodReturningTypeNameContains(Class<?> ownerClass, String typeNameFragment) {
		for (Method method : ownerClass.getMethods()) {
			if (method.getParameterCount() != 0) {
				continue;
			}
			Class<?> returnType = method.getReturnType();
			if (returnType == null || returnType == Void.TYPE) {
				continue;
			}
			String returnTypeName = returnType.getName();
			String returnTypeSimpleName = returnType.getSimpleName();
			if (returnTypeName.contains(typeNameFragment) || returnTypeSimpleName.contains(typeNameFragment)) {
				return method;
			}
		}
		return null;
	}

	@Nullable
	private static Object getFieldValue(Object owner, String fieldName) {
		Field field = findField(owner.getClass(), fieldName);
		if (field == null) {
			return null;
		}
		try {
			return field.get(owner);
		} catch (IllegalAccessException ignored) {
			return null;
		}
	}
}

