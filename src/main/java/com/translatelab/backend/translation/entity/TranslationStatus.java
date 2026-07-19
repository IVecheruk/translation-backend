package com.translatelab.backend.translation.entity;

public enum TranslationStatus {
    PENDING, // Задание создано и ожидает worker
    PROCESSING, // ML-сервис начал обработку
    DONE, // Результат успешно сохранен
    FAILED // Обработка завершилась ошибкой
}
