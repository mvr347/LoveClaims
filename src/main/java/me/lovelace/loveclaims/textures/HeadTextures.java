package me.lovelace.loveclaims.textures;

/**
 * Централизованное хранилище base64 текстур голов (skull textures), используемых в GUI LoveClaims.
 * <p>
 * Все base64-литералы текстур голов объявляются здесь, а не хардкодятся по месту
 * использования — единая точка правды для GUI-текстур.
 */
public final class HeadTextures {

    private HeadTextures() {
        // Утилитарный класс-константа, инстанцирование не предполагается
    }

    public static final String HEAD_BACK =
            HeadsConfig.get("back", "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvODY1MmUyYjkzNmNhODAyNmJkMjg2NTFkN2M5ZjI4MTlkMmU5MjM2OTc3MzRkMThkZmRiMTM1NTBmOGZkYWQ1ZiJ9fX0=");
    public static final String HEAD_BARRIER =
            HeadsConfig.get("barrier", "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvM2VkMWFiYTczZjYzOWY0YmM0MmJkNDgxOTZjNzE1MTk3YmUyNzEyYzNiOTYyYzk3ZWJmOWU5ZWQ4ZWZhMDI1In19fQ==");
    public static final String HEAD_SETTINGS =
            HeadsConfig.get("settings", "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZGViMmQ3NTRhYmJjNWQwMDRiODBmZTA4YjUzMTg2ZjY5ZGM4ZTllZjZmOGY3ZmQwNzIxNGE4Yzg0OTZlYjA4NSJ9fX0=");
    public static final String HEAD_MEMBERS =
            HeadsConfig.get("members", "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZjFhZTgwMzg0YTAwYjZmOWY2NGRkODMwN2E5MDY3NjU0NGM5N2E3OTI5NzE2NWVhNzEzMjYyYzdkODgzMzg0NyJ9fX0=");
    public static final String HEAD_INFO =
            HeadsConfig.get("info", "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZjgwZDMyOTVkM2Q5YWJkNjI3NzZhYmNiOGRhNzU2ZjI5OGE1NDVmZWU5NDk4YzRmNjlhMWMyYzc4NTI0YzgyNCJ9fX0=");
    public static final String HEAD_ADD_MEMBER =
            HeadsConfig.get("add-member", "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvM2VkZDIwYmU5MzUyMDk0OWU2Y2U3ODlkYzRmNDNlZmFlYjI4YzcxN2VlNmJmY2JiZTAyNzgwMTQyZjcxNiJ9fX0=");
    public static final String HEAD_DELETE_YES =
            HeadsConfig.get("delete-yes", "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOTMwZjQ1MzdkMjE0ZDM4NjY2ZTYzMDRlOWM4NTFjZDZmN2U0MWEwZWI3YzI1MDQ5YzlkMjJjOGM1ZjY1NDVkZiJ9fX0=");
    public static final String HEAD_DELETE_NO =
            HeadsConfig.get("delete-no", "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNWE2Nzg3YmEzMjU2NGU3YzJmM2EwY2U2NDQ5OGVjYmIyM2I4OTg0NWU1YTY2YjVjZWM3NzM2ZjcyOWVkMzcifX19");
    public static final String HEAD_HIDE_ANCHOR =
            HeadsConfig.get("hide-anchor", "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvODI1MzBmMzAxZDFhNDA2NDcyNjEzY2UzNTgwNzgwYTUzYmFmZGI2MWM4NzE0ZGQzNWRmYTU0NDQwYmFhMjE2YiJ9fX0=");
    public static final String HEAD_MOVE_ANCHOR =
            HeadsConfig.get("move-anchor", "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYTZjYjhkM2UxZjgwNTNiN2Q1ODFkMzg5YjljYmY2MTI2Y2RkYjJjYjc1NDQ5N2U1NWYxY2FmNGI4ODU1YTMifX19");
    public static final String HEAD_PROMOTE =
            HeadsConfig.get("promote", "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMzA0MGZlODM2YTZjMmZiZDJjN2E5YzhlYzZiZTUxNzRmZGRmMWFjMjBmNTVlMzY2MTU2ZmE1ZjcxMmUxMCJ9fX0=");
    public static final String HEAD_DEMOTE =
            HeadsConfig.get("demote", "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNzQzNzM0NmQ4YmRhNzhkNTI1ZDE5ZjU0MGE5NWU0ZTc5ZGFlZGE3OTVjYmM1YTEzMjU2MjM2MzEyY2YifX19");

    // gui-gen-5: пагинация (только 54-слотовые меню, слоты 36/44)
    public static final String HEAD_ARROW_LEFT =
            HeadsConfig.get("arrow-left", "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvODRkZjJjZWZhZDQ4YzEwMDYzZDczNTM5OWY5MDRmYWE0NjA4ZmQ0NjZkZWYxZGU5ZTU1YjFhMzY2NWUzODYwMyJ9fX0=");
    public static final String HEAD_ARROW_RIGHT =
            HeadsConfig.get("arrow-right", "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOWM4YzJhMDExYmU4ZTI2NDk4YjAzNmJjNDA3OTc3NDA4ODczYTYxYTc0MjYxMmM0OTdhMjI1MzU5YTMwYjRjZDMifX19");

    /**
     * Текстура «зелёного стекла» для голов-дисплеев предпросмотра границ привата
     * (см. {@code task.BlockPreviewTask}, {@code task.BorderDisplayTask}).
     */
    public static final String PREVIEW_GLASS_GREEN =
            HeadsConfig.get("preview-glass-green", "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNjQ5NjAxMTMwNzNiNTIwNDRkNzA3OTk2NTQxOTYyMTkyMjMxOGE5ZTk5ZDc2NzE3MGIxNDI4ZGVkNDhjN2NlNSJ9fX0=");

    public static final String HEAD_BORDER =
            HeadsConfig.get("border", "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMTRiZTVhN2NlMWE4YjVlYjQzNzRhYjhkOTM3MTQ0N2MzMjhhNWM4ZDI3NjI2YmE3NjE3NDYwY2NlNDcyNmNhNSJ9fX0=");
}
