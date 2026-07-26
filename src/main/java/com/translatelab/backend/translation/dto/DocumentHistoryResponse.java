package com.translatelab.backend.translation.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Objects;

public record DocumentHistoryResponse(

        List<DocumentHistoryItemResponse> items,

        int page,

        int size,

        @JsonProperty("total_elements")
        long totalElements,

        @JsonProperty("total_pages")
        int totalPages,

        boolean first,

        boolean last
) {

     public DocumentHistoryResponse {
         Objects.requireNonNull(
                 items,
                 "Список заданий не должен быть null"
         );

         if (page < 0) {
             throw new IllegalArgumentException(
                     "Номер страницы не должен быть отрицательным"
             );
         }

         if (size <= 0) {
             throw new IllegalArgumentException(
                     "Размер страницы должен быть положительным"
             );
         }

         if (totalElements < 0) {
             throw new IllegalArgumentException(
                     "Общее количество элементов не должно быть отрицательным"
             );
         }

         if (totalPages < 0) {
             throw new IllegalArgumentException(
                     "Общее количество страниц не должно быть отрицательным"
             );
         }

         items = List.copyOf(items);
     }
}