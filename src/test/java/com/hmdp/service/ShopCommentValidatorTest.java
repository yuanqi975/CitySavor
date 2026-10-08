package com.hmdp.service;

import com.hmdp.dto.ShopCommentCreateRequest;
import com.hmdp.service.impl.ShopCommentValidator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ShopCommentValidatorTest {

    @Test
    void acceptsRatingsFromOneToFiveAndTrimmedContent() {
        ShopCommentCreateRequest request = new ShopCommentCreateRequest();
        request.setRating(5);
        request.setContent("  环境很好，服务也很快  ");

        assertDoesNotThrow(() -> ShopCommentValidator.validate(request));
    }

    @Test
    void rejectsInvalidRatingOrBlankContent() {
        ShopCommentCreateRequest request = new ShopCommentCreateRequest();
        request.setRating(0);
        request.setContent("不错");
        assertThrows(IllegalArgumentException.class, () -> ShopCommentValidator.validate(request));

        request.setRating(5);
        request.setContent("   ");
        assertThrows(IllegalArgumentException.class, () -> ShopCommentValidator.validate(request));
    }

    @Test
    void rejectsContentLongerThanFiveHundredCharacters() {
        ShopCommentCreateRequest request = new ShopCommentCreateRequest();
        request.setRating(3);
        request.setContent(new String(new char[501]).replace('\0', '好'));

        assertThrows(IllegalArgumentException.class, () -> ShopCommentValidator.validate(request));
    }
}
