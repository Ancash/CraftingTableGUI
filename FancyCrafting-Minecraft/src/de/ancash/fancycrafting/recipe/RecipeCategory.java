package de.ancash.fancycrafting.recipe;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import de.ancash.fancycrafting.FancyCrafting;

@SuppressWarnings("nls")
public class RecipeCategory {

	public static final RecipeCategory DEFAULT;

	private static final Map<String, RecipeCategory> categories;

	static {
		categories = new HashMap<>();
		DEFAULT = getOrCreateCategory("Default");
	}

	public static RecipeCategory getOrCreateCategory(String name) {
		if (!existsCategory(name))
			FancyCrafting.getPlugin(FancyCrafting.class).getLogger().info("Recipe Category '" + name + "' created");
		return categories.computeIfAbsent(name, k -> new RecipeCategory(name));
	}

	public static Set<String> getCategories() {
		return Collections.unmodifiableSet(categories.keySet());
	}

	public static RecipeCategory getCategory(String name) {
		return categories.get(name);
	}

	public static boolean existsCategory(String name) {
		return categories.containsKey(name);
	}

	private final String name;

	public RecipeCategory(String name) {
		this.name = name;
	}

	public String getName() {
		return name;
	}

	@Override
	public boolean equals(Object arg0) {
		if (arg0 == null)
			return false;
		return arg0.hashCode() == hashCode();
	}

	@Override
	public int hashCode() {
		return name.hashCode();
	}
}
