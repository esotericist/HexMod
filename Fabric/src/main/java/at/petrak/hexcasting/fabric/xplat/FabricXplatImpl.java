package at.petrak.hexcasting.fabric.xplat;

import at.petrak.hexcasting.api.HexAPI;
import at.petrak.hexcasting.api.addldata.ADHexHolder;
import at.petrak.hexcasting.api.addldata.ADIotaHolder;
import at.petrak.hexcasting.api.addldata.ADMediaHolder;
import at.petrak.hexcasting.api.addldata.ADVariantItem;
import at.petrak.hexcasting.api.casting.ActionRegistryEntry;
import at.petrak.hexcasting.api.casting.arithmetic.Arithmetic;
import at.petrak.hexcasting.api.casting.castables.SpecialHandler;
import at.petrak.hexcasting.api.casting.eval.ResolvedPattern;
import at.petrak.hexcasting.api.casting.eval.sideeffects.EvalSound;
import at.petrak.hexcasting.api.casting.eval.vm.CastingImage;
import at.petrak.hexcasting.api.casting.eval.vm.CastingVM;
import at.petrak.hexcasting.api.casting.iota.IotaType;
import at.petrak.hexcasting.api.mod.HexConfig;
import at.petrak.hexcasting.api.mod.HexTags;
import at.petrak.hexcasting.api.pigment.ColorProvider;
import at.petrak.hexcasting.api.pigment.FrozenPigment;
import at.petrak.hexcasting.api.player.AltioraAbility;
import at.petrak.hexcasting.api.player.FlightAbility;
import at.petrak.hexcasting.api.player.Sentinel;
import at.petrak.hexcasting.common.lib.HexItems;
import at.petrak.hexcasting.common.msgs.IMessage;
import at.petrak.hexcasting.fabric.cc.HexCardinalComponents;
import at.petrak.hexcasting.fabric.interop.gravity.GravityApiInterop;
import at.petrak.hexcasting.fabric.interop.trinkets.TrinketsApiInterop;
import at.petrak.hexcasting.fabric.recipe.FabricUnsealedIngredient;
import at.petrak.hexcasting.interop.HexInterop;
import at.petrak.hexcasting.interop.pehkui.PehkuiInterop;
import at.petrak.hexcasting.xplat.IXplatAbstractions;
import at.petrak.hexcasting.xplat.IXplatTags;
import at.petrak.hexcasting.xplat.Platform;
import com.google.common.base.Suppliers;
import com.jamieswhiteshirt.reachentityattributes.ReachEntityAttributes;
import com.mojang.serialization.Lifecycle;
import net.fabricmc.api.EnvType;
import net.fabricmc.fabric.api.client.itemgroup.FabricItemGroupBuilder;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.event.registry.FabricRegistryBuilder;
import net.fabricmc.fabric.api.item.v1.FabricItemSettings;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidConstants;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.advancements.critereon.ItemPredicate;
import net.minecraft.core.*;
import net.minecraft.network.protocol.Packet;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.storage.loot.predicates.AlternativeLootItemCondition;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.level.storage.loot.predicates.MatchTool;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import virtuoel.pehkui.api.ScaleTypes;

import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static at.petrak.hexcasting.api.HexAPI.modLoc;

public class FabricXplatImpl implements IXplatAbstractions {
    @Override
    public Platform platform() {
        return Platform.FABRIC;
    }

    @Override
    public boolean isPhysicalClient() {
        return FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT;
    }

    @Override
    public boolean isModPresent(String id) {
        return FabricLoader.getInstance().isModLoaded(id);
    }

    @Override
    public void initPlatformSpecific() {
        if (this.isModPresent(HexInterop.Fabric.GRAVITY_CHANGER_API_ID)) {
            GravityApiInterop.init();
        }
        if (this.isModPresent(HexInterop.Fabric.TRINKETS_API_ID)) {
            TrinketsApiInterop.init();
        }
    }

    @Override
    public double getReachDistance(Player player) {
        return ReachEntityAttributes.getReachDistance(player, 5.0);
    }

    @Override
    public void sendPacketToPlayer(ServerPlayer target, IMessage packet) {
        ServerPlayNetworking.send(target, packet.getFabricId(), packet.toBuf());
    }

    @Override
    public void sendPacketNear(Vec3 pos, double radius, ServerLevel dimension, IMessage packet) {
        var pkt = ServerPlayNetworking.createS2CPacket(packet.getFabricId(), packet.toBuf());
        var nears = PlayerLookup.around(dimension, pos, radius);
        for (var p : nears) {
            p.connection.send(pkt);
        }
    }

    @Override
    public Packet<?> toVanillaClientboundPacket(IMessage message) {
        return ServerPlayNetworking.createS2CPacket(message.getFabricId(), message.toBuf());
    }

    @Override
    public void setBrainsweepAddlData(Mob mob) {
        var cc = HexCardinalComponents.BRAINSWEPT.get(mob);
        cc.setBrainswept(true);
        // CC API does the syncing for us
    }

    @Override
    public @Nullable FrozenPigment setPigment(Player target, @Nullable FrozenPigment pigment) {
        var cc = HexCardinalComponents.FAVORED_PIGMENT.get(target);
        var old = cc.getPigment();
        cc.setPigment(pigment);
        return old;
    }

    @Override
    public void setSentinel(Player target, @Nullable Sentinel sentinel) {
        var cc = HexCardinalComponents.SENTINEL.get(target);
        cc.setSentinel(sentinel);
    }

    @Override
    public void setFlight(ServerPlayer target, FlightAbility flight) {
        var cc = HexCardinalComponents.FLIGHT.get(target);
        cc.setFlight(flight);
    }

    public void setAltiora(Player target, @Nullable AltioraAbility altiora) {
        var cc = HexCardinalComponents.ALTIORA.get(target);
        cc.setAltiora(altiora);
    }

    public void setStaffcastImage(ServerPlayer target, CastingImage image) {
        var cc = HexCardinalComponents.STAFFCAST_IMAGE.get(target);
        cc.setImage(image);
    }

    @Override
    public void setPatterns(ServerPlayer target, List<ResolvedPattern> patterns) {
        var cc = HexCardinalComponents.PATTERNS.get(target);
        cc.setPatterns(patterns);
    }

    @Override
    public boolean isBrainswept(Mob mob) {
        var cc = HexCardinalComponents.BRAINSWEPT.get(mob);
        return cc.isBrainswept();
    }

    @Override
    public @Nullable FlightAbility getFlight(ServerPlayer player) {
        var cc = HexCardinalComponents.FLIGHT.get(player);
        return cc.getFlight();
    }

    @Override
    public @Nullable AltioraAbility getAltiora(Player player) {
        var cc = HexCardinalComponents.ALTIORA.get(player);
        return cc.getAltiora();
    }

    @Override
    public FrozenPigment getPigment(Player player) {
        var cc = HexCardinalComponents.FAVORED_PIGMENT.get(player);
        return cc.getPigment();
    }

    @Override
    public Sentinel getSentinel(Player player) {
        var cc = HexCardinalComponents.SENTINEL.get(player);
        return cc.getSentinel();
    }

    @Override
    public CastingVM getStaffcastVM(ServerPlayer player, InteractionHand hand) {
        var cc = HexCardinalComponents.STAFFCAST_IMAGE.get(player);
        return cc.getVM(hand);
    }

    @Override
    public List<ResolvedPattern> getPatternsSavedInUi(ServerPlayer player) {
        var cc = HexCardinalComponents.PATTERNS.get(player);
        return cc.getPatterns();
    }

    @Override
    public void clearCastingData(ServerPlayer player) {
        this.setStaffcastImage(player, null);
        this.setPatterns(player, List.of());
    }

    @Override
    public @Nullable
    ADMediaHolder findMediaHolder(ItemStack stack) {
        var cc = HexCardinalComponents.MEDIA_HOLDER.maybeGet(stack);
        return cc.orElse(null);
    }

    @Override
    public @Nullable ADMediaHolder findMediaHolder(ServerPlayer player) {
        var cc = HexCardinalComponents.MEDIA_HOLDER.maybeGet(player);
        return cc.orElse(null);
    }

    @Override
    public @Nullable
    ADIotaHolder findDataHolder(ItemStack stack) {
        var cc = HexCardinalComponents.IOTA_HOLDER.maybeGet(stack);
        return cc.orElse(null);
    }

    @Override
    public @Nullable
    ADIotaHolder findDataHolder(Entity entity) {
        var cc = HexCardinalComponents.IOTA_HOLDER.maybeGet(entity);
        return cc.orElse(null);
    }

    @Override
    public @Nullable
    ADHexHolder findHexHolder(ItemStack stack) {
        var cc = HexCardinalComponents.HEX_HOLDER.maybeGet(stack);
        return cc.orElse(null);
    }

    @Override
    public @Nullable ADVariantItem findVariantHolder(ItemStack stack) {
        var cc = HexCardinalComponents.VARIANT_ITEM.maybeGet(stack);
        return cc.orElse(null);
    }

    @Override
    public boolean isPigment(ItemStack stack) {
        return HexCardinalComponents.PIGMENT.isProvidedBy(stack);
    }

    @Override
    public ColorProvider getColorProvider(FrozenPigment pigment) {
        var cc = HexCardinalComponents.PIGMENT.maybeGet(pigment.item());
        return cc.map(col -> col.provideColor(pigment.owner())).orElse(ColorProvider.MISSING);
    }

    @Override
    public <T extends BlockEntity> BlockEntityType<T> createBlockEntityType(BiFunction<BlockPos, BlockState, T> func,
        Block... blocks) {
        return FabricBlockEntityTypeBuilder.create(func::apply, blocks).build();
    }

    @Override
    @SuppressWarnings("UnstableApiUsage")
    public boolean tryPlaceFluid(Level level, InteractionHand hand, BlockPos pos, Fluid fluid) {
        Storage<FluidVariant> target = FluidStorage.SIDED.find(level, pos, Direction.UP);
        if (target == null) {
            return false;
        }
        try (Transaction transaction = Transaction.openOuter()) {
            long insertedAmount = target.insert(FluidVariant.of(fluid), FluidConstants.BUCKET, transaction);
            if (insertedAmount > 0) {
                transaction.commit();
                return true;
            }
        }
        return false;
    }

    @Override
    @SuppressWarnings("UnstableApiUsage")
    public boolean drainAllFluid(Level level, BlockPos pos) {
        Storage<FluidVariant> target = FluidStorage.SIDED.find(level, pos, Direction.UP);
        if (target == null) {
            return false;
        }
        try (Transaction transaction = Transaction.openOuter()) {
            boolean any = false;
            for (var view : target) {
                long extracted = view.extract(view.getResource(), view.getAmount(), transaction);
                if (extracted > 0) {
                    any = true;
                }
            }

            if (any) {
                transaction.commit();
                return true;
            }
        }
        return false;
    }

    @Override
    public Ingredient getUnsealedIngredient(ItemStack stack) {
        return FabricUnsealedIngredient.of(stack);
    }

    private static final Supplier<CreativeModeTab> TAB = Suppliers.memoize(() -> FabricItemGroupBuilder.create(
            modLoc("creative_tab"))
        .icon(HexItems::tabIcon)
        .build());

    @Override
    public CreativeModeTab getTab() {
        return TAB.get();
    }

    // do a stupid hack from botania
    private static List<ItemStack> stacks(Item... items) {
        return Stream.of(items).map(ItemStack::new).toList();
    }

    private static final List<List<ItemStack>> HARVEST_TOOLS_BY_LEVEL = List.of(
        stacks(Items.WOODEN_PICKAXE, Items.WOODEN_AXE, Items.WOODEN_HOE, Items.WOODEN_SHOVEL),
        stacks(Items.STONE_PICKAXE, Items.STONE_AXE, Items.STONE_HOE, Items.STONE_SHOVEL),
        stacks(Items.IRON_PICKAXE, Items.IRON_AXE, Items.IRON_HOE, Items.IRON_SHOVEL),
        stacks(Items.DIAMOND_PICKAXE, Items.DIAMOND_AXE, Items.DIAMOND_HOE, Items.DIAMOND_SHOVEL),
        stacks(Items.NETHERITE_PICKAXE, Items.NETHERITE_AXE, Items.NETHERITE_HOE, Items.NETHERITE_SHOVEL)
    );

    @Override
    public boolean isCorrectTierForDrops(Tier tier, BlockState bs) {
        if (!bs.requiresCorrectToolForDrops()) {
            return true;
        }

        int level = HexConfig.server()
            .opBreakHarvestLevelBecauseForgeThoughtItWasAGoodIdeaToImplementHarvestTiersUsingAnHonestToGodTopoSort();
        for (var tool : HARVEST_TOOLS_BY_LEVEL.get(level)) {
            if (tool.isCorrectToolForDrops(bs)) {
                return true;
            }
        }

        return false;
    }

    @Override
    public Item.Properties addEquipSlotFabric(EquipmentSlot slot) {
        return new FabricItemSettings().equipmentSlot(s -> slot);
    }

    private static final IXplatTags TAGS = new IXplatTags() {
        @Override
        public TagKey<Item> amethystDust() {
            return HexTags.Items.create(new ResourceLocation("c", "amethyst_dusts"));
        }

        @Override
        public TagKey<Item> gems() {
            return HexTags.Items.create(new ResourceLocation("c", "gems"));
        }
    };

    @Override
    public IXplatTags tags() {
        return TAGS;
    }

    @Override
    public LootItemCondition.Builder isShearsCondition() {
        return AlternativeLootItemCondition.alternative(
            MatchTool.toolMatches(ItemPredicate.Builder.item().of(Items.SHEARS)),
            MatchTool.toolMatches(ItemPredicate.Builder.item().of(
                HexTags.Items.create(new ResourceLocation("c", "shears"))))
        );
    }

    @Override
    public String getModName(String namespace) {
        if (namespace.equals("c")) {
            return "Common";
        }
        Optional<ModContainer> container = FabricLoader.getInstance().getModContainer(namespace);
        if (container.isPresent()) {
            return container.get().getMetadata().getName();
        }
        return namespace;
    }

    private static final Supplier<Registry<ActionRegistryEntry>> ACTION_REGISTRY = Suppliers.memoize(() ->
        FabricRegistryBuilder.from(new MappedRegistry<ActionRegistryEntry>(
                ResourceKey.createRegistryKey(modLoc("action")),
                Lifecycle.stable(), null))
            .buildAndRegister()
    );
    private static final Supplier<Registry<SpecialHandler.Factory<?>>> SPECIAL_HANDLER_REGISTRY =
        Suppliers.memoize(() ->
            FabricRegistryBuilder.from(new MappedRegistry<SpecialHandler.Factory<?>>(
                    ResourceKey.createRegistryKey(modLoc("special_handler")),
                    Lifecycle.stable(), null))
                .buildAndRegister()
        );
    private static final Supplier<Registry<IotaType<?>>> IOTA_TYPE_REGISTRY = Suppliers.memoize(() ->
        FabricRegistryBuilder.from(new DefaultedRegistry<IotaType<?>>(
                HexAPI.MOD_ID + ":null", ResourceKey.createRegistryKey(modLoc("iota_type")),
                Lifecycle.stable(), null))
            .buildAndRegister()
    );

    private static final Supplier<Registry<Arithmetic>> ARITHMETIC_REGISTRY = Suppliers.memoize(() ->
            FabricRegistryBuilder.from(new DefaultedRegistry<Arithmetic>(
                            HexAPI.MOD_ID + ":null", ResourceKey.createRegistryKey(modLoc("arithmetic")),
                            Lifecycle.stable(), null))
                    .buildAndRegister()
    );
    private static final Supplier<Registry<EvalSound>> EVAL_SOUNDS_REGISTRY = Suppliers.memoize(() ->
        FabricRegistryBuilder.from(new DefaultedRegistry<EvalSound>(
                HexAPI.MOD_ID + ":nothing", ResourceKey.createRegistryKey(modLoc("eval_sound")),
                Lifecycle.stable(), null))
            .buildAndRegister()
    );

    @Override
    public Registry<ActionRegistryEntry> getActionRegistry() {
        return ACTION_REGISTRY.get();
    }

    @Override
    public Registry<SpecialHandler.Factory<?>> getSpecialHandlerRegistry() {
        return SPECIAL_HANDLER_REGISTRY.get();
    }

    @Override
    public Registry<IotaType<?>> getIotaTypeRegistry() {
        return IOTA_TYPE_REGISTRY.get();
    }

    @Override
    public Registry<Arithmetic> getArithmeticRegistry() {
        return ARITHMETIC_REGISTRY.get();
    }

    @Override
    public Registry<EvalSound> getEvalSoundRegistry() {
        return EVAL_SOUNDS_REGISTRY.get();
    }

    @Override
    public boolean isBreakingAllowed(Level world, BlockPos pos, BlockState state, Player player) {
        return PlayerBlockBreakEvents.BEFORE.invoker()
            .beforeBlockBreak(world, player, pos, state, world.getBlockEntity(pos));
    }

    @Override
    public boolean isPlacingAllowed(Level world, BlockPos pos, ItemStack blockStack, Player player) {
        ItemStack cached = player.getMainHandItem();
        player.setItemInHand(InteractionHand.MAIN_HAND, blockStack.copy());
        var success = UseItemCallback.EVENT.invoker().interact(player, world, InteractionHand.MAIN_HAND);
        player.setItemInHand(InteractionHand.MAIN_HAND, cached);
        return success.getResult() == InteractionResult.PASS; // No other mod tried to consume this
    }

    private static PehkuiInterop.ApiAbstraction PEHKUI_API = null;

    @Override
    public PehkuiInterop.ApiAbstraction getPehkuiApi() {
        if (!this.isModPresent(HexInterop.PEHKUI_ID)) {
            throw new IllegalArgumentException("cannot get the pehkui api without pehkui");
        }

        if (PEHKUI_API == null) {
            PEHKUI_API = new PehkuiInterop.ApiAbstraction() {
                @Override
                public float getScale(Entity e) {
                    return ScaleTypes.BASE.getScaleData(e).getScale();
                }

                @Override
                public void setScale(Entity e, float scale) {
                    ScaleTypes.BASE.getScaleData(e).setScale(scale);
                }
            };
        }
        return PEHKUI_API;
    }

}
