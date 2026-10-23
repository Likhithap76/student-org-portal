package com.studentorgs.portal.common.paging;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

class PageRequestsTest {

    @Test
    void of_sizeAboveMax_capsAt100() {
        assertThat(PageRequests.of(0, 500, null).getPageSize()).isEqualTo(100);
    }

    @Test
    void of_nullsAndNegatives_usesSafeDefaults() {
        PageRequest request = PageRequests.of(-3, null, null);
        assertThat(request.getPageNumber()).isZero();
        assertThat(request.getPageSize()).isEqualTo(PageRequests.DEFAULT_SIZE);
        assertThat(request.getSort().isUnsorted()).isTrue();
    }

    @Test
    void of_sizeZero_usesOne() {
        assertThat(PageRequests.of(2, 0, Sort.by("id")).getPageSize()).isEqualTo(1);
    }

    @Test
    void from_page_mapsContentAndMetadata() {
        var page = new PageImpl<>(List.of(1, 2), PageRequest.of(1, 2), 5);

        PageResponse<String> response = PageResponse.from(page, i -> "n" + i);

        assertThat(response.content()).containsExactly("n1", "n2");
        assertThat(response.page()).isEqualTo(1);
        assertThat(response.size()).isEqualTo(2);
        assertThat(response.totalElements()).isEqualTo(5);
        assertThat(response.totalPages()).isEqualTo(3);
        assertThat(response.last()).isFalse();
    }
}
