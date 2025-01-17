package de.ancash.fancycrafting.gui.manage;

import java.util.Optional;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

import de.ancash.datastructures.tuples.Tuple;
import de.ancash.fancycrafting.FancyCrafting;
import de.ancash.minecraft.inventory.IGUI;
import de.ancash.minecraft.inventory.IGUIManager;
import de.ancash.minecraft.inventory.InventoryItem;
import de.ancash.minecraft.inventory.input.StringInputGUI;

public class RecipeCreateMenuGUI extends IGUI {

	public RecipeCreateMenuGUI(FancyCrafting pl, Player player, String name) {
		super(player.getUniqueId(), 9, pl.getWorkspaceObjects().getCreateRecipeTitle());
		for (int i = 0; i < getSize(); i++)
			setItem(pl.getWorkspaceObjects().getBackgroundItem(), i);

		addInventoryItem(new InventoryItem(this, pl.getWorkspaceObjects().getCreateNormalRecipeItem(), 2,
				(a, b, c, top) -> Optional.ofNullable(top ? this : null)
						.ifPresent(self -> pl.openCreateNormalRecipe(player, name))));

		addInventoryItem(new InventoryItem(this, pl.getWorkspaceObjects().getCreateRandomRecipeItem(), 6,
				(a, b, c, top) -> Optional.ofNullable(top ? this : null)
						.ifPresent(self -> pl.openCreateRandomRecipe(player, name))));

		IGUIManager.register(this, getId());
		open();
	}

	public static void open(FancyCrafting plugin, Player owner) {
		new StringInputGUI(plugin, owner,
				(text) -> Bukkit.getScheduler().runTaskLater(plugin, () -> new RecipeCreateMenuGUI(plugin, owner, text),
						1),
				(text) -> Tuple.of(text != null && !text.isEmpty(),
						text == null || text.isEmpty() ? plugin.getResponse().INVALID_RECIPE_NAME : null))
				.setLeft(plugin.getWorkspaceObjects().getInputRecipeNameLeftItem())
				.setRight(plugin.getWorkspaceObjects().getInputRecipeNameRightItem())
				.setTitle(plugin.getWorkspaceObjects().getInputRecipeNameTitle()).setText("").open(); //$NON-NLS-1$
	}

	@Override
	public void onInventoryClick(InventoryClickEvent event) {
		event.setCancelled(true);
	}

	@Override
	public void onInventoryClose(InventoryCloseEvent event) {
		IGUIManager.remove(getId());
	}

	@Override
	public void onInventoryDrag(InventoryDragEvent event) {
		event.setCancelled(true);
	}
}
