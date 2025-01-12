package net.guavy.gravestones.block.entity;

import com.mojang.authlib.GameProfile;
//import net.fabricmc.fabric.api.block.entity.BlockEntityClientSerializable;
import net.guavy.gravestones.Gravestones;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.inventory.Inventories;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

public class GravestoneBlockEntity extends BlockEntity {
    // implements BlockEntityClientSerializable {
    private DefaultedList<ItemStack> items;
    private DefaultedList<ItemStack> armorItems;
    private DefaultedList<ItemStack> offHandItem;
    private DefaultedList<ItemStack> apiItems;
    private int xp;
    private GameProfile graveOwner;
    private String customName;

    public GravestoneBlockEntity(BlockPos pos, BlockState state) {
        super(Gravestones.GRAVESTONE_BLOCK_ENTITY, pos, state);

        this.customName = "";
        this.graveOwner = null;
        this.xp = 0;
        this.items = DefaultedList.ofSize(41, ItemStack.EMPTY);
    }

    public void setItems(DefaultedList<ItemStack> items, DefaultedList<ItemStack> armorItems, DefaultedList<ItemStack> offHandItem, DefaultedList<ItemStack> apiItems) {
        this.items = items;
        this.armorItems = armorItems;
        this.offHandItem = offHandItem;
        this.apiItems = apiItems;
        this.markDirty();
    }

    public DefaultedList<ItemStack>[] getItems() {
        return new DefaultedList[]{items, armorItems, offHandItem, apiItems};
    }

    public void setGraveOwner(GameProfile gameProfile) {
        this.graveOwner = gameProfile;
        this.markDirty();
    }

    public GameProfile getGraveOwner() {
        return graveOwner;
    }

    public void setCustomName(String text) {
        this.customName = text;
        this.markDirty();
    }

    public String getCustomName() {
        return customName;
    }

    public int getXp() {
        return xp;
    }

    public void setXp(int xp) {
        this.xp = xp;
        this.markDirty();
    }

    @Override
    public void readNbt(NbtCompound tag) {
        super.readNbt(tag);

        if (tag.contains("ItemCount")) {
            // Commence the great migration
            Inventories.readNbt(tag.getCompound("Items"), this.items);
            this.armorItems = DefaultedList.ofSize(4);
            this.offHandItem = DefaultedList.ofSize(1); // We aren't handling this
            this.apiItems = DefaultedList.of(); // We aren't handling this

            int filledLevel = 0;
            for (ItemStack itemStack : this.items) {
                if (filledLevel == 4) {
                    break;
                }
                EquipmentSlot equipmentSlot = MobEntity.getPreferredEquipmentSlot(itemStack);
                switch (equipmentSlot) {
                    case FEET -> {
                        if (this.armorItems.get(3).isEmpty()) {
                            this.armorItems.add(3, itemStack);
                            this.items.remove(itemStack);
                            filledLevel++;
                        }
                    }
                    case LEGS -> {
                        if (this.armorItems.get(2).isEmpty()) {
                            this.armorItems.add(2, itemStack);
                            this.items.remove(itemStack);
                            filledLevel++;
                        }
                    }
                    case CHEST -> {
                        if (this.armorItems.get(1).isEmpty()) {
                            this.armorItems.add(1, itemStack);
                            this.items.remove(itemStack);
                            filledLevel++;
                        }
                    }
                    case HEAD -> {
                        if (this.armorItems.get(0).isEmpty()) {
                            this.armorItems.add(0, itemStack);
                            this.items.remove(itemStack);
                            filledLevel++;
                        }
                    }
                    default -> {}
                }
            }
        } else {
            this.items = DefaultedList.ofSize(tag.getInt("InventoryItemCount"), ItemStack.EMPTY);
            this.armorItems = DefaultedList.ofSize(tag.getInt("ArmorItemCount"), ItemStack.EMPTY);
            this.offHandItem = DefaultedList.ofSize(tag.getInt("OffHandItemCount"), ItemStack.EMPTY);
            this.apiItems = DefaultedList.ofSize(tag.getInt("APIItemCount"), ItemStack.EMPTY);

            Inventories.readNbt(tag.getCompound("InventoryItems"), this.items);
            Inventories.readNbt(tag.getCompound("ArmorItems"), this.armorItems);
            Inventories.readNbt(tag.getCompound("OffHandItems"), this.offHandItem);
            Inventories.readNbt(tag.getCompound("APIItems"), this.apiItems);
        }

        this.xp = tag.getInt("XP");

        if(tag.contains("GraveOwner"))
            this.graveOwner = NbtHelper.toGameProfile(tag.getCompound("GraveOwner"));

        if(tag.contains("CustomName"))
            this.customName = tag.getString("CustomName");
    }

    @Override
    public void writeNbt(NbtCompound tag) {
        super.writeNbt(tag);

        tag.putInt("InventoryItemCount", this.items.size());
        tag.putInt("ArmorItemCount", this.armorItems.size());
        tag.putInt("OffHandItemCount", this.offHandItem.size());
        tag.putInt("APIItemCount", this.apiItems.size());

        tag.put("InventoryItems", Inventories.writeNbt(new NbtCompound(), this.items, true));
        tag.put("ArmorItems", Inventories.writeNbt(new NbtCompound(), this.armorItems, true));
        tag.put("OffHandItems", Inventories.writeNbt(new NbtCompound(), this.offHandItem, true));
        tag.put("APIItems", Inventories.writeNbt(new NbtCompound(), this.apiItems, true));

        tag.putInt("XP", xp);

        if(graveOwner != null)
            tag.put("GraveOwner", NbtHelper.writeGameProfile(new NbtCompound(), graveOwner));
        if(customName != null && !customName.isEmpty())
            tag.putString("CustomName", customName);
    }

    @Nullable
    @Override
    public Packet<ClientPlayPacketListener> toUpdatePacket() {
        return BlockEntityUpdateS2CPacket.create(this);
    }

    @Override
    public NbtCompound toInitialChunkDataNbt() {
        return createNbt();
    }
    /*
    @Override
    public NbtCompound toClientTag(NbtCompound tag) {
        if(graveOwner != null)
            tag.put("GraveOwner", NbtHelper.writeGameProfile(new NbtCompound(), this.graveOwner));
        if(customName != null && !customName.isEmpty())
            tag.putString("CustomName", customName);

        return tag;
    }

    @Override
    public void fromClientTag(NbtCompound tag) {
        if(tag.contains("GraveOwner"))
            this.graveOwner = NbtHelper.toGameProfile(tag.getCompound("GraveOwner"));
        if(tag.contains("CustomName"))
            this.customName = tag.getString("CustomName");
    }
     */
}
