package com.example.bookserver.books.dto;

import java.util.List;

public record FacetCountsDto(List<FacetBucketDto<String>> langs,
                             List<FacetBucketDto<Integer>> years,
                             List<GenreFacetBucketDto> genres) {
}
