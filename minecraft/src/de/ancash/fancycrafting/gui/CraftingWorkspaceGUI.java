package de.ancash.fancycrafting.gui;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import de.ancash.ILibrary;
import de.ancash.fancycrafting.FancyCrafting;
import de.ancash.fancycrafting.gui.handler.AutoRecipeMatcherHandler;
import de.ancash.fancycrafting.gui.handler.RecipeMatchHandler;
import de.ancash.fancycrafting.gui.handler.RecipePermissionHandler;
import de.ancash.fancycrafting.recipe.IRandomRecipe;
import de.ancash.fancycrafting.recipe.IRecipe;
import de.ancash.fancycrafting.recipe.IShapedRecipe;
import de.ancash.fancycrafting.recipe.IShapelessRecipe;
import de.ancash.fancycrafting.recipe.complex.IComplexRecipe;
import de.ancash.fancycrafting.recipe.crafting.AutoRecipeMatcher;
import de.ancash.minecraft.cryptomorin.xseries.XMaterial;
import de.ancash.minecraft.inventory.IGUIManager;
import de.ancash.minecraft.inventory.InventoryItem;
import de.ancash.nbtnexus.InventoryUtils;
import de.ancash.nbtnexus.serde.SerializedItem;
import de.ancash.nbtnexus.serde.access.SerializedMetaAccess;

public class CraftingWorkspaceGUI extends AbstractCraftingWorkspace {

	protected int quickCraftingPage;
	protected List<IRecipe> quickCraftingRecipes = new ArrayList<>();

	public CraftingWorkspaceGUI(FancyCrafting pl, Player player, WorkspaceTemplate template) {
		this(pl, player, template, true);
	}

	public CraftingWorkspaceGUI(FancyCrafting pl, Player player, WorkspaceTemplate template, boolean v) {
		this(pl, player, template, v,
				new AutoRecipeMatcher(pl, player, pl.getRecipeManager().getAutoMatchingRecipes()));
	}

	public CraftingWorkspaceGUI(FancyCrafting pl, Player player, WorkspaceTemplate template, boolean v,
			AutoRecipeMatcher matcher) {
		super(pl, player, template, v, matcher);
		setRecipeMatchCompletionHandler(new RecipeMatchHandler(pl, this));
		setPermissionHandler(new RecipePermissionHandler(this));
		setAutoRecipeMatcherHandler(new AutoRecipeMatcherHandler(pl, this, matcher));
		for (int i = 0; i < template.getDimension().getSize(); i++)
			setItem(pl.getWorkspaceObjects().getBackgroundItem(), i);
		for (int i : template.getSlots().getCraftingSlots())
			setItem(null, i);
		if (enableQuickCrafting())
			for (int i : template.getSlots().getAutoCraftingSlots())
				setItem(pl.getWorkspaceObjects().getQuickCraftingItem(), i);

		addInventoryItem(new InventoryItem(this, pl.getWorkspaceObjects().getCloseItem(),
				template.getSlots().getCloseSlot(),
				(a, b, c, top) -> Optional.ofNullable(top ? this : null).ifPresent(CraftingWorkspaceGUI::closeAll)));
		IGUIManager.register(this, getId());
		Bukkit.getScheduler().runTask(pl, () -> {
			getRecipeMatchCompletionHandler().onNoRecipeMatch();
			updateQuickCrafting();
			open();
			player.updateInventory();
		});
	}

	@Override
	public void onInventoryClick(InventoryClickEvent event) {
		if (event.getClickedInventory() == null) {
			event.setCancelled(true);
			getAutoRecipeHandler().onAutoRecipesChangePage(event);
			return;
		}
		InventoryAction a = event.getAction();
		if (event.getClick().equals(ClickType.DOUBLE_CLICK) || a.equals(InventoryAction.MOVE_TO_OTHER_INVENTORY)
				&& !event.getInventory().equals(event.getClickedInventory())) {
			if (event.isShiftClick()) {

				if (event.getView().getBottomInventory().getItem(event.getSlot()) != null) {
					if (getItem(template.getSlots().getResultSlot()) != null) {
						SerializedItem clicked = SerializedItem
								.of(event.getView().getBottomInventory().getItem(event.getSlot()));
						if (SerializedItem.of(getItem(template.getSlots().getResultSlot())).hashCode() == clicked
								.hashCode()
								|| pl.getWorkspaceObjects().getBackgroundItem().hashCode() == clicked.hashCode()
								|| pl.getWorkspaceObjects().getValidItem().hashCode() == clicked.hashCode()
								|| pl.getWorkspaceObjects().getInvalidItem().hashCode() == clicked.hashCode()
								|| pl.getWorkspaceObjects().getCloseItem().hashCode() == clicked.hashCode()) {
							event.setCancelled(true);
							return;
						}
					}
				}
			} else {
				event.setCancelled(true);
				return;
			}
		}
		boolean workbenchInv = event.getClickedInventory() != null
				&& event.getClickedInventory().equals(event.getInventory());
		boolean craftingSlot = isCraftingSlot(event.getSlot());

		if (workbenchInv && !craftingSlot && event.getSlot() != template.getSlots().getResultSlot()) {
			event.setCancelled(true);
			return;
		}

		if (workbenchInv && craftingSlot) {
			event.setCancelled(false);
		} else if (workbenchInv && event.getSlot() == template.getSlots().getResultSlot()) {
			event.setCancelled(true);
			if (getCurrentRecipe() != null
					&& (currentRecipe instanceof IRandomRecipe && event.isShiftClick() ? false : true)) {
				craftItem(event, currentRecipe);
				updateLastCraftTick();
				return;
			}
		}
		if (!event.isCancelled()) {
			if (workbenchInv && onTopInventoryClick(event)) {
				event.setCancelled(true);
				updateAll();
				return;
			}
			if (!workbenchInv && onBottomInventoryClick(event)) {
				event.setCancelled(true);
				updateAll();
				return;
			}
		}
		checkDelayed();
	}

	private boolean onBottomInventoryClick(InventoryClickEvent event) {
		if (!event.isShiftClick()) {
			return false;
		}
		ItemStack shift = event.getClickedInventory().getItem(event.getSlot());
		if (shift == null || shift.getType() == Material.AIR)
			return false;

		SerializedItem iShift = SerializedItem.of(shift);
		if (iShift.hashCode() == pl.getWorkspaceObjects().getCloseItem().hashCode()
				|| iShift.hashCode() == pl.getWorkspaceObjects().getBackgroundItem().hashCode()
				|| iShift.hashCode() == pl.getWorkspaceObjects().getValidItem().hashCode()
				|| iShift.hashCode() == pl.getWorkspaceObjects().getInvalidItem().hashCode()
				|| getAutoRecipeHandler().getResutlHashCodes().contains(iShift.hashCode())) {
			return true;
		}

		return false;
	}

	private boolean onTopInventoryClick(InventoryClickEvent event) {
		ItemStack slotItem = event.getCurrentItem();
		ItemStack cursor = event.getCursor();
		boolean isCursorNull = cursor == null || cursor.getType() == Material.AIR;
		boolean isSlotNull = slotItem == null || slotItem.getType() == Material.AIR;

		if (isSlotNull && isCursorNull && event.getAction() != InventoryAction.HOTBAR_SWAP)
			return true;
		if (event.getAction() == InventoryAction.HOTBAR_SWAP
				|| event.getAction() == InventoryAction.HOTBAR_MOVE_AND_READD) {
			onTopHotbawSwap(event, slotItem);
			return true;
		}
		if (!isSlotNull && event.isShiftClick()) {
			onTopSlotNotNullShift(event, slotItem);
			return true;
		} else if (isCursorNull && !isSlotNull) {
			onTopCursorNullSlotNotNull(event, slotItem);
			return true;
		} else if (!isCursorNull && isSlotNull) {
			onTopCursorNotNullSlotNull(event, cursor);
			return true;
		} else if (!isCursorNull && !isSlotNull) {
			onTopCursorNotNullSlotNotNull(event, slotItem, cursor);
			return true;
		}
		return false;
	}

	private void onTopHotbawSwap(InventoryClickEvent event, ItemStack slotItem) {
		event.getInventory().setItem(event.getSlot(),
				event.getWhoClicked().getInventory().getItem(event.getHotbarButton()));
		event.getWhoClicked().getInventory().setItem(event.getHotbarButton(), slotItem);
	}

	private void onTopSlotNotNullShift(InventoryClickEvent event, ItemStack slotItem) {
		ItemStack toAdd = slotItem.clone();
		toAdd.setAmount(1);
		int free = InventoryUtils.getFreeSpaceExact((Player) event.getWhoClicked(), toAdd);
		int adding = free < slotItem.getAmount() ? free : slotItem.getAmount();
		InventoryUtils.addItemStack(player, toAdd, adding);
		setAmount(slotItem.getAmount(), adding, event.getSlot());
	}

	private void onTopCursorNullSlotNotNull(InventoryClickEvent event, ItemStack slotItem) {
		if (event.isLeftClick()) {
			event.getWhoClicked().setItemOnCursor(event.getInventory().getItem(event.getSlot()));
			event.getInventory().setItem(event.getSlot(), null);
		} else {
			int taking = (int) Math.ceil(slotItem.getAmount() / 2);
			if (slotItem.getAmount() == 1)
				taking = 1;
			ItemStack clone = slotItem.clone();
			clone.setAmount(taking);
			event.getWhoClicked().setItemOnCursor(clone);
			setAmount(slotItem.getAmount(), taking, event.getSlot());
		}
	}

	private void onTopCursorNotNullSlotNull(InventoryClickEvent event, ItemStack cursor) {
		if (event.isLeftClick()) {
			event.getInventory().setItem(event.getSlot(), cursor);
			event.getWhoClicked().setItemOnCursor(null);
		} else {
			ItemStack clone = cursor.clone();
			clone.setAmount(1);
			event.getInventory().setItem(event.getSlot(), clone);
			if (cursor.getAmount() - 1 > 0)
				cursor.setAmount(cursor.getAmount() - 1);
			else
				event.getWhoClicked().setItemOnCursor(null);
		}
	}

	private void onTopCursorNotNullSlotNotNull(InventoryClickEvent event, ItemStack slotItem, ItemStack cursor) {
		SerializedItem iSlotItem = SerializedItem.of(slotItem);
		SerializedItem iCursor = SerializedItem.of(cursor);
		if (iSlotItem.hashCode() == iCursor.hashCode() && slotItem.getAmount() < slotItem.getMaxStackSize()) {
			if (event.isLeftClick()) {
				int free = slotItem.getMaxStackSize() - slotItem.getAmount();
				int adding = free > cursor.getAmount() ? cursor.getAmount() : free;
				slotItem.setAmount(slotItem.getAmount() + adding);
				if (cursor.getAmount() - adding > 0)
					cursor.setAmount(cursor.getAmount() - adding);
				else
					event.getWhoClicked().setItemOnCursor(null);
			} else {
				slotItem.setAmount(slotItem.getAmount() + 1);
				if (cursor.getAmount() - 1 > 0)
					cursor.setAmount(cursor.getAmount() - 1);
				else
					event.getWhoClicked().setItemOnCursor(null);
			}
		} else {
			event.getWhoClicked().setItemOnCursor(slotItem);
			event.getInventory().setItem(event.getSlot(), cursor);
		}
	}

	@Override
	public void onInventoryClose(InventoryCloseEvent event) {
		IGUIManager.remove(getId());
		for (int i : template.getSlots().getCraftingSlots()) {
			ItemStack item = getItem(i);
			if (item == null || item.getType().equals(Material.AIR))
				continue;
			if (InventoryUtils.getFreeSpaceExact(player, item) >= item.getAmount())
				player.getInventory().addItem(item);
			else
				player.getWorld().dropItem(player.getLocation(), item);
			player.updateInventory();
		}
	}

	@Override
	public void onInventoryDrag(InventoryDragEvent event) {
		if (event.getRawSlots().stream().filter(i -> i < template.getDimension().getSize())
				.filter(i -> !isCraftingSlot(i)).findAny().isPresent()) {
			event.setCancelled(true);
			return;
		}
		checkDelayed();
	}

	private void craftItem(InventoryClickEvent event, IRecipe recipe) {
		if (recipe == null || ILibrary.getTick() - pl.getCraftingCooldown() <= getLastCraftTick()) {
			event.getWhoClicked().sendMessage(pl.getResponse().CRAFTING_COOLDOWN_MESSAGE);
			updateAll();
			return;
		}
		updateLastCraftTick();
		ItemStack cursor = event.getCursor();
		if (event.getAction().equals(InventoryAction.PICKUP_ALL)) {
			if (cursor != null && cursor.getType() == Material.AIR) {
				collectIngredients(event.getInventory(), recipe);
				event.getWhoClicked()
						.setItemOnCursor(getRecipeMatchCompletionHandler().getSingleRecipeCraft(recipe, player));
				updateAll();
				return;
			}
		} else if (event.getAction().equals(InventoryAction.MOVE_TO_OTHER_INVENTORY)) {
			if (recipe.isShiftCollectable()) {
				InventoryUtils.addItemStack(player, recipe.getResult(),
						shiftCollectIngredients(recipe) * recipe.getResult().getAmount());
			} else {
				ItemStack result = getRecipeMatchCompletionHandler().getSingleRecipeCraft(recipe, player);
				if (InventoryUtils.getFreeSpaceExact((Player) event.getWhoClicked(), result) >= result.getAmount()) {
					collectIngredients(event.getInventory(), recipe);
					event.getWhoClicked().getInventory().addItem(result);
				}
			}
		} else if ((event.getAction().equals(InventoryAction.PLACE_ONE)
				|| event.getAction().equals(InventoryAction.PLACE_ALL))
				&& SerializedItem.of(cursor).hashCode() == recipe.getResultAsSerializedItem().hashCode()
				&& cursor.getAmount() + recipe.getResult().getAmount() <= cursor.getMaxStackSize()
				&& !(recipe instanceof IRandomRecipe)) {
			cursor.setAmount(cursor.getAmount() + recipe.getResult().getAmount());
			collectIngredients(event.getInventory(), recipe);
		}
		updateAll();
	}

	private int shiftCollectIngredients(IRecipe recipe) {
		int space = InventoryUtils.getFreeSpaceExact(player, recipe.getResult());
		if (space <= 0) {
			return 0;
		}
		int shiftSize = space / recipe.getResult().getAmount();
		if (recipe instanceof IShapedRecipe) {
			for (int i = 0; i < matrix.getArray().length; ++i) {
				if (matrix.getArray()[i] == null)
					continue;
				if (recipe.isVanilla()) {
					shiftSize = Math.min(shiftSize,
							SerializedMetaAccess.UNSPECIFIC_META_ACCESS.getAmount(matrix.getArray()[i].getMap()));
				} else {
					SerializedItem iIngredient = ((IShapedRecipe) recipe).getIngredientsArray()[i];
					SerializedItem iCompare = matrix.getArray()[i];
					if (!iIngredient.areEqualIgnoreAmount(iCompare))
						continue;
					shiftSize = Math.min(shiftSize,
							(int) (SerializedMetaAccess.UNSPECIFIC_META_ACCESS.getAmount(iCompare.getMap())
									/ SerializedMetaAccess.UNSPECIFIC_META_ACCESS.getAmount(iIngredient.getMap())));
				}
			}
		}
		if (recipe instanceof IShapelessRecipe) {
			for (ItemStack ingredient : ((IShapelessRecipe) recipe).getIngredients()) {
				if (ingredient == null)
					continue;
				for (SerializedItem currentItem : matrix.getArray()) {
					if (currentItem == null)
						continue;
					if (recipe.isVanilla())
						shiftSize = Math.min(shiftSize,
								SerializedMetaAccess.UNSPECIFIC_META_ACCESS.getAmount(currentItem.getMap()));
					else if (currentItem.areEqualIgnoreAmount(SerializedItem.of(ingredient)))
						shiftSize = Math.min(shiftSize,
								(int) (SerializedMetaAccess.UNSPECIFIC_META_ACCESS.getAmount(currentItem.getMap())
										/ ingredient.getAmount()));
				}
			}
		}
		if (recipe instanceof IShapedRecipe) {
			collectShaped((IShapedRecipe) recipe, shiftSize);
		} else if (recipe instanceof IShapelessRecipe) {
			collectShapeless((IShapelessRecipe) recipe, shiftSize);
		}
		return shiftSize;
	}

	private void collectShapeless(IShapelessRecipe shapeless, int multiplicator) {
		Set<Integer> done = new HashSet<>();
		Set<XMaterial> ignoredMaterials = new HashSet<>();

		if (shapeless instanceof IComplexRecipe)
			ignoredMaterials = ((IComplexRecipe) shapeless).getIgnoredMaterials();

		for (SerializedItem ingredient : shapeless.getSerializedIngredients()) {
			for (int craftSlot : template.getSlots().getCraftingSlots()) {
				if (done.contains(craftSlot))
					continue;
				if (getItem(craftSlot) == null)
					continue;
				if (ingredient.hashCode() != SerializedItem.of(getItem(craftSlot)).hashCode() && !shapeless.isVanilla())
					continue;
				if (!ignoredMaterials.contains(XMaterial.matchXMaterial(getItem(craftSlot))))
					setAmount(getItem(craftSlot).getAmount(),
							SerializedMetaAccess.UNSPECIFIC_META_ACCESS.getAmount(ingredient.getMap()) * multiplicator,
							craftSlot);
				done.add(craftSlot);
				break;
			}
		}
	}

	private void collectShaped(IShapedRecipe shaped, int multiplicator) {
		int base = template.getDimension().getWidth() * matrix.getUpMoves() + matrix.getLeftMoves();
		boolean isMirrored = canBeMirrored(shaped);
		for (int y = 0; y < shaped.getHeight(); y++) {
			for (int x = 0; x < shaped.getWidth(); x++) {
				int i = y * shaped.getWidth() + x;
				int j = mirror(shaped.getWidth(), y, x, isMirrored);
				SerializedItem fromInv = matrix.getArray()[j];
				SerializedItem fromRec = shaped.getIngredientsArray()[i];
				if (fromInv == null || fromRec == null)
					continue;
				int amount = SerializedMetaAccess.UNSPECIFIC_META_ACCESS.getAmount(fromInv.getMap())
						- SerializedMetaAccess.UNSPECIFIC_META_ACCESS.getAmount(fromRec.getMap()) * multiplicator;
				int slot;
				if (isMirrored)
					slot = template.getSlots().getCraftingSlots()[base + y * template.getDimension().getWidth()
							+ (matrix.getWidth() - x - 1)];
				else
					slot = template.getSlots().getCraftingSlots()[base + y * template.getDimension().getWidth() + x];
				if (amount > 0)
					getItem(slot).setAmount(amount);
				else
					setItem(null, slot);
			}
		}
	}

	private int mirror(int width, int y, int x, boolean really) {
		return really ? y * width + (width - x - 1) : y * width + x;
	}

	/**
	 * Only checks whether the items are null or not Since all ingredients in a
	 * vanilla recipe amount to one a proper check is not required
	 * 
	 * @param recipe
	 * @return
	 */
	private boolean canBeMirrored(IShapedRecipe recipe) {
		if (!recipe.isVanilla())
			return false;
		for (int y = 0; y < recipe.getHeight(); y++)
			for (int x = 0; x < recipe.getWidth(); x++)
				if ((recipe.getIngredientsArray()[y * recipe.getWidth() + x] != null) != (matrix
						.getArray()[y * recipe.getWidth() + (recipe.getWidth() - x - 1)] != null))
					return false;
		return true;
	}

	private void collectIngredients(Inventory inventory, IRecipe recipe) {
		if (recipe instanceof IShapedRecipe)
			collectShaped((IShapedRecipe) recipe, 1);
		if (recipe instanceof IShapelessRecipe)
			collectShapeless((IShapelessRecipe) recipe, 1);
	}
}