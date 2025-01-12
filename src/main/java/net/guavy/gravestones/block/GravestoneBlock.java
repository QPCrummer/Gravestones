package net.guavy.gravestones.block;

import net.guavy.gravestones.Gravestones;
import net.guavy.gravestones.api.GravestonesApi;
import net.guavy.gravestones.block.entity.GravestoneBlockEntity;
import net.guavy.gravestones.config.GravestoneDropType;
import net.guavy.gravestones.config.GravestoneRetrievalType;
import net.guavy.gravestones.config.GravestonesConfig;
import net.guavy.gravestones.mixin.CombinedInventoryAccessor;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.Properties;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.ItemScatterer;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class GravestoneBlock extends HorizontalFacingBlock implements BlockEntityProvider {
    public GravestoneBlock(Settings settings) {
        super(settings);
        setDefaultState(this.stateManager.getDefaultState().with(Properties.HORIZONTAL_FACING, Direction.NORTH));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> stateManager) {
        stateManager.add(Properties.HORIZONTAL_FACING);
    }

    @Override
    public void onSteppedOn(World world, BlockPos pos, BlockState state, Entity entity) {
        if(GravestonesConfig.getConfig().mainSettings.retrievalType == GravestoneRetrievalType.ON_STEP && entity instanceof PlayerEntity playerEntity) {
            RetrieveGrave(playerEntity, world, pos);
        }

        super.onSteppedOn(world, pos, state, entity);
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        if(GravestonesConfig.getConfig().mainSettings.retrievalType == GravestoneRetrievalType.ON_USE)
            RetrieveGrave(player, world, pos);

        return super.onUse(state, world, pos, player, hand, hit);
    }

    @Override
    public void onBreak(World world, BlockPos pos, BlockState state, PlayerEntity player) {
        if(GravestonesConfig.getConfig().mainSettings.retrievalType == GravestoneRetrievalType.ON_BREAK)
            if(RetrieveGrave(player, world, pos))
                return;

        dropAllGrave(world, pos);
        super.onBreak(world, pos, state, player);
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getOutlineShape(BlockState state, BlockView view, BlockPos pos, ShapeContext ct) {
        return VoxelShapes.cuboid(0.1f, 0f, 0.1f, 0.9f, 0.3f, 0.9f);
    }

    @Nullable
    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new GravestoneBlockEntity(pos, state);
    }

    public void dropAllGrave(World world, BlockPos pos) {
        if(world.isClient) return;

        BlockEntity be = world.getBlockEntity(pos);

        if(!(be instanceof GravestoneBlockEntity blockEntity)) return;

        blockEntity.markDirty();

        if(blockEntity.getItems() == null) return;

        DefaultedList<ItemStack> allItems = DefaultedList.of();

        for (DefaultedList<ItemStack> stacks : blockEntity.getItems()) {
            allItems.addAll(stacks);
        }

        ItemScatterer.spawn(world, pos, allItems);

        blockEntity.setItems(DefaultedList.copyOf(ItemStack.EMPTY), DefaultedList.copyOf(ItemStack.EMPTY), DefaultedList.copyOf(ItemStack.EMPTY), DefaultedList.copyOf(ItemStack.EMPTY));
    }

    public boolean RetrieveGrave(PlayerEntity playerEntity, World world, BlockPos pos) {
        if(world.isClient) return false;

        BlockEntity be = world.getBlockEntity(pos);

        if(!(be instanceof GravestoneBlockEntity blockEntity)) return false;

        blockEntity.markDirty();

        if(blockEntity.getItems() == null) return false;
        if(blockEntity.getGraveOwner() == null) return false;

        if(!GravestonesConfig.getConfig().mainSettings.enableGraveLooting) {
            if (!playerEntity.getGameProfile().getId().equals(blockEntity.getGraveOwner().getId())) {
                return false;
            }
        }

        DefaultedList<ItemStack> inventoryItems = blockEntity.getItems()[0];
        DefaultedList<ItemStack> armorItems = blockEntity.getItems()[1];
        DefaultedList<ItemStack> offHandItems = blockEntity.getItems()[2];
        DefaultedList<ItemStack> apiItems = blockEntity.getItems()[3];

        dropAllBelow(playerEntity);

        if(GravestonesConfig.getConfig().mainSettings.dropType == GravestoneDropType.PUT_IN_INVENTORY) {
            // Equip armor
            for (ItemStack itemStack : armorItems) {
                EquipmentSlot equipmentSlot = MobEntity.getPreferredEquipmentSlot(itemStack);

                playerEntity.equipStack(equipmentSlot, itemStack);
            }

            // Equip offhand
            if (!offHandItems.isEmpty()) {
                playerEntity.equipStack(EquipmentSlot.OFFHAND, offHandItems.get(0));
            }

            // Main Inventory
            boolean dropRemaining = false;
            int index = 0;
            for (ItemStack stack : inventoryItems) {
                int slot = playerEntity.getInventory().getEmptySlot();
                if (slot != -1) {
                    playerEntity.getInventory().insertStack(slot, stack);
                    index++;
                } else {
                    dropRemaining = true;
                    break;
                }
            }

            // Handle API
            int apiIndex = 0;
            for (GravestonesApi gravestonesApi : Gravestones.apiMods) {
                gravestonesApi.setInventory(apiItems.subList(apiIndex, apiIndex + gravestonesApi.getInventorySize(playerEntity)), playerEntity);
                apiIndex += gravestonesApi.getInventorySize(playerEntity);
            }

            if (dropRemaining) {
                DefaultedList<ItemStack> remaining = DefaultedList.of();
                for (int i = index; i < inventoryItems.size(); i++) {
                    remaining.add(inventoryItems.get(i));
                }
                ItemScatterer.spawn(world, pos, remaining);
            }
        }
        else if (GravestonesConfig.getConfig().mainSettings.dropType == GravestoneDropType.DROP_ITEMS) {
            DefaultedList<ItemStack> allItems = DefaultedList.of();

            for (DefaultedList<ItemStack> stacks : blockEntity.getItems()) {
                allItems.addAll(stacks);
            }

            ItemScatterer.spawn(world, pos, allItems);
        }

        playerEntity.addExperience((int) (GravestonesConfig.getConfig().mainSettings.xpPercentage * blockEntity.getXp()));


        world.removeBlock(pos, false);
        return true;
    }

    public void dropAllBelow(PlayerEntity player) {
        for(List<ItemStack> list : ((CombinedInventoryAccessor)player.getInventory()).getCombinedInventory()) {
            for(int i = 0; i < list.size(); ++i) {
                ItemStack itemStack = list.get(i);
                if (!itemStack.isEmpty()) {
                    player.dropItem(itemStack, false, false);
                    list.set(i, ItemStack.EMPTY);
                }
            }
        }

    }

    @Override
    public void onPlaced(World world, BlockPos pos, BlockState state, LivingEntity placer, ItemStack itemStack) {
        BlockEntity blockEntity = world.getBlockEntity(pos);

        if(!(blockEntity instanceof GravestoneBlockEntity gravestoneBlockEntity) || !itemStack.hasCustomName()) {
            super.onPlaced(world, pos, state, placer, itemStack);
            return;
        }

        gravestoneBlockEntity.setCustomName(itemStack.getOrCreateSubNbt("display").getString("Name"));
    }

    public BlockState getPlacementState(ItemPlacementContext ctx) {
        return this.getDefaultState().with(FACING, ctx.getHorizontalPlayerFacing());
    }
}
