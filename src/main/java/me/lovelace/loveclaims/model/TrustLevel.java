package me.lovelace.loveclaims.model;

public enum TrustLevel {
    NONE,
    ACCESS,    // Интеракт (двери, кнопки, люки)
    CONTAINER, // Использование сундуков, печек
    BUILD,     // Установка и разрушение блоков
    MANAGER,   // Может открывать GUI привата и добавлять участников
    OWNER      // Полные права
}
