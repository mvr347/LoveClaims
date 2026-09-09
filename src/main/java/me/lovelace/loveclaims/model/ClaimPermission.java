package me.lovelace.loveclaims.model;

import org.bukkit.Material;

public enum ClaimPermission {
    BUILD("Строительство", "Разрешить установку и разрушение блоков, использование вёдер и рамок", Material.IRON_PICKAXE),
    CONTAINERS("Контейнеры", "Разрешить открытие сундуков, бочек, печей, шалкеров и воронок", Material.CHEST),
    INTERACT("Взаимодействие", "Разрешить использование дверей, люков, кнопок и рычагов", Material.OAK_DOOR),
    MANAGE("Управление", "Разрешить изменять настройки привата и приглашать участников", Material.NAME_TAG);

    private final String title;
    private final String description;
    private final Material icon;

    ClaimPermission(String title, String description, Material icon) {
        this.title = title;
        this.description = description;
        this.icon = icon;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public Material getIcon() {
        return icon;
    }
}
