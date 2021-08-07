package mezz.jei.ingredients;

import com.google.common.collect.Multimap;
import mezz.jei.Internal;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.config.sorting.IngredientTypeSortingConfig;
import mezz.jei.config.sorting.ModNameSortingConfig;
import mezz.jei.gui.ingredients.IIngredientListElement;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.Tag;
import net.minecraft.tags.TagCollection;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShearsItem;
import net.minecraftforge.common.ToolType;
import org.apache.logging.log4j.LogManager;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class IngredientSorterComparators {
	private final IngredientFilter ingredientFilter;
	private final IIngredientManager ingredientManager;
	private final ModNameSortingConfig modNameSortingConfig;
	private final IngredientTypeSortingConfig ingredientTypeSortingConfig;

	public IngredientSorterComparators(
		IngredientFilter ingredientFilter,
		IIngredientManager ingredientManager,
		ModNameSortingConfig modNameSortingConfig,
		IngredientTypeSortingConfig ingredientTypeSortingConfig
	) {
		this.ingredientFilter = ingredientFilter;
		this.ingredientManager = ingredientManager;
		this.modNameSortingConfig = modNameSortingConfig;
		this.ingredientTypeSortingConfig = ingredientTypeSortingConfig;
	}

	public Comparator<IIngredientListElementInfo<?>> getComparator(List<IngredientSortStage> ingredientSorterStages) {
		return ingredientSorterStages.stream()
			.map(this::getComparator)
			.reduce(Comparator::thenComparing)
			.orElseGet(this::getDefault);
	}

	public Comparator<IIngredientListElementInfo<?>> getComparator(IngredientSortStage ingredientSortStage) {
		return switch (ingredientSortStage) {
			case ALPHABETICAL -> getAlphabeticalComparator();
			case CREATIVE_MENU -> getCreativeMenuComparator();
			case INGREDIENT_TYPE -> getIngredientTypeComparator();
			case MOD_NAME -> getModNameComparator();
			case TOOL_TYPE -> getToolsComparator();
			case TAG -> getTagComparator();
			case WEAPON_DAMAGE -> getWeaponDamageComparator();
			case ARMOR -> getArmorComparator();
			case MAX_DURABILITY -> getMaxDurabilityComparator();
		};
	}

	public Comparator<IIngredientListElementInfo<?>> getDefault() {
		return getModNameComparator()
			.thenComparing(getIngredientTypeComparator())
			.thenComparing(getCreativeMenuComparator());
	}

	private static Comparator<IIngredientListElementInfo<?>> getCreativeMenuComparator() {
		return Comparator.comparingInt(o -> {
			IIngredientListElement<?> element = o.getElement();
			return element.getOrderIndex();
		});
	}

	private static Comparator<IIngredientListElementInfo<?>> getAlphabeticalComparator() {
		return Comparator.comparing(IIngredientListElementInfo::getName);
	}

	private Comparator<IIngredientListElementInfo<?>> getModNameComparator() {
		Set<String> modNames = this.ingredientFilter.getModNamesForSorting();
		return this.modNameSortingConfig.getComparatorFromMappedValues(modNames);
	}

	private Comparator<IIngredientListElementInfo<?>> getIngredientTypeComparator() {
		Collection<IIngredientType<?>> ingredientTypes = this.ingredientManager.getRegisteredIngredientTypes();
		Set<String> ingredientTypeStrings = ingredientTypes.stream()
			.map(IIngredientType::getIngredientClass)
			.map(IngredientTypeSortingConfig::getIngredientType)
			.collect(Collectors.toSet());
		return this.ingredientTypeSortingConfig.getComparatorFromMappedValues(ingredientTypeStrings);
	}

	private static Comparator<IIngredientListElementInfo<?>> getMaxDurabilityComparator() {
		Comparator<IIngredientListElementInfo<?>> maxDamage =
			Comparator.comparing(o -> getItemStack(o).getMaxDamage());
		return maxDamage.reversed();
	}

	private static Comparator<IIngredientListElementInfo<?>> getTagComparator() {
		Comparator<IIngredientListElementInfo<?>> isTagged =
			Comparator.comparing(IngredientSorterComparators::hasTag);
		Comparator<IIngredientListElementInfo<?>> tag =
			Comparator.comparing(IngredientSorterComparators::getTagForSorting);
		return isTagged.reversed().thenComparing(tag);
	}

	private static Comparator<IIngredientListElementInfo<?>> getToolsComparator() {
		Comparator<IIngredientListElementInfo<?>> isToolComp =
			Comparator.comparing(o -> isTool(getItemStack(o)));
		Comparator<IIngredientListElementInfo<?>> toolType =
			Comparator.comparing(o -> getToolClass(getItemStack(o)));
		Comparator<IIngredientListElementInfo<?>> harvestLevel =
			Comparator.comparing(o -> getHarvestLevel(getItemStack(o)));
		Comparator<IIngredientListElementInfo<?>> maxDamage =
			Comparator.comparing(o -> getToolDurability(getItemStack(o)));

		return isToolComp.reversed() // Sort non-tools after the tools.
			.thenComparing(toolType)
			.thenComparing(harvestLevel.reversed())
			.thenComparing(maxDamage.reversed());
	}

	private static Comparator<IIngredientListElementInfo<?>> getWeaponDamageComparator() {
		Comparator<IIngredientListElementInfo<?>> isWeaponComp =
			Comparator.comparing(o -> isWeapon(getItemStack(o)));
		Comparator<IIngredientListElementInfo<?>> attackDamage =
			Comparator.comparing(o -> getWeaponDamage(getItemStack(o)));
		Comparator<IIngredientListElementInfo<?>> attackSpeed =
			Comparator.comparing(o -> getWeaponSpeed(getItemStack(o)));
		Comparator<IIngredientListElementInfo<?>> maxDamage =
			Comparator.comparing(o -> getWeaponDurability(getItemStack(o)));
		return isWeaponComp.reversed()
			.thenComparing(attackDamage.reversed())
			.thenComparing(attackSpeed.reversed())
			.thenComparing(maxDamage.reversed());
	}

	private static Comparator<IIngredientListElementInfo<?>> getArmorComparator() {
		Comparator<IIngredientListElementInfo<?>> isArmorComp =
			Comparator.comparing(o -> isArmor(getItemStack(o)));
		Comparator<IIngredientListElementInfo<?>> armorSlot =
			Comparator.comparing(o -> getArmorSlotIndex(getItemStack(o)));
		Comparator<IIngredientListElementInfo<?>> armorDamage =
			Comparator.comparing(o -> getArmorDamageReduce(getItemStack(o)));
		Comparator<IIngredientListElementInfo<?>> armorToughness =
			Comparator.comparing(o -> getArmorToughness(getItemStack(o)));
		Comparator<IIngredientListElementInfo<?>> maxDamage =
			Comparator.comparing(o -> getArmorDurability(getItemStack(o)));
		return isArmorComp.reversed()
			.thenComparing(armorSlot.reversed())
			.thenComparing(armorDamage.reversed())
			.thenComparing(armorToughness.reversed())
			.thenComparing(maxDamage.reversed());
	}

	private static int getHarvestLevel(ItemStack itemStack) {
		return itemStack.getToolTypes()
			.stream()
			.mapToInt(tool -> itemStack.getHarvestLevel(tool, null, null))
			.max()
			.orElse(-1);
	}

	private static boolean isTool(ItemStack itemStack) {
		return !getToolClass(itemStack).isEmpty();
	}

	private static int getToolDurability(ItemStack itemStack) {
		if (!isTool(itemStack)) {
			return 0;
		}
		return itemStack.getMaxDamage();
	}

	private static boolean isWeapon(ItemStack itemStack) {
		//Sort Weapons apart from tools, armor, and other random things..
		//AttackDamage also filters out Tools and Armor.  Anything that deals extra damage is a weapon.
		return getWeaponDamage(itemStack) > 0;
	}

	private static double getWeaponDamage(ItemStack itemStack) {
		if (isTool(itemStack) || isArmor(itemStack)) {
			return 0;
		}
		Multimap<Attribute, AttributeModifier> multimap = itemStack.getAttributeModifiers(EquipmentSlot.MAINHAND);
		return max(multimap, Attributes.ATTACK_DAMAGE);
	}

	private static double getWeaponSpeed(ItemStack itemStack) {
		if (!isWeapon(itemStack)) {
			return 0;
		}
		Multimap<Attribute, AttributeModifier> multimap = itemStack.getAttributeModifiers(EquipmentSlot.MAINHAND);
		return max(multimap, Attributes.ATTACK_SPEED);
	}

	private static double max(Multimap<Attribute, AttributeModifier> multimap, Attribute attribute) {
		Collection<AttributeModifier> modifiers = multimap.get(attribute);
		return max(modifiers);
	}

	private static double max(Collection<AttributeModifier> modifiers) {
		return modifiers.stream()
			.mapToDouble(AttributeModifier::getAmount)
			.max()
			.orElse(0);
	}

	private static int getWeaponDurability(ItemStack itemStack) {
		if (isWeapon(itemStack)) {
			return itemStack.getMaxDamage();
		}
		return 0;
	}

	private static boolean isArmor(ItemStack itemStack) {
		Item item = itemStack.getItem();
		return item instanceof ArmorItem;
	}

	private static int getArmorSlotIndex(ItemStack itemStack) {
		Item item = itemStack.getItem();
		if (item instanceof ArmorItem armorItem) {
			return armorItem.getSlot().getFilterFlag();
		}
		return 0;
	}

	private static int getArmorDamageReduce(ItemStack itemStack) {
		Item item = itemStack.getItem();
		if (item instanceof ArmorItem armorItem) {
			return armorItem.getDefense();
		}
		return 0;
	}

	private static float getArmorToughness(ItemStack itemStack) {
		Item item = itemStack.getItem();
		if (item instanceof ArmorItem armorItem) {
			return armorItem.getToughness();
		}
		return 0;
	}

	private static int getArmorDurability(ItemStack itemStack) {
		if (isArmor(itemStack)) {
			return itemStack.getMaxDamage();
		}
		return 0;
	}

	private static String getTagForSorting(IIngredientListElementInfo<?> elementInfo) {
		IIngredientManager ingredientManager = Internal.getIngredientManager();
		Collection<ResourceLocation> tagIds = elementInfo.getTagIds(ingredientManager);

		String bestTag = "";
		int maxTagSize = 0;
		//Group things by the most popular tag it has.
		for (ResourceLocation tagId : tagIds) {
			int thisTagSize = tagCount(tagId);
			if (thisTagSize > maxTagSize) {
				bestTag = tagId.getPath();
				maxTagSize = thisTagSize;
			}
		}

		return bestTag;
	}

	private static int tagCount(ResourceLocation tagId) {
		//TODO: make a tag blacklist.
		if (tagId.toString().equals("itemfilters:check_nbt")) {
			return 0;
		}
		TagCollection<Item> allTags = ItemTags.getAllTags();
		Tag<Item> tags = allTags.getTagOrEmpty(tagId);
		List<Item> values = tags.getValues();
		return values.size();
	}

	private static boolean hasTag(IIngredientListElementInfo<?> elementInfo) {
		return !getTagForSorting(elementInfo).isEmpty();
	}

	private static Boolean nullToolClassWarned = false;

	private static String getToolClass(ItemStack itemStack) {
		if (itemStack.isEmpty()) {
			return "";
		}
		Item item = itemStack.getItem();
		Set<ToolType> toolTypeSet = item.getToolTypes(itemStack);

		Set<String> toolClassSet = new HashSet<>();

		for (ToolType toolClass : toolTypeSet) {
			if (toolClass == null) {
				if (!nullToolClassWarned) {
					nullToolClassWarned = true;
					LogManager.getLogger().warn("Item '" + item.getRegistryName() + "' has a null tool class entry.");
				}
			} else if (!toolClass.getName().equals("sword")) {
				//Swords are not "tools".
				toolClassSet.add(toolClass.getName());
			}
		}

		//Minecraft hoes, shears, and fishing rods don't have tool class names.
		if (toolClassSet.isEmpty()) {
			if (item instanceof HoeItem) {
				return "hoe";
			}
			if (item instanceof ShearsItem) {
				return "shears";
			}
			if (item instanceof FishingRodItem) {
				return "fishingrod";
			}
			return "";
		}

		//Get the only thing.
		if (toolClassSet.size() == 1) {
			return toolClassSet.stream().findAny().get();
		}

		//We have a preferred type to list tools under, primarily the pickaxe for harvest level.
		String[] prefOrder = {"pickaxe", "axe", "shovel", "hoe", "shears", "wrench"};
		for (String s : prefOrder) {
			if (toolClassSet.contains(s)) {
				return s;
			}
		}

		return toolClassSet.stream().sorted().findFirst().orElse("");
	}

	public static <V> ItemStack getItemStack(IIngredientListElementInfo<V> ingredientInfo) {
		IIngredientListElement<V> element = ingredientInfo.getElement();
		V ingredient = element.getIngredient();
		if (ingredient instanceof ItemStack) {
			return (ItemStack) ingredient;
		}
		return ItemStack.EMPTY;
	}
}