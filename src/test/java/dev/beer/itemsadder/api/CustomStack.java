package dev.beer.itemsadder.api;
public final class CustomStack {
    public static CustomStack getInstance(String id){return id.equals("itemsadder-voidcore:invisible")?new CustomStack():null;}
    public org.bukkit.inventory.ItemStack getItemStack(){return new org.bukkit.inventory.ItemStack(org.bukkit.Material.PAPER);}
}
